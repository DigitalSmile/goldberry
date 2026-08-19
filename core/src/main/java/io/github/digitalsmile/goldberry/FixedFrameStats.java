package io.github.digitalsmile.goldberry;

/// [FrameStats] that never change — [FrameStats#of].
record FixedFrameStats(double fps, double frameMillis, double paintMillis, long count,
        double buildMillis, double styleMillis, double layoutMillis, double rasterMillis)
        implements FrameStats {

    /// Zero, because these numbers came from nowhere and are an average of no
    /// frames. A caller reading this to mean "a window of no frames" is reading
    /// it right.
    @Override
    public int capacity() {
        return 0;
    }
}
