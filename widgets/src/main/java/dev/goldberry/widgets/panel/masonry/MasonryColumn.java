package dev.goldberry.widgets.panel.masonry;

import java.util.List;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// One column of a [Masonry] — a **part**.
///
/// Every column takes an equal share of the width, which is what makes the
/// measurement stable: a card's height is a function of the width it is laid out
/// at, and moving it between equal columns cannot change that. Unequal columns
/// would make this a loop rather than a layout, which is why `masonry` takes a
/// count rather than a list of widths.
///
/// ## The width is a rule's, not the widget's
///
/// `flex-basis: 0` with `flex-grow: 1`, in `controls.css`. Every column starts
/// from nothing and they share the whole row, so `1/n` needs no `n`. An inline
/// `width: 100/n %` would be `1/n` of the row **plus** the gaps between the
/// columns: three columns and two 12px gaps would overflow their row by 24px,
/// and the last column would be the one that paid.
///
/// `flex-grow: 1` alone would size the columns to their *content*, so a column
/// holding a wide chart would be wider than one holding a statistic — and then a
/// card's height would depend on which column it landed in, which is the loop
/// this layout is built to avoid.
///
/// @param children the cards in this column
record MasonryColumn(List<Widget> children) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "masonry-column";
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
