package dev.goldberry.mic.analysis;

import java.time.Duration;
import java.util.Objects;

import dev.goldberry.mic.source.SampleBlock;
import dev.goldberry.mic.source.SampleListener;

/// The levels a meter shows: the last block's, and a peak that holds for a
/// while and then falls.
///
/// Fed on the source's thread and read from any other. Time is the blocks'
/// own ([SampleBlock#nanoTime()]), so a synthetic source gives the same hold
/// and fall every run.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class LevelTracker implements SampleListener {

    /// How long a peak stands before it falls, by default.
    public static final Duration HOLD = Duration.ofMillis(1500);

    /// How fast a held peak falls once its hold is over, by default.
    public static final double FALL_DECIBELS_PER_SECOND = 20;

    /// What the tracker reports.
    ///
    /// @param levels           the last block's
    /// @param heldPeakDecibels the held peak, in dBFS
    public record Reading(Levels levels, double heldPeakDecibels) {
        public Reading {
            Objects.requireNonNull(levels, "levels");
        }
    }

    private final long holdNanos;
    private final double fallPerNano;
    private Levels last = Levels.SILENCE;
    private double held = Levels.FLOOR_DECIBELS;
    private long heldAt;
    private long now;

    /// A tracker with the default hold and fall.
    public LevelTracker() {
        this(HOLD, FALL_DECIBELS_PER_SECOND);
    }

    /// A tracker whose peak stands for `hold` and then falls at
    /// `fallDecibelsPerSecond`.
    public LevelTracker(Duration hold, double fallDecibelsPerSecond) {
        if (hold.isNegative() || !(fallDecibelsPerSecond > 0)) {
            throw new IllegalArgumentException("hold " + hold + ", fall " + fallDecibelsPerSecond);
        }
        this.holdNanos = hold.toNanos();
        this.fallPerNano = fallDecibelsPerSecond / 1e9;
    }

    @Override
    public synchronized void onSamples(SampleBlock block) {
        last = Levels.of(block);
        now = block.nanoTime();
        var peak = last.peakDecibels();
        if (peak >= current()) {
            held = peak;
            heldAt = now;
        }
    }

    /// The last block's levels and the held peak, as of that block.
    public synchronized Reading reading() {
        return new Reading(last, current());
    }

    /// Forgets every level heard.
    public synchronized void reset() {
        last = Levels.SILENCE;
        held = Levels.FLOOR_DECIBELS;
        heldAt = now;
    }

    private double current() {
        var since = now - heldAt - holdNanos;
        return since <= 0 ? held : Math.max(Levels.FLOOR_DECIBELS, held - since * fallPerNano);
    }
}
