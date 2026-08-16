package io.github.digitalsmile.goldberry.backend.sdl3;

import java.time.Duration;

/// Holds a frame back until the display could plausibly want it.
///
/// The frame loop asks for a repaint from inside the painter, so left alone it
/// runs as fast as `present` will return. Measured, that is ~105–145 fps into a
/// 59.96 Hz panel: two frames in five are rasterized, uploaded to a texture, and
/// discarded without ever being scanned out (ADR-0046, ADR-0047). A frame nobody
/// sees costs its paint *and* its present, which is more than anything left to
/// win inside either half.
///
/// The correct pacer is the display's own vertical blank, and
/// [Sdl3Backend#VSYNC_PROPERTY] asks SDL for exactly that. Where the GL stack
/// honours it, this class has nothing to do — frames arrive slower than the
/// interval and every one is emitted immediately. Where it does not — a
/// virtualized driver, `llvmpipe`, a compositor with a deep swapchain — this is
/// the backstop, and it needs a number nobody can guess, which is why it is off
/// unless asked for.
///
/// Pure and clock-injected: every decision is a function of the nanosecond
/// stamps handed in, so the interesting cases are testable without a display.
final class FramePacer {

    /// Frames per second to hold the loop to, overriding the display's own rate.
    ///
    /// Rarely needed now that the rate is read from the display; it exists for
    /// measuring a deliberately unpaced loop (`0`) and for pinning a rate a
    /// driver reports wrongly.
    static final String RATE_PROPERTY = "goldberry.frame.rate";

    /// Nanoseconds between frames, or zero when unpaced.
    private long intervalNanos;

    /// Whether [#RATE_PROPERTY] set the interval. An explicit rate is never
    /// overwritten by what the display reports — that is the point of setting it.
    private final boolean explicit;

    /// When the last frame was handed out. Zero until the first one, so the
    /// first frame is never delayed — it is the one the start-up timeline
    /// measures.
    private long lastFrameAt;

    private boolean started;

    FramePacer(long intervalNanos) {
        this(intervalNanos, true);
    }

    private FramePacer(long intervalNanos, boolean explicit) {
        this.intervalNanos = Math.max(0L, intervalNanos);
        this.explicit = explicit;
    }

    /// Nanoseconds between frames at `fps`, or zero for a rate that cannot pace
    /// anything.
    static long intervalForRate(double fps) {
        return fps > 0 ? (long) (1_000_000_000L / fps) : 0L;
    }

    /// Reads the pacer's configuration.
    ///
    /// Without [#RATE_PROPERTY] the pacer starts unpaced and waits to be told the
    /// display's rate by [#useDisplayRate]. It does not default to a guess: a
    /// toolkit that capped a 144 Hz panel at 60 because it never asked would be a
    /// regression dressed as an optimization.
    ///
    /// A rate that will not parse is ignored rather than fatal: a malformed
    /// tuning flag should not stop a window opening.
    static FramePacer fromProperties() {
        var raw = System.getProperty(RATE_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return new FramePacer(0L, false);
        }
        try {
            return new FramePacer(intervalForRate(Double.parseDouble(raw.trim())), true);
        } catch (NumberFormatException e) {
            return new FramePacer(0L, false);
        }
    }

    /// Adopts the rate the display reports, unless [#RATE_PROPERTY] already said
    /// otherwise.
    ///
    /// `hz` of zero means the platform would not say — some drivers never fill
    /// `refresh_rate` in — and leaves the pacer as it was rather than stalling
    /// the loop on a divide by zero.
    ///
    /// @return whether this call changed the interval
    boolean useDisplayRate(double hz) {
        if (explicit) {
            return false;
        }
        var updated = intervalForRate(hz);
        if (updated == intervalNanos) {
            return false;
        }
        intervalNanos = updated;
        return true;
    }

    /// Whether the interval came from [#RATE_PROPERTY] rather than the display.
    boolean isExplicit() {
        return explicit;
    }

    /// Whether this pacer does anything at all.
    boolean isPacing() {
        return intervalNanos > 0;
    }

    /// How long until a frame requested now could be emitted.
    ///
    /// Zero means "now", and is what an unpaced pacer and a first frame both
    /// return. Never negative: a caller turning this into a timeout must not be
    /// handed a number that means "wait forever".
    long nanosUntilDue(long nowNanos) {
        if (!isPacing() || !started) {
            return 0L;
        }
        var elapsed = nowNanos - lastFrameAt;
        return elapsed >= intervalNanos ? 0L : intervalNanos - elapsed;
    }

    /// Whether a frame requested now may be emitted.
    boolean isDue(long nowNanos) {
        return nanosUntilDue(nowNanos) == 0L;
    }

    /// Records that a frame was handed out at `nowNanos`.
    ///
    /// Stamped when the frame is *emitted* rather than when it is presented, so
    /// the interval measures paint-start to paint-start. Stamping at present
    /// would add the frame's own cost to every gap and pace the loop slower than
    /// asked.
    void frameEmitted(long nowNanos) {
        lastFrameAt = nowNanos;
        started = true;
    }

    /// The wait a pump should use: the caller's timeout, shortened if a frame
    /// comes due first.
    ///
    /// Without this the loop would defer a frame and then sleep in
    /// `SDL_WaitEventTimeout` until something unrelated woke it — on an idle
    /// window, up to the event loop's one-second heartbeat. The frame would be
    /// held for a second rather than for the rest of its interval.
    Duration capWait(Duration timeout, boolean framePending, long nowNanos) {
        if (!isPacing() || !framePending) {
            return timeout;
        }
        var until = nanosUntilDue(nowNanos);
        return until < timeout.toNanos() ? Duration.ofNanos(until) : timeout;
    }

    /// The configured interval, for logging.
    Duration interval() {
        return Duration.ofNanos(intervalNanos);
    }
}
