package dev.goldberry.media.audio;

/// What an [AudioSink] plays: interleaved 32-bit float samples at one rate and
/// channel count.
///
/// One format, on purpose. The Engine converts everything to it in one
/// resampling pass, so a sink never converts.
///
/// @param sampleRate samples per second per channel
/// @param channels   the channel count, in FFmpeg's default order for it
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
public record AudioFormat(int sampleRate, int channels) {

    /// What the Engine asks for when it does not know better: CD rate is not it.
    /// Every desktop mixer runs at 48 kHz, so asking for it skips a resample in the
    /// OS.
    public static final AudioFormat DEFAULT = new AudioFormat(48_000, 2);

    public AudioFormat {
        if (sampleRate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("rate " + sampleRate + ", channels " + channels);
        }
    }

    /// The bytes one sample of every channel takes.
    public int bytesPerFrame() {
        return channels * Float.BYTES;
    }

    /// How long `samples` samples per channel play, in nanoseconds, rounded to the
    /// nearest. Rounded, not truncated, so that a position computed from a sample
    /// count lands on the value a caller wrote (`500 ms`, not `499.999999 ms`).
    public long nanos(long samples) {
        return Math.round(samples * 1e9 / sampleRate);
    }

    /// The sample index at `nanos` of stream time, rounded to the nearest: the
    /// inverse of [#nanos].
    public long samples(long nanos) {
        return Math.round(nanos * (double) sampleRate / 1e9);
    }
}
