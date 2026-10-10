package dev.goldberry.image.lottie;

import dev.goldberry.image.Image;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// An offscreen raster the size of the composition's box, for what cannot be
/// drawn straight onto the frame: a matte, a mask, a gradient stroke.
///
/// Each of those is "draw this, draw that, keep the first where the second
/// is", and the painter's compositing operators are not public. So the two
/// pictures are drawn into two of these and combined here, pixel by pixel, in
/// Java, and the result is drawn onto the frame as an image. That is slower
/// than drawing straight through, and it is paid only by a layer that asks for
/// it — Telegram forbids masks and gradient strokes in a sticker, and a matte
/// is the one of the three a sticker uses.
final class Scratch {

    private final PixelBuffer pixels;
    private final Frame frame;

    /// A cleared raster of `width` by `height` device pixels at `scale`.
    Scratch(int width, int height, float scale) {
        pixels = PixelBuffer.allocate(new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED);
        // Synchronous: a sticker-sized raster has nothing to divide between
        // threads, and waking them would cost more than it saves.
        frame = Frame.over(pixels, new DisplayScale(scale), 0);
    }

    /// The frame to draw into, until [#finish()].
    Frame frame() {
        return frame;
    }

    /// Ends the frame, so the pixels are complete.
    void finish() {
        frame.end();
    }

    /// The pixels as an image, handed over rather than copied.
    Image image() {
        return Image.of(pixels);
    }

    /// Each pixel's alpha, 0 to 255.
    int[] alpha() {
        return read(false);
    }

    /// Each pixel's luminance over black, 0 to 255: a transparent pixel is dark.
    int[] luma() {
        return read(true);
    }

    private int[] read(boolean luma) {
        var size = pixels.size();
        var out = new int[size.width() * size.height()];
        var bytes = pixels.pixels();
        var i = 0;
        for (var y = 0; y < size.height(); y++) {
            var row = y * pixels.stride();
            for (var x = 0; x < size.width(); x++) {
                var argb = bytes.getInt(row + x * 4);
                if (luma) {
                    // Premultiplied, so this is the luminance composited over
                    // black, which is how a luma matte reads a transparent pixel.
                    var r = (argb >>> 16) & 0xFF;
                    var g = (argb >>> 8) & 0xFF;
                    var b = argb & 0xFF;
                    out[i++] = (int) Math.round(0.2126 * r + 0.7152 * g + 0.0722 * b);
                } else {
                    out[i++] = argb >>> 24;
                }
            }
        }
        return out;
    }

    /// Scales every pixel by `factor` (0 to 255, one per pixel), which keeps a
    /// premultiplied pixel premultiplied.
    void multiply(int[] factor) {
        var size = pixels.size();
        var bytes = pixels.pixels();
        var i = 0;
        for (var y = 0; y < size.height(); y++) {
            var row = y * pixels.stride();
            for (var x = 0; x < size.width(); x++) {
                var f = factor[i++];
                if (f >= 255) {
                    continue;
                }
                var at = row + x * 4;
                if (f <= 0) {
                    bytes.putInt(at, 0);
                    continue;
                }
                var argb = bytes.getInt(at);
                var a = scale((argb >>> 24) & 0xFF, f);
                var r = scale((argb >>> 16) & 0xFF, f);
                var g = scale((argb >>> 8) & 0xFF, f);
                var b = scale(argb & 0xFF, f);
                bytes.putInt(at, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static int scale(int channel, int factor) {
        return (channel * factor + 127) / 255;
    }
}
