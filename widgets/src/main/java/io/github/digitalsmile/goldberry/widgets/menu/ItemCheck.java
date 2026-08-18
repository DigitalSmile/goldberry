package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The tick column of an [Item] — a **part**, so it is CSS-selectable and not
/// constructible from a document
/// ([ADR-0065](../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// **Always built, checked or not.** A column that appeared with the first tick
/// would shift every label in the menu sideways the moment one item became
/// checkable — and `radio-dot`'s reason applies too: a node that only exists
/// while something is on cannot transition, because the first frame of a newly
/// built element starts nothing.
///
/// The mark is drawn only when checked; the box is there either way, and its
/// width is `controls.css`'s.
///
/// @param checked whether to draw the tick
record ItemCheck(boolean checked) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "item-check";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var box = Box.of().style(style);
        return checked
                ? box.mark(new Box.Mark(Box.Mark.Kind.CHECK, style.color(), 2))
                : box;
    }
}
