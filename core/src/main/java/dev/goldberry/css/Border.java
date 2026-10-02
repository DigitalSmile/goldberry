package dev.goldberry.css;

import java.util.Objects;

import dev.goldberry.css.value.CssColor;

/// The four sides of a box's border, each a width and a colour, in CSS's order.
///
/// ```css
/// card { border: 1px solid var(--gb-border) }
/// td { border-left: 1px solid var(--gb-border) }
/// ```
///
/// Four sides rather than one, because a line sometimes has to be drawn between
/// things a stylesheet cannot count: a document's table has as many columns as
/// its author wrote, and a rule between each pair of cells is a border on one
/// side of every cell rather than a box the builder inserts. Every border the
/// design system pins is still uniform (`button.outlined`, `card`, `group-box`,
/// the checkbox's glyph), and [#isUniform()] is what lets the painter draw those
/// as one stroked rounded rectangle; the per-side drawing is only reached by a
/// box whose sides actually differ.
///
/// A border takes no room. It is drawn inside the box's own edge, over its
/// padding, and never handed to the layout engine, which every bordered widget's
/// padding already accounts for. A side is no different: `border-left: 4px` on a
/// cell with `padding: 6px 8px` is a 4px bar over the first 4 of those 8 pixels,
/// and the cell's text does not move.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
///
/// @param top    the top side
/// @param right  the right side
/// @param bottom the bottom side
/// @param left   the left side
public record Border(Line top, Line right, Line bottom, Line left) {

    /// No border on any side — what every box starts as.
    public static final Border NONE = new Border(Line.NONE, Line.NONE, Line.NONE, Line.NONE);

    public Border {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(bottom, "bottom");
        Objects.requireNonNull(left, "left");
    }

    /// The same line on all four sides — `border: 1px solid red`.
    public static Border all(double width, int argb) {
        var line = new Line(width, argb);
        return new Border(line, line, line, line);
    }

    /// One side's width and colour.
    ///
    /// @param width thickness in logical pixels, drawn inside the box's edge;
    ///              clamped at zero for [Decoration]'s reason
    /// @param argb  `0xAARRGGBB`, not premultiplied
    public record Line(double width, int argb) {

        /// Nothing drawn.
        public static final Line NONE = new Line(0, CssColor.TRANSPARENT);

        public Line {
            if (!Double.isFinite(width)) {
                throw new IllegalArgumentException("border-width must be a finite number, not " + width);
            }
            width = Math.max(0, width);
        }

        /// Whether this side would put ink on the screen: a width, and a colour
        /// that is not fully transparent.
        public boolean hasInk() {
            return width > 0 && (argb >>> 24) != 0;
        }

        public Line width(double value) {
            return new Line(value, argb);
        }

        public Line argb(int value) {
            return new Line(width, value);
        }
    }

    /// The four sides, named once — shared by the per-side properties and the
    /// painter, which both have to say *which* side.
    public enum Side {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT
    }

    /// Whether all four sides are the same line, and so whether one stroked
    /// rectangle draws them.
    public boolean isUniform() {
        return top.equals(right) && right.equals(bottom) && bottom.equals(left);
    }

    /// Whether any side would put ink on the screen.
    public boolean hasInk() {
        return top.hasInk() || right.hasInk() || bottom.hasInk() || left.hasInk();
    }

    /// One side.
    public Line side(Side side) {
        return switch (side) {
            case TOP -> top;
            case RIGHT -> right;
            case BOTTOM -> bottom;
            case LEFT -> left;
        };
    }

    /// This border with one side replaced.
    public Border side(Side side, Line line) {
        Objects.requireNonNull(line, "line");
        return switch (side) {
            case TOP -> new Border(line, right, bottom, left);
            case RIGHT -> new Border(top, line, bottom, left);
            case BOTTOM -> new Border(top, right, line, left);
            case LEFT -> new Border(top, right, bottom, line);
        };
    }

    /// Every side's width set, colours kept — `border-width: 2px`.
    public Border widths(double top, double right, double bottom, double left) {
        return new Border(
                this.top.width(top), this.right.width(right), this.bottom.width(bottom), this.left.width(left));
    }

    /// Every side's colour set, widths kept — `border-color: red`.
    public Border colours(int top, int right, int bottom, int left) {
        return new Border(this.top.argb(top), this.right.argb(right), this.bottom.argb(bottom), this.left.argb(left));
    }

    /// This border's widths with `other`'s colours — how a `border-color`
    /// transition writes its value back without touching a width the cascade
    /// set.
    public Border coloursOf(Border other) {
        return colours(other.top.argb, other.right.argb, other.bottom.argb, other.left.argb);
    }

    /// Whether the two agree on every colour, whatever their widths — the
    /// question a `border-color` transition asks before it starts.
    public boolean sameColours(Border other) {
        return top.argb == other.top.argb
                && right.argb == other.right.argb
                && bottom.argb == other.bottom.argb
                && left.argb == other.left.argb;
    }

    /// `to`'s widths, with each side's colour `t` of the way from this one's to
    /// `to`'s, through OKLCH as every colour transition is.
    public Border mixColours(Border to, double t) {
        return to.colours(
                CssColor.mix(top.argb, to.top.argb, t),
                CssColor.mix(right.argb, to.right.argb, t),
                CssColor.mix(bottom.argb, to.bottom.argb, t),
                CssColor.mix(left.argb, to.left.argb, t));
    }

    /// Every side's alpha scaled by `alpha` — see [Decoration#fade(double)].
    public Border fade(double alpha) {
        return colours(
                CssColor.fade(top.argb, alpha),
                CssColor.fade(right.argb, alpha),
                CssColor.fade(bottom.argb, alpha),
                CssColor.fade(left.argb, alpha));
    }
}
