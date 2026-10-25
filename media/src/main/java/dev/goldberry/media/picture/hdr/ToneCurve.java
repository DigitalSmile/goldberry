package dev.goldberry.media.picture.hdr;

import java.util.Objects;

import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.VideoFrame;

/// HDR and wide-gamut R'G'B' to SDR BT.709 for CPU present: the arithmetic,
/// and the tables `goldberry_media_tone_map` maps a converted picture through.
///
/// The same four steps GPU present's shader takes, in the same numbers, so
/// the two present paths draw one picture:
///
/// 1. **To light**, in units of SDR's reference white (203 nits, ITU-R
///    BT.2408): PQ's EOTF (SMPTE ST 2084); HLG's inverse OETF, then BT.2100's
///    display OOTF `Lw × Ys^(γ−1) × E` at `Lw` 1000 nits and `γ` 1.2, with
///    `Ys` the scene luminance in the picture's primaries; SDR through BT.1886's
///    gamma of 2.4.
/// 2. **Into BT.709's primaries**, through CIE XYZ, the whites both D65, and
///    below zero clipped to zero.
/// 3. **Tone-mapped**, HDR only and only from a peak brighter than the target:
///    the largest channel `m` through BT.2390's EETF in PQ's perceptual scale,
///    from the peak down to the reference white, and all three scaled by
///    `m′ / m`, which keeps hue and keeps every channel within white.
/// 4. **Encoded**: clamped to white and raised to `1 / 2.4`.
///
/// The media natives convert the planes themselves for such a picture, in
/// floats, as GPU present does: libyuv's conversions round to its fixed point
/// first, half a percent short at white, which PQ's highlights magnify into
/// several levels. The tables sample the curves so that one pass in C does a
/// pixel with no `pow`: 4097 points each of the decoding over R', of the
/// OOTF's gain over the square root of `Ys`, of `m′ / m` over `m`, and of the
/// encoding over the square root of the light, which spends the points where
/// the curves bend.
///
/// @param primaries the picture's primaries, with unspecified read as BT.709
/// @param transfer  the picture's transfer function, with unspecified read as
///                  SDR
/// @param peakNits  the brightest the picture reaches
public record ToneCurve(ColorDescription.Primaries primaries, ColorDescription.Transfer transfer, double peakNits) {

    /// SDR's reference white, in nits: the target's white.
    public static final double REFERENCE_WHITE = ColorDescription.REFERENCE_WHITE;

    /// How many steps the sampled curves have; each has one more point.
    public static final int STEPS = 4096;

    /// How many floats come before the curves in [#tables].
    public static final int HEADER = 32;

    /// `goldberry_media_tone_map`'s flag for HLG's OOTF.
    public static final int FLAG_HLG = 2;

    /// `goldberry_media_tone_map`'s flag for the EETF.
    public static final int FLAG_EETF = 4;

    private static final double HLG_PEAK = ColorDescription.DEFAULT_HDR_PEAK;
    private static final double HLG_GAMMA = 1.2;
    private static final double SDR_GAMMA = 2.4;

    private static final double PQ_M1 = 2610.0 / 16384;
    private static final double PQ_M2 = 2523.0 / 4096 * 128;
    private static final double PQ_C1 = 3424.0 / 4096;
    private static final double PQ_C2 = 2413.0 / 4096 * 32;
    private static final double PQ_C3 = 2392.0 / 4096 * 32;
    private static final double PQ_PEAK = 10_000;

    private static final double HLG_A = 0.17883277;
    private static final double HLG_B = 1 - 4 * HLG_A;
    private static final double HLG_C = 0.5 - HLG_A * Math.log(4 * HLG_A);

    private static final double WHITE_X = 0.3127;
    private static final double WHITE_Y = 0.3290;

    public ToneCurve {
        Objects.requireNonNull(primaries, "primaries");
        Objects.requireNonNull(transfer, "transfer");
        if (!(peakNits > 0) || !Double.isFinite(peakNits)) {
            throw new IllegalArgumentException("a peak of " + peakNits + " nits");
        }
    }

    /// The curve for `colour`: [#isIdentity()] for SDR in BT.709 primaries,
    /// with what is unspecified read as those.
    public static ToneCurve of(ColorDescription colour) {
        Objects.requireNonNull(colour, "colour");
        return new ToneCurve(colour.primaries(), colour.transfer(), colour.peakNits());
    }

    /// Whether the picture is drawn as libyuv converts it.
    public boolean isIdentity() {
        return gamut() == ColorDescription.Primaries.BT709 && curve() == ColorDescription.Transfer.SDR;
    }

    /// Whether the EETF runs: an HDR picture brighter than the target.
    public boolean tonemaps() {
        return curve() != ColorDescription.Transfer.SDR && sourcePeak() > REFERENCE_WHITE;
    }

    /// The flags for these tables.
    public int flags() {
        return (curve() == ColorDescription.Transfer.HLG ? FLAG_HLG : 0) | (tonemaps() ? FLAG_EETF : 0);
    }

    /// The picture's R'G'B', 0 to 1, as the 0-to-1 R'G'B' it is drawn as: the
    /// reference, in doubles.
    public double[] apply(double[] rgb) {
        Objects.requireNonNull(rgb, "rgb");
        if (rgb.length != 3) {
            throw new IllegalArgumentException("three channels, not " + rgb.length);
        }
        var light = new double[3];
        for (var i = 0; i < 3; i++) {
            light[i] = decode(rgb[i]);
        }
        if (curve() == ColorDescription.Transfer.HLG) {
            var gain = hlgGain(dot(luminance(gamut()), light));
            for (var i = 0; i < 3; i++) {
                light[i] *= gain;
            }
        }
        var mapped = clipped(multiply(toBt709(gamut()), light));
        if (tonemaps()) {
            var largest = Math.max(mapped[0], Math.max(mapped[1], mapped[2]));
            if (largest > 0) {
                var ratio = ratio(largest);
                for (var i = 0; i < 3; i++) {
                    mapped[i] *= ratio;
                }
            }
        }
        var out = new double[3];
        for (var i = 0; i < 3; i++) {
            out[i] = Math.pow(Math.clamp(mapped[i], 0, 1), 1 / SDR_GAMMA);
        }
        return out;
    }

    /// What `goldberry_media_tone_map` converts a picture of `matrix` and
    /// `fullRange`, `bitDepth` bits a sample, through: a header of
    /// [#HEADER] floats, the Y'CbCr range and matrix, the matrix into BT.709's
    /// primaries, HLG's luminance weights and the ratio table's scale, then four
    /// curves of [#STEPS] + 1 points: R' to light (HLG's relative scene light),
    /// HLG's OOTF gain over the square root of the scene luminance, `m′ / m`
    /// over the largest channel, and the encoding over the square root of the
    /// light.
    public float[] tables(VideoFrame.ColorMatrix matrix, boolean fullRange, int bitDepth) {
        Objects.requireNonNull(matrix, "matrix");
        if (bitDepth != 8 && bitDepth != 10) {
            throw new IllegalArgumentException(bitDepth + " bits a sample");
        }
        var points = STEPS + 1;
        var table = new float[HEADER + 4 * points];
        var max = (1 << bitDepth) - 1.0;
        var scale = 1 << (bitDepth - 8);
        var middle = (1 << (bitDepth - 1)) / max;
        table[0] = fullRange ? 0 : (float) (16.0 * scale / max);
        table[1] = fullRange ? 1 : (float) (max / (219.0 * scale));
        table[2] = (float) middle;
        table[3] = fullRange ? 1 : (float) (max / (224.0 * scale));
        var kr = kr(matrix);
        var kb = kb(matrix);
        var kg = 1 - kr - kb;
        table[4] = (float) (2 * (1 - kr));
        table[5] = (float) (-2 * kb * (1 - kb) / kg);
        table[6] = (float) (-2 * kr * (1 - kr) / kg);
        table[7] = (float) (2 * (1 - kb));
        var gamutMatrix = toBt709(gamut());
        for (var row = 0; row < 3; row++) {
            for (var column = 0; column < 3; column++) {
                table[8 + row * 3 + column] = (float) gamutMatrix[row][column];
            }
        }
        var weights = luminance(gamut());
        for (var i = 0; i < 3; i++) {
            table[17 + i] = (float) weights[i];
        }
        var end = sourcePeak() / REFERENCE_WHITE;
        table[20] = (float) (STEPS / end);
        var hlg = curve() == ColorDescription.Transfer.HLG;
        for (var step = 0; step <= STEPS; step++) {
            var at = step / (double) STEPS;
            table[HEADER + step] = (float) decode(at);
            table[HEADER + points + step] = hlg ? (float) hlgGain(at * at) : 0;
            var light = end * at;
            table[HEADER + 2 * points + step] = (float) (tonemaps() && step > 0 ? ratio(light) : 1);
            table[HEADER + 3 * points + step] = (float) (255 * Math.pow(at * at, 1 / SDR_GAMMA));
        }
        return table;
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

    /// The light of `encoded`, 0 to 1, in units of the reference white; for
    /// HLG, relative scene light.
    private double decode(double encoded) {
        return switch (transfer) {
            case PQ -> pqDecode(encoded) / REFERENCE_WHITE;
            case HLG -> hlgInverseOetf(encoded);
            case SDR, UNSPECIFIED -> Math.pow(Math.clamp(encoded, 0, 1), SDR_GAMMA);
        };
    }

    /// HLG's OOTF gain for a scene luminance of `ys`, in units of the
    /// reference white.
    private static double hlgGain(double ys) {
        return HLG_PEAK / REFERENCE_WHITE * (ys > 0 ? Math.pow(ys, HLG_GAMMA - 1) : 0);
    }

    /// `m′ / m` for the largest channel at `light` units of the reference white.
    private double ratio(double light) {
        return eetf(light * REFERENCE_WHITE) / REFERENCE_WHITE / light;
    }

    /// BT.2390's EETF, with no black lift: a light of `nits` from the source
    /// peak down to the reference white.
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

    private double sourcePeak() {
        return curve() == ColorDescription.Transfer.HLG ? HLG_PEAK : peakNits;
    }

    private ColorDescription.Primaries gamut() {
        return primaries == ColorDescription.Primaries.UNSPECIFIED ? ColorDescription.Primaries.BT709 : primaries;
    }

    private ColorDescription.Transfer curve() {
        return transfer == ColorDescription.Transfer.UNSPECIFIED ? ColorDescription.Transfer.SDR : transfer;
    }

    /// SMPTE ST 2084's EOTF: the light, in nits, of PQ code `encoded`.
    public static double pqDecode(double encoded) {
        var e = Math.pow(Math.clamp(encoded, 0, 1), 1 / PQ_M2);
        return PQ_PEAK * Math.pow(Math.max(e - PQ_C1, 0) / (PQ_C2 - PQ_C3 * e), 1 / PQ_M1);
    }

    /// SMPTE ST 2084's inverse EOTF: the PQ code of a light of `nits`.
    public static double pqEncode(double nits) {
        var y = Math.pow(Math.clamp(nits / PQ_PEAK, 0, 1), PQ_M1);
        return Math.pow((PQ_C1 + PQ_C2 * y) / (1 + PQ_C3 * y), PQ_M2);
    }

    /// ARIB STD-B67's inverse OETF: relative scene light of HLG code `encoded`.
    public static double hlgInverseOetf(double encoded) {
        var e = Math.clamp(encoded, 0, 1);
        return e <= 0.5 ? e * e / 3 : (Math.exp((e - HLG_C) / HLG_A) + HLG_B) / 12;
    }

    /// The chromaticities of `primaries`: red, green, blue, each `x` then `y`.
    private static double[] chromaticities(ColorDescription.Primaries primaries) {
        return switch (primaries) {
            case UNSPECIFIED, BT709 -> new double[] {0.640, 0.330, 0.300, 0.600, 0.150, 0.060};
            case BT2020 -> new double[] {0.708, 0.292, 0.170, 0.797, 0.131, 0.046};
            case DISPLAY_P3 -> new double[] {0.680, 0.320, 0.265, 0.690, 0.150, 0.060};
        };
    }

    /// Linear RGB in `primaries` to CIE XYZ, white at `Y = 1`.
    static double[][] toXyz(ColorDescription.Primaries primaries) {
        var c = chromaticities(primaries);
        var columns = new double[3][3];
        for (var i = 0; i < 3; i++) {
            var x = c[2 * i];
            var y = c[2 * i + 1];
            columns[0][i] = x / y;
            columns[1][i] = 1;
            columns[2][i] = (1 - x - y) / y;
        }
        var scale = multiply(invert(columns), new double[] {WHITE_X / WHITE_Y, 1, (1 - WHITE_X - WHITE_Y) / WHITE_Y});
        var matrix = new double[3][3];
        for (var row = 0; row < 3; row++) {
            for (var column = 0; column < 3; column++) {
                matrix[row][column] = columns[row][column] * scale[column];
            }
        }
        return matrix;
    }

    static double[] luminance(ColorDescription.Primaries primaries) {
        return toXyz(primaries)[1];
    }

    static double[][] toBt709(ColorDescription.Primaries primaries) {
        return multiply(invert(toXyz(ColorDescription.Primaries.BT709)), toXyz(primaries));
    }

    private static double[] clipped(double[] values) {
        return new double[] {Math.max(0, values[0]), Math.max(0, values[1]), Math.max(0, values[2])};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        return new double[] {dot(matrix[0], vector), dot(matrix[1], vector), dot(matrix[2], vector)};
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
        var det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
        return new double[][] {
            {
                (m[1][1] * m[2][2] - m[1][2] * m[2][1]) / det,
                (m[0][2] * m[2][1] - m[0][1] * m[2][2]) / det,
                (m[0][1] * m[1][2] - m[0][2] * m[1][1]) / det
            },
            {
                (m[1][2] * m[2][0] - m[1][0] * m[2][2]) / det,
                (m[0][0] * m[2][2] - m[0][2] * m[2][0]) / det,
                (m[0][2] * m[1][0] - m[0][0] * m[1][2]) / det
            },
            {
                (m[1][0] * m[2][1] - m[1][1] * m[2][0]) / det,
                (m[0][1] * m[2][0] - m[0][0] * m[2][1]) / det,
                (m[0][0] * m[1][1] - m[0][1] * m[1][0]) / det
            }
        };
    }
}
