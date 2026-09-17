package io.github.digitalsmile.goldberry.widgets.core.scroll;

import io.github.digitalsmile.goldberry.motion.Easing;

/// A programmatic scroll on its way — §3.1's "`scrollIntoView` / programmatic:
/// overlay duration".
///
/// ## The offset is the target; only the drawing travels
///
/// [ScrollState] moves its offset to where the glide ends the moment it starts,
/// so every clamp, every wheel and every later reveal is measured from where the
/// view is *going*. What travels is the translation the viewport draws the
/// content at, which is a function of the frame clock and is applied in
/// [ScrollViewport#render] — the one place a widget is handed a clock, as
/// [ScrollFade] already found (ADR-0363).
///
/// A glide that starts during another starts from where the first had got to,
/// so two reveals in quick succession never jump back.
///
/// Direct input cancels it: §1.7's first rule is that drags and the wheel track
/// the pointer 1:1, and a wheel notch that waited for a glide to finish would be
/// input lagging behind animation.
final class ScrollGlide {

    /// §1.7's `--gb-motion-overlay`. A constant rather than the token for
    /// [ScrollFade#FADE_MILLIS]'s reason: a clock-driven animation cannot read a
    /// `transition` declaration, because it is not one.
    static final double DURATION_MILLIS = 240;

    private double fromX;
    private double fromY;
    private double toX;
    private double toY;

    /// When the glide began, or NaN when none is running.
    private double startedAt = Double.NaN;

    /// A glide asked for since the last frame, waiting for a frame to say when.
    private boolean pending;

    /// The last frame time, and whether that frame asked for reduced motion.
    private double now = Double.NaN;

    private boolean reduced;

    /// Starts a glide from what is drawn now to `(x, y)`.
    ///
    /// @param currentX the offset the state holds, used when nothing is gliding
    /// @param currentY likewise
    void start(double currentX, double currentY, double x, double y) {
        var shownX = shownX(currentX);
        var shownY = shownY(currentY);
        fromX = shownX;
        fromY = shownY;
        toX = x;
        toY = y;
        pending = true;
        startedAt = Double.NaN;
    }

    /// Stops any glide, so the next frame draws the state's offset.
    void cancel() {
        pending = false;
        startedAt = Double.NaN;
    }

    /// Gives the frame's clock, and starts a pending glide at it.
    void stamp(double nowMillis, boolean reducedMotion) {
        now = nowMillis;
        reduced = reducedMotion;
        if (pending) {
            pending = false;
            startedAt = reducedMotion ? Double.NaN : nowMillis;
        }
        if (!Double.isNaN(startedAt) && now - startedAt >= DURATION_MILLIS) {
            startedAt = Double.NaN;
        }
    }

    /// Whether a glide is still drawing between its ends.
    boolean isAnimating() {
        return pending || (!Double.isNaN(startedAt) && !reduced);
    }

    /// The horizontal offset to draw, given the one the state holds.
    double shownX(double target) {
        return Double.isNaN(startedAt) ? (pending ? fromX : target) : fromX + (toX - fromX) * progress();
    }

    /// The vertical offset to draw.
    double shownY(double target) {
        return Double.isNaN(startedAt) ? (pending ? fromY : target) : fromY + (toY - fromY) * progress();
    }

    private double progress() {
        var t = Math.clamp((now - startedAt) / DURATION_MILLIS, 0, 1);
        return Easing.EASE_ENTER.at(t);
    }
}
