package io.github.digitalsmile.goldberry.paint;

import java.util.Objects;

/// How a [Path] is stroked — everything about the pen except its colour.
///
/// ## Why the style is an argument and not frame state
///
/// [Frame] has never had a `setStrokeWidth`, and this type is what keeps it that
/// way. Blend2D's stroke style *is* context state, so a frame that set it once
/// would leak the last icon's weight into whatever drew next — and the bug is a
/// hairline somewhere else entirely, three widgets away, in a golden image
/// nobody was looking at. Passing the whole style with the call means the
/// question "what is the stroke width here?" is answered by the line you are
/// reading (ADR-0043, ADR-0277).
///
/// The colour is deliberately *not* in here. It is an argument to the drawing
/// call alongside this, like every other fill in the toolkit, so that the common
/// case — one style, eight series colours — is one `Stroke` and eight `int`s.
///
/// ## Units
///
/// [#width()] and the lengths in [#dash()] are logical pixels: a 2px stroke is
/// two CSS pixels at any display scale, and the rasterizer's transform is what
/// turns that into device pixels.
///
/// @param width      the pen width in logical pixels; must be finite and positive
/// @param cap        what the ends of an open sub-path look like
/// @param join       what a corner looks like
/// @param miterLimit how far a [Join#MITER] corner may run out before it is cut
///        off, as a multiple of the width — SVG's and CSS's default is 4, and the
///        number only has meaning for a mitered join
/// @param dash       the on/off pattern, or [Dash#NONE] for a solid stroke
public record Stroke(double width, Cap cap, Join join, double miterLimit, Dash dash) {

    /// SVG's `stroke-miterlimit` default, and CSS's, and Blend2D's.
    ///
    /// Named rather than written as `4` in three places, because a reader who
    /// meets a bare 4 in a constructor has no way to tell whether it was chosen
    /// or inherited.
    public static final double DEFAULT_MITER_LIMIT = 4;

    /// A one-pixel butt-capped mitered solid stroke — what a context starts as,
    /// and the thing [#of] varies.
    public static final Stroke HAIRLINE = new Stroke(1, Cap.BUTT, Join.MITER, DEFAULT_MITER_LIMIT, Dash.NONE);

    public Stroke {
        if (!Double.isFinite(width) || width <= 0) {
            throw new IllegalArgumentException("a stroke width must be a finite positive number, not " + width);
        }
        if (!Double.isFinite(miterLimit) || miterLimit < 1) {
            // Below 1 a miter is shorter than the bevel it would fall back to,
            // which is not a corner any renderer draws -- SVG says the same.
            throw new IllegalArgumentException(
                    "a miter limit must be a finite number of at least 1, not " + miterLimit);
        }
        Objects.requireNonNull(cap, "cap");
        Objects.requireNonNull(join, "join");
        Objects.requireNonNull(dash, "dash");
    }

    /// A solid mitered stroke of `width`, butt-capped — the plainest pen there
    /// is, and the one every other factory here starts from.
    public static Stroke of(double width) {
        return new Stroke(width, Cap.BUTT, Join.MITER, DEFAULT_MITER_LIMIT, Dash.NONE);
    }

    /// A solid stroke of `width` that is round at both its ends and its corners.
    ///
    /// The shape a line chart and an icon both want, and a pair of calls often
    /// enough to be worth naming: Lucide is drawn this way, and a data series
    /// drawn any other way has visible mitre spikes wherever it turns sharply.
    public static Stroke round(double width) {
        return new Stroke(width, Cap.ROUND, Join.ROUND, DEFAULT_MITER_LIMIT, Dash.NONE);
    }

    /// This stroke with a different width.
    public Stroke width(double value) {
        return new Stroke(value, cap, join, miterLimit, dash);
    }

    /// This stroke with a different cap.
    public Stroke cap(Cap value) {
        return new Stroke(width, value, join, miterLimit, dash);
    }

    /// This stroke with a different join.
    public Stroke join(Join value) {
        return new Stroke(width, cap, value, miterLimit, dash);
    }

    /// This stroke with a different miter limit, which only [Join#MITER] reads.
    public Stroke miterLimit(double value) {
        return new Stroke(width, cap, join, value, dash);
    }

    /// This stroke with a different dash pattern.
    public Stroke dash(Dash value) {
        return new Stroke(width, cap, join, miterLimit, value);
    }

    /// This stroke cut into `on` pixels drawn and `off` pixels skipped.
    ///
    /// The short form of `dash(Dash.of(on, off))`, because the two-number case is
    /// nearly every dashed stroke there is: a marquee, a ghost layer, a
    /// missing-asset placeholder.
    public Stroke dashed(double on, double off) {
        return dash(Dash.of(on, off));
    }

    /// Whether this pen draws an unbroken line.
    public boolean isSolid() {
        return dash.isSolid();
    }
}
