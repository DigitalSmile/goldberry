package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;

/// The surface a [Window] paints into.
///
/// Coordinates are **logical**, like everything an application writes: a
/// rectangle at `(10, 10)` is ten points from the corner whether the display runs
/// at 100% or 150%, and [Frame] does the conversion. The physical size is
/// available for the rare code that needs it, but reaching for it usually means
/// something is about to be wrong on somebody's laptop.
///
/// Colours are `0xAARRGGBB` — the packing everyone already knows from CSS and
/// Java 2D. Blend2D's buffers want premultiplied BGRA in memory, and that
/// conversion happens here rather than in application code.
///
/// This is a placeholder for a real canvas. Blend2D takes over in M1 with paths,
/// gradients and text; `fill` and `fillRect` are what a blank window needs and
/// no more.
public final class Frame {

    private final PixelBuffer buffer;
    private final DisplayScale scale;

    Frame(PixelBuffer buffer, DisplayScale scale) {
        this.buffer = buffer;
        this.scale = scale;
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

    /// Fills the whole frame.
    ///
    /// @param argb a colour as `0xAARRGGBB`
    public void fill(int argb) {
        var premultiplied = premultiply(argb);
        var pixels = buffer.pixels();
        var width = buffer.size().width();
        for (var row = 0; row < buffer.size().height(); row++) {
            var base = row * buffer.stride();
            for (var column = 0; column < width; column++) {
                pixels.putInt(base + column * 4, premultiplied);
            }
        }
    }

    /// Fills a rectangle given in logical coordinates.
    ///
    /// Clipped to the frame rather than throwing: a rectangle that runs off the
    /// edge is ordinary in a UI, and layout has not run yet to prevent it.
    ///
    /// @param argb a colour as `0xAARRGGBB`
    public void fillRect(float x, float y, float width, float height, int argb) {
        var left = Math.max(0, scale.toPhysical(x));
        var top = Math.max(0, scale.toPhysical(y));
        var right = Math.min(buffer.size().width(), scale.toPhysical(x + width));
        var bottom = Math.min(buffer.size().height(), scale.toPhysical(y + height));
        if (left >= right || top >= bottom) {
            return;
        }

        var premultiplied = premultiply(argb);
        var pixels = buffer.pixels();
        for (var row = top; row < bottom; row++) {
            var base = row * buffer.stride();
            for (var column = left; column < right; column++) {
                pixels.putInt(base + column * 4, premultiplied);
            }
        }
    }

    /// Converts `0xAARRGGBB` to the premultiplied form the compositor expects.
    ///
    /// Compositing straight-alpha data as if it were premultiplied darkens every
    /// edge in the frame, which reads as "the antialiasing looks wrong" rather
    /// than as a format bug — so the conversion is not optional, and it is done
    /// once here instead of hoped for.
    static int premultiply(int argb) {
        var alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0xFF) {
            return argb;
        }
        if (alpha == 0) {
            return 0;
        }
        var red = ((argb >>> 16) & 0xFF) * alpha / 0xFF;
        var green = ((argb >>> 8) & 0xFF) * alpha / 0xFF;
        var blue = (argb & 0xFF) * alpha / 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
