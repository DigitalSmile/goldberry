package io.github.digitalsmile.goldberry.media.platform.windows;

import io.github.digitalsmile.goldberry.media.codec.Frame;

/// When each decoded audio sample plays: timed by counting samples from an
/// anchor, as the macOS audio decoder times them (ADR-0472).
///
/// The first packet after an open or a flush anchors the clock, and each chunk
/// after it is timed by the samples before it. Timing by sample count is exact,
/// where a packet's timestamp would have to be matched to output that the
/// decoder can delay. A packet whose timestamp strays from the count by more
/// than [#DISCONTINUITY_NANOS] re-anchors it: a gap in the stream.
final class SampleClock {

    /// How far a packet's timestamp may be from the sample count before it is
    /// taken as a gap in the stream.
    static final long DISCONTINUITY_NANOS = 200_000_000L;

    private int sampleRate;
    private long anchorNanos = Frame.NO_PTS;
    private long samplesSinceAnchor;

    SampleClock(int sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("a rate of " + sampleRate + " Hz");
        }
        this.sampleRate = sampleRate;
    }

    /// Takes a packet's timestamp: the anchor when there is none, and a new one
    /// when the packet strays from the count while no decoded samples are
    /// waiting to be timed by the old one.
    ///
    /// @return whether the clock was re-anchored at a gap
    boolean packet(long packetNanos, boolean nothingPending) {
        if (packetNanos == Frame.NO_PTS) {
            return false;
        }
        if (anchorNanos == Frame.NO_PTS) {
            anchor(packetNanos);
            return false;
        }
        if (nothingPending && Math.abs(packetNanos - next()) > DISCONTINUITY_NANOS) {
            anchor(packetNanos);
            return true;
        }
        return false;
    }

    /// The time of the next `frames` samples, which the clock then moves past.
    long advance(int frames) {
        var pts = next();
        samplesSinceAnchor += frames;
        return pts;
    }

    /// When the next decoded sample plays, or [Frame#NO_PTS] before any packet
    /// had a timestamp.
    long next() {
        return anchorNanos == Frame.NO_PTS
                ? Frame.NO_PTS
                : anchorNanos + samplesSinceAnchor * 1_000_000_000L / sampleRate;
    }

    /// Changes the rate the samples are counted at, from the next one on, as
    /// when the decoder's output type changes.
    void rate(int newRate) {
        if (newRate <= 0 || newRate == sampleRate) {
            return;
        }
        if (anchorNanos != Frame.NO_PTS) {
            anchor(next());
        }
        sampleRate = newRate;
    }

    /// Forgets the anchor, for a flush: the next packet sets a new one.
    void reset() {
        anchorNanos = Frame.NO_PTS;
        samplesSinceAnchor = 0;
    }

    private void anchor(long nanos) {
        anchorNanos = nanos;
        samplesSinceAnchor = 0;
    }
}
