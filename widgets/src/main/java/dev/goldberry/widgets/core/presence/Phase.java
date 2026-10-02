package dev.goldberry.widgets.core.presence;

/// Where something is in its arrival or its departure: entering, leaving, or
/// settled.
///
/// A `tab`, a `carousel` slide, a `collapse` body, a `dialog` and a `message`
/// all arrive and leave the same way, and this is the state that says how far
/// along they are. A widget's `render` asks [#progressAt(double)] with the frame
/// clock and draws the fraction it answers.
///
/// ## Why this is not a transition
///
/// Everything else that moves in this catalogue moves *between two styles the
/// cascade resolved*, which the renderer interpolates. Something arriving has no
/// two styles: its element did not exist last frame, and the first frame of a
/// newly built element starts nothing. Something leaving is worse — a tab's
/// application has already dropped it from its list, so without something
/// holding on there is nothing left to animate.
///
/// So this is `spinner`'s shape instead: a **function of the frame clock**,
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
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html#motion).
public final class Phase {

    /// How long an arrival or a departure takes.
    ///
    /// The design system's `base` duration, the one for something entering or
    /// leaving the layout — the same 160ms a `--gb-motion-base` transition uses.
    /// A constant rather than a token because a clock-driven animation cannot
    /// read a `transition` declaration: it is not one.
    public static final double DURATION_MILLIS = 160;

    /// How long *this* phase takes, which is [#DURATION_MILLIS] unless somebody
    /// said otherwise.
    ///
    /// A field rather than the constant everywhere, because the design system
    /// gives an entrance and an exit different durations: a `message` comes in
    /// at `base` and goes out at `fast`, and a departure that took as long as an
    /// arrival would make dismissing something feel like a negotiation. Every
    /// other phase in the catalogue leaves this alone.
    private final double duration;

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
        this(kind, DURATION_MILLIS);
    }

    /// A phase of a chosen length — see [#duration].
    ///
    /// @throws IllegalArgumentException if the duration is not positive and finite
    public Phase(Kind kind, double durationMillis) {
        if (!Double.isFinite(durationMillis) || durationMillis <= 0) {
            throw new IllegalArgumentException(
                    "a phase takes a positive, finite number of milliseconds, not " + durationMillis);
        }
        this.kind = kind;
        this.duration = durationMillis;
    }

    /// How long this phase takes, in milliseconds.
    public double duration() {
        return duration;
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
    /// one: a golden image of a half-finished arrival is impossible otherwise,
    /// and a virtual clock is what makes it possible.
    public double progressAt(double now) {
        if (kind == Kind.SETTLED) {
            return 1;
        }
        if (Double.isNaN(startedAt)) {
            startedAt = now;
        }
        var elapsed = now - startedAt;
        if (elapsed >= duration) {
            if (kind == Kind.ENTERING) {
                kind = Kind.SETTLED;
            }
            return 1;
        }
        return Math.max(0, elapsed / duration);
    }

    /// Whether this phase still has frames to draw.
    public boolean isRunning() {
        return kind != Kind.SETTLED;
    }

    /// Whether a departure has finished, so the thing may be dropped.
    public boolean hasDeparted(double now) {
        return kind == Kind.LEAVING && !Double.isNaN(startedAt) && now - startedAt >= duration;
    }

    /// Ends the phase immediately — what reduced motion does to both of them.
    public void skip() {
        if (kind == Kind.ENTERING) {
            kind = Kind.SETTLED;
        }
    }
}
