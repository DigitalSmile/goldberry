package dev.goldberry.media.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FrameRate")
class FrameRateTest {

    private static final FrameRate SIXTY = new FrameRate(60, 1);
    private static final FrameRate NTSC = new FrameRate(60_000, 1001);

    @Test
    @DisplayName("FFmpeg's 0/0 is an unknown rate, and a known one is kept as its fraction")
    void known() {
        assertEquals(Optional.empty(), FrameRate.known(0, 0));
        assertEquals(Optional.empty(), FrameRate.known(25, 0));
        assertEquals(Optional.of(SIXTY), FrameRate.known(60, 1));
        assertEquals("60000/1001", NTSC.toString());
    }

    @Test
    @DisplayName("refuses a rate that is not positive")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> new FrameRate(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new FrameRate(30, -1));
    }

    @Test
    @DisplayName("says pictures a second, and how long one shows, to the nanosecond")
    void perSecondAndDuration() {
        assertEquals(60.0, SIXTY.perSecond());
        assertEquals(59.94, NTSC.perSecond(), 0.001);
        assertEquals(Duration.ofNanos(16_666_666), SIXTY.frameDuration());
        assertEquals(Duration.ofNanos(16_683_333), NTSC.frameDuration());
    }

    @Test
    @DisplayName("counts the pictures in a duration, to the nearest: a loop's length in pictures")
    void framesIn() {
        assertEquals(1200, SIXTY.framesIn(Duration.ofSeconds(20)));
        assertEquals(1131, SIXTY.framesIn(Duration.ofMillis(18_850)));
        assertEquals(1199, NTSC.framesIn(Duration.ofSeconds(20)), "59.94 a second falls a picture short");
        assertEquals(0, SIXTY.framesIn(Duration.ZERO));
    }
}
