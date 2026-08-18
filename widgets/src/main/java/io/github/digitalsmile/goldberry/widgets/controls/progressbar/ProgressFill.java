package io.github.digitalsmile.goldberry.widgets.controls.progressbar;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The coloured part of a [Progress] — a **part**, and the fourteenth.
///
/// It is one node drawn two ways, because the thing that differs between a bar
/// that knows how far along it is and one that does not is **its geometry**, and
/// geometry is what this box is.
///
/// ## Determinate: a width
///
/// `width: 40%`, and nothing moves. Worth noticing next to `SliderFill`, which
/// cannot do this: a slider's fill has to share its track with a 16px thumb, so
/// its share is a flex ratio of what is left rather than a percentage of the
/// whole ([ADR-0079]). A progress bar has no thumb, so the plain answer is
/// available and is the one taken.
///
/// ## Indeterminate: a fixed width and a transform
///
/// §3.1's "sweep loop 1.2s `linear`". The bar is a third of the track and travels
/// by `transform` rather than by width or margin — animating either of those
/// would run Yoga on **every frame of a loop that never ends**, which is
/// precisely the cost §1.7's whitelist exists to refuse.
///
/// It travels **there and back within the track**, rather than off one end and in
/// at the other. The off-the-edges version is the more common drawing and it
/// depends on something this toolkit does not have: `overflow: hidden`. Nothing
/// clips a box here, so a bar that ran past its track would be drawn across
/// whatever is beside it — and the wrap from one end to the other, which
/// clipping is what hides, would be a visible jump once a loop. A bar that
/// reverses has no wrap to hide.
///
/// The offset is a percentage, and a percentage inside `translate` is a
/// proportion of the **moving box**. That is CSS's rule, it is exactly what is
/// wanted here — the bar's own width is the unit the travel is naturally
/// expressed in — and it is the same rule that made `translate` *unable* to place
/// a slider's thumb. Two controls, one rule, opposite conclusions
/// ([ADR-0081](../../../../../../../../book/src/adr/0081-a-perpetual-loop-has-no-state.md)).
///
/// @param fraction      how far along, `0..1`; ignored when indeterminate
/// @param indeterminate which of the two drawings this is
record ProgressFill(double fraction, boolean indeterminate)
        implements Widget.Leaf, Styled, Paints {

    /// §3.1's "sweep loop **1.2s** linear", in milliseconds.
    private static final double SWEEP_PERIOD = 1200;

    /// How much of the track the sweeping bar covers.
    ///
    /// Not in §3 — the metrics row gives a progress bar a track height and a
    /// radius, and says nothing about a sweep — so it lives here, beside the
    /// arithmetic that moves it, rather than in the stylesheet. A width in CSS
    /// and a travel in Java would be one statement in two files, which is how the
    /// two stop agreeing (ADR-0074's argument against a `density-regular.css`).
    private static final double SWEEP_WIDTH = 0.3;

    @Override
    public String cssType() {
        return "progress-fill";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// Mirrored from the bar, so `progress-fill:indeterminate` reaches it without
    /// a descendant combinator — the rule every other part in this catalog
    /// follows for `disabled`.
    @Override
    public boolean isIndeterminate() {
        return indeterminate;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var width = StyleLength.percent((float) ((indeterminate ? SWEEP_WIDTH : fraction) * 100));
        // Applied after `style`, so the theme still owns the colour, the height
        // and the radius of what is being placed -- the split SliderFill states.
        return Box.of().style(style).size(width, style.height())
                .transform(sweepAt(context));
    }

    /// Where the sweeping bar is at this frame's time, or no transform at all.
    ///
    /// Reduced motion draws the bar **still**, across a third of the track, and
    /// lets the stylesheet pulse its opacity — §3.1: "reduced-motion: opacity
    /// pulse 1.2s". A pulse between two values is a transition, and transitions
    /// are already CSS's; what could not be written in CSS is the sweep, and that
    /// is the half that goes away.
    private Transform sweepAt(Context context) {
        if (!indeterminate || context.reducedMotion()) {
            return Transform.NONE;
        }
        // The travel is what is left of the track, measured in bars: a bar
        // covering 0.3 of the track has 0.7 to cross, which is 233% of itself.
        var offset = travelAt(phaseAt(context.nowMillis())) * (1 - SWEEP_WIDTH) / SWEEP_WIDTH * 100;
        return Transform.of(new Transform.Function.Translate(
                Transform.Length.percent(offset), Transform.Length.ZERO));
    }

    /// How far across the track the bar is at `phase`, `0..1`.
    ///
    /// Out in the first half of the loop and back in the second, which is what
    /// keeps it inside a control nothing clips. Linear each way (§3.1), so the
    /// only thing that happens at the turn is that the direction changes —
    /// easing it would make the bar hesitate at both ends, which reads as work
    /// stalling rather than as a control.
    static double travelAt(double phase) {
        return phase < 0.5 ? phase * 2 : (1 - phase) * 2;
    }

    /// Where in the loop `now` is, `0..1`.
    ///
    /// Against the **clock** rather than against a remembered start, which is the
    /// whole of ADR-0081: there is nothing to remember, nothing to dispose, and
    /// two bars in one window sweep together because they are reading the same
    /// number. `now` is milliseconds on an arbitrary origin and may be large or
    /// negative, so the modulus is taken first and folded into `0..1`.
    static double phaseAt(double now) {
        var phase = (now % SWEEP_PERIOD) / SWEEP_PERIOD;
        return phase < 0 ? phase + 1 : phase;
    }
}
