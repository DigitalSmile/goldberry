package io.github.digitalsmile.goldberry.widgets.menu;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The `>` on a menu row that leads to a submenu — a **part**, so it is
/// CSS-selectable and not constructible
/// (ADR-0065).
///
/// It is the only thing that distinguishes a row which opens something from a row
/// which does something, and a menu without it asks the reader to hover every row
/// to find out (ADR-0113).
///
/// A painter mark rather than Lucide's `chevron-right`, for [ItemLead]'s reason
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
        return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CHEVRON_END, style.color(), 1.5));
    }
}
