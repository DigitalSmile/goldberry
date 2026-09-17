package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// An entry's body, under its head — a **part** holding whatever the
/// application wrote inside the entry.
///
/// @param content the body
record TimelineContent(List<Widget> content) implements Widget.Leaf, Styled, Paints {

    @Override
    public List<Widget> children() {
        return content;
    }

    @Override
    public String cssType() {
        return "timeline-content";
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
