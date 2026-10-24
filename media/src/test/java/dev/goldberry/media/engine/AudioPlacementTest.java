package dev.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.codec.Frame;

/// Where a decoded run of audio samples goes: on from the last one, unless its
/// own time is really somewhere else.
@DisplayName("AudioPlacement")
class AudioPlacementTest {

    private static final long MILLI = 1_000_000L;

    @ParameterizedTest(name = "stated {0} µs after {1} µs follows on")
    @DisplayName("a frame within a millisecond timescale's rounding follows the last one")
    @CsvSource({
        // An Opus stream's first frame in Matroska: -7 ms + 6.5 ms of pre-skip.
        "-500, 0",
        // Its second: 13.5 ms stamped 14 ms, after a first that ended at 13.5.
        "14000, 13500",
        "13000, 13500",
        "2000, 0",
        "-2000, 0",
    })
    void roundingFollowsOn(long statedMicros, long expectedMicros) {
        assertEquals(expectedMicros * 1000, AudioPlacement.place(statedMicros * 1000, expectedMicros * 1000, false));
    }

    @Test
    @DisplayName("a frame further off than that is placed at its own time")
    void aRealGapIsKept() {
        assertEquals(42 * MILLI, AudioPlacement.place(42 * MILLI, 20 * MILLI, false));
        assertEquals(10 * MILLI, AudioPlacement.place(10 * MILLI, 20 * MILLI, false));
        assertEquals(
                20 * MILLI + AudioPlacement.JITTER_NANOS + 1,
                AudioPlacement.place(20 * MILLI + AudioPlacement.JITTER_NANOS + 1, 20 * MILLI, false));
    }

    @Test
    @DisplayName("after a seam, the stated time stands, however close, for the seam to trim or fill against")
    void aSeamResyncs() {
        assertEquals(999_500_000L, AudioPlacement.place(999_500_000L, 1_000_500_000L, true));
    }

    @Test
    @DisplayName("a frame with no time follows the last one")
    void noTimeFollowsOn() {
        assertEquals(20 * MILLI, AudioPlacement.place(Frame.NO_PTS, 20 * MILLI, false));
        assertEquals(20 * MILLI, AudioPlacement.place(Frame.NO_PTS, 20 * MILLI, true));
    }
}
