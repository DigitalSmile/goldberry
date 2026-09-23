package io.github.digitalsmile.goldberry.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/// 16-bit PCM WAV files, written in memory.
///
/// PCM in a RIFF header is forty-four bytes of arithmetic, so a test knows its
/// input's duration, rate, channel count and every sample value exactly without a
/// fixture corpus. The corpus of `docs/goldberry-media.md` §9 is for the codecs
/// that cannot be written by hand.
public final class Wav {

    private Wav() {}

    /// `frames` frames of silence.
    public static byte[] silence(int sampleRate, int channels, int frames) {
        return sine(sampleRate, channels, frames, 0, 0);
    }

    /// `frames` frames of a sine at `frequency` Hz and `amplitude` (0 to 32767),
    /// the same on every channel.
    public static byte[] sine(int sampleRate, int channels, int frames, double frequency, int amplitude) {
        var dataSize = frames * channels * 2;
        var buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize);
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        buffer.putShort((short) 1).putShort((short) channels).putInt(sampleRate);
        buffer.putInt(sampleRate * channels * 2)
                .putShort((short) (channels * 2))
                .putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
        for (var frame = 0; frame < frames; frame++) {
            var value = sineSample(sampleRate, frame, frequency, amplitude);
            for (var channel = 0; channel < channels; channel++) {
                buffer.putShort(value);
            }
        }
        return buffer.array();
    }

    /// `wav` with its format tag replaced: `6` is A-law, `7` is µ-law. The WAV
    /// demuxer maps the tag to a codec whether or not this build decodes it, which
    /// makes this the smallest file with a codec no decoder here plays.
    public static byte[] withFormatTag(byte[] wav, int tag) {
        var copy = wav.clone();
        copy[20] = (byte) tag;
        copy[21] = (byte) (tag >>> 8);
        return copy;
    }

    /// The sample value [#sine] writes for `frame`.
    public static short sineSample(int sampleRate, int frame, double frequency, int amplitude) {
        return (short) Math.round(amplitude * Math.sin(2 * Math.PI * frequency * frame / sampleRate));
    }
}
