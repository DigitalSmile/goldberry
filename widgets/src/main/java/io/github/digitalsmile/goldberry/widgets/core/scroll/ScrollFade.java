package io.github.digitalsmile.goldberry.widgets.core.scroll;

/// When a scroll view last moved, and therefore how visible its bars are.
///
/// §2.4 asks for "overlay auto-hiding scrollbars […] fade after 800ms idle".
/// That is not a transition and cannot be one: a transition interpolates between
/// two styles the cascade resolved
/// ([ADR-0067](../../../../../../../../book/src/adr/0067-motion-is-an-overlay-on-a-frame-clock.md)),
/// and "800ms after the last time anything happened" is not a style — no selector
/// can express *when*. So this is [Phase]'s shape and `spinner`'s before it: a
/// function of the frame clock, read in `render`, which is the only place a
/// widget is handed one
/// ([ADR-0081](../../../../../../../../book/src/adr/0081-a-perpetual-loop-has-no-state.md)).
///
/// Mutable and confined to the UI thread, for [Phase]'s reason exactly: when
/// something last happened cannot be known until the frame that draws it, and a
/// record would mean rebuilding the widget tree to record the passage of time.
final class ScrollFade {

    /// How long the bars stay at full strength after the last movement — §2.4's
    /// number, and the one thing in this file that is quoted rather than derived.
    static final double IDLE_MILLIS = 800;

    /// How long they take to go once they start going.
    ///
    /// §1.7's `base`. A constant rather than a token for [Phase#DURATION_MILLIS]'s
    /// reason: a clock-driven animation cannot read a `transition` declaration,
    /// because it is not one.
    static final double FADE_MILLIS = 160;

    /// When the scroll view last moved, or `NaN` before anything has happened.
    private double movedAt = Double.NaN;

    /// That something moved since the last frame, waiting for a frame to say
    /// *when*.
    ///
    /// The wake and the clock arrive at different moments: a wheel event knows
    /// something happened and has no time, and `render` has a time and does not
    /// know what happened. This is the gap between them, and it is exactly how
    /// [Phase] stamps the beginning of an arrival.
    private boolean pending;

    /// The last frame time this was stamped with.
    ///
    /// Held here rather than on the widget because the widget is a record
    /// rebuilt every frame, and because `isAnimating()` is asked *after* `render`
    /// with no clock of its own — so the object that was handed the time is the
    /// only thing that can still remember it.
    private double now = Double.NaN;

    /// Whether the pointer is over the viewport, which holds the bars open
    /// regardless of the clock — §2.4 widens them on hover, and a bar that faded
    /// out from under a pointer resting on it would be absurd.
    private boolean held;

    /// Notes that something moved. The next frame decides when that was.
    void woken() {
        pending = true;
    }

    /// Gives the pending wake a time — called from `render`, the only place a
    /// widget is handed a clock.
    void stamp(double now) {
        this.now = now;
        if (pending) {
            pending = false;
            movedAt = now;
        }
    }

    /// Holds the bars open, or stops holding them.
    void hold(boolean value) {
        held = value;
    }

    boolean isHeld() {
        return held;
    }

    /// How opaque the bars are at `now`, from 0 to 1.
    ///
    /// Zero before anything has ever moved, which is deliberate: a window that
    /// opens on a scrollable document shows no bar until the user does something
    /// or points at it. §2.4 calls these *overlay* scrollbars, and an overlay
    /// that greets you is a reserved gutter with extra steps.
    double opacity() {
        if (held) {
            return 1;
        }
        if (Double.isNaN(movedAt)) {
            return 0;
        }
        var idle = now - movedAt;
        if (idle <= IDLE_MILLIS) {
            return 1;
        }
        var fading = (idle - IDLE_MILLIS) / FADE_MILLIS;
        return fading >= 1 ? 0 : 1 - fading;
    }

    /// Whether another frame is owed at `now` — true while the bars are still on
    /// their way out.
    ///
    /// The other half of ADR-0081's contract: without this, §1.7's idle frame
    /// loop would paint the bars once and stop, leaving them permanently at
    /// whatever opacity the last frame happened to catch.
    boolean isAnimating() {
        // A pending wake owes a frame even before it has a time, or a scroll
        // whose frame arrived early would stamp itself and then never be drawn
        // again.
        return pending || (!held && !Double.isNaN(movedAt) && !Double.isNaN(now)
                && now - movedAt < IDLE_MILLIS + FADE_MILLIS);
    }
}
