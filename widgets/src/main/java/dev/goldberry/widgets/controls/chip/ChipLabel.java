package dev.goldberry.widgets.controls.chip;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A [Chip]'s words — a **part**, and the same one
/// [dev.goldberry.widgets.controls.select.SelectChip.SelectChipLabel]
/// is, for the same reason.
///
/// A box with text is a **measured leaf** and Yoga never lays a measured node's
/// children out. So a chip that drew its own text could hold neither a dot before
/// it nor a × after it, and the label has to be a node of its own for either to
/// have anywhere to go.
///
/// @param text the chip's label, resolved
record ChipLabel(String text) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "chip-label";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(Box.text(context.paragraph(style, text), style.color()));
    }
}
