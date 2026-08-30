package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import java.util.List;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One column of a [Masonry] — a **part**.
///
/// Every column takes an equal share of the width, which is what makes the
/// measurement stable: a card's height is a function of the width it is laid out
/// at, and moving it between equal columns cannot change that. Unequal columns
/// would make this a loop rather than a layout, which is why `masonry` takes a
/// count rather than a list of widths.
///
/// ## The width is written by the widget, not by a rule
///
/// `1/n` of the row, where `n` is a number **no selector can count** — which is
/// ADR-0099's
/// situation exactly, and takes its answer: `restyle` writes the inline value the
/// cascade cannot express. `flex-grow: 1` alone would size the columns to their
/// *content*, so a column holding a wide chart would be wider than one holding a
/// statistic — and then a card's height would depend on which column it landed
/// in, which is the loop this layout is built to avoid. `flex-basis: 0` would
/// have said it in CSS and is the one §8 property still unimplemented.
///
/// @param children the cards in this column
/// @param count    how many columns the row has, which is this one's share
record MasonryColumn(List<Widget> children, int count) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "masonry-column";
    }

    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        return resolved.width(StyleLength.percent((float) (100.0 / Math.max(1, count))));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
