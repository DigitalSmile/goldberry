package dev.goldberry.example.capture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.mic.source.SampleFormat;

@DisplayName("a recorded sound clip")
class AudioClipTest {

    private static final SampleFormat STEREO = new SampleFormat(8_000, 2);

    private static String tag(ByteBuffer wav, int at) {
        var bytes = new byte[4];
        wav.get(at, bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    @Test
    @DisplayName("is written as a WAV of IEEE float samples: RIFF, a 16-byte fmt chunk, then the data")
    void wav() {
        var clip = new AudioClip(new float[] {0.5f, -0.5f, 0.25f, -0.25f}, STEREO);
        var wav = ByteBuffer.wrap(clip.wav()).order(ByteOrder.LITTLE_ENDIAN);

        assertAll(
                () -> assertEquals(AudioClip.HEADER_BYTES + 16, wav.capacity()),
                () -> assertEquals("RIFF", tag(wav, 0)),
                () -> assertEquals(wav.capacity() - 8, wav.getInt(4)),
                () -> assertEquals("WAVE", tag(wav, 8)),
                () -> assertEquals("fmt ", tag(wav, 12)),
                () -> assertEquals(16, wav.getInt(16)),
                () -> assertEquals(AudioClip.IEEE_FLOAT, wav.getShort(20)),
                () -> assertEquals(2, wav.getShort(22)),
                () -> assertEquals(8_000, wav.getInt(24)),
                () -> assertEquals(8_000 * 2 * 4, wav.getInt(28), "bytes a second"),
                () -> assertEquals(8, wav.getShort(32), "bytes a frame"),
                () -> assertEquals(32, wav.getShort(34), "bits a sample"),
                () -> assertEquals("data", tag(wav, 36)),
                () -> assertEquals(16, wav.getInt(40)),
                () -> assertEquals(0.5f, wav.getFloat(44)),
                () -> assertEquals(-0.25f, wav.getFloat(56)));
    }

    @Test
    @DisplayName("says how long it is, and writes the same bytes to a file")
    void file(@TempDir Path directory) throws IOException {
        var clip = new AudioClip(new float[8_000], SampleFormat.VOICE);
        var file = directory.resolve("clip.wav");
        clip.writeWav(file);

        assertEquals(8_000, clip.frames());
        assertEquals(Duration.ofNanos(SampleFormat.VOICE.nanosOf(8_000)), clip.duration());
        assertArrayEquals(clip.wav(), Files.readAllBytes(file));
    }

    @Test
    @DisplayName("keeps a copy of its samples, outlines them, and refuses a part of a frame")
    void copies() {
        var samples = new float[] {1f, -1f, 0f, 0f};
        var clip = new AudioClip(samples, new SampleFormat(8_000, 1));
        samples[0] = 0f;

        assertEquals(1f, clip.envelope(2).highest(0));
        assertThrows(IllegalArgumentException.class, () -> new AudioClip(new float[3], STEREO));
    }
}
