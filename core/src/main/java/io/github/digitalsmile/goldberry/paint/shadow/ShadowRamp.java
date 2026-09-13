package io.github.digitalsmile.goldberry.paint.shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.css.value.Shadow;

/// A blurred edge, as a stack of hard-edged fills.
///
/// ## Why a stack and not a blur
///
/// A real `box-shadow` is a Gaussian blur of a rounded rectangle, and the
/// rasterizer binding has no blur: Blend2D's image filters are not on the export
/// list, there is no offscreen pass a box could afford per frame, and the frost
/// material's 3-pass box blur (`docs/design-system.md` §1.5) runs over a
/// *backdrop*, not over a shape the painter is in the middle of drawing.
///
/// What it does have is a fast rounded-rectangle fill. So the fade is built out
/// of them: a run of nested rounded rectangles, the outermost nearly invisible
/// and the innermost at the shadow's full colour, each one a little smaller than
/// the last. Seen from far enough away — which is one logical pixel, because the
/// bands are about that wide — that is a gradient.
///
/// This class is the arithmetic and nothing else: no rasterizer, no [Shadow]
/// geometry, no `Frame`. It says *how opaque* each band is, [ShadowGeometry] says
/// what shape it is, and `ShadowPainter` puts the two together (ADR-0310).
///
/// ## Getting the alphas right
///
/// The obvious version of this is wrong. Nested fills composite `over` one
/// another, so a point covered by the outer five bands does **not** end up at the
/// fifth band's alpha — it ends up at `1 - Π(1 - aᵢ)`, which is much darker.
/// Bands whose alphas are read straight off the fade curve give a shadow that is
/// far too heavy in the middle and has visible rings.
///
/// So the curve is treated as the **accumulated** alpha, and each band's own
/// alpha is solved for:
///
/// ```text
/// Aₖ = T · C(uₖ)              the alpha the fade wants after k bands
/// aₖ = 1 − (1 − Aₖ)/(1 − Aₖ₋₁)  the alpha this band must be painted at
/// ```
///
/// where `T` is the shadow colour's own alpha and `C` is the fade profile. The
/// bands are painted outermost first, so `Aₖ₋₁` is what is already on the surface
/// when band `k` goes down.
///
/// ## The profile
///
/// `C(u)` runs across the blur, with `u = 1` at the outer edge of the fade,
/// `u = 0` on the shape's own edge and `u = -1` at the inner edge:
///
/// ```text
/// C(u) = 1 − t²(3 − 2t),  t = (u + 1)/2
/// ```
///
/// Smoothstep, which is not a Gaussian — it is a cubic with the two properties
/// that matter: it is exactly `0.5` on the shape's edge, which is what a blur
/// does, and its first derivative is zero at both ends, so the fade meets
/// "nothing" and "solid" without a visible seam. Against a true Gaussian of
/// σ = blur/2 it is a few percent light in the shoulders. At the alphas a shadow
/// is painted at — 0.16 to 0.68 across the two themes — a few percent of a few
/// percent is under a bit of one channel.
public final class ShadowRamp {

    /// The narrowest fade worth more than four bands, and the widest worth more
    /// than forty-eight.
    ///
    /// One band per logical pixel of blur, which is the resolution the eye gets
    /// on the result. The floor keeps a 2px blur from being a single hard step;
    /// the ceiling keeps `0 32px 128px` — which nothing sane writes — from
    /// costing a hundred and twenty-eight fills.
    private static final int MIN_BANDS = 4;

    private static final int MAX_BANDS = 48;

    private ShadowRamp() {}

    /// One band: how far its rounded rectangle is grown past the box, and what
    /// colour to fill it with.
    ///
    /// @param grow how much larger than the border box this band's shape is, on
    ///        every side — negative for the bands inside the shape's edge. It
    ///        already includes the shadow's `spread`
    /// @param argb `0xAARRGGBB`, not premultiplied, at the alpha this band must
    ///        be painted at **given that every earlier band is already down**
    public record Band(double grow, int argb) {}

    /// The bands of `shadow`, outermost first.
    ///
    /// Empty when the shadow would put no ink on the screen — which is the case
    /// for every box in an ordinary window, so this is the cheap answer and not
    /// the exceptional one.
    ///
    /// @param occluded whether the box will paint an **opaque** fill over its own
    ///        border box. When it will, the bands that lie entirely inside that
    ///        rectangle cannot be seen and are not returned. They are always a
    ///        suffix — `grow` only decreases — so dropping them changes no
    ///        earlier band's alpha, and the visible result is identical
    public static List<Band> bands(Shadow shadow, boolean occluded) {
        Objects.requireNonNull(shadow, "shadow");
        if (!shadow.hasInk()) {
            return List.of();
        }
        var hidden = occluded ? -Math.max(Math.abs(shadow.offsetX()), Math.abs(shadow.offsetY())) : Double.NaN;

        if (shadow.blur() <= 0) {
            // A hard shadow: one fill at the shadow's own colour. No fade to
            // approximate, so nothing to solve for.
            return occluded && shadow.spread() <= hidden
                    ? List.of()
                    : List.of(new Band(shadow.spread(), shadow.argb()));
        }

        var reach = shadow.reach();
        var count = bandCount(shadow.blur());
        var target = ((shadow.argb() >>> 24) & 0xFF) / 255.0;
        var rgb = shadow.argb() & 0x00FFFFFF;

        var bands = new ArrayList<Band>(count);
        var accumulated = 0.0;
        // From k = 1: at k = 0 the profile is zero by construction, and a band
        // painted at no alpha is a native fill that changes nothing.
        for (var k = 1; k <= count; k++) {
            var u = 1 - 2.0 * k / count;
            var grow = shadow.spread() + reach * u;
            if (occluded && grow <= hidden) {
                // This band and every band after it is under the box. Stop
                // rather than continue: `grow` is monotonically decreasing.
                break;
            }
            var wanted = target * coverage(u);
            var alpha = Math.clamp(1 - (1 - wanted) / (1 - accumulated), 0, 1);
            var quantized = (int) Math.round(alpha * 255);
            if (quantized == 0) {
                // Rounded away, so nothing lands on the surface and nothing is
                // added to what is already there. A band emitted at alpha zero
                // would be a native fill that changes no pixel.
                continue;
            }
            // What is *actually* on the surface now, not what was asked for.
            // Eight bits of alpha is a coarse grid and the difference compounds
            // across forty-eight bands; carrying the painted value rather than
            // the wanted one lets each band correct the last one's rounding.
            accumulated += quantized / 255.0 * (1 - accumulated);
            bands.add(new Band(grow, quantized << 24 | rgb));
        }
        return List.copyOf(bands);
    }

    /// How many bands a fade of this width is drawn with — one per logical pixel,
    /// within the two limits.
    ///
    /// A pure function of the blur in **logical** pixels, so a window at 150%
    /// draws the same bands at 1.5× the size rather than more of them. That is
    /// what keeps a shadow scale-invariant, the property
    /// `ScaleInvariance` exists to hold every drawing in the toolkit to.
    static int bandCount(double blur) {
        return Math.clamp((long) Math.ceil(blur), MIN_BANDS, MAX_BANDS);
    }

    /// How much of the shadow's colour is on the surface at `u`, where `u` runs
    /// from 1 at the outer edge of the fade to -1 at the inner edge.
    static double coverage(double u) {
        var t = (u + 1) / 2;
        return 1 - t * t * (3 - 2 * t);
    }
}
