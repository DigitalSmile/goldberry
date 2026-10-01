package dev.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// One run of an entry's words — `timeline-label` or `timeline-time`, two CSS
/// types over one record because the only difference is the rank each takes.
///
/// @param text what it says
/// @param type its CSS type
record TimelineText(String text, String type) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return type;
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.text(context.paragraph(style, text), style.color()).style(style);
    }
}
