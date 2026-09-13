package io.github.digitalsmile.goldberry.widgets.controls.chip;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The 6-point status dot before a [Chip]'s label — a **part**, so it is
/// CSS-selectable and not constructible
/// (ADR-0065).
///
/// ## It is a box, not a mark
///
/// Every other small glyph in this catalog is a [Box.Mark] — a tick, a ×, a
/// chevron — because each is a *stroke* whose geometry the painter has to know.
/// A dot is a filled square with a `full` radius, which is what a box already is,
/// and adding a `DOT` kind to the painter would put a second way to draw a circle
/// beside `border-radius`.
///
/// The consequence is the reason it is worth stating: the dot takes its colour
/// from `background`, not from `color`, so `chip.danger chip-dot { background: … }`
/// is the rule that moves it — and a chip's dot **does not** inherit the label's
/// ink. That is what lets a muted chip carry a live red dot, which is the only
/// arrangement the widget is really for.
record ChipDot() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "chip-dot";
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
