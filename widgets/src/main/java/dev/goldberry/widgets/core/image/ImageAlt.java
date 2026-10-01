package dev.goldberry.widgets.core.image;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The alt text, written inside an image that failed to load — a **part**,
/// `image-alt`.
///
/// What a browser does, and for the reason it does: the words were written for a
/// reader who cannot see the picture, and a picture that did not arrive is that
/// case for everybody.
///
/// @param text the alt text
record ImageAlt(String text) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "image-alt";
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
