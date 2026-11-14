package dev.goldberry.widgets.form.richinput;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.Length;

/// How far a `rich-input`'s text sits in from its border box on each side: the
/// padding and the border together.
///
/// The painter draws in the content box, but a pointer, the measured width and
/// the field's height are the border box's, and every conversion between the two
/// goes through this.
///
/// @param left   in from the left edge
/// @param top    down from the top edge
/// @param right  in from the right edge
/// @param bottom up from the bottom edge
record Edges(double left, double top, double right, double bottom) {

    static final Edges NONE = new Edges(0, 0, 0, 0);

    static Edges of(ComputedStyle style) {
        var padding = style.padding();
        var border = style.decoration().border();
        return new Edges(
                points(padding.left()) + border.left().width(),
                points(padding.top()) + border.top().width(),
                points(padding.right()) + border.right().width(),
                points(padding.bottom()) + border.bottom().width());
    }

    double horizontal() {
        return left + right;
    }

    double vertical() {
        return top + bottom;
    }

    private static double points(Length length) {
        return length instanceof Length.Points points ? points.value() : 0;
    }
}
