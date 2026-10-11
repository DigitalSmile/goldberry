package dev.goldberry.paint;

import java.util.Objects;

import dev.goldberry.layout.Length;

/// How far in from a box's outer edge something starts, on each side: the
/// border, for the padding box, and the border and the padding together, for
/// the content box.
///
/// A border takes room outside the padding, as in CSS, so a box's content
/// starts inside both. Every painter that places content in a box, and the hit
/// test that finds it, reads it from here, so they cannot disagree about where
/// that is.
///
/// @param top    in logical pixels
/// @param right  in logical pixels
/// @param bottom in logical pixels
/// @param left   in logical pixels
public record BoxEdges(double top, double right, double bottom, double left) {

    /// Where the content box of `box`, laid out `width` by `height`, starts:
    /// inside its border and its padding. A percentage padding is of the box's
    /// own width or height, as the painter has always resolved it.
    public static BoxEdges content(Box box, double width, double height) {
        Objects.requireNonNull(box, "box");
        var border = box.decoration().border();
        var padding = box.padding();
        return new BoxEdges(
                border.top().width() + Length.resolve(padding.top(), (float) height),
                border.right().width() + Length.resolve(padding.right(), (float) width),
                border.bottom().width() + Length.resolve(padding.bottom(), (float) height),
                border.left().width() + Length.resolve(padding.left(), (float) width));
    }

    /// Where the padding box of `box` starts: inside its border.
    public static BoxEdges padding(Box box) {
        Objects.requireNonNull(box, "box");
        var border = box.decoration().border();
        return new BoxEdges(
                border.top().width(),
                border.right().width(),
                border.bottom().width(),
                border.left().width());
    }

    /// The left and the right together: what comes off a width.
    public double horizontal() {
        return left + right;
    }

    /// The top and the bottom together: what comes off a height.
    public double vertical() {
        return top + bottom;
    }
}
