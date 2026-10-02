package dev.goldberry.example.ui.sheet;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.handler.Measured;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The box the icon sheet fills, and the thing that reports **how wide** it
/// turned out.
///
/// [IconsScreen] wants as many columns as fit, and nothing can tell a widget how
/// much room it has before it is laid out — the same wall
/// [dev.goldberry.widgets.panel.masonry.Masonry] hits for
/// heights, answered the same way: read last frame, act on the next
/// ([Measured]).
///
/// ## Why it is safe here, which is the only question [Measured] asks
///
/// Rule 3 is that **what it triggers must not change what it reports**. A widget
/// that resized itself from its own measurement would be told a new size, resize,
/// and never settle.
///
/// This reports a **width** and changes a **column count**. The width is the
/// parent's to decide — this node fills the viewport it is in — so re-columning
/// the masonry inside it changes how tall the sheet is and cannot change how wide
/// this box is. The number being reported is stable under the thing it causes,
/// which is exactly the argument `Masonry` makes for moving a card between
/// columns of equal width.
///
/// The height is deliberately ignored. Reacting to that *would* be the loop.
///
/// @param child   the masonry
/// @param onWidth told what the last frame made this box, in logical pixels
record IconSheet(Widget child, DoubleConsumer onWidth) implements Widget.Leaf, Styled, Paints, Measured {

    @Override
    public String cssType() {
        return "icon-sheet";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(child);
    }

    @Override
    public void measured(Extent bounds, Extent part) {
        onWidth.accept(bounds.width());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
