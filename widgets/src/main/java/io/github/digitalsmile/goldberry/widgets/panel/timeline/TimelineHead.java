package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The label and the time on one line — a **part**. Two text parts rather
/// than one string, so the time can be `caption` in muted ink beside a
/// `body-strong` label without joining its text run.
///
/// @param label what happened
/// @param time  when, or null
record TimelineHead(String label, @Nullable String time) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "timeline-head";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return time == null
                ? List.of(new TimelineText(label, "timeline-label"))
                : List.of(new TimelineText(label, "timeline-label"), new TimelineText(time, "timeline-time"));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
