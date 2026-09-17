package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One side of the axis — a **part**. Holds the entry's body on the side it
/// sits on, and nothing on the other, so an alternating timeline keeps its
/// axis in the middle.
///
/// @param content the body, or nothing
record TimelineSide(List<Widget> content) implements Widget.Leaf, Styled, Paints {

    @Override
    public List<Widget> children() {
        return content;
    }

    @Override
    public String cssType() {
        return "timeline-side";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
