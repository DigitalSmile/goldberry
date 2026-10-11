package dev.goldberry.css.background;

import java.util.ArrayList;
import java.util.List;

/// Where CSS's `background-position` puts a layer: a length and a percentage
/// on each axis, the percentage being of the room the layer leaves.
///
/// ```css
/// .crest  { background-position: right 8px bottom 8px }   /* 100% - 8px, 100% - 8px */
/// .centre { background-position: center }                   /* 50%, 50% */
/// ```
///
/// CSS places a layer at a percentage of *the area less the layer*: `100%`
/// puts a picture's right edge on the area's right edge, and a gradient, the
/// size of its area, is moved by a percentage not at all. A keyword is a
/// percentage, `left` and `top` 0, `center` 50 and `right` and `bottom` 100,
/// and an offset from `right` or `bottom` is a length taken away.
///
/// Animatable: moving a `repeating-linear-gradient` by one period is how a
/// stripe marches.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
///
/// @param x        how far right, in logical pixels; negative is left
/// @param y        how far down; negative is up
/// @param xPercent how far across the room left over, in percent
/// @param yPercent how far down it
public record BackgroundPosition(double x, double y, double xPercent, double yPercent) {

    /// Where every layer starts: the top left corner.
    public static final BackgroundPosition ZERO = new BackgroundPosition(0, 0);

    /// The middle of the area.
    public static final BackgroundPosition CENTRE = new BackgroundPosition(0, 0, 50, 50);

    public BackgroundPosition {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(xPercent) || !Double.isFinite(yPercent)) {
            throw new IllegalArgumentException("a background position is finite, and (" + x + ", " + y + ", " + xPercent
                    + "%, " + yPercent + "%) is not");
        }
    }

    /// An offset in pixels from the top left corner.
    public BackgroundPosition(double x, double y) {
        this(x, y, 0, 0);
    }

    /// Where a layer `layerWidth` by `layerHeight` goes in an area `width` by
    /// `height`: its top left corner's offset from the area's.
    ///
    /// @return the offset across and the offset down
    public double[] offset(double width, double height, double layerWidth, double layerHeight) {
        return new double[] {x + xPercent * (width - layerWidth) / 100, y + yPercent * (height - layerHeight) / 100};
    }

    /// This position with its horizontal part taken from `other`.
    public BackgroundPosition withX(BackgroundPosition other) {
        return new BackgroundPosition(other.x, y, other.xPercent, yPercent);
    }

    /// This position with its vertical part taken from `other`.
    public BackgroundPosition withY(BackgroundPosition other) {
        return new BackgroundPosition(x, other.y, xPercent, other.yPercent);
    }

    /// This position `t` of the way to `to`.
    public BackgroundPosition mix(BackgroundPosition to, double t) {
        return new BackgroundPosition(
                x + (to.x - x) * t,
                y + (to.y - y) * t,
                xPercent + (to.xPercent - xPercent) * t,
                yPercent + (to.yPercent - yPercent) * t);
    }

    /// Two lists of positions `t` of the way from one to the other, entry by
    /// entry, the shorter repeated as CSS repeats it.
    public static List<BackgroundPosition> mix(List<BackgroundPosition> from, List<BackgroundPosition> to, double t) {
        var count = Math.max(from.size(), to.size());
        var mixed = new ArrayList<BackgroundPosition>(count);
        for (var i = 0; i < count; i++) {
            mixed.add(from.get(i % from.size()).mix(to.get(i % to.size()), t));
        }
        return List.copyOf(mixed);
    }

    /// The position as CSS writes it.
    @Override
    public String toString() {
        return axis(x, xPercent) + " " + axis(y, yPercent);
    }

    private static String axis(double pixels, double percent) {
        if (percent == 0) {
            return format(pixels) + "px";
        }
        if (pixels == 0) {
            return format(percent) + "%";
        }
        return "calc(" + format(percent) + "% " + (pixels < 0 ? "- " : "+ ") + format(Math.abs(pixels)) + "px)";
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
