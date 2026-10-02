package dev.goldberry.stats;

/// [FrameStats] whose numbers never change: what [FrameStats#of] returns, for a
/// test or a preview that wants a HUD to draw numbers somebody chose.
record FixedFrameStats(
        double fps,
        double frameMillis,
        double paintMillis,
        long count,
        double buildMillis,
        double styleMillis,
        double layoutMillis,
        double rasterMillis,
        double displayHertz,
        long lateFrames)
        implements FrameStats {

    /// Zero: these numbers are fixed, not averaged over frames, so there is no
    /// window to have a size.
    @Override
    public int capacity() {
        return 0;
    }
}
