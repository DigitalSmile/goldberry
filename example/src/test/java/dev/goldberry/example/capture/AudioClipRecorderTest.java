package dev.goldberry.example.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.FloatBuffer;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.mic.source.SampleBlock;
import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.Signal;
import dev.goldberry.mic.source.SyntheticSource;

@DisplayName("a sound clip's recorder")
class AudioClipRecorderTest {

    @Test
    @DisplayName("keeps every block a source delivers, in order")
    void keeps() throws Exception {
        var source = SyntheticSource.of(new Signal.Tone(1_000, 0.5));
        var recorder = new AudioClipRecorder(source.format(), Duration.ofSeconds(1));
        try (var _ = source.onSamples(recorder)) {
            source.advance(Duration.ofMillis(250));
        }
        source.advance(Duration.ofMillis(250));

        var clip = recorder.clip();
        assertEquals(SampleFormat.VOICE.framesIn(Duration.ofMillis(250).toNanos()), clip.frames());
        assertEquals(Duration.ofMillis(250), recorder.recorded());
        assertEquals(0.5f, clip.envelope(1).peak(), 0.01f);
        assertFalse(recorder.full());
    }

    @Test
    @DisplayName("stops at the longest clip and says it is full")
    void full() throws Exception {
        var source = SyntheticSource.of(new Signal.Noise(0.5, 7));
        var recorder = new AudioClipRecorder(source.format(), Duration.ofMillis(100));
        try (var _ = source.onSamples(recorder)) {
            source.advance(Duration.ofMillis(95));
            assertFalse(recorder.full());
            source.advance(Duration.ofMillis(50));
        }

        assertTrue(recorder.full());
        assertEquals(
                SampleFormat.VOICE.framesIn(Duration.ofMillis(100).toNanos()),
                recorder.clip().frames());
    }

    @Test
    @DisplayName("refuses a block in another format, and a clip too short to hold a frame")
    void refuses() {
        var recorder = new AudioClipRecorder(SampleFormat.VOICE, Duration.ofSeconds(1));
        var stereo = new SampleBlock(FloatBuffer.allocate(8), new SampleFormat(48_000, 2), 4, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> recorder.onSamples(stereo));
        assertThrows(IllegalArgumentException.class, () -> new AudioClipRecorder(SampleFormat.VOICE, Duration.ZERO));
    }
}
