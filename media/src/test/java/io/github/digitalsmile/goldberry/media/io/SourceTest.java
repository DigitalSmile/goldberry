package io.github.digitalsmile.goldberry.media.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Source")
class SourceTest {

    @Test
    @DisplayName("a path becomes an absolute file: URI")
    void path() {
        var source = Source.of(Path.of("clip.webm"));
        assertEquals("file", source.scheme());
        assertTrue(source.uri().isAbsolute());
        assertEquals(Optional.of("clip.webm"), source.fileName());
        assertEquals(Source.DEFAULT_TIMEOUT, source.timeout());
    }

    @Test
    @DisplayName("the scheme is read in lower case")
    void scheme() {
        assertEquals(
                "https", Source.of(URI.create("HTTPS://radio.example/stream")).scheme());
    }

    @Test
    @DisplayName("has no file name when the path ends in a slash or is absent")
    void noFileName() {
        assertEquals(
                Optional.empty(),
                Source.of(URI.create("https://radio.example/")).fileName());
        assertEquals(
                Optional.empty(), Source.of(URI.create("https://radio.example")).fileName());
    }

    @Test
    @DisplayName("refuses a URI without a scheme, and a timeout that is not positive")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> Source.of(URI.create("clip.webm")));
        var source = Source.of(URI.create("https://a/b"));
        assertThrows(IllegalArgumentException.class, () -> source.withTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> source.withTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("keeps headers in the order they were added, and replaces one of the same name")
    void headers() {
        var source = Source.of(URI.create("https://a/b"))
                .withHeader("User-Agent", "brd")
                .withHeader("Accept", "*/*")
                .withHeader("User-Agent", "brd/2");
        assertEquals(
                List.of("User-Agent", "Accept"), List.copyOf(source.headers().keySet()));
        assertEquals("brd/2", source.headers().get("User-Agent"));
    }

    @Test
    @DisplayName("copies the headers it is given, and hands out a map nobody can change")
    void headersAreCopied() {
        var headers = new HashMap<String, String>();
        headers.put("A", "1");
        var source = new Source(URI.create("https://a/b"), headers, Duration.ofSeconds(1));
        headers.put("B", "2");
        assertEquals(1, source.headers().size());
        assertThrows(UnsupportedOperationException.class, () -> source.headers().put("C", "3"));
    }
}
