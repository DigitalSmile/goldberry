package io.github.digitalsmile.goldberry.paint;

import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// What a `canvas` draws — `docs/core-widgets.md` §1's `onPaint(ctx, size)`.
///
/// The one place an application is handed the toolkit's own rasterizer. It is
/// called during the frame, at the point in the tree where the canvas sits, with
/// the frame already **translated so `(0, 0)` is the canvas's own top-left** and
/// **clipped to its content box** — so a painter draws in its own coordinates and
/// cannot escape its bounds, however wrong its arithmetic is.
///
/// ## What it may do to the frame
///
/// Anything. Set a clip, a transform, a global alpha, leave them set — the
/// toolkit brackets the call in a `save`/`restore` pair and the context comes
/// back exactly as it was
/// (ADR-0193).
/// That pair is why this can be an application's code at all: every other painter
/// in the toolkit is trusted to unset what it set, and this one is not asked to
/// be.
///
/// ## What it must not do
///
/// **Not close the frame**, and not keep it: a [Frame] is valid for the call and
/// belongs to the window. Holding one past the return is holding a buffer the
/// compositor may already have taken back — the same rule
/// [io.github.digitalsmile.goldberry.natives.blend2d.BlendImage] states about
/// borrowed pixels.
///
/// It runs on the UI thread, inside the frame, so it is also the wrong place for
/// anything slow: a painter that blocks blocks the window.
@FunctionalInterface
public interface Painter {

    /// Draws one frame of this canvas.
    ///
    /// @param frame the surface, in logical pixels, with the origin at this
    ///        canvas's top-left corner
    /// @param size  the canvas's content size in logical pixels — what the layout
    ///        gave it, which is the only way a painter learns how big it is
    void paint(Frame frame, LogicalSize size);
}
