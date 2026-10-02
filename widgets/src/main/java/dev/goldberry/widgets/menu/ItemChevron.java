package dev.goldberry.widgets.menu;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The `>` on a menu row that leads to a submenu: a part, so a stylesheet can
/// select it as `item-chevron` and a document cannot write it.
///
/// It is the only thing that distinguishes a row which opens something from a
/// row which does something; a menu without it asks the reader to hover every
/// row to find out.
///
/// A painter mark rather than an icon, because an icon owns native memory that
/// must be closed exactly once, and a menu is built and thrown away every time
/// it opens.
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
