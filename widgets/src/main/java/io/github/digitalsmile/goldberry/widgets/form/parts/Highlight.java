package io.github.digitalsmile.goldberry.widgets.form.parts;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The wash behind selected text — `text-selection`. See [Caret] for why this
/// package exists.
///
/// Drawn *behind* the glyphs rather than over them, which is why it is built
/// first: §1.2 requires selected text to stay readable, and a translucent wash
/// over a glyph dims the glyph.
///
/// **One per visual line.** A `text-input` has exactly one; a `text-area` has one
/// per line the selection covers, because a run of text that wraps is not a
/// rectangle. That is the only difference between the two controls' selections,
/// and it is why `Paragraph`'s measurements take a *line's* range.
///
/// @param visible whether this rectangle covers anything
public record Highlight(boolean visible) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "text-selection";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return visible ? Box.of().style(style) : Box.of();
    }
}
