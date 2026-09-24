package io.github.digitalsmile.goldberry.gpu.render;

import java.util.Objects;

/// Y'CbCr to R'G'B' for one picture's tags: the arithmetic
/// [BuiltInShader#YUV2_FRAGMENT] and [BuiltInShader#YUV3_FRAGMENT] do, as the
/// uniform block they read, and again in Java as the reference the tests hold
/// them to (`docs/gpu-plan.md`, phase 6).
///
/// A code value `c` of a `b`-bit channel, normalised `n = c / (2^b - 1)`:
///
/// - limited range, scaled to `b` bits from 8: luma `(n - 16/255) × 255/219`,
///   chroma `(n - 128/255) × 255/224` (for 10 bits, 64 and 876, 512 and 896);
/// - full range: luma `n`, chroma `n - 2^(b-1) / (2^b - 1)`;
/// - then `R = Y + 2(1-Kr) Cr`, `G = Y - 2Kb(1-Kb)/Kg Cb - 2Kr(1-Kr)/Kg Cr`,
///   `B = Y + 2(1-Kb) Cb`, clamped to 0 to 1.
///
/// Chroma is sited left, as MPEG-2 and swscale have it: a chroma sample sits on
/// its first luma column, a quarter of a chroma texel left of the texel's centre.
///
/// @param layout    how the planes are stored
/// @param matrix    the picture's matrix
/// @param fullRange whether the picture uses the full code range (JPEG) rather
///                  than the limited one (video)
public record YuvConversion(YuvLayout layout, YuvMatrix matrix, boolean fullRange) {

    /// Checks nothing is missing.
    public YuvConversion {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(matrix, "matrix");
    }

    /// The luma offset and gain over a normalised code.
    private double[] lumaRange() {
        if (fullRange) {
            return new double[] {0, 1};
        }
        var scale = 1 << (layout.bitDepth() - 8);
        return new double[] {16.0 * scale / layout.maxCode(), layout.maxCode() / (219.0 * scale)};
    }

    /// The chroma offset and gain over a normalised code.
    private double[] chromaRange() {
        var middle = (double) (1 << (layout.bitDepth() - 1)) / layout.maxCode();
        if (fullRange) {
            return new double[] {middle, 1};
        }
        var scale = 1 << (layout.bitDepth() - 8);
        return new double[] {middle, layout.maxCode() / (224.0 * scale)};
    }

    /// The shader's uniform block, twelve floats: the luma transform (scale to
    /// code, offset, gain, unused), the chroma transform (the same, then the
    /// siting offset in texture coordinates), then the four coefficients.
    ///
    /// @param chromaWidth the chroma planes' width in texels, which the siting
    ///                    offset is a quarter of one of
    public float[] uniforms(int chromaWidth) {
        if (chromaWidth <= 0) {
            throw new IllegalArgumentException("chroma width " + chromaWidth);
        }
        var luma = lumaRange();
        var chroma = chromaRange();
        var scale = (float) layout.sampleToCode();
        return new float[] {
            scale,
            (float) luma[0],
            (float) luma[1],
            0f,
            scale,
            (float) chroma[0],
            (float) chroma[1],
            0.25f / chromaWidth,
            (float) matrix.crToRed(),
            (float) matrix.cbToGreen(),
            (float) matrix.crToGreen(),
            (float) matrix.cbToBlue()
        };
    }

    /// R', G' and B', 0 to 1, for code values `y`, `cb` and `cr`: the reference.
    public double[] toRgb(int y, int cb, int cr) {
        var max = layout.maxCode();
        for (var code : new int[] {y, cb, cr}) {
            if (code < 0 || code > max) {
                throw new IllegalArgumentException("code " + code + " outside 0 to " + max);
            }
        }
        var luma = lumaRange();
        var chroma = chromaRange();
        var lumaValue = ((double) y / max - luma[0]) * luma[1];
        var blue = ((double) cb / max - chroma[0]) * chroma[1];
        var red = ((double) cr / max - chroma[0]) * chroma[1];
        return new double[] {
            clamp(lumaValue + matrix.crToRed() * red),
            clamp(lumaValue + matrix.cbToGreen() * blue + matrix.crToGreen() * red),
            clamp(lumaValue + matrix.cbToBlue() * blue)
        };
    }

    /// [#toRgb] as the bytes an 8-bit `UNORM` target stores: rounded to the
    /// nearest of 255 steps.
    public int[] toRgbBytes(int y, int cb, int cr) {
        var rgb = toRgb(y, cb, cr);
        return new int[] {(int) Math.round(rgb[0] * 255), (int) Math.round(rgb[1] * 255), (int) Math.round(rgb[2] * 255)
        };
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
