package dev.goldberry.example.capture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import dev.goldberry.mic.analysis.Envelope;
import dev.goldberry.mic.source.SampleFormat;

/// A recorded sound: interleaved 32-bit float samples in `format`.
///
/// Written out as a WAV file of IEEE float samples, which the media module's
/// own PCM decoder plays, so a clip is played back by an ordinary
/// `MediaPlayer`.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
public final class AudioClip {

    /// WAV's format tag for IEEE float samples.
    static final int IEEE_FLOAT = 3;

    /// The bytes before the samples: the RIFF header, a 16-byte `fmt ` chunk,
    /// and the `data` chunk's header.
    static final int HEADER_BYTES = 44;

    private final float[] samples;
    private final SampleFormat format;

    /// A clip of `samples`, copied, interleaved in `format`.
    ///
    /// @throws IllegalArgumentException when the samples are not whole frames
    public AudioClip(float[] samples, SampleFormat format) {
        this.format = Objects.requireNonNull(format, "format");
        if (samples.length % format.channels() != 0) {
            throw new IllegalArgumentException(
                    samples.length + " samples are not whole frames of " + format.channels() + " channels");
        }
        this.samples = samples.clone();
    }

    /// The format the samples are in.
    public SampleFormat format() {
        return format;
    }

    /// How many frames it holds.
    public int frames() {
        return samples.length / format.channels();
    }

    /// How long it plays.
    public Duration duration() {
        return Duration.ofNanos(format.nanosOf(frames()));
    }

    /// Its outline in `buckets` buckets, for a `WaveformPlot`.
    public Envelope envelope(int buckets) {
        return Envelope.of(samples, format.channels(), buckets);
    }

    /// The clip as a WAV file's bytes.
    public byte[] wav() {
        var channels = format.channels();
        var rate = format.sampleRate();
        var dataBytes = samples.length * Float.BYTES;
        var out = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        out.put(ascii("RIFF")).putInt(HEADER_BYTES - 8 + dataBytes).put(ascii("WAVE"));
        out.put(ascii("fmt ")).putInt(16);
        out.putShort((short) IEEE_FLOAT)
                .putShort((short) channels)
                .putInt(rate)
                .putInt(rate * channels * Float.BYTES)
                .putShort((short) (channels * Float.BYTES))
                .putShort((short) Float.SIZE);
        out.put(ascii("data")).putInt(dataBytes);
        for (var sample : samples) {
            out.putFloat(sample);
        }
        return out.array();
    }

    /// Writes the clip as a WAV file at `path`, replacing what is there.
    ///
    /// @throws IOException when it cannot be written
    public void writeWav(Path path) throws IOException {
        Files.write(path, wav());
    }

    private static byte[] ascii(String tag) {
        return tag.getBytes(StandardCharsets.US_ASCII);
    }
}
