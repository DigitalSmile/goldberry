package io.github.digitalsmile.goldberry.widgets.form.textinput;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The highlight behind selected text — a **part**, so the theme owns its
/// colour.
///
/// Drawn *behind* the text rather than over it, which is why it is the first
/// child: §1.2 requires the selected text to stay readable, and a translucent
/// wash over a glyph dims the glyph. So the selection is an opaque-enough
/// rectangle under the text and the text keeps its own colour — which is also
/// what makes `--gb-selection` a background token rather than a pair of them.
///
/// One rectangle, because a `text-input` is one line. `text-area` will want one
/// per line and that is a list of these rather than a different part.
///
/// @param visible whether anything is selected at all
record TextSelection(boolean visible) implements Widget.Leaf, Styled, Paints {

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
        if (!visible) {
            return Box.of();
        }
        return Box.of().style(style);
    }
}
