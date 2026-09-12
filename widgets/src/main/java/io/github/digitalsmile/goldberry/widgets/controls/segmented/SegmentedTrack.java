package io.github.digitalsmile.goldberry.widgets.controls.segmented;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;

/// The box a [Segmented]'s segments are laid along, and the one its indicator is
/// placed against — a **part**, and the reason there is one at all.
///
/// ## Why the bar is not the track
///
/// The indicator is absolutely positioned and its width is a percentage. Yoga
/// resolves an in-flow child's percentage against its parent's **content** box
/// and an absolute child's against the parent's **padding** box — CSS's rule,
/// and a real 2px of disagreement on a bar whose padding is 1. A pill one-third
/// of the padding box is not one-third of the row it is meant to cover, and the
/// error is per segment, so the last one is visibly off.
///
/// A track with no padding of its own makes the two bases the same box. The bar
/// keeps the padding, the border and the radius; the track keeps the grid, and
/// the hairlines between its cells are placed against it for the same reason
/// (ADR-0217). That
/// is `slider`'s anatomy for the same reason it grew one: two boxes were doing
/// one job, and the day a third thing joined they stopped being the same box
/// (ADR-0080,
/// ADR-0099).
///
/// @param segments the options, already told whether they are selected
/// @param index    the selected segment, or -1 when the value matches none
record SegmentedTrack(List<Widget> segments, int index) implements Widget.Leaf, Styled, Paints {

    SegmentedTrack {
        segments = List.copyOf(segments == null ? List.of() : segments);
    }

    @Override
    public String cssType() {
        return "segmented-track";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// **Dividers, then the indicator, then the labels** — a box tree has no
    /// z-order beyond document order (ADR-0053), so this list *is* the stacking
    /// and it is a decision rather than an accident.
    ///
    /// The hairlines go under the pill because the pill **travels**: painted
    /// after it, a divider would draw a line across the moving fill for the
    /// 160 ms it takes to cross. Under it, the pill covers whatever it passes.
    /// The labels go on top of both, because a segment's own wash is translucent
    /// and its text has to be legible on the fill (ADR-0217).
    @Override
    public List<Widget> children() {
        var count = optionCount();
        if (count == 0) {
            // A bar with no segments has nothing to indicate. Not an error: a
            // group whose options have not loaded is a normal frame.
            return segments;
        }
        var children = new ArrayList<Widget>(segments.size() + count);
        for (var boundary = 1; boundary < count; boundary++) {
            children.add(new SegmentedDivider(boundary, count, index));
        }
        children.add(new SegmentedIndicator(index, count));
        children.addAll(segments);
        return List.copyOf(children);
    }

    /// How many of this track's children are segments.
    ///
    /// Not `segments.size()`: a bar carries what a document wrote, and something
    /// that is not an [Option] is laid out and left alone rather than counted —
    /// a heading between two segments is not a segment, and counting it would put
    /// the indicator one cell along from the option it marks.
    private int optionCount() {
        return (int) segments.stream().filter(Option.class::isInstance).count();
    }

    /// The cells, all the same width, and the width is a **proportion**.
    ///
    /// This is where the grid is made, and it is made here rather than in
    /// `controls.css` because a stylesheet cannot count the segments. `flex-grow`
    /// alone would not do it either: with a content basis each cell is its label
    /// plus an equal *share* of the space left over, so three cells with three
    /// different labels are three different widths and "one segment along" stops
    /// being a distance anything can name.
    ///
    /// The consequence is worth stating where it happens: **a segmented control
    /// has no width of its own.** A cell is a percentage, so the track's content
    /// size is indefinite and the bar takes the width it is given — filling its
    /// parent when nothing gives it one. That is the trade the travelling
    /// indicator costs, and §3's row records it
    /// (ADR-0099).
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var count = optionCount();
        if (count == 0) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
        var share = Length.percent((float) (100.0 / count));
        // The parts come first and size themselves: `count - 1` dividers, then
        // the indicator, whose width is the same proportion written through
        // `restyle` so that the travel beside it can transition. Everything after
        // them is what the document wrote.
        var parts = count; // (count - 1) dividers, and one indicator
        var cells = new ArrayList<Box>(children.size());
        var seen = 0;
        for (var i = 0; i < children.size(); i++) {
            var child = children.get(i);
            if (i < parts) {
                cells.add(child);
                continue;
            }
            var cell = child.size(share, child.height());
            if (segments.get(i - parts) instanceof Option) {
                // §3's "radius 8 outer, 0 between", on the segment: a cell is
                // round only where the bar it is joined into is. Which cell is an
                // end is a fact about a count, and no selector can count -- so
                // the *radius* stays in `controls.css` and only the choice of
                // which corners keep it is made here (ADR-0217).
                cell = cell.decoration(
                        cell.decoration().corners(cell.decoration().corners().inRow(seen == 0, seen == count - 1)));
                seen++;
            }
            cells.add(cell);
        }
        return Box.of().style(style).children(cells.toArray(Box[]::new));
    }
}
