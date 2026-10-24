package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.io.Source;

/// A player stepping through the chapters of the tone fixture: Intro at 0,
/// Middle at 0.3 s and Coda at 0.7 s, paused so every step lands where it was
/// sent and stays there.
@DisplayName("MediaPlayer chapters")
@Timeout(60)
class MediaPlayerChaptersTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final Duration MIDDLE = Duration.ofMillis(300);
    private static final Duration CODA = Duration.ofMillis(700);

    private MediaPlayer player;

    @BeforeEach
    void open() {
        MediaNativesRequirement.enforce();
        player = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new MediaPlayerTest.MemoryProtocol(ClipFixtures.bytes("tone-chapters.mkv"))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///tone-chapters.mkv")));
        await(status -> status.state().hasMedia());
        player.pause();
        await(status -> status.state() == PlaybackState.PAUSED);
    }

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    private PlayerStatus await(Predicate<PlayerStatus> condition) {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            var status = player.status();
            if (condition.test(status)) {
                return status;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("timed out; last status " + player.status());
    }

    private void awaitAt(Duration position) {
        await(status -> status.position().equals(position));
    }

    @Test
    @DisplayName("the status carries the chapters, and the one playing")
    void inTheStatus() {
        var info = player.status().info().orElseThrow();
        assertEquals(
                List.of(Optional.of("Intro"), Optional.of("Middle"), Optional.of("Coda")),
                info.chapters().stream().map(Chapter::title).toList());
        assertEquals(Optional.of("Intro"), info.chapterAt(Duration.ZERO).flatMap(Chapter::title));
    }

    @Test
    @DisplayName("next goes to each start in turn, and says so when there is none after the last")
    void next() {
        player.seek(Duration.ZERO);
        awaitAt(Duration.ZERO);
        assertTrue(player.nextChapter());
        awaitAt(MIDDLE);
        assertTrue(player.nextChapter());
        awaitAt(CODA);
        assertFalse(player.nextChapter(), "Coda is the last");
        assertEquals(CODA, player.status().position(), "and nothing moved");
    }

    @Test
    @DisplayName("previous, just into a chapter, goes to the one before; in the first, to its start")
    void previous() {
        player.seek(CODA.plus(Duration.ofMillis(100)));
        awaitAt(CODA.plus(Duration.ofMillis(100)));
        assertTrue(player.previousChapter());
        awaitAt(MIDDLE);
        assertTrue(player.previousChapter());
        awaitAt(Duration.ZERO);
        assertTrue(player.previousChapter(), "in the first, back to its own start");
        awaitAt(Duration.ZERO);
    }

    @Test
    @DisplayName("a source without chapters has nowhere to step")
    void none() {
        player.close();
        player = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new MediaPlayerTest.MemoryProtocol(ClipFixtures.bytes("tone-flac.mkv"))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///tone-flac.mkv")));
        await(status -> status.state().hasMedia());
        assertEquals(List.of(), player.status().info().orElseThrow().chapters());
        assertFalse(player.nextChapter());
        assertFalse(player.previousChapter());
    }
}
