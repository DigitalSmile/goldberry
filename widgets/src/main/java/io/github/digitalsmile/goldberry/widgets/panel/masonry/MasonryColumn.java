package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import java.util.List;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
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
/// ## The width is a rule's, and it used to be the widget's
///
/// `flex-basis: 0` with `flex-grow: 1`, in `controls.css`. Every column starts
/// from nothing and they share the whole row, so `1/n` needs no `n` — which is
/// what this could not say until §8's last flex property was resolved
/// ([ADR-0373]). Before that the count was carried into the widget and written
/// out as an inline `width: 100/n %`, which was `1/n` of the row **plus** the
/// gaps between the columns: three columns and two 12px gaps overflowed their
/// row by 24px, and the last column was the one that paid.
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
