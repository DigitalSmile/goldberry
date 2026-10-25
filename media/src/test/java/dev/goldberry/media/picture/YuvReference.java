package dev.goldberry.media.picture;

import java.util.Objects;

import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.hdr.ToneCurve;

/// What a picture of planes looks like in RGB, computed in Java: the arithmetic
/// both present paths are held to, and, under a [Reading] that is not the
/// picture's own, what a present path with a particular bug would draw.
///
/// The arithmetic is written out here rather than borrowed from `:gpu`, so the
/// GPU is held to something other than its own module, and the tests that use
/// this run without `:gpu` on the class path. A code `c` of a `b`-bit channel:
/// limited range takes luma as `(c - 16·2^(b-8)) / (219·2^(b-8))` and chroma as
/// `(c - 128·2^(b-8)) / (224·2^(b-8))`; full range takes `c / (2^b - 1)` and
/// `(c - 2^(b-1)) / (2^b - 1)`; then `R = Y + 2(1-Kr) Cr`, `G = Y - 2Kb(1-Kb)/Kg
/// Cb - 2Kr(1-Kr)/Kg Cr`, `B = Y + 2(1-Kb) Cb`, clamped and rounded to 255
/// steps.
///
/// The picture's own reading is that over chroma blended as a linear sampler
/// blends it, centred: at 1:1 a luma pixel's centre falls, in chroma
/// texels, at `x / 2 - 1/4` across and `y / 2 - 1/4` down, so each pixel takes
/// three quarters of the nearer chroma sample and a quarter of the other, both
/// ways, clamped at the edges. GPU present samples the planes so, and CPU
/// present's libyuv interpolates so (9:3:3:1).
///
/// A picture whose [YuvSource#colour()] is HDR or in wider primaries is then
/// mapped to SDR BT.709 by [ToneCurve#apply], from its R'G'B' before it is
/// rounded: the curve's own tests hold it to SMPTE ST 2084, ARIB STD-B67 and
/// BT.2390, and both present paths to it.
public final class YuvReference {

    /// How chroma reaches a luma pixel.
    public enum Chroma {
        /// Interpolated, each chroma sample at the centre of its 2×2 block: what
        /// both present paths do.
        BLENDED,
        /// Each chroma sample repeated over its 2×2 block, with no interpolation.
        REPEATED,
        /// Interpolated, each chroma sample on its block's left column (MPEG-2's
        /// siting) rather than centred.
        BLENDED_LEFT
    }

    /// A way of reading a picture: the picture's own tags and the right chroma,
    /// or a mistake a present path could make.
    ///
    /// @param matrix         the matrix the picture is read with
    /// @param fullRange      whether it is read as full range
    /// @param chroma         how chroma reaches the luma pixels
    /// @param chromaSwapped  whether Cb is read as Cr and Cr as Cb
    /// @param redBlueSwapped whether the output's red and blue trade places, as
    ///                       RGBA written where BGRA is read
    public record Reading(
            VideoFrame.ColorMatrix matrix,
            boolean fullRange,
            Chroma chroma,
            boolean chromaSwapped,
            boolean redBlueSwapped) {

        public Reading {
            Objects.requireNonNull(matrix, "matrix");
            Objects.requireNonNull(chroma, "chroma");
        }

        /// The reading `source` asks for: its own matrix and range, chroma
        /// blended, nothing swapped.
        public static Reading of(YuvSource source) {
            return new Reading(source.matrix(), source.fullRange(), Chroma.BLENDED, false, false);
        }
    }

    private YuvReference() {}

    /// `source` read its own way, as `0xAARRGGBB`, row by row.
    public static int[] argb(YuvSource source) {
        return argb(source, Reading.of(source));
    }

    /// `source` read as `reading` says, as `0xAARRGGBB`, row by row.
    public static int[] argb(YuvSource source, Reading reading) {
        var bits = source.format().bytesPerSample() == 2 ? 10 : 8;
        var width = source.width();
        var height = source.height();
        var chromaWidth = (width + 1) / 2;
        var chromaHeight = (height + 1) / 2;
        var cbPlane = reading.chromaSwapped() ? 1 : 0;
        var curve = ToneCurve.of(source.colour());
        var out = new int[width * height];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                double cb;
                double cr;
                if (reading.chroma() == Chroma.REPEATED) {
                    cb = source.chroma(cbPlane, x / 2, y / 2);
                    cr = source.chroma(1 - cbPlane, x / 2, y / 2);
                } else {
                    var cx = reading.chroma() == Chroma.BLENDED_LEFT ? x / 2.0 : x / 2.0 - 0.25;
                    var cy = y / 2.0 - 0.25;
                    cb = bilinear(source, cbPlane, cx, cy, chromaWidth, chromaHeight);
                    cr = bilinear(source, 1 - cbPlane, cx, cy, chromaWidth, chromaHeight);
                }
                var rgb = rgb(source.code(0, x, y), cb, cr, bits, reading, curve);
                var red = reading.redBlueSwapped() ? rgb[2] : rgb[0];
                var blue = reading.redBlueSwapped() ? rgb[0] : rgb[2];
                out[y * width + x] = 0xFF000000 | red << 16 | rgb[1] << 8 | blue;
            }
        }
        return out;
    }

    /// The largest difference of any channel of any pixel of two pictures.
    public static int worst(int[] a, int[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(a.length + " pixels against " + b.length);
        }
        var worst = 0;
        for (var i = 0; i < a.length; i++) {
            worst = Math.max(worst, worst(a[i], b[i]));
        }
        return worst;
    }

    /// The largest difference of any of two pixels' colour channels.
    public static int worst(int a, int b) {
        var worst = 0;
        for (var shift = 0; shift < 24; shift += 8) {
            worst = Math.max(worst, Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF)));
        }
        return worst;
    }

    /// The mean difference of the colour channels: what tells the readings
    /// apart, where the worst pixel only says how far the worst one is.
    static double mean(int[] a, int[] b) {
        var sum = 0L;
        for (var i = 0; i < a.length; i++) {
            for (var shift = 0; shift < 24; shift += 8) {
                sum += Math.abs(((a[i] >>> shift) & 0xFF) - ((b[i] >>> shift) & 0xFF));
            }
        }
        return (double) sum / (3.0 * a.length);
    }

    /// R, G and B, 0 to 255, for luma code `y` and chroma codes `cb` and `cr`
    /// (between whole codes where chroma was blended) of a `bits`-bit picture.
    private static int[] rgb(double y, double cb, double cr, int bits, Reading reading, ToneCurve curve) {
        var max = (1 << bits) - 1;
        var scale = 1 << (bits - 8);
        double luma;
        double blue;
        double red;
        if (reading.fullRange()) {
            luma = y / max;
            blue = (cb - (1 << (bits - 1))) / max;
            red = (cr - (1 << (bits - 1))) / max;
        } else {
            luma = (y - 16.0 * scale) / (219.0 * scale);
            blue = (cb - 128.0 * scale) / (224.0 * scale);
            red = (cr - 128.0 * scale) / (224.0 * scale);
        }
        var kr = kr(reading.matrix());
        var kb = kb(reading.matrix());
        var kg = 1 - kr - kb;
        var encoded = new double[] {
            clamp(luma + 2 * (1 - kr) * red),
            clamp(luma - 2 * kb * (1 - kb) / kg * blue - 2 * kr * (1 - kr) / kg * red),
            clamp(luma + 2 * (1 - kb) * blue)
        };
        var drawn = curve.isIdentity() ? encoded : curve.apply(encoded);
        return new int[] {level(drawn[0]), level(drawn[1]), level(drawn[2])};
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double kr(VideoFrame.ColorMatrix matrix) {
        return switch (matrix) {
            case BT601 -> 0.299;
            case BT709 -> 0.2126;
            case BT2020 -> 0.2627;
        };
    }

    private static double kb(VideoFrame.ColorMatrix matrix) {
        return switch (matrix) {
            case BT601 -> 0.114;
            case BT709 -> 0.0722;
            case BT2020 -> 0.0593;
        };
    }

    private static int level(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }

    private static double bilinear(
            YuvSource source, int component, double cx, double cy, int chromaWidth, int chromaHeight) {
        var x0 = (int) Math.floor(cx);
        var y0 = (int) Math.floor(cy);
        var fx = cx - x0;
        var fy = cy - y0;
        var left = clamp(x0, chromaWidth);
        var right = clamp(x0 + 1, chromaWidth);
        var top = clamp(y0, chromaHeight);
        var bottom = clamp(y0 + 1, chromaHeight);
        var upper = source.chroma(component, left, top) * (1 - fx) + source.chroma(component, right, top) * fx;
        var lower = source.chroma(component, left, bottom) * (1 - fx) + source.chroma(component, right, bottom) * fx;
        return upper * (1 - fy) + lower * fy;
    }

    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(size - 1, index));
    }
}
