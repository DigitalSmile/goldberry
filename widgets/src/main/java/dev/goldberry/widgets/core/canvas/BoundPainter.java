package dev.goldberry.widgets.core.canvas;

import dev.goldberry.paint.CanvasStyle;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Painter;
import dev.goldberry.paint.StyledPainter;
import dev.goldberry.render.model.LogicalSize;

/// A [StyledPainter] with the style it is drawn under decided: what a
/// [Canvas] puts on its box.
///
/// **A value, so that a canvas nothing changed is a box nothing changed.** The
/// render tree keeps a raster for an isolated box only while the box is equal
/// to last frame's, and `StyledPainter.bound` answers a new lambda every time,
/// which is equal to nothing. So a styled canvas under `isolation: isolate` was
/// drawn again on every frame while a plain one was copied.
///
/// Two are equal when they draw with the same painter, the painter compared by
/// identity, and the same style. For a still canvas this frame's time is left
/// out of the comparison: such a canvas is painted once and left there, so the
/// time it was painted at is history and not a change. For an animating one the
/// time is the whole point, and a new time is a new drawing.
///
/// @param painter the application's painter
/// @param style   what the cascade resolved for the canvas this frame
/// @param still   whether the canvas asks for no frames of its own, so that its
///                time is not part of what it draws
record BoundPainter(StyledPainter painter, CanvasStyle style, boolean still) implements Painter {

    @Override
    public void paint(Frame frame, LogicalSize size) {
        painter.paint(frame, size, style);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BoundPainter(var otherPainter, var otherStyle, var otherStill)
                && painter == otherPainter
                && still == otherStill
                && (still ? sameExceptTime(style, otherStyle) : style.equals(otherStyle));
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(painter) * 31 + style.font().hashCode() * 7 + style.ink();
    }

    private static boolean sameExceptTime(CanvasStyle one, CanvasStyle two) {
        return one.font().equals(two.font()) && one.ink() == two.ink() && one.reducedMotion() == two.reducedMotion();
    }
}
