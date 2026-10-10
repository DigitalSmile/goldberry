package dev.goldberry.media.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The values the Decoder SPI is written in. None of them needs FFmpeg.
@DisplayName("Decoder SPI values")
class SpiValuesTest {

    @Nested
    @DisplayName("Rational")
    class RationalTest {

        @Test
        @DisplayName("converts ticks to nanoseconds exactly, and back")
        void exact() {
            var ms = new Rational(1, 1000);
            assertEquals(1_500_000_000L, ms.toNanos(1500));
            assertEquals(1500, ms.fromNanos(1_500_000_000L));
            assertEquals(Duration.ofSeconds(2), new Rational(1, 48_000).toDuration(96_000));
            assertEquals(11_111, new Rational(1, 90_000).toNanos(1)); // truncated
        }

        @Test
        @DisplayName("does not overflow where a long multiplication would, and saturates past a long")
        void large() {
            assertEquals(Duration.ofHours(10).toNanos(), new Rational(1, 90_000).toNanos(10L * 3600 * 90_000));
            assertEquals(Long.MAX_VALUE, new Rational(1000, 1).toNanos(Long.MAX_VALUE / 2));
        }

        @Test
        @DisplayName("refuses a time base that is not positive")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> new Rational(0, 1));
            assertThrows(IllegalArgumentException.class, () -> new Rational(1, -1));
        }
    }

    @Test
    @DisplayName("a sample format knows its planes and plane size")
    void sampleFormat() {
        assertEquals(1, SampleFormat.S16.planes(6));
        assertEquals(6, SampleFormat.F32_PLANAR.planes(6));
        assertEquals(2L * 100 * 2, SampleFormat.S16.planeSize(2, 100));
        assertEquals(4L * 100, SampleFormat.F32_PLANAR.planeSize(2, 100));
    }

    @Nested
    @DisplayName("AudioFrame")
    class AudioFrameTest {

        @Test
        @DisplayName("checks the plane count and that each plane holds the samples")
        void checks() {
            try (var arena = Arena.ofConfined()) {
                var plane = arena.allocate(400);
                new AudioFrame(SampleFormat.S16, 48_000, 2, 100, List.of(plane), Frame.NO_PTS);
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AudioFrame(SampleFormat.S16, 48_000, 2, 101, List.of(plane), 0));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AudioFrame(SampleFormat.F32_PLANAR, 48_000, 2, 10, List.of(plane), 0));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AudioFrame(SampleFormat.S16, 0, 2, 10, List.of(plane), 0));
            }
        }
    }

    @Test
    @DisplayName("a video frame has as many planes and strides as its format")
    void videoFrame() {
        try (var arena = Arena.ofConfined()) {
            var y = arena.allocate(16);
            var uv = arena.allocate(8);
            var frame = new VideoFrame(
                    PixelFormat.NV12, 4, 4, List.of(y, uv), List.of(4, 4), VideoFrame.ColorMatrix.BT709, false, 0);
            assertEquals(2, frame.planes().size());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new VideoFrame(
                            PixelFormat.I420,
                            4,
                            4,
                            List.of(y, uv),
                            List.of(4, 4),
                            VideoFrame.ColorMatrix.BT709,
                            false,
                            0));
        }
    }

    @Test
    @DisplayName("a packet runs its release once, however often it is closed, and converts its pts")
    void packet() {
        var releases = new AtomicInteger();
        var packet = Packet.owning(
                MemorySegment.NULL, 0, 480, 480, 960, true, new Rational(1, 48_000), releases::incrementAndGet);
        assertEquals(10_000_000L, packet.ptsNanos());
        packet.close();
        packet.close();
        assertEquals(1, releases.get());
        var borrowed = Packet.of(MemorySegment.NULL, 0, Packet.NO_TIMESTAMP, 0, 0, false, Rational.MICROSECONDS);
        assertEquals(Frame.NO_PTS, borrowed.ptsNanos());
        borrowed.close();
    }

    @Test
    @DisplayName("received answers are switched over exhaustively")
    void received() {
        for (var answer : List.of(Received.NEEDS_INPUT, Received.ENDED)) {
            var name = switch (answer) {
                case Received.Decoded _ -> "frame";
                case Received.NeedsInput _ -> "input";
                case Received.Ended _ -> "end";
            };
            assertTrue(name.equals("input") || name.equals("end"));
        }
    }
}
