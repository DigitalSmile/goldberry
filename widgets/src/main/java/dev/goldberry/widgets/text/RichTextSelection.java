package dev.goldberry.widgets.text;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The part a stylesheet calls `rich-text-selection`: what colour a selection
/// in a `rich-text` is highlighted in, as its `background`.
///
/// Read and not drawn. Its `rich-text` takes the colour off its box and paints
/// the highlight behind the selected words itself, on every line they reach.
record RichTextSelection() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "rich-text-selection";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }
}
