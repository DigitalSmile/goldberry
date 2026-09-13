package io.github.digitalsmile.goldberry.widgets.controls.chip;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A [Chip]'s words — a **part**, and the same one
/// [io.github.digitalsmile.goldberry.widgets.controls.select.SelectChip.SelectChipLabel]
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
