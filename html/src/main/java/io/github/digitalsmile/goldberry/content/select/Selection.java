package io.github.digitalsmile.goldberry.content.select;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// What is selected, as the two ends of a drag.
///
/// **Mutable, and that is the design.** Everything else in the widget layer is a
/// value rebuilt every frame; this is deliberately not, because a selection changes
/// on every pointer move and rebuilding a six-hundred-word document to say so is the
/// frame cost ADR-0299 removed. So the pointer handler writes here, the overlay's
/// painter reads here, and a drag is a repaint (ADR-0301).
///
/// Confined to the UI thread, like everything else a frame touches.
final class Selection {

    private Caret anchor = Caret.NONE;

    private Caret focus = Caret.NONE;

    /// Where the overlay was laid out, so the painter can turn the geometry's window
    /// rectangles into its own coordinates.
    private LogicalRect layer = LogicalRect.of(0, 0, 0, 0);

    Caret anchor() {
        return anchor;
    }

    Caret focus() {
        return focus;
    }

    LogicalRect layer() {
        return layer;
    }

    void layerAt(LogicalRect rect) {
        layer = rect;
    }

    /// Whether anything is selected — two ends that are not the same place.
    boolean isEmpty() {
        return anchor.isNone() || focus.isNone() || anchor.equals(focus);
    }

    /// Starts a drag at `caret`, which is also how a click clears a selection.
    void begin(Caret caret) {
        anchor = caret;
        focus = caret;
    }

    /// Moves the free end. The anchor stays where the press was, so dragging back
    /// past it selects the other way round without the ends swapping.
    void extendTo(Caret caret) {
        if (!caret.isNone()) {
            focus = caret;
        }
    }

    /// Selects everything between two places — a double-click's word, a triple-click's
    /// block, or `Ctrl+A`.
    void select(Caret from, Caret to) {
        anchor = from;
        focus = to;
    }

    void clear() {
        anchor = Caret.NONE;
        focus = Caret.NONE;
    }
}
