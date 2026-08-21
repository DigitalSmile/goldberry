package io.github.digitalsmile.goldberry.widgets.core;

/// Where something is in its arrival or its departure — the state behind §1.7's
/// missing "overlay enter/exit lifecycle".
///
/// Written for `tabs`, which was the first widget to need one, and shared since:
/// `carousel`'s slides and `collapse`'s body arrive exactly the same way, and
/// there was never anything tab-shaped in it ([ADR-0166]). The `..Phase` it was
/// called in that package is this, unchanged.
///
/// ## Why this is not a transition
///
/// Everything else that moves in this catalog moves *between two styles the
/// cascade resolved*, which the renderer interpolates
/// ([ADR-0067](../../../../../../../../book/src/adr/0067-motion-is-an-overlay-on-a-frame-clock.md)).
/// Something arriving has no two styles: its element did not exist last frame, and
/// the first frame of a newly built element starts nothing
/// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
/// Something leaving is worse — a tab's application has already dropped it from
/// its list, so without something holding on there is nothing left to animate.
///
/// So this is `spinner`'s shape instead: a **function of the frame clock**
/// ([ADR-0081](../../../../../../../../book/src/adr/0081-a-perpetual-loop-has-no-state.md)),
/// with the one thing a spinner does not need — a beginning. The clock is read in
/// `render`, which is the only place a widget has one, and the first read is what
/// stamps [#startedAt].
///
/// ## Confined to the UI thread, and mutable on purpose
///
/// One of these belongs to one arriving thing for as long as it is on screen. It is
/// mutable because "when did this start" cannot be known until the first frame
/// that draws it, and a record would mean rebuilding the widget tree to record
/// the passage of time.
public final class Phase {

    /// How long an arrival or a departure takes.
    ///
    /// §1.7's `base`, which is the duration for "something entering or leaving the
    /// layout" — the same 160ms a `--gb-motion-base` transition uses. A constant
    /// rather than a token because a clock-driven animation cannot read a
    /// `transition` declaration: it is not one
    /// ([ADR-0109](../../../../../../../../book/src/adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)).
    public static final double DURATION_MILLIS = 160;

    /// What this phase is.
    public enum Kind {

        /// Arriving: fading up and settling into place.
        ENTERING,

        /// Leaving: fading down. Whatever is going has already been dropped by
        /// whoever owned it, and is drawn only until this finishes.
        LEAVING,

        /// Neither — the ordinary state of something that has been there a while,
        /// and the state everything is in on the first build.
        SETTLED
    }

    private Kind kind;

    /// When the current phase began, or `NaN` before its first frame.
    private double startedAt = Double.NaN;

    public Phase(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /// Starts a departure, from wherever it currently is.
    public void leave() {
        if (kind != Kind.LEAVING) {
            kind = Kind.LEAVING;
            startedAt = Double.NaN;
        }
    }

    /// How far through, `0..1`, at `now` — stamping the start on the first call.
    ///
    /// The stamp is here because `render` is the only place a widget is given the
    /// frame clock, and it must be the *renderer's* clock rather than the wall
    /// one: a golden image of a half-finished arrival is impossible otherwise
    /// (ADR-0067's argument for `Clock.virtual`).
    public double progressAt(double now) {
        if (kind == Kind.SETTLED) {
            return 1;
        }
        if (Double.isNaN(startedAt)) {
            startedAt = now;
        }
        var elapsed = now - startedAt;
        if (elapsed >= DURATION_MILLIS) {
            if (kind == Kind.ENTERING) {
                kind = Kind.SETTLED;
            }
            return 1;
        }
        return Math.max(0, elapsed / DURATION_MILLIS);
    }

    /// Whether this phase still has frames to draw.
    public boolean isRunning() {
        return kind != Kind.SETTLED;
    }

    /// Whether a departure has finished, so the thing may be dropped.
    public boolean hasDeparted(double now) {
        return kind == Kind.LEAVING && !Double.isNaN(startedAt)
                && now - startedAt >= DURATION_MILLIS;
    }

    /// Ends the phase immediately — what reduced motion does to both of them.
    public void skip() {
        if (kind == Kind.ENTERING) {
            kind = Kind.SETTLED;
        }
    }
}
