package dev.goldberry.mic.analysis;

import dev.goldberry.mic.source.SampleBlock;
import dev.goldberry.mic.source.SampleListener;

/// Keeps the [Envelope] of the last `buckets × framesPerBucket` frames heard,
/// their channels averaged: a waveform that scrolls.
///
/// Fed on the source's thread and read from any other. Buckets not yet heard
/// read as silence.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class EnvelopeTracker implements SampleListener {

    private final int framesPerBucket;
    private final float[] lowest;
    private final float[] highest;
    private int next;
    private int filled;
    private float low = Float.POSITIVE_INFINITY;
    private float high = Float.NEGATIVE_INFINITY;

    /// A tracker of `buckets` buckets of `framesPerBucket` frames each.
    public EnvelopeTracker(int buckets, int framesPerBucket) {
        if (buckets < 1 || framesPerBucket < 1) {
            throw new IllegalArgumentException(buckets + " buckets of " + framesPerBucket + " frames");
        }
        this.framesPerBucket = framesPerBucket;
        this.lowest = new float[buckets];
        this.highest = new float[buckets];
    }

    @Override
    public synchronized void onSamples(SampleBlock block) {
        for (var frame = 0; frame < block.frames(); frame++) {
            var sample = block.mono(frame);
            low = Math.min(low, sample);
            high = Math.max(high, sample);
            if (++filled == framesPerBucket) {
                lowest[next] = low;
                highest[next] = high;
                next = (next + 1) % lowest.length;
                filled = 0;
                low = Float.POSITIVE_INFINITY;
                high = Float.NEGATIVE_INFINITY;
            }
        }
    }

    /// The buckets finished so far, oldest first.
    public synchronized Envelope envelope() {
        var buckets = lowest.length;
        var low = new float[buckets];
        var high = new float[buckets];
        for (var index = 0; index < buckets; index++) {
            low[index] = lowest[(next + index) % buckets];
            high[index] = highest[(next + index) % buckets];
        }
        return new Envelope(low, high);
    }
}
