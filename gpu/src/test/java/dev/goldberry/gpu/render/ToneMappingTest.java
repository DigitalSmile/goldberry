package dev.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [ToneMapping] as the reference the HDR shaders are held to: the standards'
/// own numbers, and the uniform block the shaders read.
@DisplayName("ToneMapping")
class ToneMappingTest {

    private static final ToneMapping HDR10 = new ToneMapping(ToneMapping.Gamut.BT2020, ToneMapping.Curve.PQ, 1000);

    @Test
    @DisplayName("decodes PQ and HLG as SMPTE ST 2084 and ARIB STD-B67 define them")
    void curves() {
        assertEquals(10_000, ToneMapping.pqDecode(1), 1e-6);
        assertEquals(0.5081, ToneMapping.pqEncode(100), 1e-4);
        assertEquals(92.25, ToneMapping.pqDecode(0.5), 0.01);
        assertEquals(1.0 / 12, ToneMapping.hlgInverseOetf(0.5), 1e-9);
        assertEquals(1, ToneMapping.hlgInverseOetf(1), 1e-6);
    }

    @Test
    @DisplayName("BT.709's matrix into BT.709 is the identity, and BT.2020's keeps white white")
    void gamuts() {
        var identity = ToneMapping.Gamut.BT709.toBt709();
        for (var row = 0; row < 3; row++) {
            for (var column = 0; column < 3; column++) {
                assertEquals(row == column ? 1 : 0, identity[row][column], 1e-9);
            }
        }
        var wide = ToneMapping.Gamut.BT2020.toBt709();
        for (var row = 0; row < 3; row++) {
            assertEquals(1, wide[row][0] + wide[row][1] + wide[row][2], 1e-9, "row " + row + " sums to one");
        }
        // BT.2020's luminance weights, as BT.2020 states them.
        assertArrayEquals(new double[] {0.2627, 0.6780, 0.0593}, ToneMapping.Gamut.BT2020.luminance(), 1e-4);
    }

    @Test
    @DisplayName("maps HDR10's peak to white and keeps light below the knee")
    void eetf() {
        var peak = ToneMapping.pqEncode(1000);
        assertArrayEquals(new double[] {1, 1, 1}, HDR10.apply(new double[] {peak, peak, peak}), 1e-6);
        var dim = ToneMapping.pqEncode(10);
        var expected = Math.pow(10 / 203.0, 1 / 2.4);
        assertArrayEquals(new double[] {expected, expected, expected}, HDR10.apply(new double[] {dim, dim, dim}), 1e-9);
        assertTrue(HDR10.tonemaps());
        assertFalse(new ToneMapping(ToneMapping.Gamut.BT2020, ToneMapping.Curve.SDR, 1000).tonemaps());
        assertFalse(new ToneMapping(ToneMapping.Gamut.BT709, ToneMapping.Curve.PQ, 150).tonemaps());
    }

    @Test
    @DisplayName("the uniform block is six vectors: the curve, the EETF, three gamut rows, HLG's weights")
    void uniforms() {
        var block = HDR10.uniforms();
        assertEquals(24, block.length);
        assertEquals(1, block[0], "PQ");
        assertEquals(1, block[1], "tone-mapped");
        assertEquals(1000, block[2]);
        assertEquals((float) ToneMapping.pqEncode(1000), block[4]);
        var maxLum = ToneMapping.pqEncode(203) / ToneMapping.pqEncode(1000);
        assertEquals((float) maxLum, block[5]);
        assertEquals((float) (1.5 * maxLum - 0.5), block[6]);
        var matrix = ToneMapping.Gamut.BT2020.toBt709();
        assertEquals((float) matrix[0][0], block[8]);
        assertEquals((float) matrix[2][2], block[18]);
        assertEquals(1000, block[23]);
    }
}
