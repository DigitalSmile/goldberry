package dev.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The line from one marker to the next — a **part**, and a drawing: §10 says
/// it is not announced, and it carries no semantics.
record TimelineLine() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "timeline-line";
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
