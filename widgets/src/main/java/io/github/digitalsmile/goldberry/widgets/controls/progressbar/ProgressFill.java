package io.github.digitalsmile.goldberry.widgets.controls.progressbar;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

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
/// It travels **off one edge and in at the other**: the bar starts entirely to the
/// left of the track, crosses it, and leaves entirely to the right before the loop
/// begins again. That is the drawing every other toolkit ships, and it says the
/// one thing a there-and-back sweep cannot — that the work has no far end to
/// reverse at.
///
/// It depends on a clip, and the clip is `progress`'s own `overflow: hidden` in
/// `controls.css`. Two things need hiding and it hides both: the overhang, which
/// would otherwise be drawn across whatever is beside the control, and the **wrap**
/// — the instant the bar's left edge jumps from the track's right-hand side back
/// to before its left one, which is invisible only because the bar is outside the
/// clip at both ends of it.
///
/// This drawing was not available until ADR-0114 shipped `overflow`, and for a
/// while afterwards the code still said it was not — so what was left was a
/// decision about a shipped animation rather than a missing mechanism. The
/// decision is taken in [ADR-0418].
///
/// The clip is a **rectangle**, where CSS's would follow the border radius. At
/// §3's metrics — a 4px track with a 2px radius — the difference is the four
/// corner wedges of a semicircular cap, under a square pixel each, and it shows
/// only while the bar is passing an end. Named rather than left to be found.
///
/// The offset is a percentage, and a percentage inside `translate` is a
/// proportion of the **moving box**. That is CSS's rule, it is exactly what is
/// wanted here — the bar's own width is the unit the travel is naturally
/// expressed in — and it is the same rule that made `translate` *unable* to place
/// a slider's thumb. Two controls, one rule, opposite conclusions
/// (ADR-0081).
///
/// @param fraction      how far along, `0..1`; ignored when indeterminate
/// @param indeterminate which of the two drawings this is
record ProgressFill(double fraction, boolean indeterminate) implements Widget.Leaf, Styled, Paints {

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
        var width = Length.percent((float) ((indeterminate ? SWEEP_WIDTH : fraction) * 100));
        // Applied after `style`, so the theme still owns the colour, the height
        // and the radius of what is being placed -- the split SliderFill states.
        return Box.of().style(style).size(width, style.height()).transform(sweepAt(context));
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
        var offset = offsetAt(phaseAt(context.nowMillis()));
        return Transform.of(new Transform.Function.Translate(Transform.Length.percent(offset), Transform.Length.ZERO));
    }

    /// Where the bar is at `phase`, as a percentage of **its own width** — which
    /// is what a percentage inside `translate` means.
    ///
    /// The bar's leading edge runs from `-SWEEP_WIDTH` to `1` in track fractions:
    /// it begins one whole bar before the track and ends one whole track after its
    /// own start, so it is entirely outside the clip at both ends of the loop and
    /// the wrap between them cannot be seen. That is a travel of
    /// `1 + SWEEP_WIDTH` of the track, and dividing by `SWEEP_WIDTH` converts it
    /// into the unit `translate` is written in: a bar covering 0.3 of the track
    /// crosses 1.3 of it, which is 433% of itself.
    ///
    /// Linear (§3.1), so the bar arrives and leaves at one speed. Easing it would
    /// make it hesitate at the edges, which reads as work stalling.
    static double offsetAt(double phase) {
        return (phase * (1 + SWEEP_WIDTH) - SWEEP_WIDTH) / SWEEP_WIDTH * 100;
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
