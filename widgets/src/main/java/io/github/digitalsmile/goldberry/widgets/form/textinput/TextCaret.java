package io.github.digitalsmile.goldberry.widgets.form.textinput;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The insertion point — a **part**, so a stylesheet owns its colour and its
/// width.
///
/// A node rather than a `Box.Mark`, because every mark's shape is fixed by its
/// kind and a caret's is not: it is as tall as a line of the field's own text and
/// as wide as the theme says. It is placed by [TextField], which is the only
/// thing that can measure where a character offset lands.
///
/// ## It draws nothing when it is not shown
///
/// A caret that existed only while a field had focus would be an element mounted
/// and unmounted twice a second by the blink, which is a rebuild of the field's
/// subtree for something that is one rectangle. So it is always in the tree and
/// `visible` is what changes — and an invisible caret is a box with **no
/// background**, not one with zero opacity, because §1.7's transition whitelist
/// includes `opacity` and a caret that faded would be a caret that is wrong for
/// 150 ms of every blink.
///
/// @param visible whether this is the half of the blink where it is drawn
record TextCaret(boolean visible) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "text-caret";
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
