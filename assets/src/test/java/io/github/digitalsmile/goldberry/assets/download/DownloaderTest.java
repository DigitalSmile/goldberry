package io.github.digitalsmile.goldberry.assets.download;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// A download that the network fails is asked for again; one the request fails
/// is not.
class DownloaderTest {

    private static final URI URL = URI.create("https://example.invalid/Inter-4.1.zip");

    /// What each attempt answers, in order: a status, or a dropped connection.
    private sealed interface Answer {
        record Status(int code, String body) implements Answer {}

        record Dropped() implements Answer {}
    }

    private final Deque<Answer> answers = new ArrayDeque<>();
    private final List<Duration> waits = new ArrayList<>();
    private int requests;

    private Downloader downloader() {
        Downloader.Transport transport = _ -> {
            requests++;
            return switch (answers.removeFirst()) {
                case Answer.Status(var code, var body) ->
                    new Downloader.Response(code, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                case Answer.Dropped() -> throw new IOException("Connection reset");
            };
        };
        return new Downloader(transport, 4, Duration.ofSeconds(2), waits::add);
    }

    private void answer(Answer... sequence) {
        answers.addAll(List.of(sequence));
    }

    @Test
    @DisplayName("a 500 is asked for again, and the second answer is the one kept")
    void aServerErrorIsRetried() throws IOException {
        answer(new Answer.Status(500, "oops"), new Answer.Status(200, "font"));

        assertArrayEquals("font".getBytes(StandardCharsets.UTF_8), downloader().readAll(URL));
        assertEquals(2, requests);
        assertEquals(List.of(Duration.ofSeconds(2)), waits);
    }

    @Test
    @DisplayName("a dropped connection is retried, and each wait doubles")
    void aDroppedConnectionIsRetried() throws IOException {
        answer(new Answer.Dropped(), new Answer.Status(503, ""), new Answer.Status(429, ""), new Answer.Status(200, "x"));

        downloader().readAll(URL);

        assertEquals(4, requests);
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8)), waits);
    }

    @Test
    @DisplayName("a 404 fails on the first attempt: a pin that names nothing will not start to")
    void aMissingAssetIsNotRetried() {
        answer(new Answer.Status(404, "Not Found"));

        var failure = assertThrows(IOException.class, () -> downloader().readAll(URL));

        assertEquals(1, requests);
        assertTrue(waits.isEmpty());
        assertTrue(failure.getMessage().contains("404"), failure.getMessage());
    }

    @Test
    @DisplayName("after every attempt fails, the failure names the count and the last cause")
    void givesUp() {
        answer(
                new Answer.Status(500, ""),
                new Answer.Status(502, ""),
                new Answer.Dropped(),
                new Answer.Status(500, ""));

        var failure = assertThrows(IOException.class, () -> downloader().readAll(URL));

        assertEquals(4, requests);
        assertTrue(failure.getMessage().contains("failed 4 times"), failure.getMessage());
        assertTrue(failure.getMessage().contains("HTTP 500"), failure.getMessage());
    }

    @Test
    @DisplayName("a copy that succeeds after a failure leaves only the good bytes")
    void copyReplaces(@TempDir Path directory) throws IOException {
        var target = directory.resolve("Inter.zip");
        Files.writeString(target, "stale and longer than the answer");
        answer(new Answer.Status(500, "partial"), new Answer.Status(200, "whole"));

        downloader().copyTo(URL, target);

        assertEquals("whole", Files.readString(target));
    }

    @Test
    @DisplayName("408, 429 and 5xx are transient; the rest are the request's")
    void whatIsTransient() {
        for (var status : List.of(408, 429, 500, 502, 503, 504)) {
            assertTrue(Downloader.isTransient(status), "status " + status);
        }
        for (var status : List.of(400, 401, 403, 404, 410)) {
            assertFalse(Downloader.isTransient(status), "status " + status);
        }
    }

    @Test
    @DisplayName("fewer than one attempt is refused")
    void atLeastOnce() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Downloader(_ -> null, 0, Duration.ZERO, _ -> {}));
    }
}
