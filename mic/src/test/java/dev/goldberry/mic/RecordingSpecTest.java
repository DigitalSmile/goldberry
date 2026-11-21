package dev.goldberry.mic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.mic.source.SampleFormat;

@DisplayName("RecordingSpec")
class RecordingSpecTest {

    @Test
    @DisplayName("a voice is 48 kHz mono in 10 ms blocks with two seconds kept")
    void voice() {
        var voice = RecordingSpec.voice();
        assertAll(
                () -> assertEquals(SampleFormat.VOICE, voice.format()),
                () -> assertEquals(480, voice.blockFrames()),
                () -> assertEquals(96_000, voice.bufferFrames()),
                () -> assertEquals(960, voice.withBlock(Duration.ofMillis(20)).blockFrames()));
    }

    @Test
    @DisplayName("refuses an empty block, a block over a second, and a buffer smaller than a block")
    void refuses() {
        assertThrows(
                IllegalArgumentException.class, () -> new RecordingSpec(SampleFormat.VOICE, 0, Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RecordingSpec(SampleFormat.VOICE, 48_001, Duration.ofSeconds(9)));
        assertThrows(
                IllegalArgumentException.class, () -> new RecordingSpec(SampleFormat.VOICE, 480, Duration.ofMillis(5)));
        assertThrows(IllegalArgumentException.class, () -> new SampleFormat(4_000, 1));
        assertThrows(IllegalArgumentException.class, () -> new SampleFormat(48_000, 0));
    }
}
