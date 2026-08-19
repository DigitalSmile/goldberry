package io.github.digitalsmile.goldberry.widgets.controls.segmented;

import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// The box a [Segmented]'s segments are laid along, and the one its indicator is
/// placed against — a **part**, and the reason there is one at all.
///
/// ## Why the bar is not the track
///
/// The indicator is absolutely positioned and its width is a percentage. Yoga
/// resolves an in-flow child's percentage against its parent's **content** box
/// and an absolute child's against the parent's **padding** box — CSS's rule,
/// and a real 4px of disagreement on a bar whose padding is 2. A pill one-third
/// of the padding box is not one-third of the row it is meant to cover, and the
/// error is per segment, so the last one is visibly off.
///
/// A track with no padding of its own makes the two bases the same box. The bar
/// keeps the padding, the border and the radius; the track keeps the grid. That
/// is `slider`'s anatomy for the same reason it grew one: two boxes were doing
/// one job, and the day a third thing joined they stopped being the same box
/// ([ADR-0080](../../../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md),
/// [ADR-0099](../../../../../../../../book/src/adr/0099-an-indicator-travels-on-a-grid.md)).
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

    /// The indicator **first**, so it is painted first and the labels sit on top
    /// of it. A box tree has no z-order beyond document order (ADR-0053), which
    /// is the whole of why this is a list and not a decision.
    @Override
    public List<Widget> children() {
        var count = (int) segments.stream().filter(Option.class::isInstance).count();
        if (count == 0) {
            // A bar with no segments has nothing to indicate. Not an error: a
            // group whose options have not loaded is a normal frame.
            return segments;
        }
        var children = new ArrayList<Widget>(segments.size() + 1);
        children.add(new SegmentedIndicator(index, count));
        children.addAll(segments);
        return List.copyOf(children);
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
    /// ([ADR-0099](../../../../../../../../book/src/adr/0099-an-indicator-travels-on-a-grid.md)).
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var count = (int) segments.stream().filter(Option.class::isInstance).count();
        if (count == 0) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
        var share = StyleLength.percent((float) (100.0 / count));
        var cells = new ArrayList<Box>(children.size());
        for (var i = 0; i < children.size(); i++) {
            var child = children.get(i);
            // The indicator is child 0 and sizes itself -- its width is the same
            // proportion, written through `restyle` so that the *travel* beside
            // it can transition. Everything after it is a segment.
            cells.add(i == 0 ? child : child.size(share, child.height()));
        }
        return Box.of().style(style).children(cells.toArray(Box[]::new));
    }
}
