package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

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
