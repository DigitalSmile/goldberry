package dev.goldberry.mic.source;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.FloatBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The synthetic source and its signals: the same frames however it is
/// advanced, which is what a golden image of a meter relies on.
@DisplayName("SyntheticSource")
class SyntheticSourceTest {

    private static List<float[]> record(SyntheticSource source, Duration... steps) throws Exception {
        var blocks = new ArrayList<float[]>();
        try (var _ = source.onSamples(block -> blocks.add(block.copy()))) {
            for (var step : steps) {
                source.advance(step);
            }
        }
        return blocks;
    }

    @Test
    @DisplayName("delivers whole blocks only, carrying the remainder to the next advance")
    void wholeBlocks() throws Exception {
        var source = new SyntheticSource(SampleFormat.VOICE, Signal.tone(1_000, 0.5), 480);
        var once = record(source, Duration.ofMillis(25));
        var later = record(source, Duration.ofMillis(5));
        assertAll(
                () -> assertEquals(2, once.size()),
                () -> assertEquals(1, later.size(), "the 5 ms left over and 5 ms more make a block"),
                () -> assertEquals(1_440, source.position()));
    }

    @Test
    @DisplayName("gives the same frames however the time is cut up")
    void deterministic() throws Exception {
        var signal = Signal.mix(Signal.tone(440, 0.3), new Signal.Noise(0.1, 42));
        var whole = record(new SyntheticSource(SampleFormat.VOICE, signal, 480), Duration.ofMillis(40));
        var pieces = record(
                new SyntheticSource(SampleFormat.VOICE, signal, 480),
                Duration.ofMillis(7),
                Duration.ofMillis(13),
                Duration.ofMillis(20));
        assertEquals(whole.size(), pieces.size());
        for (var index = 0; index < whole.size(); index++) {
            assertArrayEquals(whole.get(index), pieces.get(index), "block " + index);
        }
    }

    @Test
    @DisplayName("a tone peaks at its amplitude and repeats each period; every channel carries it")
    void tone() throws Exception {
        var stereo = new SampleFormat(48_000, 2);
        var source = new SyntheticSource(stereo, Signal.tone(1_000, 0.25), 48);
        var block = record(source, Duration.ofMillis(1)).getFirst();
        assertAll(
                () -> assertEquals(0f, block[0], 1e-6),
                () -> assertEquals(0.25f, block[2 * 12], 1e-6, "a quarter period in"),
                () -> assertEquals(block[2 * 12], block[2 * 12 + 1], "the right channel is the left"),
                () -> assertEquals(
                        0.25f,
                        Signal.tone(1_000, 0.25).valueAt(48 * 3_600_000L + 12, 48_000),
                        1e-5,
                        "an hour in, the phase is as precise"));
    }

    @Test
    @DisplayName("noise depends on its seed alone, and a clip plays once or loops")
    void noiseAndClip() {
        var noise = new Signal.Noise(1, 7);
        var clip = new Signal.Clip(new float[] {0.1f, 0.2f}, false);
        var loop = new Signal.Clip(new float[] {0.1f, 0.2f}, true);
        assertAll(
                () -> assertEquals(noise.valueAt(99, 48_000), new Signal.Noise(1, 7).valueAt(99, 48_000)),
                () -> assertNotEquals(noise.valueAt(99, 48_000), new Signal.Noise(1, 8).valueAt(99, 48_000)),
                () -> assertEquals(0f, clip.valueAt(2, 48_000)),
                () -> assertEquals(0.1f, loop.valueAt(2, 48_000)),
                () -> assertEquals(clip, new Signal.Clip(new float[] {0.1f, 0.2f}, false)),
                () -> assertEquals(0f, Signal.silence().valueAt(5, 48_000)));
    }

    @Test
    @DisplayName("plays a new signal from the next block, without starting over")
    void play() throws Exception {
        var source = new SyntheticSource(SampleFormat.VOICE, Signal.silence(), 480);
        record(source, Duration.ofMillis(10));
        source.play(Signal.tone(100, 1));
        var block = record(source, Duration.ofMillis(10)).getFirst();
        assertEquals(Signal.tone(100, 1).valueAt(480, 48_000), block[0]);
    }

    @Test
    @DisplayName("refuses a block of no frames, a tone of no frequency, and a block past its buffer")
    void refuses() {
        assertThrows(
                IllegalArgumentException.class, () -> new SyntheticSource(SampleFormat.VOICE, Signal.silence(), 0));
        assertThrows(IllegalArgumentException.class, () -> Signal.tone(0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SampleBlock(FloatBuffer.allocate(3), new SampleFormat(48_000, 2), 2, 0, 0));
    }
}
