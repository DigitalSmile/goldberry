package dev.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.io.Source;

@DisplayName("PrefetchWindow")
class PrefetchWindowTest {

    private static final Source SOURCE = Source.of(URI.create("https://example.com/film.webm"));

    private static MediaInfo info(Optional<Duration> duration, boolean live) {
        return new MediaInfo(SOURCE, duration, List.of(), !live, live);
    }

    @Test
    @DisplayName("half a minute of the stream at its mean bitrate")
    void halfAMinute() {
        // 20 Mbit/s for ten minutes: 1.5 GB, and 75 MB in half a minute.
        var film = info(Optional.of(Duration.ofMinutes(10)), false);
        assertEquals(OptionalLong.of(75_000_000L), PrefetchWindow.bytes(film, OptionalLong.of(1_500_000_000L)));
        // 64 kbit/s for an hour: 240 KB in half a minute.
        var podcast = info(Optional.of(Duration.ofHours(1)), false);
        assertEquals(OptionalLong.of(240_000L), PrefetchWindow.bytes(podcast, OptionalLong.of(28_800_000L)));
    }

    @Test
    @DisplayName("nothing to say without a length, a duration, or for a live stream")
    void unknown() {
        var ten = Optional.of(Duration.ofSeconds(10));
        assertEquals(OptionalLong.empty(), PrefetchWindow.bytes(info(ten, false), OptionalLong.empty()));
        assertEquals(OptionalLong.empty(), PrefetchWindow.bytes(info(Optional.empty(), false), OptionalLong.of(9)));
        assertEquals(
                OptionalLong.empty(),
                PrefetchWindow.bytes(info(Optional.of(Duration.ZERO), false), OptionalLong.of(9)));
        assertEquals(OptionalLong.empty(), PrefetchWindow.bytes(info(ten, true), OptionalLong.of(1_000)));
    }
}
