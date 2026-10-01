package dev.goldberry.gpu.render;

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
/// Chroma is sited as CPU present's swscale conversion sites it, by default
/// ([Siting#CENTRED]): a chroma sample midway between its two luma columns and
/// its two rows, which is where a texel's centre already is, so the sampler
/// reads it with no offset. Measured against swscale's own pictures
/// (ADR-0484), which corrected ADR-0477's assumption that swscale sites chroma
/// left. [Siting#LEFT], MPEG-2's, is a quarter of a chroma texel to the left.
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

    /// Where a chroma sample sits among the luma samples it covers.
    public enum Siting {
        /// Midway between its two luma columns: JPEG's and MPEG-1's, and what
        /// swscale's conversion to BGRA does with no location given.
        CENTRED(0),
        /// On its first luma column: MPEG-2's, H.264's and HEVC's default.
        LEFT(0.25f);

        private final float texels;

        Siting(float texels) {
            this.texels = texels;
        }

        /// How far left of a centred sample it sits, in chroma texels.
        public float texels() {
            return texels;
        }
    }

    /// [#uniforms(int, Siting)] with chroma [Siting#CENTRED], as CPU present
    /// converts it.
    public float[] uniforms(int chromaWidth) {
        return uniforms(chromaWidth, Siting.CENTRED);
    }

    /// The shader's uniform block, twelve floats: the luma transform (scale to
    /// code, offset, gain, unused), the chroma transform (the same, then the
    /// siting offset in texture coordinates), then the four coefficients.
    ///
    /// @param chromaWidth the chroma planes' width in texels, which the siting
    ///                    offset is a fraction of one of
    public float[] uniforms(int chromaWidth, Siting siting) {
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
            siting.texels() / chromaWidth,
            (float) matrix.crToRed(),
            (float) matrix.cbToGreen(),
            (float) matrix.crToGreen(),
            (float) matrix.cbToBlue()
        };
    }

    /// R', G' and B', 0 to 1, for code values `y`, `cb` and `cr`: the reference.
    public double[] toRgb(int y, int cb, int cr) {
        return toRgb((double) y, cb, cr);
    }

    /// [#toRgb(int, int, int)] for codes between whole codes: what a shader
    /// reads where a linear sampler has interpolated two chroma samples.
    public double[] toRgb(double y, double cb, double cr) {
        var max = layout.maxCode();
        for (var code : new double[] {y, cb, cr}) {
            if (!(code >= 0 && code <= max)) {
                throw new IllegalArgumentException("code " + code + " outside 0 to " + max);
            }
        }
        var luma = lumaRange();
        var chroma = chromaRange();
        var lumaValue = (y / max - luma[0]) * luma[1];
        var blue = (cb / max - chroma[0]) * chroma[1];
        var red = (cr / max - chroma[0]) * chroma[1];
        return new double[] {
            clamp(lumaValue + matrix.crToRed() * red),
            clamp(lumaValue + matrix.cbToGreen() * blue + matrix.crToGreen() * red),
            clamp(lumaValue + matrix.cbToBlue() * blue)
        };
    }

    /// [#toRgb] as the bytes an 8-bit `UNORM` target stores: rounded to the
    /// nearest of 255 steps.
    public int[] toRgbBytes(int y, int cb, int cr) {
        return toRgbBytes((double) y, cb, cr);
    }

    /// [#toRgb(double, double, double)] as the bytes an 8-bit `UNORM` target
    /// stores.
    public int[] toRgbBytes(double y, double cb, double cr) {
        var rgb = toRgb(y, cb, cr);
        return new int[] {(int) Math.round(rgb[0] * 255), (int) Math.round(rgb[1] * 255), (int) Math.round(rgb[2] * 255)
        };
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
