package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

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
