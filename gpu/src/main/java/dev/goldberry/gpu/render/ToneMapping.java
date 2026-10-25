package dev.goldberry.gpu.render;

import java.util.Objects;

/// HDR and wide-gamut R'G'B' to the layer's SDR BT.709: the arithmetic
/// [BuiltInShader#YUV2_HDR_FRAGMENT] and [BuiltInShader#YUV3_HDR_FRAGMENT] do
/// after [YuvConversion], as the uniform block they read, and again in Java as
/// the reference the tests hold them to.
///
/// From R'G'B' between 0 and 1, the picture's own encoding:
///
/// 1. **To light**, in nits: PQ's EOTF (SMPTE ST 2084); HLG's inverse OETF
///    and then BT.2100's display OOTF, `Lw × Ys^(γ−1) × E` with `Lw` 1000 nits
///    and `γ` 1.2, `Ys` the scene luminance in the picture's primaries; SDR
///    through BT.1886's gamma of 2.4 with its white at [#REFERENCE_WHITE].
/// 2. **Into BT.709's primaries**, by the 3×3 matrix through CIE XYZ (D65
///    whites alike, so no adaptation), and below zero clipped to zero.
/// 3. **Tone-mapped**, an HDR picture only: the largest of the three channels,
///    `m`, is mapped by BT.2390's EETF from the picture's peak down to
///    [#REFERENCE_WHITE], in PQ's perceptual space, and all three are scaled
///    by `m′ / m`, which keeps hue and keeps every channel within the target.
///    A picture whose peak is no brighter than the target is left as it is.
/// 4. **Encoded** for the layer: divided by [#REFERENCE_WHITE], clamped to 0
///    to 1, and raised to `1 / 2.4`, the inverse of the gamma an SDR picture is
///    displayed through, so a tone-mapped picture sits among SDR ones as their
///    own pixels do.
///
/// An SDR picture in wider primaries goes through steps 1, 2 and 4, which for
/// a colour inside BT.709's gamut gives back what came in.
///
/// @param gamut     the picture's primaries
/// @param curve     the picture's transfer function
/// @param peakNits  the brightest the picture reaches, which the EETF maps to
///                  [#REFERENCE_WHITE]
public record ToneMapping(Gamut gamut, Curve curve, double peakNits) {

    /// SDR's reference white, ITU-R BT.2408's 203 nits: what an HDR picture's
    /// brightest becomes, and what an SDR picture's white is.
    public static final double REFERENCE_WHITE = 203;

    /// HLG's nominal peak, which its OOTF is taken at.
    public static final double HLG_PEAK = 1000;

    private static final double HLG_GAMMA = 1.2;
    private static final double SDR_GAMMA = 2.4;

    // SMPTE ST 2084.
    private static final double PQ_M1 = 2610.0 / 16384;
    private static final double PQ_M2 = 2523.0 / 4096 * 128;
    private static final double PQ_C1 = 3424.0 / 4096;
    private static final double PQ_C2 = 2413.0 / 4096 * 32;
    private static final double PQ_C3 = 2392.0 / 4096 * 32;
    private static final double PQ_PEAK = 10_000;

    // ARIB STD-B67.
    private static final double HLG_A = 0.17883277;
    private static final double HLG_B = 1 - 4 * HLG_A;
    private static final double HLG_C = 0.5 - HLG_A * Math.log(4 * HLG_A);

    /// The transfer functions, by the shader's number for each.
    public enum Curve {
        SDR(0),
        PQ(1),
        HLG(2);

        private final int code;

        Curve(int code) {
            this.code = code;
        }

        /// The number the shader branches on.
        public int code() {
            return code;
        }
    }

    /// Primaries by their chromaticities, all with D65's white point.
    public enum Gamut {
        BT709(0.640, 0.330, 0.300, 0.600, 0.150, 0.060),
        BT2020(0.708, 0.292, 0.170, 0.797, 0.131, 0.046),
        DISPLAY_P3(0.680, 0.320, 0.265, 0.690, 0.150, 0.060);

        private static final double WHITE_X = 0.3127;
        private static final double WHITE_Y = 0.3290;

        private final double redX;
        private final double redY;
        private final double greenX;
        private final double greenY;
        private final double blueX;
        private final double blueY;

        Gamut(double redX, double redY, double greenX, double greenY, double blueX, double blueY) {
            this.redX = redX;
            this.redY = redY;
            this.greenX = greenX;
            this.greenY = greenY;
            this.blueX = blueX;
            this.blueY = blueY;
        }

        /// Linear RGB in these primaries to CIE XYZ, rows of three, with
        /// white at `Y = 1`.
        public double[][] toXyz() {
            var c = new double[] {redX, redY, greenX, greenY, blueX, blueY};
            double[][] primaries = new double[3][3];
            for (var i = 0; i < 3; i++) {
                var x = c[2 * i];
                var y = c[2 * i + 1];
                primaries[0][i] = x / y;
                primaries[1][i] = 1;
                primaries[2][i] = (1 - x - y) / y;
            }
            var white = new double[] {WHITE_X / WHITE_Y, 1, (1 - WHITE_X - WHITE_Y) / WHITE_Y};
            var scale = multiply(invert(primaries), white);
            double[][] matrix = new double[3][3];
            for (var row = 0; row < 3; row++) {
                for (var column = 0; column < 3; column++) {
                    matrix[row][column] = primaries[row][column] * scale[column];
                }
            }
            return matrix;
        }

        /// The luminance of linear RGB in these primaries: the middle row of
        /// [#toXyz()].
        public double[] luminance() {
            return toXyz()[1];
        }

        /// Linear RGB in these primaries to linear RGB in BT.709's, rows of three.
        public double[][] toBt709() {
            return multiply(invert(BT709.toXyz()), toXyz());
        }
    }

    public ToneMapping {
        Objects.requireNonNull(gamut, "gamut");
        Objects.requireNonNull(curve, "curve");
        if (!(peakNits > 0) || !Double.isFinite(peakNits)) {
            throw new IllegalArgumentException("a peak of " + peakNits + " nits");
        }
    }

    /// Whether the EETF runs: an HDR picture brighter than the target.
    public boolean tonemaps() {
        return curve != Curve.SDR && sourcePeak() > REFERENCE_WHITE;
    }

    /// The peak the EETF maps from: the stated one for PQ, HLG's nominal peak.
    private double sourcePeak() {
        return curve == Curve.HLG ? HLG_PEAK : peakNits;
    }

    /// The shader's second uniform block, 24 floats in six vectors: the curve,
    /// whether to tone-map, the source peak, then nothing; the EETF's PQ of the
    /// source peak, its target in that scale, its knee start, then nothing; the
    /// three rows of the gamut matrix, each padded to four; HLG's luminance
    /// weights in the picture's primaries, then its peak.
    public float[] uniforms() {
        var source = sourcePeak();
        var sourcePq = pqEncode(source);
        var maxLum = pqEncode(REFERENCE_WHITE) / sourcePq;
        var gamutMatrix = gamut.toBt709();
        var weights = gamut.luminance();
        return new float[] {
            curve.code(),
            tonemaps() ? 1 : 0,
            (float) source,
            0,
            (float) sourcePq,
            (float) maxLum,
            (float) (1.5 * maxLum - 0.5),
            0,
            (float) gamutMatrix[0][0],
            (float) gamutMatrix[0][1],
            (float) gamutMatrix[0][2],
            0,
            (float) gamutMatrix[1][0],
            (float) gamutMatrix[1][1],
            (float) gamutMatrix[1][2],
            0,
            (float) gamutMatrix[2][0],
            (float) gamutMatrix[2][1],
            (float) gamutMatrix[2][2],
            0,
            (float) weights[0],
            (float) weights[1],
            (float) weights[2],
            (float) HLG_PEAK
        };
    }

    /// R'G'B' as the layer stores it, 0 to 1, for the picture's R'G'B' `rgb`,
    /// 0 to 1: the reference.
    public double[] apply(double[] rgb) {
        Objects.requireNonNull(rgb, "rgb");
        if (rgb.length != 3) {
            throw new IllegalArgumentException("three channels, not " + rgb.length);
        }
        var light = toLight(rgb);
        var matrix = gamut.toBt709();
        var mapped = new double[3];
        for (var row = 0; row < 3; row++) {
            mapped[row] =
                    Math.max(0, matrix[row][0] * light[0] + matrix[row][1] * light[1] + matrix[row][2] * light[2]);
        }
        if (tonemaps()) {
            var largest = Math.max(mapped[0], Math.max(mapped[1], mapped[2]));
            if (largest > 0) {
                var ratio = eetf(largest) / largest;
                for (var i = 0; i < 3; i++) {
                    mapped[i] *= ratio;
                }
            }
        }
        var out = new double[3];
        for (var i = 0; i < 3; i++) {
            out[i] = Math.pow(Math.clamp(mapped[i] / REFERENCE_WHITE, 0, 1), 1 / SDR_GAMMA);
        }
        return out;
    }

    /// Each channel of `rgb` in nits, in the picture's own primaries.
    private double[] toLight(double[] rgb) {
        var light = new double[3];
        switch (curve) {
            case SDR -> {
                for (var i = 0; i < 3; i++) {
                    light[i] = REFERENCE_WHITE * Math.pow(Math.clamp(rgb[i], 0, 1), SDR_GAMMA);
                }
            }
            case PQ -> {
                for (var i = 0; i < 3; i++) {
                    light[i] = pqDecode(rgb[i]);
                }
            }
            case HLG -> {
                var scene = new double[3];
                for (var i = 0; i < 3; i++) {
                    scene[i] = hlgInverseOetf(rgb[i]);
                }
                var weights = gamut.luminance();
                var ys = weights[0] * scene[0] + weights[1] * scene[1] + weights[2] * scene[2];
                var gain = HLG_PEAK * (ys > 0 ? Math.pow(ys, HLG_GAMMA - 1) : 0);
                for (var i = 0; i < 3; i++) {
                    light[i] = gain * scene[i];
                }
            }
        }
        return light;
    }

    /// BT.2390's EETF for a light of `nits`, with no black lift: the light it
    /// becomes, from the source peak down to [#REFERENCE_WHITE].
    double eetf(double nits) {
        var sourcePq = pqEncode(sourcePeak());
        var maxLum = pqEncode(REFERENCE_WHITE) / sourcePq;
        var knee = 1.5 * maxLum - 0.5;
        var e1 = Math.min(1, pqEncode(nits) / sourcePq);
        double e2;
        if (e1 < knee) {
            e2 = e1;
        } else {
            var t = (e1 - knee) / (1 - knee);
            var t2 = t * t;
            var t3 = t2 * t;
            e2 = (2 * t3 - 3 * t2 + 1) * knee + (t3 - 2 * t2 + t) * (1 - knee) + (-2 * t3 + 3 * t2) * maxLum;
        }
        return pqDecode(e2 * sourcePq);
    }

    /// SMPTE ST 2084's EOTF: the light, in nits, of PQ code `encoded`, 0 to 1.
    public static double pqDecode(double encoded) {
        var e = Math.pow(Math.clamp(encoded, 0, 1), 1 / PQ_M2);
        return PQ_PEAK * Math.pow(Math.max(e - PQ_C1, 0) / (PQ_C2 - PQ_C3 * e), 1 / PQ_M1);
    }

    /// SMPTE ST 2084's inverse EOTF: the PQ code, 0 to 1, of a light of `nits`.
    public static double pqEncode(double nits) {
        var y = Math.pow(Math.clamp(nits / PQ_PEAK, 0, 1), PQ_M1);
        return Math.pow((PQ_C1 + PQ_C2 * y) / (1 + PQ_C3 * y), PQ_M2);
    }

    /// ARIB STD-B67's inverse OETF: relative scene light, 0 to 1, of HLG code
    /// `encoded`, 0 to 1.
    public static double hlgInverseOetf(double encoded) {
        var e = Math.clamp(encoded, 0, 1);
        return e <= 0.5 ? e * e / 3 : (Math.exp((e - HLG_C) / HLG_A) + HLG_B) / 12;
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        var out = new double[3];
        for (var row = 0; row < 3; row++) {
            out[row] = matrix[row][0] * vector[0] + matrix[row][1] * vector[1] + matrix[row][2] * vector[2];
        }
        return out;
    }

    private static double[][] multiply(double[][] left, double[][] right) {
        var out = new double[3][3];
        for (var row = 0; row < 3; row++) {
            for (var column = 0; column < 3; column++) {
                out[row][column] = left[row][0] * right[0][column]
                        + left[row][1] * right[1][column]
                        + left[row][2] * right[2][column];
            }
        }
        return out;
    }

    private static double[][] invert(double[][] m) {
        var a = m[0][0];
        var b = m[0][1];
        var c = m[0][2];
        var d = m[1][0];
        var e = m[1][1];
        var f = m[1][2];
        var g = m[2][0];
        var h = m[2][1];
        var i = m[2][2];
        var det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        return new double[][] {
            {(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det},
            {(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det},
            {(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det}
        };
    }
}
