package dev.goldberry.media.audio;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.media.Wav;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.SampleFormat;

/// The Java converter on frames written by hand: each sample format's exact
/// values, the channel arithmetic, a resampled sine's pitch and length, and the
/// capacity contract the audio thread relies on.
@DisplayName("PcmConverter")
class PcmConverterTest {

    private static final int RATE = 48_000;
    private static final double TONE = 440;
    private static final int AMPLITUDE = 12_000;
    private static final float SURROUND = (float) (1 / Math.sqrt(2));

    private final Arena arena = Arena.ofConfined();

    @AfterEach
    void closeArena() {
        arena.close();
    }

    @Test
    @DisplayName("says its rate and channels, and refuses nonsense")
    void describesItself() {
        try (var converter = new PcmConverter(RATE, 2)) {
            assertEquals(RATE, converter.rate());
            assertEquals(2, converter.channels());
        }
        assertThrows(IllegalArgumentException.class, () -> new PcmConverter(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new PcmConverter(RATE, 0));
    }

    @Test
    @DisplayName("S16: 32767 is 32767/32768, -32768 is -1, 0 is 0")
    void s16() {
        var out = convert(RATE, 1, frame(SampleFormat.S16, RATE, new double[][] {{32767, -32768, 0, 1, -1}}));
        assertArrayEquals(new float[] {32767 / 32768f, -1f, 0f, 1 / 32768f, -1 / 32768f}, out, 0f);
    }

    @Test
    @DisplayName("U8 is offset binary: 128 is 0, 0 is -1, 255 is 127/128")
    void u8() {
        var out = convert(RATE, 1, frame(SampleFormat.U8, RATE, new double[][] {{128, 0, 255, 129}}));
        assertArrayEquals(new float[] {0f, -1f, 127 / 128f, 1 / 128f}, out, 0f);
    }

    @Test
    @DisplayName("S32 and S64 divide by 2^31 and 2^63, the extremes landing exactly")
    void wideIntegers() {
        var s32 = convert(RATE, 1, frame(SampleFormat.S32, RATE, new double[][] {
            {Integer.MAX_VALUE, Integer.MIN_VALUE, 1 << 30, 0}
        }));
        assertArrayEquals(new float[] {(float) (Integer.MAX_VALUE / 0x1p31), -1f, 0.5f, 0f}, s32, 0f);
        var s64 = convert(RATE, 1, frame(SampleFormat.S64, RATE, new double[][] {
            {Long.MAX_VALUE, Long.MIN_VALUE, 1L << 62, 0}
        }));
        assertArrayEquals(new float[] {1f, -1f, 0.5f, 0f}, s64, 0f);
    }

    @Test
    @DisplayName("F32 passes through, F64 is narrowed")
    void floats() {
        var f32 = convert(RATE, 1, frame(SampleFormat.F32, RATE, new double[][] {{0.25, -0.75, 1.5}}));
        assertArrayEquals(new float[] {0.25f, -0.75f, 1.5f}, f32, 0f);
        var f64 = convert(RATE, 1, frame(SampleFormat.F64, RATE, new double[][] {{0.1, -0.2}}));
        assertArrayEquals(new float[] {(float) 0.1, (float) -0.2}, f64, 0f);
    }

    @ParameterizedTest
    @EnumSource(SampleFormat.class)
    @DisplayName("planar and interleaved give identical output, and a stereo sine comes back as itself")
    void planarMatchesInterleaved(SampleFormat format) {
        var samples = new double[2][200];
        for (var i = 0; i < 200; i++) {
            samples[0][i] = scaled(format, Wav.sineSample(RATE, i, TONE, AMPLITUDE));
            samples[1][i] = scaled(format, Wav.sineSample(RATE, i + 7, TONE, AMPLITUDE));
        }
        var out = convert(RATE, 2, frame(format, RATE, samples));
        assertEquals(400, out.length);
        var tolerance = format.bytesPerSample() == 1 ? 1 / 128f : 1e-6f;
        for (var i = 0; i < 200; i++) {
            assertEquals(Wav.sineSample(RATE, i, TONE, AMPLITUDE) / 32768f, out[2 * i], tolerance, "left " + i);
            assertEquals(
                    Wav.sineSample(RATE, i + 7, TONE, AMPLITUDE) / 32768f, out[2 * i + 1], tolerance, "right " + i);
        }
        if (!format.planar()) {
            var planar = SampleFormat.valueOf(format.name() + "_PLANAR");
            assertArrayEquals(out, convert(RATE, 2, frame(planar, RATE, samples)), 0f);
        }
    }

    @Test
    @DisplayName("5.1 to stereo: L = FL + 0.707 FC + 0.707 BL, R likewise, the LFE dropped")
    void downmix51() {
        var out =
                convert(RATE, 2, frame(SampleFormat.F32, RATE, new double[][] {{0.1}, {0.2}, {0.3}, {0.9}, {0.4}, {0.5}
                }));
        assertEquals(0.1f + SURROUND * 0.3f + SURROUND * 0.4f, out[0], 1e-6f);
        assertEquals(0.2f + SURROUND * 0.3f + SURROUND * 0.5f, out[1], 1e-6f);
    }

    @Test
    @DisplayName("7.1 adds the sides, 6.1 halves the back centre, 4.0 has no LFE, 2.1 drops its LFE")
    void downmixOthers() {
        var sevenOne = convert(RATE, 2, frame(SampleFormat.F32, RATE, new double[][] {
            {0.1}, {0.2}, {0.3}, {0.9}, {0.1}, {0.2}, {0.1}, {0.2}
        }));
        assertEquals(0.1f + SURROUND * 0.3f + SURROUND * 0.1f + SURROUND * 0.1f, sevenOne[0], 1e-6f);
        assertEquals(0.2f + SURROUND * 0.3f + SURROUND * 0.2f + SURROUND * 0.2f, sevenOne[1], 1e-6f);
        var sixOne = convert(RATE, 2, frame(SampleFormat.F32, RATE, new double[][] {
            {0.1}, {0.2}, {0.3}, {0.9}, {0.4}, {0.1}, {0.2}
        }));
        assertEquals(0.1f + SURROUND * 0.3f + 0.5f * 0.4f + SURROUND * 0.1f, sixOne[0], 1e-6f);
        assertEquals(0.2f + SURROUND * 0.3f + 0.5f * 0.4f + SURROUND * 0.2f, sixOne[1], 1e-6f);
        var fourZero = convert(RATE, 2, frame(SampleFormat.F32, RATE, new double[][] {{0.1}, {0.2}, {0.3}, {0.4}}));
        assertEquals(0.1f + SURROUND * 0.3f + 0.5f * 0.4f, fourZero[0], 1e-6f);
        assertEquals(0.2f + SURROUND * 0.3f + 0.5f * 0.4f, fourZero[1], 1e-6f);
        var twoOne = convert(RATE, 2, frame(SampleFormat.F32, RATE, new double[][] {{0.1}, {0.2}, {0.9}}));
        assertArrayEquals(new float[] {0.1f, 0.2f}, twoOne, 0f);
    }

    @Test
    @DisplayName("a downmix that overflows is clamped to [-1, 1]")
    void downmixClamps() {
        var out = convert(RATE, 2, frame(SampleFormat.F32, RATE, new double[][] {{1}, {-1}, {1}, {1}, {1}, {-1}}));
        assertArrayEquals(new float[] {1f, -1f}, out, 1e-6f);
    }

    @Test
    @DisplayName("past eight channels the first two are left and right")
    void downmixUnknownLayout() {
        var channels = new double[10][1];
        for (var c = 0; c < 10; c++) {
            channels[c][0] = (c + 1) / 100.0;
        }
        assertArrayEquals(new float[] {0.01f, 0.02f}, convert(RATE, 2, frame(SampleFormat.F32, RATE, channels)), 0f);
    }

    @Test
    @DisplayName("mono is duplicated into every channel; anything to mono is the average")
    void monoEitherWay() {
        var mono = frame(SampleFormat.F32, RATE, new double[][] {{0.5, -0.25}});
        assertArrayEquals(new float[] {0.5f, 0.5f, -0.25f, -0.25f}, convert(RATE, 2, mono), 0f);
        assertArrayEquals(
                new float[] {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.25f, -0.25f, -0.25f, -0.25f, -0.25f, -0.25f},
                convert(RATE, 6, mono),
                0f);
        var stereo = frame(SampleFormat.F32, RATE, new double[][] {{0.5, 1}, {-0.25, 0}});
        assertArrayEquals(new float[] {0.125f, 0.5f}, convert(RATE, 1, stereo), 0f);
        var fiveOne = frame(SampleFormat.F32, RATE, new double[][] {{0.6}, {0.6}, {0.6}, {0}, {0}, {0}});
        assertArrayEquals(new float[] {0.3f}, convert(RATE, 1, fiveOne), 1e-7f);
    }

    @Test
    @DisplayName("stereo to more channels: left and right in the first two, silence in the rest")
    void stereoUp() {
        var stereo = frame(SampleFormat.S16, RATE, new double[][] {{1000, 2000}, {-1000, -2000}});
        var out = convert(RATE, 6, stereo);
        assertArrayEquals(
                new float[] {1000 / 32768f, -1000 / 32768f, 0, 0, 0, 0, 2000 / 32768f, -2000 / 32768f, 0, 0, 0, 0},
                out,
                0f);
        var fiveOneToFour =
                convert(RATE, 4, frame(SampleFormat.F32, RATE, new double[][] {{0.1}, {0.2}, {0.3}, {0.4}, {0.5}, {0.6}
                }));
        assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f, 0.4f}, fiveOneToFour, 0f);
    }

    @Test
    @DisplayName("s16 at the converter's rate converts exactly, with no tail to drain")
    void sameRateIsExact() {
        var frames = 8_000;
        try (var converter = new PcmConverter(8_000, 1)) {
            var out = new ArrayList<Float>();
            for (var start = 0; start < frames; start += 1_000) {
                var frame = sine(SampleFormat.S16, 8_000, 1, start, 1_000);
                var capacity = converter.capacityFor(frame);
                assertEquals(1_000, capacity);
                var segment = arena.allocate(JAVA_FLOAT, capacity);
                var written = converter.convert(frame, segment, capacity);
                assertEquals(1_000, written);
                for (var i = 0; i < written; i++) {
                    out.add(segment.getAtIndex(JAVA_FLOAT, i));
                }
            }
            assertEquals(0, converter.drain(arena.allocate(JAVA_FLOAT, 64), 64));
            assertEquals(frames, out.size());
            for (var i = 0; i < frames; i++) {
                assertEquals(Wav.sineSample(8_000, i, TONE, AMPLITUDE) / 32768f, out.get(i), 0f, "sample " + i);
            }
        }
    }

    @Test
    @DisplayName("8 kHz to 48 kHz: six times the samples, and the sine keeps its pitch")
    void upsamples() {
        var out = resampled(8_000, RATE, 8_000, 400);
        assertEquals(RATE, out.length, "a second of 8 kHz is a second of 48 kHz");
        assertPitch(out, RATE, 1f / 32768);
    }

    @Test
    @DisplayName("48 kHz to 8 kHz: a sixth of the samples, and the sine keeps its pitch")
    void downsamples() {
        var out = resampled(RATE, 8_000, RATE, 1_024);
        assertEquals(8_000, out.length);
        assertPitch(out, 8_000, 1f / 32768);
    }

    @Test
    @DisplayName("48 kHz to 44.1 kHz: the length rounds, and the signal is continuous across frames")
    void resamplesToCdRate() {
        var out = resampled(RATE, 44_100, RATE, 1_000);
        assertTrue(Math.abs(out.length - 44_100) <= 1, "resampled to " + out.length);
        assertPitch(out, 44_100, 1f / 32768);
        // Linear interpolation never overshoots and never jumps: each step is at
        // most what the sine itself steps.
        var maxStep = (float) (2 * Math.PI * TONE / 44_100 * AMPLITUDE / 32768) * 1.01f;
        for (var i = 1; i < out.length; i++) {
            assertTrue(Math.abs(out[i] - out[i - 1]) <= maxStep, "jump at " + i + ": " + out[i - 1] + " → " + out[i]);
        }
    }

    @Test
    @DisplayName("capacity is respected: the remainder comes with the next call, then from drain")
    void holdsBackPastCapacity() {
        try (var converter = new PcmConverter(RATE, 2)) {
            var frame = sine(SampleFormat.S16, RATE, 2, 0, 1_000);
            var out = arena.allocate(JAVA_FLOAT, 2_000L * 2);
            assertEquals(1_000, converter.capacityFor(frame));
            assertEquals(300, converter.convert(frame, out, 300));
            assertSine(out, 0, 300, 2);

            var next = sine(SampleFormat.S16, RATE, 2, 1_000, 100);
            assertEquals(800, converter.capacityFor(next), "700 held and 100 incoming");
            assertEquals(800, converter.convert(next, out, 2_000));
            assertSine(out, 300, 800, 2);

            var big = sine(SampleFormat.S16, RATE, 2, 1_100, 500);
            assertEquals(0, converter.convert(big, out, 0));
            assertEquals(200, converter.drain(out, 200));
            assertSine(out, 1_100, 200, 2);
            assertEquals(300, converter.drain(out, 2_000));
            assertSine(out, 1_300, 300, 2);
            assertEquals(0, converter.drain(out, 2_000));
        }
    }

    @Test
    @DisplayName("a reset drops what was held back and the resampler's position")
    void resets() {
        try (var converter = new PcmConverter(RATE, 1)) {
            var out = arena.allocate(JAVA_FLOAT, 4_096);
            converter.convert(sine(SampleFormat.S16, 8_000, 1, 0, 100), out, 10);
            converter.reset();
            assertEquals(0, converter.drain(out, 4_096));
            var frame = sine(SampleFormat.S16, 8_000, 1, 0, 100);
            // After a reset the first output sample is the first input sample
            // again, not an interpolation from the old signal's last.
            assertTrue(converter.convert(frame, out, 4_096) > 0);
            assertEquals(Wav.sineSample(8_000, 0, TONE, AMPLITUDE) / 32768f, out.getAtIndex(JAVA_FLOAT, 0), 0f);
        }
    }

    @Test
    @DisplayName("an empty frame writes nothing")
    void emptyFrame() {
        try (var converter = new PcmConverter(RATE, 2)) {
            var empty = new AudioFrame(SampleFormat.F32, 8_000, 2, 0, List.of(arena.allocate(0)), Frame.NO_PTS);
            var out = arena.allocate(JAVA_FLOAT, 16);
            assertTrue(converter.capacityFor(empty) >= 0);
            assertEquals(0, converter.convert(empty, out, 8));
            assertEquals(0, converter.drain(out, 8));
        }
    }

    @Test
    @DisplayName("refuses an output smaller than the capacity it is told")
    void refusesSmallOutput() {
        try (var converter = new PcmConverter(RATE, 2)) {
            var frame = sine(SampleFormat.S16, RATE, 2, 0, 10);
            var out = arena.allocate(JAVA_FLOAT, 10);
            assertThrows(IllegalArgumentException.class, () -> converter.convert(frame, out, 10));
            assertThrows(IllegalArgumentException.class, () -> converter.drain(out, 10));
            assertEquals(5, converter.convert(frame, out, 5));
        }
    }

    @Test
    @DisplayName("a closed converter refuses to convert, drains nothing, and closes again quietly")
    void closed() {
        var converter = new PcmConverter(RATE, 2);
        var frame = sine(SampleFormat.S16, RATE, 2, 0, 10);
        var out = arena.allocate(JAVA_FLOAT, 64);
        converter.convert(frame, out, 4);
        converter.close();
        converter.close();
        assertThrows(IllegalStateException.class, () -> converter.convert(frame, out, 32));
        assertThrows(IllegalStateException.class, () -> converter.capacityFor(frame));
        assertEquals(0, converter.drain(out, 32));
    }

    /// `frames` frames of the test sine at `inRate`, mono S16, converted to
    /// `outRate` in frames of `frameSize` and drained.
    private float[] resampled(int inRate, int outRate, int frames, int frameSize) {
        var out = new ArrayList<Float>();
        try (var converter = new PcmConverter(outRate, 1)) {
            for (var start = 0; start < frames; start += frameSize) {
                var frame = sine(SampleFormat.S16, inRate, 1, start, Math.min(frameSize, frames - start));
                var capacity = converter.capacityFor(frame);
                var segment = arena.allocate(JAVA_FLOAT, capacity);
                var written = converter.convert(frame, segment, capacity);
                assertTrue(written <= capacity);
                for (var i = 0; i < written; i++) {
                    out.add(segment.getAtIndex(JAVA_FLOAT, i));
                }
            }
            var tail = arena.allocate(JAVA_FLOAT, 64);
            var written = converter.drain(tail, 64);
            for (var i = 0; i < written; i++) {
                out.add(tail.getAtIndex(JAVA_FLOAT, i));
            }
        }
        var result = new float[out.size()];
        for (var i = 0; i < result.length; i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    /// `signal` at `rate` is the 440 Hz test sine: its zero crossings and its
    /// RMS say so.
    private static void assertPitch(float[] signal, int rate, float tolerance) {
        var crossings = 0;
        var sum = 0d;
        for (var i = 0; i < signal.length; i++) {
            sum += signal[i] * signal[i];
            if (i > 0 && (signal[i - 1] < 0) != (signal[i] < 0)) {
                crossings++;
            }
        }
        var expected = 2 * TONE * signal.length / rate;
        assertTrue(Math.abs(crossings - expected) <= 2, crossings + " crossings, expected " + expected);
        var rms = Math.sqrt(sum / signal.length);
        var expectedRms = AMPLITUDE / 32768d / Math.sqrt(2);
        assertTrue(Math.abs(rms - expectedRms) / expectedRms < 0.02, "RMS " + rms + ", expected " + expectedRms);
        assertTrue(Math.abs(signal[0]) <= tolerance, "starts at zero: " + signal[0]);
    }

    /// `count` interleaved samples of `channels` in `out` are the test sine from
    /// sample index `from` on, as [#sine] writes it.
    private static void assertSine(MemorySegment out, int from, int count, int channels) {
        for (var i = 0; i < count; i++) {
            var expected = Wav.sineSample(RATE, from + i, TONE, AMPLITUDE) / 32768f;
            for (var c = 0; c < channels; c++) {
                assertEquals(expected, out.getAtIndex(JAVA_FLOAT, (long) i * channels + c), 0f, "sample " + (from + i));
            }
        }
    }

    /// A frame of the test sine at `rate` in `format`, the same on every
    /// channel, from sample index `from` for `count` samples.
    private AudioFrame sine(SampleFormat format, int rate, int channels, int from, int count) {
        var samples = new double[channels][count];
        for (var i = 0; i < count; i++) {
            var value = scaled(format, Wav.sineSample(rate, from + i, TONE, AMPLITUDE));
            for (var c = 0; c < channels; c++) {
                samples[c][i] = value;
            }
        }
        return frame(format, rate, samples);
    }

    /// A 16-bit sample value as `format` stores it: shifted for the wider
    /// integers, offset for U8, divided for the floats.
    private static double scaled(SampleFormat format, short value) {
        return switch (format) {
            case U8, U8_PLANAR -> (value >> 8) + 128;
            case S16, S16_PLANAR -> value;
            case S32, S32_PLANAR -> (double) ((long) value << 16);
            case S64, S64_PLANAR -> (double) ((long) value << 48);
            case F32, F32_PLANAR, F64, F64_PLANAR -> value / 32768d;
        };
    }

    /// A frame of `samples[channel][index]` stored as `format`, each value
    /// written as the format's own type.
    private AudioFrame frame(SampleFormat format, int rate, double[][] samples) {
        var channels = samples.length;
        var count = samples[0].length;
        var planes = new ArrayList<MemorySegment>();
        if (format.planar()) {
            for (var c = 0; c < channels; c++) {
                var plane = arena.allocate(format.planeSize(channels, count));
                for (var i = 0; i < count; i++) {
                    put(format, plane, i, samples[c][i]);
                }
                planes.add(plane);
            }
        } else {
            var plane = arena.allocate(format.planeSize(channels, count));
            for (var i = 0; i < count; i++) {
                for (var c = 0; c < channels; c++) {
                    put(format, plane, (long) i * channels + c, samples[c][i]);
                }
            }
            planes.add(plane);
        }
        return new AudioFrame(format, rate, channels, count, planes, Frame.NO_PTS);
    }

    private static void put(SampleFormat format, MemorySegment plane, long index, double value) {
        switch (format) {
            case U8, U8_PLANAR -> plane.set(ValueLayout.JAVA_BYTE, index, (byte) (int) value);
            case S16, S16_PLANAR -> plane.setAtIndex(ValueLayout.JAVA_SHORT, index, (short) value);
            case S32, S32_PLANAR -> plane.setAtIndex(ValueLayout.JAVA_INT, index, (int) value);
            case S64, S64_PLANAR -> plane.setAtIndex(ValueLayout.JAVA_LONG, index, (long) value);
            case F32, F32_PLANAR -> plane.setAtIndex(ValueLayout.JAVA_FLOAT, index, (float) value);
            case F64, F64_PLANAR -> plane.setAtIndex(ValueLayout.JAVA_DOUBLE, index, value);
        }
    }

    private float[] convert(int rate, int channels, AudioFrame frame) {
        try (var converter = new PcmConverter(rate, channels)) {
            var capacity = converter.capacityFor(frame);
            var out = arena.allocate(JAVA_FLOAT, (long) capacity * channels);
            var written = converter.convert(frame, out, capacity);
            var result = new float[written * channels];
            MemorySegment.copy(out, JAVA_FLOAT, 0, result, 0, result.length);
            return result;
        }
    }
}
