package io.github.digitalsmile.goldberry.widgets.controls.segmented;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.style.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One of the hairlines between a [Segmented]'s segments — §3's "1px divider in
/// `--gb-border`", and a **part**.
///
/// ## Why it is a node and not an edge
///
/// §8's subset has one `border` and no per-edge longhands, so "a line on the left
/// of every segment but the first" is not a declaration anything can write. A box
/// one pixel wide with a background is
/// ADR-0215's
/// answer to exactly that question, one widget later — the same shape as
/// `table-rule` and `separator`.
///
/// ## Why it is out of flow
///
/// A divider in flow would take a pixel of the row, and the row is a **grid**:
/// every segment is exactly `1/n` of the track, which is what lets the indicator
/// travel by a percentage of its own width and never measure anything
/// (ADR-0099).
/// Three dividers between four segments would make each cell `(100% - 3px) / 4`,
/// which no percentage names. Absolute, at a percentage of the track, costs the
/// grid nothing.
///
/// ## Why the two beside the selection are invisible
///
/// A hairline at the edge of the filled pill draws a border between the selection
/// and the segment next to it, which is a line the selection already is. Both
/// neighbours go, not one: the pill covers the boundary on its left and abuts the
/// one on its right, and hiding only what is covered would make the control
/// asymmetric for no reason a reader could see.
///
/// They **fade** rather than vanish, on §1.7's `fast`, because the pill takes
/// `base` to travel: a hairline that blinked out the instant a segment was
/// clicked would beat the movement that explains it.
///
/// @param boundary which gap this is — 1 is between the first two segments, and
///                 there are `count - 1` of them
/// @param count    how many segments there are, which is the grid
/// @param index    the selected segment, or -1 for none
record SegmentedDivider(int boundary, int count, int index) implements Widget.Leaf, Styled, Paints {

    SegmentedDivider {
        if (count <= 1) {
            throw new IllegalArgumentException("a row of " + count + " segment(s) has no gap to divide;"
                    + " SegmentedTrack builds one divider per boundary");
        }
        if (boundary < 1 || boundary >= count) {
            throw new IllegalArgumentException(
                    "boundary " + boundary + " is not between two of " + count + " segments");
        }
    }

    @Override
    public String cssType() {
        return "segmented-divider";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// Whether the pill is on one side of this line or the other.
    boolean besideTheSelection() {
        return boundary == index || boundary == index + 1;
    }

    /// Where along the track this line sits, and whether it shows.
    ///
    /// A percentage, so it needs no measurement — the same reason the indicator's
    /// width is one. Written here rather than in `controls.css` because both
    /// numbers come from a **count**, and a selector cannot count the segments.
    /// The stylesheet keeps what a theme should own: the colour, the width, and
    /// how long the fade takes.
    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        var placed = resolved.inset(new Insets(
                StyleLength.points(0), StyleLength.UNDEFINED, StyleLength.points(0), StyleLength.percent((float)
                        (100.0 * boundary / count))));
        return besideTheSelection() ? placed.opacity(0) : placed;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // Out of flow in Java rather than in the stylesheet, for the indicator's
        // reason: a rule that put this back in flow would take a pixel out of
        // every cell and break the grid rather than restyle the line.
        return Box.of().style(style).position(PositionType.ABSOLUTE);
    }
}
