package dev.goldberry.paint.shadow;

import java.util.Objects;

/// A Gaussian blur of a coverage mask, as three box blurs: what a text shadow's
/// `blur` is.
///
/// ```java
/// CoverageBlur.blur(alpha, width, height, sigma); // in place
/// ```
///
/// Three passes of a box blur in each direction come within a few percent of a
/// Gaussian, and each pass is a running sum, so the cost is the same at any
/// radius: six reads and writes of the mask per pixel. The box widths are the
/// standard choice for three passes at `sigma`.
///
/// One channel only. A text shadow is one colour over the glyphs' coverage, so
/// the coverage is blurred and the colour is applied after, which is a quarter
/// of the work of blurring four premultiplied channels and gives the same
/// pixels.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#typography).
public final class CoverageBlur {

    private CoverageBlur() {}

    /// Blurs `coverage`, `width` by `height` values of 0 to 255 row by row, in
    /// place, as a Gaussian of standard deviation `sigma` pixels would.
    ///
    /// @throws IllegalArgumentException if the mask is not `width` × `height`
    public static void blur(int[] coverage, int width, int height, double sigma) {
        Objects.requireNonNull(coverage, "coverage");
        if (coverage.length != width * height) {
            throw new IllegalArgumentException(
                    "a " + width + "x" + height + " mask has " + width * height + " values, not " + coverage.length);
        }
        if (!(sigma > 0) || width == 0 || height == 0) {
            return;
        }
        var scratch = new int[coverage.length];
        for (var box : boxes(sigma)) {
            var radius = (box - 1) / 2;
            if (radius == 0) {
                continue;
            }
            horizontal(coverage, scratch, width, height, radius);
            vertical(scratch, coverage, width, height, radius);
        }
    }

    /// The three box widths whose passes approximate a Gaussian at `sigma`.
    static int[] boxes(double sigma) {
        var ideal = Math.sqrt(12 * sigma * sigma / 3 + 1);
        var lower = (int) Math.floor(ideal);
        if (lower % 2 == 0) {
            lower--;
        }
        var upper = lower + 2;
        var m = (int)
                Math.round((12 * sigma * sigma - 3.0 * lower * lower - 4.0 * 3 * lower - 3 * 3) / (-4.0 * lower - 4));
        var sizes = new int[3];
        for (var i = 0; i < 3; i++) {
            sizes[i] = i < m ? lower : upper;
        }
        return sizes;
    }

    /// One box pass along each row, from `source` into `target`. Outside the
    /// mask is no coverage.
    private static void horizontal(int[] source, int[] target, int width, int height, int radius) {
        var span = 2 * radius + 1;
        for (var y = 0; y < height; y++) {
            var row = y * width;
            var sum = 0;
            for (var x = -radius; x <= radius; x++) {
                sum += at(source, row, x, width);
            }
            for (var x = 0; x < width; x++) {
                target[row + x] = (sum + span / 2) / span;
                sum += at(source, row, x + radius + 1, width) - at(source, row, x - radius, width);
            }
        }
    }

    /// One box pass down each column.
    private static void vertical(int[] source, int[] target, int width, int height, int radius) {
        var span = 2 * radius + 1;
        for (var x = 0; x < width; x++) {
            var sum = 0;
            for (var y = -radius; y <= radius; y++) {
                sum += y >= 0 && y < height ? source[y * width + x] : 0;
            }
            for (var y = 0; y < height; y++) {
                target[y * width + x] = (sum + span / 2) / span;
                var in = y + radius + 1;
                var out = y - radius;
                sum += (in < height ? source[in * width + x] : 0) - (out >= 0 ? source[out * width + x] : 0);
            }
        }
    }

    private static int at(int[] mask, int row, int x, int width) {
        return x >= 0 && x < width ? mask[row + x] : 0;
    }
}
