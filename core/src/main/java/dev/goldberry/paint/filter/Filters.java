package dev.goldberry.paint.filter;

import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

import dev.goldberry.css.compositing.FilterFunction;
import dev.goldberry.paint.shadow.CoverageBlur;
import dev.goldberry.render.PixelBuffer;

/// The `filter` functions, run over a raster's pixels in order.
///
/// The raster is premultiplied BGRA, which reads as `0xAARRGGBB` in little-endian
/// order. The colour functions are CSS Filter Effects' matrices, applied to each
/// pixel's unpremultiplied sRGB values; `blur()` is the three-box-pass
/// approximation of a Gaussian `text-shadow` uses, run on each channel; and
/// `drop-shadow()` is the raster's alpha blurred, coloured, offset and drawn
/// under it.
///
/// On the CPU, in Java, and the same on every backend: the raster a box is
/// painted into is the rasterizer's, and nothing places it on the GPU.
public final class Filters {

    private Filters() {}

    /// Applies `filters` to `buffer` in place.
    ///
    /// @param factor physical pixels per logical one, which a blur's radius and
    ///               a shadow's offset are scaled by
    public static void apply(PixelBuffer buffer, List<FilterFunction> filters, double factor) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(filters, "filters");
        if (filters.isEmpty()) {
            return;
        }
        var width = buffer.size().width();
        var height = buffer.size().height();
        var pixels = read(buffer, width, height);
        for (var filter : filters) {
            switch (filter) {
                case FilterFunction.Blur(var radius) -> blur(pixels, width, height, radius * factor);
                case FilterFunction.DropShadow shadow -> dropShadow(pixels, width, height, shadow, factor);
                case FilterFunction.Opacity(var amount) -> fade(pixels, amount);
                case FilterFunction.ColourFunction colour -> matrix(pixels, ColourMatrix.of(colour));
            }
        }
        write(buffer, pixels, width, height);
    }

    private static int[] read(PixelBuffer buffer, int width, int height) {
        var bytes = buffer.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        var stride = buffer.stride();
        var pixels = new int[width * height];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                pixels[y * width + x] = bytes.getInt(y * stride + x * 4);
            }
        }
        return pixels;
    }

    private static void write(PixelBuffer buffer, int[] pixels, int width, int height) {
        var bytes = buffer.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        var stride = buffer.stride();
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                bytes.putInt(y * stride + x * 4, pixels[y * width + x]);
            }
        }
    }

    /// Each channel blurred on its own; premultiplied, so a colour does not
    /// bleed into what is transparent.
    private static void blur(int[] pixels, int width, int height, double sigma) {
        if (!(sigma > 0)) {
            return;
        }
        var channels = new int[4][pixels.length];
        for (var i = 0; i < pixels.length; i++) {
            var p = pixels[i];
            channels[0][i] = p >>> 24;
            channels[1][i] = (p >>> 16) & 0xFF;
            channels[2][i] = (p >>> 8) & 0xFF;
            channels[3][i] = p & 0xFF;
        }
        for (var channel : channels) {
            CoverageBlur.blur(channel, width, height, sigma);
        }
        for (var i = 0; i < pixels.length; i++) {
            var a = Math.clamp(channels[0][i], 0, 255);
            var r = Math.clamp(channels[1][i], 0, a);
            var g = Math.clamp(channels[2][i], 0, a);
            var b = Math.clamp(channels[3][i], 0, a);
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    /// The raster's own shape, blurred, coloured and offset, drawn under it.
    private static void dropShadow(
            int[] pixels, int width, int height, FilterFunction.DropShadow shadow, double factor) {
        var coverage = new int[pixels.length];
        for (var i = 0; i < pixels.length; i++) {
            coverage[i] = pixels[i] >>> 24;
        }
        CoverageBlur.blur(coverage, width, height, shadow.radius() * factor);
        var dx = (int) Math.round(shadow.dx() * factor);
        var dy = (int) Math.round(shadow.dy() * factor);
        var argb = shadow.argb();
        var alpha = (argb >>> 24) / 255.0;
        var red = (argb >>> 16) & 0xFF;
        var green = (argb >>> 8) & 0xFF;
        var blue = argb & 0xFF;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var sx = x - dx;
                var sy = y - dy;
                var cast = sx >= 0 && sx < width && sy >= 0 && sy < height ? coverage[sy * width + sx] : 0;
                if (cast == 0) {
                    continue;
                }
                var shade = alpha * cast / 255.0;
                var i = y * width + x;
                var top = pixels[i];
                var under = 1 - (top >>> 24) / 255.0;
                var a = (top >>> 24) + (int) Math.round(255 * shade * under);
                var r = ((top >>> 16) & 0xFF) + (int) Math.round(red * shade * under);
                var g = ((top >>> 8) & 0xFF) + (int) Math.round(green * shade * under);
                var b = (top & 0xFF) + (int) Math.round(blue * shade * under);
                a = Math.min(255, a);
                pixels[i] = (a << 24) | (Math.min(a, r) << 16) | (Math.min(a, g) << 8) | Math.min(a, b);
            }
        }
    }

    /// `opacity()`: every premultiplied channel scaled alike.
    private static void fade(int[] pixels, double amount) {
        if (amount >= 1) {
            return;
        }
        for (var i = 0; i < pixels.length; i++) {
            var p = pixels[i];
            pixels[i] = scaled(p >>> 24, amount) << 24
                    | scaled((p >>> 16) & 0xFF, amount) << 16
                    | scaled((p >>> 8) & 0xFF, amount) << 8
                    | scaled(p & 0xFF, amount);
        }
    }

    private static int scaled(int channel, double amount) {
        return (int) Math.round(channel * amount);
    }

    /// A colour matrix over each pixel's unpremultiplied values, alpha kept.
    private static void matrix(int[] pixels, ColourMatrix matrix) {
        for (var i = 0; i < pixels.length; i++) {
            var p = pixels[i];
            var a = p >>> 24;
            if (a == 0) {
                continue;
            }
            // Unpremultiplied, as 0 to 1: a premultiplied channel over its alpha.
            var r = ((p >>> 16) & 0xFF) / (double) a;
            var g = ((p >>> 8) & 0xFF) / (double) a;
            var b = (p & 0xFF) / (double) a;
            var out = matrix.apply(r, g, b);
            pixels[i] = (a << 24)
                    | premultiplied(out[0], a) << 16
                    | premultiplied(out[1], a) << 8
                    | premultiplied(out[2], a);
        }
    }

    private static int premultiplied(double channel, int alpha) {
        return (int) Math.round(Math.clamp(channel, 0, 1) * alpha);
    }
}
