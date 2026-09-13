package io.github.digitalsmile.goldberry.content.select;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.handler.Located;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The wash behind a selection — `selection-layer`, a **part**.
///
/// ## Why a painter and not a class on six hundred words
///
/// The obvious implementation is a `selected` class on each covered word, and it is
/// the wrong one by an order of magnitude: a class is part of a widget's description,
/// so every pointer move during a drag would rebuild the document and re-resolve six
/// hundred styles. That is the shape of frame ADR-0299 had just removed.
///
/// A painter reads **mutable state** instead. The selection is a field the pointer
/// handler writes and this reads, so a drag costs one `Host.repaint()` and one paint
/// pass: no build, no cascade, no layout (ADR-0301).
///
/// ## Why it is absolutely positioned, and first
///
/// - **Absolute**, inset to nothing, so it fills the document's padding box exactly
///   and contributes no layout of its own. Being [Located] it is then told that
///   rectangle in window coordinates, which is what converts the geometry's window
///   rectangles into this painter's own space — with no assumption about the
///   document's padding or borders.
/// - **First**, because paint order is document order: the wash is drawn before the
///   words, so it is behind them. Drawing a translucent wash *over* the text is the
///   other way to do it and it dims what it highlights.
///
/// The colour is the cascade's: `selection-layer { color: var(--gb-selection) }`, so a
/// theme decides what a selection looks like and an application can override it.
record SelectionLayer(Selection selection, WordGeometry geometry) implements Widget.Leaf, Styled, Paints, Located {

    @Override
    public String cssType() {
        return "selection-layer";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        selection.layerAt(self);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var wash = style.color();
        return Box.of()
                .style(style)
                .position(Position.ABSOLUTE)
                .inset(Insets.all(Length.points(0)))
                .painting((frame, size) -> {
                    // Read at paint time rather than captured at build: that is what
                    // makes a drag a repaint. `rectangles` allocates only while
                    // something is selected, and nothing at all when it is not.
                    //
                    // The origin is read here too, and from the same frame's geometry
                    // as the rectangles: both are what the *last* frame laid out
                    // (`Located` is told after a frame is painted), so their
                    // difference is right even while a scroll is moving both.
                    var origin = selection.layer();
                    for (var rectangle : rectangles()) {
                        frame.fillRect(
                                rectangle.left() - origin.left(),
                                rectangle.top() - origin.top(),
                                rectangle.width(),
                                rectangle.height(),
                                wash);
                    }
                });
    }

    private List<LogicalRect> rectangles() {
        return geometry.rectangles(selection.anchor(), selection.focus());
    }
}
