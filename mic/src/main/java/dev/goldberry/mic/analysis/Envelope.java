package dev.goldberry.mic.analysis;

/// The outline of a stretch of sound: for each bucket of frames, the lowest and
/// the highest sample in it, oldest first. What a waveform view draws.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class Envelope {

    private final float[] lowest;
    private final float[] highest;

    Envelope(float[] lowest, float[] highest) {
        this.lowest = lowest;
        this.highest = highest;
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
}
