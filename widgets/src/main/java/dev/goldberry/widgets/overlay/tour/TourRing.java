package dev.goldberry.widgets.overlay.tour;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The edge drawn around the widget a [TourStop] is describing — a **part**.
///
/// Without it the target is "the part that is not dim", which reads as a hole in
/// the veil rather than as the subject. It matters most in the case that is
/// otherwise invisible: a widget whose own background is the window's, such as a
/// toolbar, has no boundary of its own for the veil to stop at.
///
/// It draws no fill, so the widget underneath is untouched — and it takes no
/// pointer, because the whole point of the cut-out is that the target stays live.
record TourRing(LogicalRect target) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "tour-ring";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }
}
