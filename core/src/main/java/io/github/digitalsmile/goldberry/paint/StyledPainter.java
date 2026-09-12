package io.github.digitalsmile.goldberry.paint;

import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// A [Painter] that is also told what the cascade resolved for the box it is
/// drawing in — `docs/gaps.md` G11.
///
/// ```java
/// new Canvas((frame, size, style) -> {
///     var title = Paragraph.of(style.font(), "Revenue");
///     title.paint(frame, 0, 0, size.width(), style.ink());
/// });
/// ```
///
/// **A subtype of [Painter] rather than a replacement for it**, and that is what
/// makes `new Canvas(...)` take either: a two-parameter lambda can only be a
/// `Painter` and a three-parameter one can only be this, so the compiler picks
/// without a cast and neither form is second class. A painter that does not read
/// the style should stay a `Painter` — most of the catalog's do, because a widget
/// that implements
/// [io.github.digitalsmile.goldberry.widget.style.Paints] already has the style
/// in `render` and captures what it needs.
///
/// ## It has to be bound before it is painted
///
/// [Box#painting] holds a `Painter`, and the style is resolved where the style
/// exists — in `render`, against the node. [#bound] is that step:
///
/// ```java
/// return Box.of().style(style).painting(painter.bound(context.canvasStyle(style)));
/// ```
///
/// Used unbound — handed straight to a `Box`, or to
/// [io.github.digitalsmile.goldberry.offscreen.Offscreen#paint] — it is painted
/// with [CanvasStyle#none()], which is the honest answer for a painter with no
/// cascade over it rather than a failure mid-frame.
@FunctionalInterface
public interface StyledPainter extends Painter {

    /// Draws one frame of this canvas, with the style its box resolved.
    ///
    /// @param frame the surface, in logical pixels, with the origin at this
    ///        canvas's top-left corner
    /// @param size  the canvas's content size in logical pixels
    /// @param style what the cascade resolved for this canvas, snapshotted when
    ///        its box was built
    void paint(Frame frame, LogicalSize size, CanvasStyle style);

    /// This painter, with the style it should be drawn under already decided.
    ///
    /// The result is an ordinary [Painter], which is what a [Box] can carry.
    public default Painter bound(CanvasStyle style) {
        Objects.requireNonNull(style, "style");
        return (frame, size) -> paint(frame, size, style);
    }

    /// Painting unbound, which is painting with no cascade over it.
    ///
    /// See the class note: this is what [io.github.digitalsmile.goldberry.offscreen.Offscreen]
    /// and a direct [Box#painting] do, and [CanvasStyle#none()] says plainly that
    /// the values are defaults rather than the cascade's answers.
    @Override
    default void paint(Frame frame, LogicalSize size) {
        paint(frame, size, CanvasStyle.none());
    }
}
