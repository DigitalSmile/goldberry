package dev.goldberry.mic.source;

/// The shape of a stream of samples: how many frames a second, and how many
/// channels to a frame. Samples are always 32-bit float, interleaved, nominally
/// between -1 and 1.
///
/// @param sampleRate frames a second
/// @param channels   samples to a frame
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public record SampleFormat(int sampleRate, int channels) {

    /// 48 kHz mono: what a voice is recorded at, and the rate every desktop's
    /// mixer runs at.
    public static final SampleFormat VOICE = new SampleFormat(48_000, 1);

    public SampleFormat {
        if (sampleRate < 8_000 || sampleRate > 384_000) {
            throw new IllegalArgumentException("a rate of " + sampleRate + " Hz");
        }
        if (channels < 1 || channels > 8) {
            throw new IllegalArgumentException(channels + " channels");
        }
    }

    /// How many frames last `nanos` nanoseconds, rounded down.
    public long framesIn(long nanos) {
        return Math.multiplyExact(nanos, (long) sampleRate) / 1_000_000_000L;
    }

    /// How long `frames` frames last, in nanoseconds.
    public long nanosOf(long frames) {
        return Math.multiplyExact(frames, 1_000_000_000L) / sampleRate;
    }
}
