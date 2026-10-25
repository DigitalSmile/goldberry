package dev.goldberry.gpu.upscale.fsr;

import java.util.Objects;

/// FSR 1's EASU and RCAS, and the bilinear filter, in Java: what the toolkit's
/// shaders are held to, pixel for pixel.
///
/// A line-by-line transliteration of `easu.frag.hlsl` and `rcas.frag.hlsl`,
/// with the same bit-trick approximations and the same constants
/// ([Fsr1]), in floats. Reads outside the picture are clamped to its edge, as
/// the shaders' sampler and ring are. What the GPU's rounding may change is
/// the last bit of a float, which an 8-bit target hides but for a level now
/// and then.
public final class Fsr1Reference {

    private static final float RCAS_LIMIT = 0.25f - (1f / 16f);

    /// The twelve taps around the texel `f` a pixel falls in, by their offset
    /// from it:
    ///    b c
    ///  e f g h
    ///  i j k l
    ///    n o
    private static final int[][] TAPS = {
        {0, -1}, {1, -1}, {-1, 0}, {0, 0}, {1, 0}, {2, 0}, {-1, 1}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2}
    };

    private static final int B = 0;
    private static final int C = 1;
    private static final int E = 2;
    private static final int F = 3;
    private static final int G = 4;
    private static final int H = 5;
    private static final int I = 6;
    private static final int J = 7;
    private static final int K = 8;
    private static final int L = 9;
    private static final int N = 10;
    private static final int O = 11;

    /// The order FSR's shader accumulates the taps in, so the sums round alike.
    private static final int[] ACCUMULATED = {B, C, I, J, F, E, K, L, H, G, O, N};

    private Fsr1Reference() {}

    /// A picture's colour, red, green and blue a pixel, 0 to 1, rows from the
    /// top.
    ///
    /// @param width  its width
    /// @param height its height
    /// @param rgb    `3 * width * height` values
    public record Rgb(int width, int height, float[] rgb) {

        public Rgb {
            Objects.requireNonNull(rgb, "rgb");
            if (width < 1 || height < 1 || rgb.length != 3 * width * height) {
                throw new IllegalArgumentException(width + "x" + height + " in " + rgb.length + " values");
            }
        }

        /// From tightly packed `B8G8R8A8_UNORM` bytes.
        public static Rgb fromBgra(byte[] bgra, int width, int height) {
            var rgb = new float[3 * width * height];
            for (var i = 0; i < width * height; i++) {
                rgb[3 * i] = (bgra[4 * i + 2] & 0xFF) / 255f;
                rgb[3 * i + 1] = (bgra[4 * i + 1] & 0xFF) / 255f;
                rgb[3 * i + 2] = (bgra[4 * i] & 0xFF) / 255f;
            }
            return new Rgb(width, height, rgb);
        }

        /// As an 8-bit target stores it: each value clamped, rounded to the
        /// nearest level, and alpha opaque.
        public byte[] toBgra() {
            var bgra = new byte[4 * width * height];
            for (var i = 0; i < width * height; i++) {
                bgra[4 * i] = level(rgb[3 * i + 2]);
                bgra[4 * i + 1] = level(rgb[3 * i + 1]);
                bgra[4 * i + 2] = level(rgb[3 * i]);
                bgra[4 * i + 3] = (byte) 0xFF;
            }
            return bgra;
        }

        /// This picture as an 8-bit target holds it, read back: what the next
        /// pass reads.
        public Rgb quantized() {
            return fromBgra(toBgra(), width, height);
        }

        public float at(int x, int y, int channel) {
            var cx = Math.clamp(x, 0, width - 1);
            var cy = Math.clamp(y, 0, height - 1);
            return rgb[3 * (cy * width + cx) + channel];
        }

        private static byte level(float value) {
            return (byte) Math.round(Math.clamp(value, 0f, 1f) * 255f);
        }
    }

    /// `ffx_a.h`'s `APrxLoRcpF1`.
    static float rcpLow(float a) {
        return Float.intBitsToFloat(0x7ef07ebb - Float.floatToRawIntBits(a));
    }

    /// `ffx_a.h`'s `APrxLoRsqF1`.
    static float rsqLow(float a) {
        return Float.intBitsToFloat(0x5f347d74 - (Float.floatToRawIntBits(a) >>> 1));
    }

    /// `ffx_a.h`'s `APrxMedRcpF1`.
    static float rcpMedium(float a) {
        var b = Float.intBitsToFloat(0x7ef19fff - Float.floatToRawIntBits(a));
        return b * (-b * a + 2f);
    }

    private static float saturate(float value) {
        return Math.clamp(value, 0f, 1f);
    }

    /// EASU of `input` into a picture of `width` by `height`.
    public static Rgb easu(Rgb input, int width, int height) {
        var con = Fsr1.easu(input.width(), input.height(), width, height);
        var out = new float[3 * width * height];
        var pixel = new float[3];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                easuPixel(input, x, y, con, pixel);
                System.arraycopy(pixel, 0, out, 3 * (y * width + x), 3);
            }
        }
        return new Rgb(width, height, out);
    }

    private static void easuPixel(Rgb input, int x, int y, float[] con, float[] result) {
        var ppX = (float) x * con[0] + con[2];
        var ppY = (float) y * con[1] + con[3];
        var fpX = (float) Math.floor(ppX);
        var fpY = (float) Math.floor(ppY);
        ppX -= fpX;
        ppY -= fpY;
        var fx = (int) fpX;
        var fy = (int) fpY;

        var colour = new float[TAPS.length][3];
        var luma = new float[TAPS.length];
        for (var t = 0; t < TAPS.length; t++) {
            for (var channel = 0; channel < 3; channel++) {
                colour[t][channel] = input.at(fx + TAPS[t][0], fy + TAPS[t][1], channel);
            }
            luma[t] = colour[t][2] * 0.5f + (colour[t][0] * 0.5f + colour[t][1]);
        }

        var dir = new float[2];
        var len = new float[1];
        easuSet(dir, len, (1f - ppX) * (1f - ppY), luma[B], luma[E], luma[F], luma[G], luma[J]);
        easuSet(dir, len, ppX * (1f - ppY), luma[C], luma[F], luma[G], luma[H], luma[K]);
        easuSet(dir, len, (1f - ppX) * ppY, luma[F], luma[I], luma[J], luma[K], luma[N]);
        easuSet(dir, len, ppX * ppY, luma[G], luma[J], luma[K], luma[L], luma[O]);

        var dirX = dir[0];
        var dirY = dir[1];
        var dirR = dirX * dirX + dirY * dirY;
        var zro = dirR < (1f / 32768f);
        dirR = rsqLow(dirR);
        dirR = zro ? 1f : dirR;
        dirX = zro ? 1f : dirX;
        dirX *= dirR;
        dirY *= dirR;
        var length = len[0] * 0.5f;
        length *= length;
        var stretch = (dirX * dirX + dirY * dirY) * rcpLow(Math.max(Math.abs(dirX), Math.abs(dirY)));
        var len2X = 1f + (stretch - 1f) * length;
        var len2Y = 1f + -0.5f * length;
        var lob = 0.5f + ((1f / 4f - 0.04f) - 0.5f) * length;
        var clp = rcpLow(lob);

        var aC = new float[3];
        var aW = 0f;
        for (var t : ACCUMULATED) {
            var offX = TAPS[t][0] - ppX;
            var offY = TAPS[t][1] - ppY;
            var vX = (offX * dirX) + (offY * dirY);
            var vY = (offX * (-dirY)) + (offY * dirX);
            vX *= len2X;
            vY *= len2Y;
            var d2 = vX * vX + vY * vY;
            d2 = Math.min(d2, clp);
            var wB = (2f / 5f) * d2 + -1f;
            var wA = lob * d2 + -1f;
            wB *= wB;
            wA *= wA;
            wB = (25f / 16f) * wB + -(25f / 16f - 1f);
            var w = wB * wA;
            for (var channel = 0; channel < 3; channel++) {
                aC[channel] += colour[t][channel] * w;
            }
            aW += w;
        }
        var rcpW = 1f / aW;
        for (var channel = 0; channel < 3; channel++) {
            var min4 = Math.min(
                    Math.min(colour[F][channel], Math.min(colour[G][channel], colour[J][channel])), colour[K][channel]);
            var max4 = Math.max(
                    Math.max(colour[F][channel], Math.max(colour[G][channel], colour[J][channel])), colour[K][channel]);
            result[channel] = Math.min(max4, Math.max(min4, aC[channel] * rcpW));
        }
    }

    private static void easuSet(float[] dir, float[] len, float w, float lA, float lB, float lC, float lD, float lE) {
        var dc = lD - lC;
        var cb = lC - lB;
        var lenX = Math.max(Math.abs(dc), Math.abs(cb));
        lenX = rcpLow(lenX);
        var dirX = lD - lB;
        dir[0] += dirX * w;
        lenX = saturate(Math.abs(dirX) * lenX);
        lenX *= lenX;
        len[0] += lenX * w;

        var ec = lE - lC;
        var ca = lC - lA;
        var lenY = Math.max(Math.abs(ec), Math.abs(ca));
        lenY = rcpLow(lenY);
        var dirY = lE - lA;
        dir[1] += dirY * w;
        lenY = saturate(Math.abs(dirY) * lenY);
        lenY *= lenY;
        len[0] += lenY * w;
    }

    /// RCAS of `input` at `sharpness`, 0 to 1.
    public static Rgb rcas(Rgb input, double sharpness) {
        var width = input.width();
        var height = input.height();
        var con = Fsr1.rcas(sharpness, width, height);
        var out = new float[3 * width * height];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var lobe = -Float.MAX_VALUE;
                for (var channel = 0; channel < 3; channel++) {
                    var b = input.at(x, y - 1, channel);
                    var d = input.at(x - 1, y, channel);
                    var e = input.at(x, y, channel);
                    var f = input.at(x + 1, y, channel);
                    var h = input.at(x, y + 1, channel);
                    var mn4 = Math.min(Math.min(b, Math.min(d, f)), h);
                    var mx4 = Math.max(Math.max(b, Math.max(d, f)), h);
                    var hitMin = Math.min(mn4, e) / Math.max(4f * mx4, 1f / 1024f);
                    var hitMax = (1f - Math.max(mx4, e)) / Math.min(4f * mn4 + -4f, -1f / 1024f);
                    lobe = Math.max(lobe, Math.max(-hitMin, hitMax));
                }
                lobe = Math.max(-RCAS_LIMIT, Math.min(lobe, 0f)) * con[0];
                var rcpL = rcpMedium(4f * lobe + 1f);
                for (var channel = 0; channel < 3; channel++) {
                    var b = input.at(x, y - 1, channel);
                    var d = input.at(x - 1, y, channel);
                    var e = input.at(x, y, channel);
                    var f = input.at(x + 1, y, channel);
                    var h = input.at(x, y + 1, channel);
                    out[3 * (y * width + x) + channel] = (lobe * b + lobe * d + lobe * h + lobe * f + e) * rcpL;
                }
            }
        }
        return new Rgb(width, height, out);
    }

    /// Bilinear resampling of `input` into `width` by `height`, each pixel's
    /// centre mapped onto the input's and clamped to its edge texels.
    public static Rgb linear(Rgb input, int width, int height) {
        var out = new float[3 * width * height];
        var scaleX = (float) input.width() / width;
        var scaleY = (float) input.height() / height;
        for (var y = 0; y < height; y++) {
            var v = (y + 0.5f) * scaleY - 0.5f;
            var y0 = (int) Math.floor(v);
            var ty = v - y0;
            for (var x = 0; x < width; x++) {
                var u = (x + 0.5f) * scaleX - 0.5f;
                var x0 = (int) Math.floor(u);
                var tx = u - x0;
                for (var channel = 0; channel < 3; channel++) {
                    var top = input.at(x0, y0, channel) * (1 - tx) + input.at(x0 + 1, y0, channel) * tx;
                    var bottom = input.at(x0, y0 + 1, channel) * (1 - tx) + input.at(x0 + 1, y0 + 1, channel) * tx;
                    out[3 * (y * width + x) + channel] = top * (1 - ty) + bottom * ty;
                }
            }
        }
        return new Rgb(width, height, out);
    }
}
