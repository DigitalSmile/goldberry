package dev.goldberry.gpu.upscale.fsr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.upscale.TestPattern;

/// FSR 1's constants, and the Java reference the shaders are held to: that it
/// does what FSR is for -- keeps flat areas flat, stays within the range of
/// what it reads, sharpens without ringing -- before any GPU is compared with
/// it. Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
@DisplayName("FSR 1")
class Fsr1Test {

    @Nested
    @DisplayName("constants")
    class Constants {

        @Test
        @DisplayName("are FsrEasuCon's for a picture upscaled from 960x540 to 1920x1080")
        void easu() {
            var con = Fsr1.easu(960, 540, 1920, 1080);
            assertEquals(Fsr1.EASU_FLOATS, con.length);
            var texelX = 1f / 960;
            var texelY = 1f / 540;
            assertArrayEquals(
                    new float[] {
                        0.5f,
                        0.5f,
                        -0.25f,
                        -0.25f,
                        texelX,
                        texelY,
                        texelX,
                        -texelY,
                        -texelX,
                        2 * texelY,
                        texelX,
                        2 * texelY,
                        0,
                        4 * texelY,
                        0,
                        0
                    },
                    con);
        }

        @Test
        @DisplayName("are the sharpness and the last pixel for RCAS")
        void rcas() {
            assertArrayEquals(new float[] {0.87f, 1919, 1079, 0}, Fsr1.rcas(0.87, 1920, 1080));
            assertEquals(Fsr1.RCAS_FLOATS, Fsr1.rcas(0, 1, 1).length);
        }

        @Test
        @DisplayName("refuse an empty picture and a sharpness outside 0 to 1")
        void refuse() {
            assertThrows(IllegalArgumentException.class, () -> Fsr1.easu(0, 1, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> Fsr1.easu(1, 1, 1, 0));
            assertThrows(IllegalArgumentException.class, () -> Fsr1.rcas(1.5, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> Fsr1.rcas(Double.NaN, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> Fsr1.rcas(0.5, 0, 1));
        }
    }

    @Nested
    @DisplayName("approximations")
    class Approximations {

        @Test
        @DisplayName("are within ffx_a.h's errors of the reciprocal and the reciprocal square root")
        void closeEnough() {
            for (var a = 0.01f; a < 100f; a *= 1.37f) {
                assertEquals(1 / a, Fsr1Reference.rcpLow(a), 0.13 / a, "rcp " + a);
                assertEquals(1 / Math.sqrt(a), Fsr1Reference.rsqLow(a), 0.06 / Math.sqrt(a), "rsq " + a);
                assertEquals(1 / a, Fsr1Reference.rcpMedium(a), 0.004 / a, "medium rcp " + a);
            }
        }
    }

    @Nested
    @DisplayName("the reference")
    class Reference {

        private static Fsr1Reference.Rgb flat(int width, int height, float value) {
            var rgb = new float[3 * width * height];
            Arrays.fill(rgb, value);
            return new Fsr1Reference.Rgb(width, height, rgb);
        }

        @Test
        @DisplayName("upscales a flat picture to the same flat picture")
        void flatStaysFlat() {
            var out = Fsr1Reference.easu(flat(8, 6, 0.4f), 16, 12);
            for (var value : out.rgb()) {
                assertEquals(0.4f, value, 1e-6f);
            }
        }

        @Test
        @DisplayName("never leaves the range of the four texels nearest each pixel: no ringing")
        void staysInRange() {
            var input = TestPattern.of(48, 36);
            var out = Fsr1Reference.easu(input, 96, 72);
            var min = 1f;
            var max = 0f;
            for (var value : input.rgb()) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            for (var value : out.rgb()) {
                assertTrue(value >= min - 1e-6f && value <= max + 1e-6f, value + " outside " + min + " to " + max);
            }
        }

        @Test
        @DisplayName("keeps an edge sharper than bilinear does")
        void sharperThanLinear() {
            // A vertical edge between columns 3 and 4, upscaled 2x: the pixels
            // either side of it should be nearer to its two sides with EASU.
            var rgb = new float[3 * 8 * 8];
            for (var y = 0; y < 8; y++) {
                for (var x = 4; x < 8; x++) {
                    Arrays.fill(rgb, 3 * (y * 8 + x), 3 * (y * 8 + x) + 3, 1f);
                }
            }
            var edge = new Fsr1Reference.Rgb(8, 8, rgb);
            var easu = Fsr1Reference.easu(edge, 16, 16);
            var linear = Fsr1Reference.linear(edge, 16, 16);
            var easuSpread = Math.abs(easu.at(7, 8, 0) - 0) + Math.abs(1 - easu.at(8, 8, 0));
            var linearSpread = Math.abs(linear.at(7, 8, 0) - 0) + Math.abs(1 - linear.at(8, 8, 0));
            assertTrue(easuSpread < linearSpread, "EASU " + easuSpread + ", bilinear " + linearSpread);
        }

        @Test
        @DisplayName("sharpens a flat picture to within FSR's medium reciprocal of itself, and a soft edge more")
        void rcas() {
            for (var value : Fsr1Reference.rcas(flat(4, 4, 0.6f), 1).rgb()) {
                assertEquals(0.6f, value, 0.004f);
            }
            var soft = Fsr1Reference.linear(TestPattern.of(24, 18), 48, 36);
            var gentle = Fsr1Reference.rcas(soft, 0.25);
            var strong = Fsr1Reference.rcas(soft, 1);
            assertTrue(contrast(strong) > contrast(gentle) && contrast(gentle) > contrast(soft));
        }

        @Test
        @DisplayName("resamples bilinearly from texel centres: a 2x downscale is a box filter")
        void linear() {
            var input = TestPattern.of(8, 6);
            var half = Fsr1Reference.linear(input, 4, 3);
            for (var channel = 0; channel < 3; channel++) {
                var box = (input.at(2, 2, channel)
                                + input.at(3, 2, channel)
                                + input.at(2, 3, channel)
                                + input.at(3, 3, channel))
                        / 4;
                assertEquals(box, half.at(1, 1, channel), 1e-6f);
            }
            assertArrayEquals(input.rgb(), Fsr1Reference.linear(input, 8, 6).rgb(), "the same size changes nothing");
        }

        /// The sum of the differences between horizontal neighbours.
        private static double contrast(Fsr1Reference.Rgb picture) {
            var sum = 0.0;
            for (var y = 0; y < picture.height(); y++) {
                for (var x = 1; x < picture.width(); x++) {
                    sum += Math.abs(picture.at(x, y, 1) - picture.at(x - 1, y, 1));
                }
            }
            return sum;
        }
    }
}
