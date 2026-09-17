package io.github.digitalsmile.goldberry.widgets.nav.steps;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One line of a step's words — `step-label` or `step-description`, which are
/// two CSS types over one record because the only difference between them is
/// the rank the stylesheet gives each.
///
/// A box *holding* the text rather than the text itself, so the stylesheet can
/// give the label the marker's height and centre the words in it: a step's
/// label sits on the disc's centre whatever the line-height token is, which is
/// what keeps it aligned at every density (ADR-0344).
///
/// @param text what it says
/// @param type its CSS type
record StepText(String text, String type) implements Widget.Leaf, Styled, Paints {

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
        return Box.of().style(style).children(Box.text(context.paragraph(style, text), style.color()));
    }
}
