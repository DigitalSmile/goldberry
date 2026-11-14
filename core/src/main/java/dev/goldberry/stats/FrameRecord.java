package dev.goldberry.stats;

/// One frame, kept: what [FrameSample#copy] returns, and what a test builds to
/// feed whatever reads frames.
///
/// The components are [FrameSample]'s, in its order.
///
/// Read more: [Measuring](https://goldberry.dev/docs/performance/measuring.html#per-frame-timings-at-trace).
public record FrameRecord(
        long number,
        long finishedNanos,
        long intervalNanos,
        long paintNanos,
        long buildNanos,
        long styleNanos,
        long layoutNanos,
        long rasterNanos,
        long lateRefreshes,
        boolean composited,
        long uploadNanos,
        long acquireNanos,
        long submitNanos,
        long layerNanos,
        long uploadBytes)
        implements FrameSample {

    @Override
    public FrameRecord copy() {
        return this;
    }
}
