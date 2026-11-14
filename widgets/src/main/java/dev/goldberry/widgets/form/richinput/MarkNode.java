package dev.goldberry.widgets.form.richinput;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The node a stylesheet calls `mark`: one set of kinds that occurs in a
/// `rich-input`'s text, with a class per kind.
///
/// Drawn by nothing. It is there to be styled, and its box carries back what
/// the cascade resolved: the font on an empty paragraph, the `color` and the
/// `text-decoration` beside it, and the `background` as the box's fill. The
/// field reads those and draws its marked words with them.
///
/// @param classes the class of each kind in the set: `bold`, `link`, `mention`
record MarkNode(Set<String> classes) implements Widget.Leaf, Styled, Paints {

    MarkNode {
        classes = Set.copyOf(Objects.requireNonNull(classes, "classes"));
    }

    @Override
    public String cssType() {
        return "mark";
    }

    @Override
    public Set<String> classes() {
        return classes;
    }

    @Override
    public Object key() {
        return classes;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.text(context.paragraph(style, ""), style.color(), style.textFlow())
                .style(style);
    }
}
