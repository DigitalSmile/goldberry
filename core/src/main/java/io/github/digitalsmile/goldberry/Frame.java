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
        fillPixels(0, 0, buffer.size().width(), buffer.size().height(), premultiply(argb));
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

        fillPixels(left, top, right - left, bottom - top, premultiply(argb));
    }

    /// Fills a rectangle of the buffer, in physical pixels.
    ///
    /// One row is built and then copied down the rectangle, rather than writing
    /// every pixel individually. At 1080p the per-pixel version is over two
    /// million `putInt` calls per frame, which is enough to be visible as
    /// hesitation while a window is being dragged — the copy runs at memory
    /// speed instead.
    private void fillPixels(int x, int y, int width, int height, int premultiplied) {
        if (width <= 0 || height <= 0) {
            return;
        }

        var rowBytes = new byte[Math.multiplyExact(width, 4)];
        for (var i = 0; i < rowBytes.length; i += 4) {
            // Little-endian, matching the BGRA memory order PixelBuffer
            // normalises to: blue lowest, alpha highest.
            rowBytes[i] = (byte) premultiplied;
            rowBytes[i + 1] = (byte) (premultiplied >>> 8);
            rowBytes[i + 2] = (byte) (premultiplied >>> 16);
            rowBytes[i + 3] = (byte) (premultiplied >>> 24);
        }

        var pixels = buffer.pixels();
        var offset = y * buffer.stride() + x * 4;
        for (var row = 0; row < height; row++) {
            pixels.put(offset, rowBytes, 0, rowBytes.length);
            offset += buffer.stride();
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
