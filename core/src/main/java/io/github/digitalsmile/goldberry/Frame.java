package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendContext;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendImage;

/// The surface a [Window] paints into.
///
/// Coordinates are **logical**, like everything an application writes: a
/// rectangle at `(10, 10)` is ten points from the corner whether the display runs
/// at 100% or 150%. That conversion is not arithmetic done here — the Blend2D
/// context is scaled once when the frame begins, so a fractional coordinate
/// reaches the rasterizer intact and is antialiased across the physical pixels
/// it actually covers. A rectangle at logical `x = 10.5` on a 1.5&times; display
/// lands at physical 15.75 and looks like it; snapping it to 15 or 16 would move
/// it by a third of a logical pixel (ADR-0031).
///
/// Colours are `0xAARRGGBB` — the packing everyone already knows from CSS and
/// Java 2D — and are **not** premultiplied. The buffer underneath is, and
/// Blend2D converts when it composites. Callers who premultiply first get a
/// frame that is visibly too dark with nothing reporting a problem.
///
/// The physical size is available for the rare code that needs it, but reaching
/// for it usually means something is about to be wrong on somebody's laptop.
///
/// A frame is valid only for the duration of the paint callback it was handed
/// to. [Window] ends it before presenting, because pixels a context has not
/// finished with are not pixels worth showing.
public final class Frame {

    private final PixelBuffer buffer;
    private final DisplayScale scale;
    private final BlendImage image;
    private final BlendContext context;

    private boolean ended;

    Frame(PixelBuffer buffer, DisplayScale scale) {
        this.buffer = buffer;
        this.scale = scale;

        // The image is a view over the buffer, not a copy of it: when the
        // platform lends its own surface, Blend2D rasterizes straight into the
        // memory that will be presented, and the frame costs no blit at all.
        this.image = BlendImage.wrapping(
                buffer.pixels(), buffer.size().width(), buffer.size().height(), buffer.stride());
        try {
            this.context = BlendContext.on(image, scale.factor());
        } catch (RuntimeException | Error e) {
            image.close();
            throw e;
        }
    }

    /// The size to paint in, in logical pixels.
    public LogicalSize size() {
        return scale.toLogical(buffer.size());
    }

    /// The size of the underlying buffer, in real pixels.
    public PhysicalSize pixelSize() {
        return buffer.size();
    }

    /// The display scale this frame is being rasterized at.
    public DisplayScale scale() {
        return scale;
    }

    /// Fills the whole frame, **replacing** whatever is there.
    ///
    /// A replacement rather than a blend, because this is what a background is:
    /// blending a translucent colour over the previous frame composites onto it,
    /// so the same call every frame would darken until it was opaque.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fill(int argb) {
        requireOpen();
        context.clearTo(argb);
    }

    /// Fills a rectangle given in logical coordinates, blending over what is
    /// already there.
    ///
    /// Clipped to the frame rather than throwing: a rectangle that runs off the
    /// edge is ordinary in a UI, and layout has not run yet to prevent it.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillRect(float x, float y, float width, float height, int argb) {
        requireOpen();
        context.fillRect(x, y, width, height, argb);
    }

    /// Finishes the frame, so the pixels are complete before anything presents
    /// them.
    ///
    /// Package-private: ending a frame is [Window]'s job, and an application
    /// that could do it would be able to invalidate its own canvas halfway
    /// through painting.
    void end() {
        if (ended) {
            return;
        }
        ended = true;
        try {
            context.close();
        } finally {
            image.close();
        }
    }

    private void requireOpen() {
        if (ended) {
            throw new IllegalStateException(
                    "this frame has already been presented — a Frame is valid only inside the"
                            + " paint callback it was handed to");
        }
    }
}
