package io.github.digitalsmile.goldberry.widgets.form.textinput;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The text inside a [TextInput] — a **part**, styleable and not constructible
/// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// A node of its own for [io.github.digitalsmile.goldberry.widgets.controls.select]'s
/// reason: a box with text is a measured leaf and Yoga never lays a measured
/// node's children out, so a field that held its own text could hold neither the
/// caret nor the selection behind it.
///
/// It carries whether it is showing the **placeholder**, expressed as a class for
/// the same reason `select-value` does: §8's subset has no pseudo-class meaning
/// "this is standing in for a value", and §3 wants `--gb-text-muted` here against
/// `--gb-text` for a real one.
///
/// ## Masking happens above this
///
/// A `password` field hands this the bullets rather than the characters, and this
/// node never sees the real text. That is not tidiness: what a widget hands to
/// [Paints.Context#paragraph] is shaped, cached by its own text, and drawn — so a
/// part that received the password and chose not to draw it would still have put
/// it in the paragraph cache.
///
/// @param text        what to draw, already masked if the field masks
/// @param placeholder whether `text` is the placeholder rather than a value
record TextValue(String text, boolean placeholder) implements Widget.Leaf, Styled, Paints {

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
            // An empty field is a box with nothing in it rather than a paragraph
            // of no characters, which would still report a line's height.
            return Box.of().style(style);
        }
        return Box.text(context.paragraph(style, text), style.color()).style(style);
    }
}
