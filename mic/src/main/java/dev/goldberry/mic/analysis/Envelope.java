package dev.goldberry.mic.analysis;

import java.util.Objects;

/// The outline of a stretch of sound: for each bucket of frames, the lowest and
/// the highest sample in it, oldest first. What a waveform view draws.
///
/// An [EnvelopeTracker] keeps one of the last stretch heard; [#of] makes one
/// of a whole clip, a voice message's say.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class Envelope {

    private final float[] lowest;
    private final float[] highest;

    Envelope(float[] lowest, float[] highest) {
        this.lowest = lowest;
        this.highest = highest;
    }

    /// The outline of `samples`, interleaved in `channels` channels and
    /// averaged across them, in `buckets` buckets of as near the same length
    /// as the frames allow.
    ///
    /// A clip shorter than `buckets` frames gets one bucket a frame, and
    /// nothing at all gets no buckets.
    ///
    /// @throws IllegalArgumentException if `channels` or `buckets` is below
    ///         one, or the samples are not whole frames
    public static Envelope of(float[] samples, int channels, int buckets) {
        Objects.requireNonNull(samples, "samples");
        if (channels < 1 || buckets < 1 || samples.length % channels != 0) {
            throw new IllegalArgumentException(
                    samples.length + " samples in " + channels + " channels, " + buckets + " buckets");
        }
        var frames = samples.length / channels;
        var count = Math.min(buckets, frames);
        var low = new float[count];
        var high = new float[count];
        for (var bucket = 0; bucket < count; bucket++) {
            // Long arithmetic, so an hour at 48 kHz times a few hundred buckets
            // does not wrap.
            var from = (int) ((long) bucket * frames / count);
            var to = (int) ((long) (bucket + 1) * frames / count);
            var min = Float.POSITIVE_INFINITY;
            var max = Float.NEGATIVE_INFINITY;
            for (var frame = from; frame < to; frame++) {
                var sum = 0f;
                for (var channel = 0; channel < channels; channel++) {
                    sum += samples[frame * channels + channel];
                }
                var sample = sum / channels;
                min = Math.min(min, sample);
                max = Math.max(max, sample);
            }
            low[bucket] = min;
            high[bucket] = max;
        }
        return new Envelope(low, high);
    }

    /// How many buckets.
    public int buckets() {
        return lowest.length;
    }

    /// The lowest sample in bucket `bucket`, -1 to 1.
    public float lowest(int bucket) {
        return lowest[bucket];
    }

    /// The highest sample in bucket `bucket`, -1 to 1.
    public float highest(int bucket) {
        return highest[bucket];
    }

    /// The largest magnitude in any bucket: what a view that fills its height
    /// with the clip divides by.
    public float peak() {
        var peak = 0f;
        for (var bucket = 0; bucket < lowest.length; bucket++) {
            peak = Math.max(peak, Math.max(Math.abs(lowest[bucket]), Math.abs(highest[bucket])));
        }
        return peak;
    }
}
