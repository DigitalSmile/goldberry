package dev.goldberry.widgets.form.field;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A [Field]'s label — a **part**, styleable and not constructible.
///
/// It carries the required marker in its own text rather than as a second node.
/// A separate `field-required` node would be styleable, which sounds like what a
/// part should be — but the marker is *part of the sentence*: it has to sit
/// against the last letter of the label with no gap a flex row can promise, and a
/// row of two text nodes cannot say "no space here" in the stylesheet's subset.
///
/// Colouring it therefore has to be a property of the whole label, which is what
/// `.required` is for. That is a real cost and it is the smaller one: a marker
/// in the wrong place is visible on every field, and a marker that cannot be
/// coloured on its own is visible on none.
///
/// @param text     the label, which may be empty
/// @param required whether the marker is appended
record FieldLabel(String text, boolean required) implements Widget.Leaf, Styled, Paints {

    /// What marks a required field. An asterisk, which is what every form on
    /// every platform has used for forty years — colour is never the only carrier
    /// of meaning, and this is the character that carries it.
    private static final String MARKER = " *";

    @Override
    public String cssType() {
        return "field-label";
    }

    @Override
    public Set<String> classes() {
        return required ? Set.of("required") : Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (text.isEmpty()) {
            return Box.of().style(style);
        }
        var written = required ? text + MARKER : text;
        return Box.text(context.paragraph(style, written), style.color()).style(style);
    }
}
