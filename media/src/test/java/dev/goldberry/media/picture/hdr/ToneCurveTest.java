package dev.goldberry.media.picture.hdr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.VideoFrame;

@DisplayName("ToneCurve")
class ToneCurveTest {

    private static final ToneCurve HDR10 = ToneCurve.of(ColorDescription.fromH273(9, 16));
    private static final ToneCurve HLG = ToneCurve.of(ColorDescription.fromH273(9, 18));
    private static final ToneCurve WIDE_SDR = ToneCurve.of(ColorDescription.fromH273(9, 1));

    @Test
    @DisplayName("decodes PQ as SMPTE ST 2084 tabulates it")
    void pq() {
        assertEquals(0, ToneCurve.pqDecode(0), 1e-9);
        assertEquals(10_000, ToneCurve.pqDecode(1), 1e-6);
        // ST 2084's code for 100 nits, and the code a half is.
        assertEquals(0.5081, ToneCurve.pqEncode(100), 1e-4);
        assertEquals(92.25, ToneCurve.pqDecode(0.5), 0.01);
        for (var nits : new double[] {0.01, 1, 203, 1000, 4000}) {
            assertEquals(nits, ToneCurve.pqDecode(ToneCurve.pqEncode(nits)), nits * 1e-9, "round trip at " + nits);
        }
    }

    @Test
    @DisplayName("decodes HLG as ARIB STD-B67 defines it, continuous at a half")
    void hlg() {
        assertEquals(1.0 / 12, ToneCurve.hlgInverseOetf(0.5), 1e-9);
        assertEquals(1.0, ToneCurve.hlgInverseOetf(1), 1e-6);
        assertEquals(ToneCurve.hlgInverseOetf(0.5), ToneCurve.hlgInverseOetf(0.5 + 1e-9), 1e-8);
    }

    @Test
    @DisplayName("SDR in BT.709 is the identity and needs no tables")
    void identity() {
        assertTrue(ToneCurve.of(ColorDescription.UNSPECIFIED).isIdentity());
        assertTrue(ToneCurve.of(ColorDescription.SDR).isIdentity());
        assertFalse(WIDE_SDR.isIdentity());
        assertFalse(WIDE_SDR.tonemaps(), "an SDR picture is only converted into BT.709");
    }

    @Test
    @DisplayName("an SDR picture in BT.2020 keeps its greys, and BT.709's colours come back as they went in")
    void wideGamutRoundTrip() {
        for (var grey : new double[] {0, 0.25, 0.5, 1}) {
            assertArrayEquals(new double[] {grey, grey, grey}, WIDE_SDR.apply(new double[] {grey, grey, grey}), 1e-9);
        }
        // BT.709's red, expressed in BT.2020: converted back, it is red again.
        var toBt2020 = invert3(ToneCurve.toBt709(ColorDescription.Primaries.BT2020));
        var red2020 = new double[3];
        for (var row = 0; row < 3; row++) {
            red2020[row] = Math.pow(toBt2020[row][0] * 0.5, 1 / 2.4);
        }
        assertArrayEquals(new double[] {Math.pow(0.5, 1 / 2.4), 0, 0}, WIDE_SDR.apply(red2020), 1e-9);
    }

    @Test
    @DisplayName("maps HDR10's peak to white, and leaves the darks as they are")
    void eetf() {
        var peak = ToneCurve.pqEncode(1000);
        assertArrayEquals(new double[] {1, 1, 1}, HDR10.apply(new double[] {peak, peak, peak}), 1e-6);
        // Below the knee the light is kept: 10 nits are 10 nits of the 203.
        var dim = ToneCurve.pqEncode(10);
        var expected = Math.pow(10 / 203.0, 1 / 2.4);
        assertArrayEquals(new double[] {expected, expected, expected}, HDR10.apply(new double[] {dim, dim, dim}), 1e-9);
        // Brighter than the stated peak is white too.
        assertArrayEquals(new double[] {1, 1, 1}, HDR10.apply(new double[] {1, 1, 1}), 1e-6);
    }

    @Test
    @DisplayName("the EETF never darkens a brighter light below a dimmer one")
    void monotonic() {
        var last = -1.0;
        for (var nits = 0.0; nits <= 1200; nits += 1) {
            var mapped = HDR10.eetf(nits);
            assertTrue(mapped >= last, "at " + nits);
            assertTrue(mapped <= ToneCurve.REFERENCE_WHITE + 1e-6, "at " + nits);
            last = mapped;
        }
    }

    @Test
    @DisplayName("keeps a tone-mapped colour's hue: its channels in the same ratios")
    void hue() {
        var orange = new double[] {ToneCurve.pqEncode(900), ToneCurve.pqEncode(450), ToneCurve.pqEncode(90)};
        var bt709 = ToneCurve.of(new ColorDescription(
                ColorDescription.Primaries.BT709,
                ColorDescription.Transfer.PQ,
                OptionalInt.empty(),
                OptionalInt.empty()));
        var out = bt709.apply(orange);
        var r = Math.pow(out[0], 2.4);
        var g = Math.pow(out[1], 2.4);
        var b = Math.pow(out[2], 2.4);
        assertEquals(0.5, g / r, 1e-9);
        assertEquals(0.1, b / r, 1e-9);
    }

    @Test
    @DisplayName("HLG's OOTF puts its nominal white at the peak, which tone-maps to white")
    void hlgWhite() {
        assertArrayEquals(new double[] {1, 1, 1}, HLG.apply(new double[] {1, 1, 1}), 1e-6);
        assertTrue(HLG.tonemaps());
        assertEquals(ToneCurve.FLAG_HLG | ToneCurve.FLAG_EETF, HLG.flags());
    }

    @Test
    @DisplayName("a PQ picture no brighter than the target is not tone-mapped")
    void dimPq() {
        var dim = ToneCurve.of(ColorDescription.fromH273(1, 16).withLightLevels(150, 0));
        assertFalse(dim.tonemaps());
        assertEquals(0, dim.flags() & ToneCurve.FLAG_EETF);
        var tables = dim.tables(VideoFrame.ColorMatrix.BT709, false, 10);
        var ratio = ToneCurve.HEADER + 2 * (ToneCurve.STEPS + 1);
        for (var step = 0; step <= ToneCurve.STEPS; step++) {
            assertEquals(1, tables[ratio + step]);
        }
    }

    @Test
    @DisplayName("the tables the natives read, followed in Java, agree with the reference to a level")
    void tablesAgree() {
        for (var curve : new ToneCurve[] {HDR10, HLG, WIDE_SDR}) {
            var tables = curve.tables(VideoFrame.ColorMatrix.BT2020, false, 10);
            for (var r = 0; r <= 1023; r += 31) {
                for (var g = 0; g <= 1023; g += 93) {
                    for (var b = 0; b <= 1023; b += 201) {
                        var rgb = new double[] {r / 1023.0, g / 1023.0, b / 1023.0};
                        var expected = curve.apply(rgb);
                        var actual = followTables(curve.flags(), tables, rgb);
                        for (var c = 0; c < 3; c++) {
                            assertEquals(
                                    expected[c] * 255,
                                    actual[c],
                                    1.0,
                                    curve + " channel " + c + " at " + r + "," + g + "," + b);
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the header holds the Y'CbCr range and matrix of the picture, as GPU present's uniforms do")
    void header() {
        var tables = HDR10.tables(VideoFrame.ColorMatrix.BT2020, false, 10);
        assertEquals(64 / 1023.0, tables[0], 1e-7, "limited luma's offset");
        assertEquals(1023 / 876.0, tables[1], 1e-6, "and gain");
        assertEquals(512 / 1023.0, tables[2], 1e-7, "chroma's middle");
        assertEquals(1023 / 896.0, tables[3], 1e-6);
        assertEquals(2 * (1 - 0.2627), tables[4], 1e-6, "BT.2020's Cr to R");
        var full = HDR10.tables(VideoFrame.ColorMatrix.BT709, true, 8);
        assertEquals(0, full[0]);
        assertEquals(1, full[1]);
        assertEquals(128 / 255.0, full[2], 1e-7);
        assertEquals(ToneCurve.HEADER + 4 * (ToneCurve.STEPS + 1), full.length);
    }

    /// `goldberry_media_tone_map`'s arithmetic after its Y'CbCr conversion, in
    /// Java: what the tables are for.
    private static double[] followTables(int flags, float[] t, double[] rgb) {
        var points = ToneCurve.STEPS + 1;
        var base = ToneCurve.HEADER;
        double lr = lerp(t, base, rgb[0] * ToneCurve.STEPS);
        double lg = lerp(t, base, rgb[1] * ToneCurve.STEPS);
        double lb = lerp(t, base, rgb[2] * ToneCurve.STEPS);
        if ((flags & ToneCurve.FLAG_HLG) != 0) {
            var ys = t[17] * lr + t[18] * lg + t[19] * lb;
            var gain = lerp(t, base + points, Math.sqrt(Math.max(ys, 0)) * ToneCurve.STEPS);
            lr *= gain;
            lg *= gain;
            lb *= gain;
        }
        var mr = Math.max(0, t[8] * lr + t[9] * lg + t[10] * lb);
        var mg = Math.max(0, t[11] * lr + t[12] * lg + t[13] * lb);
        var mb = Math.max(0, t[14] * lr + t[15] * lg + t[16] * lb);
        if ((flags & ToneCurve.FLAG_EETF) != 0) {
            var largest = Math.max(mr, Math.max(mg, mb));
            if (largest > 0) {
                var at = largest * t[20];
                var k = at >= ToneCurve.STEPS ? 1 / largest : lerp(t, base + 2 * points, at);
                mr *= k;
                mg *= k;
                mb *= k;
            }
        }
        var encode = base + 3 * points;
        return new double[] {
            lerp(t, encode, Math.sqrt(Math.min(mr, 1)) * ToneCurve.STEPS),
            lerp(t, encode, Math.sqrt(Math.min(mg, 1)) * ToneCurve.STEPS),
            lerp(t, encode, Math.sqrt(Math.min(mb, 1)) * ToneCurve.STEPS)
        };
    }

    private static double lerp(float[] table, int offset, double x) {
        if (!(x > 0)) {
            return table[offset];
        }
        if (x >= ToneCurve.STEPS) {
            return table[offset + ToneCurve.STEPS];
        }
        var i = (int) x;
        var f = x - i;
        return table[offset + i] + (table[offset + i + 1] - table[offset + i]) * f;
    }

    private static double[][] invert3(double[][] m) {
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
