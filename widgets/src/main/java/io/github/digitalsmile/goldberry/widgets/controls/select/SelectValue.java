package io.github.digitalsmile.goldberry.widgets.controls.select;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The text in a closed [Select] — a **part**, so it is CSS-selectable and not
/// constructible
/// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// A node of its own rather than text on the field, for the reason a `button`'s
/// content is boxes: a box with text is a measured leaf and Yoga never lays a
/// measured node's children out, so a field that held its own text could not also
/// hold the chevron beside it.
///
/// It carries whether it is showing the **placeholder**, which is the one thing a
/// stylesheet needs to tell apart here: §3's placeholder is `--gb-text-muted`
/// where a chosen value is `--gb-text`, and the two are otherwise the same node
/// in the same place. Expressed as a class rather than a pseudo-class because
/// nothing in §8's subset means "this is standing in for a value" and inventing
/// a pseudo-class for one widget would be inventing a language
/// ([ADR-0141](../../../../../../../../book/src/adr/0141-a-select-is-a-closed-control-and-a-list.md)).
///
/// @param text        the label to draw, which may be empty
/// @param placeholder whether `text` is the placeholder rather than a chosen
///                    option's label
record SelectValue(String text, boolean placeholder) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "select-value";
    }

    @Override
    public Set<String> classes() {
        return placeholder ? Set.of("placeholder") : Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (text.isEmpty()) {
            // An empty field is a box with nothing in it and not a paragraph of
            // no characters: a measured leaf over an empty string still reports a
            // line's height, which would make a select with no placeholder taller
            // than one with a value.
            return Box.of().style(style);
        }
        return Box.text(context.paragraph(style, text), style.color()).style(style);
    }
}
