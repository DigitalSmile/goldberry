package io.github.digitalsmile.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// The reference conversion against values the standards give. No device.
@DisplayName("YuvConversion")
class YuvConversionTest {

    @ParameterizedTest
    @EnumSource(YuvMatrix.class)
    @DisplayName("maps limited-range black and white to 0 and 1, 8-bit and 10-bit")
    void limitedBlackAndWhite(YuvMatrix matrix) {
        var eight = new YuvConversion(YuvLayout.NV12, matrix, false);
        assertArrayEquals(new int[] {0, 0, 0}, eight.toRgbBytes(16, 128, 128));
        assertArrayEquals(new int[] {255, 255, 255}, eight.toRgbBytes(235, 128, 128));
        var ten = new YuvConversion(YuvLayout.P010, matrix, false);
        assertArrayEquals(new int[] {0, 0, 0}, ten.toRgbBytes(64, 512, 512));
        assertArrayEquals(new int[] {255, 255, 255}, ten.toRgbBytes(940, 512, 512));
    }

    @Test
    @DisplayName("maps full-range black and white to 0 and 1")
    void fullBlackAndWhite() {
        var full = new YuvConversion(YuvLayout.I420, YuvMatrix.BT601, true);
        assertArrayEquals(new int[] {0, 0, 0}, full.toRgbBytes(0, 128, 128));
        assertArrayEquals(new int[] {255, 255, 255}, full.toRgbBytes(255, 128, 128));
    }

    @ParameterizedTest
    @EnumSource(YuvMatrix.class)
    @DisplayName("inverts each matrix's own encoding of the primaries, within a code")
    void roundTripsThePrimaries(YuvMatrix matrix) {
        var conversion = new YuvConversion(YuvLayout.I420, matrix, false);
        double[][] primaries = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 0}, {0.5, 0.25, 0.75}};
        for (var rgb : primaries) {
            var y = matrix.kr() * rgb[0] + matrix.kg() * rgb[1] + matrix.kb() * rgb[2];
            var cb = (rgb[2] - y) / (2 * (1 - matrix.kb()));
            var cr = (rgb[0] - y) / (2 * (1 - matrix.kr()));
            var back = conversion.toRgbBytes(
                    (int) Math.round(16 + 219 * y), (int) Math.round(128 + 224 * cb), (int) Math.round(128 + 224 * cr));
            for (var channel = 0; channel < 3; channel++) {
                assertEquals(rgb[channel] * 255, back[channel], 2.5, matrix + " channel " + channel);
            }
        }
    }

    @Test
    @DisplayName("gives BT.601's and BT.709's familiar red, within the rounding of its codes")
    void familiarRed() {
        // Limited-range 8-bit codes for pure red, as the standards' tables round
        // them: whole codes, so the way back is within two steps, not exact.
        assertWithin(
                new int[] {255, 0, 0},
                new YuvConversion(YuvLayout.NV12, YuvMatrix.BT601, false).toRgbBytes(81, 90, 240));
        assertWithin(
                new int[] {255, 0, 0},
                new YuvConversion(YuvLayout.NV12, YuvMatrix.BT709, false).toRgbBytes(63, 102, 240));
    }

    private static void assertWithin(int[] expected, int[] actual) {
        for (var channel = 0; channel < 3; channel++) {
            assertEquals(expected[channel], actual[channel], 2, "channel " + channel);
        }
    }

    @Test
    @DisplayName("is twelve uniforms: the transforms with the layout's scale, the siting, then the matrix")
    void uniforms() {
        var conversion = new YuvConversion(YuvLayout.P010, YuvMatrix.BT709, false);
        var block = conversion.uniforms(8);
        assertEquals(12, block.length);
        assertEquals((float) YuvLayout.P010.sampleToCode(), block[0]);
        assertEquals(64f / 1023, block[1], 1e-7);
        assertEquals(1023f / 876, block[2], 1e-6);
        assertEquals(512f / 1023, block[5], 1e-7);
        assertEquals(1023f / 896, block[6], 1e-6);
        assertEquals(0.25f / 8, block[7]);
        assertEquals(2 * (1 - 0.2126), block[8], 1e-6);
        assertEquals(2 * (1 - 0.0722), block[11], 1e-6);
        assertThrows(IllegalArgumentException.class, () -> conversion.uniforms(0));
    }

    @Test
    @DisplayName("refuses a code outside the layout's depth")
    void refusesCodesOutOfRange() {
        var conversion = new YuvConversion(YuvLayout.NV12, YuvMatrix.BT709, false);
        assertThrows(IllegalArgumentException.class, () -> conversion.toRgb(256, 128, 128));
        assertThrows(IllegalArgumentException.class, () -> conversion.toRgb(16, -1, 128));
    }

    @ParameterizedTest
    @EnumSource(YuvLayout.class)
    @DisplayName("names planes that halve for chroma, rounding up")
    void planeSizes(YuvLayout layout) {
        assertEquals(15, layout.planeWidth(0, 15));
        assertEquals(8, layout.planeWidth(1, 15));
        assertEquals(5, layout.planeHeight(layout.planeFormats().size() - 1, 9));
        assertEquals(
                layout.shader() == BuiltInShader.YUV2_FRAGMENT ? 2 : 3,
                layout.planeFormats().size());
        assertEquals(layout.planeFormats().size(), layout.shader().samplers());
    }
}
