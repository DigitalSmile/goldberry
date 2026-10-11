package dev.goldberry.css;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;

/// The four corner radii of a box, in CSS's order.
///
/// ```css
/// card { border-radius: 8px }
/// group-box-title { border-radius: 7px 7px 0 0 }
/// avatar { border-radius: 50% }
/// tab { border-radius: 12px 12px 0 0 / 8px 8px 0 0 }
/// ```
///
/// Four radii rather than one, because a box sometimes has to meet a *rounded
/// parent's* corner on one edge and its sibling's square one on the other.
/// `group-box-title` is that box: it fills the top of an 8px-rounded frame, so
/// its top corners are the frame's less the border and its bottom ones are
/// square. Every radius the design system pins is still uniform (4, 8, 12 and
/// `full`), and [#isUniform()] says when one number would have done.
///
/// Each corner is a [Radius]: a horizontal and a vertical radius, so a corner
/// may be a quarter ellipse, and each may be partly a percentage of the box's
/// width or height. A percentage is resolved by [#fittedTo], which every
/// painter calls with the box's size before drawing; the radii it returns are
/// in logical pixels.
///
/// [#topLeft()] and its three siblings answer the **horizontal** radius in
/// pixels, which for a circular corner is the radius. A painter that draws
/// ellipses reads [#topLeftRadius()] and its siblings.
///
/// Units are logical pixels, non-negative (see [Decoration]). Clamped rather
/// than refused, for its reason: these arrive from a stylesheet, and a negative
/// corner should not take a window down mid-frame.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
///
/// @param topLeftRadius     the top-left corner; [Radius#SQUARE] is a square
///                          corner
/// @param topRightRadius    the top-right corner
/// @param bottomRightRadius the bottom-right corner
/// @param bottomLeftRadius  the bottom-left corner
public record Corners(Radius topLeftRadius, Radius topRightRadius, Radius bottomRightRadius, Radius bottomLeftRadius) {

    /// Four square corners — what every box starts as.
    public static final Corners SQUARE = new Corners(0, 0, 0, 0);

    public Corners {
        Objects.requireNonNull(topLeftRadius, "topLeftRadius");
        Objects.requireNonNull(topRightRadius, "topRightRadius");
        Objects.requireNonNull(bottomRightRadius, "bottomRightRadius");
        Objects.requireNonNull(bottomLeftRadius, "bottomLeftRadius");
    }

    /// Four circular corners, in logical pixels — `border-radius: 7px 7px 0 0`.
    public Corners(double topLeft, double topRight, double bottomRight, double bottomLeft) {
        this(Radius.circle(topLeft), Radius.circle(topRight), Radius.circle(bottomRight), Radius.circle(bottomLeft));
    }

    /// The same radius on all four corners — `border-radius: 8px`.
    public static Corners all(double radius) {
        return new Corners(radius, radius, radius, radius);
    }

    /// The same corner four times — `border-radius: 50%`.
    public static Corners all(Radius radius) {
        return new Corners(radius, radius, radius, radius);
    }

    /// The top-left corner's horizontal radius, in logical pixels.
    public double topLeft() {
        return topLeftRadius.x();
    }

    /// The top-right corner's horizontal radius, in logical pixels.
    public double topRight() {
        return topRightRadius.x();
    }

    /// The bottom-right corner's horizontal radius, in logical pixels.
    public double bottomRight() {
        return bottomRightRadius.x();
    }

    /// The bottom-left corner's horizontal radius, in logical pixels.
    public double bottomLeft() {
        return bottomLeftRadius.x();
    }

    /// The four corners clockwise from the top-left, which is the order a
    /// painter walks them in.
    public Radius[] clockwise() {
        return new Radius[] {topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius};
    }

    /// Whether every corner is square, and so whether a rectangle would do.
    ///
    /// Asked before building a path, because a rectangle is Blend2D's fastest
    /// primitive and the overwhelming majority of boxes are still rectangles.
    public boolean isSquare() {
        return topLeftRadius.isSquare()
                && topRightRadius.isSquare()
                && bottomRightRadius.isSquare()
                && bottomLeftRadius.isSquare();
    }

    /// Whether all four corners are the same, which is what the design system's
    /// radii are.
    public boolean isUniform() {
        return topLeftRadius.equals(topRightRadius)
                && topRightRadius.equals(bottomRightRadius)
                && bottomRightRadius.equals(bottomLeftRadius);
    }

    /// Whether any corner is still partly a percentage, which only [#fittedTo]
    /// can turn into pixels.
    public boolean isRelative() {
        return !topLeftRadius.isResolved()
                || !topRightRadius.isResolved()
                || !bottomRightRadius.isResolved()
                || !bottomLeftRadius.isResolved();
    }

    /// These corners with their percentages resolved against a `width` x
    /// `height` box, when they have any, and as they are when they do not.
    ///
    /// What a ring or a band drawn around the box asks before it grows or
    /// shrinks them: a percentage is of the box, not of the ring. Corners in
    /// pixels are left alone, so the ring fits them as it always has.
    public Corners resolvedOn(double width, double height) {
        return isRelative() ? fittedTo(width, height) : this;
    }

    /// Every corner grown by `by`, leaving square corners square.
    ///
    /// What a focus ring does: it is concentric with the corner it surrounds, so
    /// its radius grows by the distance it moved out — and a square corner stays
    /// square, because a ring that rounded itself around a sharp box would not
    /// follow the control.
    public Corners grownBy(double by) {
        return new Corners(
                topLeftRadius.grownBy(by),
                topRightRadius.grownBy(by),
                bottomRightRadius.grownBy(by),
                bottomLeftRadius.grownBy(by));
    }

    /// Every corner shrunk by `by`, floored at square.
    ///
    /// What a border does: it is stroked down the middle of a path inset by half
    /// its width, and the inner arc of a corner is that much tighter.
    public Corners shrunkBy(double by) {
        return inset(by, by, by, by);
    }

    /// These corners `t` of the way to `to`, corner by corner: what a
    /// `border-radius` transition draws.
    public Corners mix(Corners to, double t) {
        return new Corners(
                topLeftRadius.mix(to.topLeftRadius, t),
                topRightRadius.mix(to.topRightRadius, t),
                bottomRightRadius.mix(to.bottomRightRadius, t),
                bottomLeftRadius.mix(to.bottomLeftRadius, t));
    }

    /// The corners of the box inside these by `top`, `right`, `bottom` and
    /// `left`: each corner's horizontal radius less the side beside it, and its
    /// vertical radius less the side above or below, floored at square.
    ///
    /// CSS's rule for a border's inner edge, which is why a corner between a
    /// thick side and a thin one comes out elliptical.
    public Corners inset(double top, double right, double bottom, double left) {
        return new Corners(
                topLeftRadius.shrunkBy(left, top),
                topRightRadius.shrunkBy(right, top),
                bottomRightRadius.shrunkBy(right, bottom),
                bottomLeftRadius.shrunkBy(left, bottom));
    }

    /// These corners kept only where a joined row **ends**, and squared where it
    /// does not.
    ///
    /// The joined-buttons drawing, which is CSS's `border-radius: 8px 0 0 8px` on
    /// the first cell and `0 8px 8px 0` on the last: a row of things that meet is
    /// round at its two ends and square where they touch. `segmented` is the
    /// caller — its segments and the pill that travels along them both take it,
    /// and which cell is an end is a fact about a **count**, which no selector can
    /// write.
    ///
    /// Both true is a row of one, which keeps every corner; both false is a cell
    /// in the middle, which keeps none.
    ///
    /// @param atStart whether this is the leftmost cell of the row
    /// @param atEnd   whether it is the rightmost
    public Corners inRow(boolean atStart, boolean atEnd) {
        return new Corners(
                atStart ? topLeftRadius : Radius.SQUARE,
                atEnd ? topRightRadius : Radius.SQUARE,
                atEnd ? bottomRightRadius : Radius.SQUARE,
                atStart ? bottomLeftRadius : Radius.SQUARE);
    }

    /// These corners resolved against a `width` x `height` box and scaled down
    /// until they fit inside it.
    ///
    /// A percentage is of the width for a horizontal radius and of the height
    /// for a vertical one, so `50%` on a box that is not square is an ellipse.
    /// Then CSS's own rule, and the one `border-radius: 9999px` relies on to be a
    /// pill rather than a rendering error: if the two radii on any edge together
    /// overrun that edge, **all four** corners are scaled by the same factor —
    /// the smallest edge's — so they stay in proportion instead of the longest
    /// one alone being cut back.
    ///
    /// For a uniform circular radius this is exactly the old
    /// `min(radius, min(w, h) / 2)`, which is what keeps every existing golden
    /// image byte-identical.
    public Corners fittedTo(double width, double height) {
        var resolved = isRelative()
                ? new Corners(
                        topLeftRadius.resolved(width, height),
                        topRightRadius.resolved(width, height),
                        bottomRightRadius.resolved(width, height),
                        bottomLeftRadius.resolved(width, height))
                : this;
        var tl = resolved.topLeftRadius;
        var tr = resolved.topRightRadius;
        var br = resolved.bottomRightRadius;
        var bl = resolved.bottomLeftRadius;
        var scale = Math.min(
                Math.min(ratio(width, tl.x() + tr.x()), ratio(width, bl.x() + br.x())),
                Math.min(ratio(height, tl.y() + bl.y()), ratio(height, tr.y() + br.y())));
        if (scale >= 1) {
            return resolved;
        }
        var fitted = Math.max(0, scale);
        return new Corners(tl.scaled(fitted), tr.scaled(fitted), br.scaled(fitted), bl.scaled(fitted));
    }

    /// How much of an edge's demand it can meet — 1 or more when it fits.
    private static double ratio(double side, double demanded) {
        return demanded <= 0 ? Double.POSITIVE_INFINITY : side / demanded;
    }

    /// One corner: a horizontal and a vertical radius, each in logical pixels
    /// plus a percentage of the box's width or height. A resolved corner with
    /// either radius zero is square, as CSS says, and is stored as square.
    ///
    /// `border-radius: calc(50% - 4px)` is `x = -4`, `xPercent = 50`, and the
    /// same on the vertical axis. A pixel part may be negative only when a
    /// percentage goes with it, and a resolved radius is floored at zero.
    ///
    /// @param x        the horizontal radius's pixels
    /// @param y        the vertical radius's pixels
    /// @param xPercent the horizontal radius's percentage of the box's width,
    ///                 `50` meaning 50%
    /// @param yPercent the vertical radius's percentage of the box's height
    public record Radius(double x, double y, double xPercent, double yPercent) {

        /// A square corner.
        public static final Radius SQUARE = new Radius(0, 0, 0, 0);

        public Radius {
            xPercent = clamp(xPercent, "a border-radius percentage");
            yPercent = clamp(yPercent, "a border-radius percentage");
            x = xPercent > 0 ? finite(x) : clamp(x, "border-radius");
            y = yPercent > 0 ? finite(y) : clamp(y, "border-radius");
            // CSS: a corner with either radius zero is square.
            if (xPercent == 0 && yPercent == 0 && (x == 0 || y == 0)) {
                x = 0;
                y = 0;
            }
        }

        /// A circular corner of `radius` logical pixels.
        public static Radius circle(double radius) {
            return radius == 0 ? SQUARE : new Radius(radius, radius, 0, 0);
        }

        /// An elliptical corner, in logical pixels.
        public static Radius of(double x, double y) {
            return new Radius(x, y, 0, 0);
        }

        /// Whether this is a square corner.
        public boolean isSquare() {
            return x == 0 && y == 0 && xPercent == 0 && yPercent == 0;
        }

        /// Whether neither radius has a percentage left in it.
        public boolean isResolved() {
            return xPercent == 0 && yPercent == 0;
        }

        /// This corner on a `width` x `height` box, in logical pixels.
        public Radius resolved(double width, double height) {
            if (isResolved()) {
                return this;
            }
            return new Radius(Math.max(0, x + xPercent * width / 100), Math.max(0, y + yPercent * height / 100), 0, 0);
        }

        /// This corner `t` of the way to `to`, each radius and each percentage
        /// by itself.
        public Radius mix(Radius to, double t) {
            return new Radius(
                    x + (to.x - x) * t,
                    y + (to.y - y) * t,
                    xPercent + (to.xPercent - xPercent) * t,
                    yPercent + (to.yPercent - yPercent) * t);
        }

        Radius scaled(double factor) {
            return new Radius(x * factor, y * factor, xPercent * factor, yPercent * factor);
        }

        Radius grownBy(double by) {
            if (isSquare()) {
                return this;
            }
            return new Radius(
                    x > 0 || xPercent > 0 ? x + by : 0, y > 0 || yPercent > 0 ? y + by : 0, xPercent, yPercent);
        }

        Radius shrunkBy(double horizontal, double vertical) {
            if (!isResolved()) {
                return new Radius(x - horizontal, y - vertical, xPercent, yPercent);
            }
            return new Radius(Math.max(0, x - horizontal), Math.max(0, y - vertical), 0, 0);
        }

        /// Here and not on [Corners], so that making a radius never starts the
        /// outer class's initialization, whose constants are made of radii.
        private static double clamp(double value, String name) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be a finite number, not " + value);
            }
            return Math.max(0, value);
        }

        private static double finite(double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("border-radius must be a finite number, not " + value);
            }
            return value;
        }
    }

    /// `border-radius`'s value: one to four radii, and after a `/` the
    /// vertical ones, in CSS's order and with CSS's fill-in rule. Empty when
    /// any part does not parse. What `inset(… round …)` in a `clip-path` reads
    /// too.
    public static Optional<Corners> parse(List<Token> value, CssLength.Context context) {
        return ComputedStyle.corners(value, context);
    }
}
