package dev.goldberry.render.backend.sdl3;

/// Holds one window's frames to a rate of its own: a game's frame cap.
///
/// [FramePacer] holds the whole loop to the display, once for every window.
/// This holds a single window back further, and is the only thing that holds
/// one back at all when it presents in `MAILBOX` or `IMMEDIATE`, which do not
/// wait for the display. A cap above the display's rate on a window that does
/// wait changes nothing, since the pacer is reached first.
///
/// Stamped when a frame is emitted, paint-start to paint-start, as the pacer
/// is. Pure and clock-injected, so every case is testable without a display.
final class FrameCap {

    /// Nanoseconds between frames, or zero for no cap.
    private long intervalNanos;

    private long lastFrameAt;
    private boolean started;

    /// The cap, in frames per second; zero for none.
    private double hertz;

    /// Caps the window at `hertz` frames a second, or lifts the cap for zero.
    ///
    /// @throws IllegalArgumentException when `hertz` is negative or not a number
    void rate(double hertz) {
        if (!(hertz >= 0) || Double.isInfinite(hertz)) {
            throw new IllegalArgumentException("a frame cap of " + hertz + " frames a second");
        }
        this.hertz = hertz;
        this.intervalNanos = FramePacer.intervalForRate(hertz);
    }

    /// The cap, in frames per second; zero for none.
    double rate() {
        return hertz;
    }

    /// Whether there is a cap.
    boolean isCapped() {
        return intervalNanos > 0;
    }

    /// How long until this window may have a frame. Zero for no cap and for a
    /// first frame; never negative.
    long nanosUntilDue(long nowNanos) {
        if (!isCapped() || !started) {
            return 0L;
        }
        var elapsed = nowNanos - lastFrameAt;
        return elapsed >= intervalNanos ? 0L : intervalNanos - elapsed;
    }

    /// Whether this window may have a frame now.
    boolean isDue(long nowNanos) {
        return nanosUntilDue(nowNanos) == 0L;
    }

    /// When the next frame comes due, for counting lateness from there rather
    /// than from the request: a frame the cap held back is held, not late.
    /// Zero for no cap and before the first frame.
    long dueAt() {
        return isCapped() && started ? lastFrameAt + intervalNanos : 0L;
    }

    /// Records that this window's frame was emitted at `nowNanos`.
    void frameEmitted(long nowNanos) {
        lastFrameAt = nowNanos;
        started = true;
    }
}
