package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;

/// One card in a [Masonry], and the thing that reports how tall it turned out.
///
/// A wrapper rather than a rule on the card itself, because the card is the
/// **application's** widget: it cannot be asked to implement
/// [Measured], and a layout that only worked for cards the toolkit wrote would
/// not be a layout.
///
/// It adds a node per card and nothing else — no padding, no background, no
/// size of its own. What a stylesheet reaches through `masonry-cell` is the gap
/// between cards, and that is deliberately the column's rather than this one's.
///
/// @param child      the application's card
/// @param index      its position in the description, which is how the state
///                   banks its height
/// @param onMeasured told what the last frame laid it out as
record MasonryCell(Widget child, int index, OnMeasured onMeasured)
        implements Widget.Leaf, Styled, Paints, Measured {

    /// What a cell reports.
    @FunctionalInterface
    interface OnMeasured {

        /// @param height the cell's own height, in logical pixels
        void measured(double height);
    }

    @Override
    public String cssType() {
        return "masonry-cell";
    }

    @Override
    public Object key() {
        // Keyed by position, so a card moving between columns keeps its element
        // and its state -- a chart that reset its scroll or its animation every
        // time the layout settled would be worse than no masonry at all.
        return index;
    }

    @Override
    public List<Widget> children() {
        return List.of(child);
    }

    @Override
    public void measured(Extent bounds, Extent part) {
        onMeasured.measured(bounds.height());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
