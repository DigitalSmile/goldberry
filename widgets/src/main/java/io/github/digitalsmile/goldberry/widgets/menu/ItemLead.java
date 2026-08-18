package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The column before an [Item]'s label — a **part**, so it is CSS-selectable and
/// not constructible
/// ([ADR-0065](../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// ## One column, three things in it
///
/// A tick, an icon, or nothing — and never a tick *and* an icon, which is what
/// every desktop menu does and what this got wrong twice
/// ([ADR-0113](../../../../../../../book/src/adr/0113-a-submenu-is-placed-beside-its-menu.md)):
/// first by giving every row a tick column whether its menu had anything
/// checkable in it or not, and then, once that was fixed, by drawing an icon
/// *after* the tick column so a row with an icon was indented further than the
/// rows above it. The showcase's menu had both faults at once and read as a
/// ragged left edge with an unexplained gutter.
///
/// So the leading slot is one part with one width. A menu reserves it when
/// **anything in it** has an icon or is checkable, and then every row has one —
/// which is what keeps the labels in a line.
///
/// @param checked whether to draw a tick
/// @param icon    the row's icon, drawn when there is no tick to draw
record ItemLead(boolean checked, Icon icon) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "item-lead";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// Mirrored to `:checked`, so a tick is a stylesheet's business and not a
    /// second drawing.
    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var box = Box.of().style(style);
        if (checked) {
            return box.mark(new Box.Mark(Box.Mark.Kind.CHECK, style.color(), 2));
        }
        // An icon where the tick would be. `Box.icon` sizes the box to the icon,
        // so the column's own width from `controls.css` is overridden by it —
        // which is right: the icon is what has to line up with the tick, and both
        // are 16.
        return icon == null ? box : Box.icon(icon, style.color()).style(style);
    }
}
