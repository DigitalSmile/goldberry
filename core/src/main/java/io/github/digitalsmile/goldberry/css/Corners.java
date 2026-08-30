package io.github.digitalsmile.goldberry.css;

/// The four corner radii of a box, in CSS's order.
///
/// ## Why four numbers and not one
///
/// It was one number until a `group-box` asked for two. Every radius the design
/// system pins is uniform (§1.5's 4, 8, 12 and `full`), and one number is the
/// right shape for all of them — until a box has to meet a *rounded parent's*
/// corner on one edge and its sibling's square one on the other. `group-box-title`
/// is that box: it fills the top of an 8px-rounded frame, so its top corners are
/// the frame's less the border and its bottom ones are square, and there is no
/// arrangement of nodes that fakes it (the trick ADR-0215 used for a rule under a
/// table header). The stylesheet had said `border-radius: 7px 7px 0 0` since the
/// widget shipped; the engine dropped the declaration with a warning and drew
/// four square corners spilling out of the frame.
///
/// ## Circular only
///
/// CSS's full grammar allows an ellipse per corner — `border-radius: 10px / 20px`
/// — and this does not: [io.github.digitalsmile.goldberry.paint.RoundRect] draws
/// quarter circles, and an elliptical corner is a different curve rather than a
/// different number. A declaration with a `/` in it is dropped and says so, which
/// is the same answer §8 gives to `border-radius: 50%`.
///
/// ## Units
///
/// Logical pixels, resolved, non-negative — see [Decoration]. Clamped rather than
/// refused, for its reason: these arrive from a stylesheet, and a negative corner
/// should not take a window down mid-frame.
///
/// @param topLeft     the top-left radius; 0 is a square corner
/// @param topRight    the top-right radius
/// @param bottomRight the bottom-right radius
/// @param bottomLeft  the bottom-left radius
public record Corners(double topLeft, double topRight, double bottomRight, double bottomLeft) {

    /// Four square corners — what every box starts as.
    public static final Corners SQUARE = new Corners(0, 0, 0, 0);

    public Corners {
        topLeft = clamp(topLeft, "border-radius");
        topRight = clamp(topRight, "border-radius");
        bottomRight = clamp(bottomRight, "border-radius");
        bottomLeft = clamp(bottomLeft, "border-radius");
    }

    /// The same radius on all four corners — `border-radius: 8px`.
    public static Corners all(double radius) {
        return new Corners(radius, radius, radius, radius);
    }

    /// Whether every corner is square, and so whether a rectangle would do.
    ///
    /// Asked before building a path, because a rectangle is Blend2D's fastest
    /// primitive and the overwhelming majority of boxes are still rectangles.
    public boolean isSquare() {
        return topLeft == 0 && topRight == 0 && bottomRight == 0 && bottomLeft == 0;
    }

    /// Whether all four corners are the same, which is what §1.5's radii are.
    public boolean isUniform() {
        return topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft;
    }

    /// Every corner grown by `by`, leaving square corners square.
    ///
    /// What a focus ring does: it is concentric with the corner it surrounds, so
    /// its radius grows by the distance it moved out — and a square corner stays
    /// square, because a ring that rounded itself around a sharp box would not
    /// follow the control.
    public Corners grownBy(double by) {
        return new Corners(
                topLeft > 0 ? topLeft + by : 0,
                topRight > 0 ? topRight + by : 0,
                bottomRight > 0 ? bottomRight + by : 0,
                bottomLeft > 0 ? bottomLeft + by : 0);
    }

    /// Every corner shrunk by `by`, floored at square.
    ///
    /// What a border does: it is stroked down the middle of a path inset by half
    /// its width, and the inner arc of a corner is that much tighter.
    public Corners shrunkBy(double by) {
        return new Corners(
                Math.max(0, topLeft - by),
                Math.max(0, topRight - by),
                Math.max(0, bottomRight - by),
                Math.max(0, bottomLeft - by));
    }

    /// These corners kept only where a joined row **ends**, and squared where it
    /// does not.
    ///
    /// The joined-buttons drawing, which is CSS's `border-radius: 8px 0 0 8px` on
    /// the first cell and `0 8px 8px 0` on the last: a row of things that meet is
    /// round at its two ends and square where they touch. `segmented` is the
    /// caller — its segments and the pill that travels along them both take it,
    /// and which cell is an end is a fact about a **count**, which no selector can
    /// write ([ADR-0217]).
    ///
    /// Both true is a row of one, which keeps every corner; both false is a cell
    /// in the middle, which keeps none.
    ///
    /// @param atStart whether this is the leftmost cell of the row
    /// @param atEnd   whether it is the rightmost
    public Corners inRow(boolean atStart, boolean atEnd) {
        return new Corners(
                atStart ? topLeft : 0, atEnd ? topRight : 0, atEnd ? bottomRight : 0, atStart ? bottomLeft : 0);
    }

    /// These corners scaled down until they fit inside a `width` x `height` box.
    ///
    /// CSS's own rule, and the one `border-radius: 9999px` relies on to be a pill
    /// rather than a rendering error: if the two radii on any edge together
    /// overrun that edge, **all four** are scaled by the same factor — the
    /// smallest edge's — so the corners stay in proportion instead of the longest
    /// one alone being cut back.
    ///
    /// For a uniform radius this is exactly the old `min(radius, min(w, h) / 2)`,
    /// which is what keeps every existing golden image byte-identical.
    public Corners fittedTo(double width, double height) {
        var scale = Math.min(
                Math.min(ratio(width, topLeft + topRight), ratio(width, bottomLeft + bottomRight)),
                Math.min(ratio(height, topLeft + bottomLeft), ratio(height, topRight + bottomRight)));
        if (scale >= 1) {
            return this;
        }
        var fitted = Math.max(0, scale);
        return new Corners(topLeft * fitted, topRight * fitted, bottomRight * fitted, bottomLeft * fitted);
    }

    /// How much of an edge's demand it can meet — 1 or more when it fits.
    private static double ratio(double side, double demanded) {
        return demanded <= 0 ? Double.POSITIVE_INFINITY : side / demanded;
    }

    private static double clamp(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, not " + value);
        }
        return Math.max(0, value);
    }
}
