package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The `>` on a menu row that leads to a submenu — a **part**, so it is
/// CSS-selectable and not constructible
/// ([ADR-0065](../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// It is the only thing that distinguishes a row which opens something from a row
/// which does something, and a menu without it asks the reader to hover every row
/// to find out ([ADR-0113](../../../../../../../book/src/adr/0113-a-submenu-is-placed-beside-its-menu.md)).
///
/// A painter mark rather than Lucide's `chevron-right`, for [ItemCheck]'s reason
/// and one more: an icon owns native memory that must be closed exactly once, and
/// a menu is built and thrown away every time it opens.
record ItemChevron() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "item-chevron";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style)
                .mark(new Box.Mark(Box.Mark.Kind.CHEVRON_END, style.color(), 1.5));
    }
}
