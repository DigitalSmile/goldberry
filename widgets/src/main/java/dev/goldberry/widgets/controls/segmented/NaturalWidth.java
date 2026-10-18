package dev.goldberry.widgets.controls.segmented;

import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;

/// How wide a box would be on one line with nothing to squeeze it: its border,
/// its padding, and its content laid side by side with its gap between.
///
/// What a segment is sized from. Every cell of a bar is the width of its widest
/// segment, which is the one width that keeps the cells equal, so the indicator
/// still travels by its own width, and keeps the bar no wider than its labels.
///
/// Read off the box the segment rendered rather than off last frame's layout:
/// a text box's width is its shaped paragraph's, and an icon box is sized to
/// its glyph. So it is exact on the first frame and costs no `Measured` round
/// trip, `select`'s field width's reasoning.
///
/// It reads a **row**: a box laid out as a column would be as wide as its widest
/// child, and nothing a segment draws is one.
final class NaturalWidth {

    private NaturalWidth() {}

    /// The natural width of `box`, in logical pixels.
    static double of(Box box) {
        if (box.width() instanceof Length.Points points) {
            return points.value();
        }
        var content = 0.0;
        var text = box.text();
        if (text != null) {
            var paragraph = text.paragraph();
            content += paragraph.widthBetween(0, paragraph.text().length());
        }
        var counted = 0;
        for (var child : box.children()) {
            // An absolutely placed child takes no room in the row.
            if (child.position() == Position.ABSOLUTE) {
                continue;
            }
            content += of(child)
                    + points(child.margin().left())
                    + points(child.margin().right());
            counted++;
        }
        if (counted > 1) {
            content += points(box.columnGap()) * (counted - 1);
        }
        var border = box.decoration().border();
        return content
                + points(box.padding().left())
                + points(box.padding().right())
                + border.left().width()
                + border.right().width();
    }

    /// A length in points, and zero for anything that is not one: a percentage
    /// of a cell whose width is the thing being worked out cannot be resolved.
    private static double points(Length length) {
        return length instanceof Length.Points points ? points.value() : 0;
    }
}
