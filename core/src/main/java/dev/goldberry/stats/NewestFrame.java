package dev.goldberry.stats;

/// The [FrameSample] a [FrameRing] hands its window's frame listeners: one
/// object, reading whichever frame the ring recorded last.
final class NewestFrame implements FrameSample {

    private final FrameRing ring;

    NewestFrame(FrameRing ring) {
        this.ring = ring;
    }

    @Override
    public long number() {
        return ring.count();
    }

    @Override
    public long finishedNanos() {
        return ring.newest(FrameRing.Column.FINISHED);
    }

    @Override
    public long intervalNanos() {
        return ring.newestInterval();
    }

    @Override
    public long paintNanos() {
        return ring.newest(FrameRing.Column.PAINT);
    }

    @Override
    public long buildNanos() {
        return ring.newest(FrameRing.Column.BUILD);
    }

    @Override
    public long styleNanos() {
        return ring.newest(FrameRing.Column.STYLE);
    }

    @Override
    public long layoutNanos() {
        return ring.newest(FrameRing.Column.LAYOUT);
    }

    @Override
    public long rasterNanos() {
        return ring.newest(FrameRing.Column.RASTER);
    }

    @Override
    public long lateRefreshes() {
        return ring.newest(FrameRing.Column.LATE);
    }

    @Override
    public boolean composited() {
        return ring.newestComposited();
    }

    @Override
    public long uploadNanos() {
        return ring.newest(FrameRing.Column.UPLOAD);
    }

    @Override
    public long acquireNanos() {
        return ring.newest(FrameRing.Column.ACQUIRE);
    }

    @Override
    public long submitNanos() {
        return ring.newest(FrameRing.Column.SUBMIT);
    }

    @Override
    public long layerNanos() {
        return ring.newest(FrameRing.Column.LAYERS);
    }

    @Override
    public long uploadBytes() {
        return ring.newest(FrameRing.Column.UPLOAD_BYTES);
    }

    @Override
    public String toString() {
        return copy().toString();
    }
}
