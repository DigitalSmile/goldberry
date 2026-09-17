package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// An entry's words and content — a **part**: the label with the time beside
/// it, and the body under them.
///
/// @param label   what happened
/// @param time    when, or null
/// @param content the body
record TimelineBody(String label, @Nullable String time, List<Widget> content) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "timeline-body";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(2);
        parts.add(new TimelineHead(label, time));
        if (!content.isEmpty()) {
            parts.add(new TimelineContent(content));
        }
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
