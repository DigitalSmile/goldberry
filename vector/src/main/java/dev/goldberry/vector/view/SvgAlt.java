package dev.goldberry.vector.view;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The alt text, written inside a view whose document did not arrive: a
/// **part**, `svg-view-alt`.
///
/// @param text the alt text
record SvgAlt(String text) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "svg-view-alt";
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
