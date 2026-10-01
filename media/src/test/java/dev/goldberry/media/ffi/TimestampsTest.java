package dev.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Timestamps")
class TimestampsTest {

    private static final long NO_PTS = Long.MIN_VALUE;

    @Test
    @DisplayName("converts ticks of a time base exactly")
    void exact() {
        assertEquals(Optional.of(Duration.ofSeconds(2)), Timestamps.toDuration(96_000, 1, 48_000, NO_PTS));
        assertEquals(Optional.of(Duration.ofMillis(1500)), Timestamps.toDuration(1_500_000, 1, 1_000_000, NO_PTS));
        // 1/90000 s ticks, which do not divide a nanosecond evenly: truncated.
        assertEquals(Optional.of(Duration.ofNanos(11_111)), Timestamps.toDuration(1, 1, 90_000, NO_PTS));
    }

    @Test
    @DisplayName("does not overflow where a long multiplication would")
    void large() {
        // Ten hours in 1/90000 s ticks: ticks * 1e9 alone exceeds a long.
        var tenHours = 10L * 3600 * 90_000;
        assertEquals(Optional.of(Duration.ofHours(10)), Timestamps.toDuration(tenHours, 1, 90_000, NO_PTS));
    }

    @Test
    @DisplayName("is empty for no timestamp, a broken time base, a negative count, and centuries")
    void empty() {
        assertEquals(Optional.empty(), Timestamps.toDuration(NO_PTS, 1, 1000, NO_PTS));
        assertEquals(Optional.empty(), Timestamps.toDuration(10, 0, 1000, NO_PTS));
        assertEquals(Optional.empty(), Timestamps.toDuration(10, 1, 0, NO_PTS));
        assertEquals(Optional.empty(), Timestamps.toDuration(-1, 1, 1000, NO_PTS));
        assertEquals(Optional.empty(), Timestamps.toDuration(Long.MAX_VALUE, 1, 1, NO_PTS));
    }
}
