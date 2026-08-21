package io.github.digitalsmile.goldberry.widgets.form.parts;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The text itself — `text-value`. See [Caret] for why this package exists.
///
/// A node of its own rather than text on the field, because a box with text is a
/// measured leaf and Yoga never lays a measured node's children out — so a field
/// that held its own text could hold neither the caret nor the wash behind it.
///
/// It carries whether it is showing the **placeholder**, as a class, because §8's
/// subset has no pseudo-class meaning "standing in for a value" and §3 wants
/// `--gb-text-placeholder` here against `--gb-text` for a real one.
///
/// A `password` hands this the bullets and never the characters — not tidiness:
/// what a widget passes to `Paints.Context.paragraph` is shaped, cached **by its
/// own text**, and drawn, so a part that received a password and chose not to
/// draw it would still have put it in the paragraph cache.
///
/// @param text        what to draw, already masked if the field masks
/// @param placeholder whether `text` is standing in for a value
public record Value(String text, boolean placeholder) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "text-value";
    }

    @Override
    public Set<String> classes() {
        return placeholder ? Set.of("placeholder") : Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (text.isEmpty()) {
            // Not a paragraph of no characters: a measured leaf over an empty
            // string still reports a line's height, so an empty field would stand
            // a line taller than it needs to.
            return Box.of().style(style);
        }
        return Box.text(context.paragraph(style, text), style.color()).style(style);
    }
}
