package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendCompOp;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendExtendMode;

/// The rasterizer primitives a COLRv1 colour glyph is drawn with (ADR-0456):
/// the radial and conic gradients, a gradient with a matrix of its own, and a
/// composite operator other than the two a frame uses.
///
/// Every assertion is a pixel worked out by hand, for [BlendGradientTest]'s
/// reason. Each of these has one characteristic way of being wrong that still
/// renders — the radial's two circles swapped, a matrix read in the wrong field
/// order, an operator left set on the context — and a pixel is what tells them
/// apart from right.
class BlendColourGlyphPrimitivesTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Nested
    @DisplayName("a radial gradient")
    class Radial {

        @Test
        @DisplayName("puts the first stop on the focal circle and the last on the outer one")
        void theCirclesAreTheRightWayRound() {
            // Concentric: focal radius 0 at the centre, outer radius 32. Red at
            // the first stop, blue at the last. Swap the struct's circles and
            // the centre is blue.
            var pixels = buffer(64, 64);
            try (var image = BlendImage.wrapping(pixels, 64, 64, 256);
                    var context = BlendContext.on(image);
                    var path = BlendPath.create();
                    var gradient =
                            BlendGradient.radial(32, 32, 32, 32, 32, 0, BlendExtendMode.PAD, BlendMatrix.IDENTITY)) {
                gradient.addStop(0, RED);
                gradient.addStop(1, BLUE);
                context.clearTo(0xFFFFFFFF);
                rect(path, 0, 0, 64, 64);
                context.fillPath(0, 0, path, gradient);
            }

            var centre = pixelAt(pixels, 32, 32);
            assertTrue(red(centre) > 200 && blue(centre) < 60, () -> "the centre is red: #" + hex(centre));

            // PAD holds the last stop beyond the outer circle.
            var corner = pixelAt(pixels, 1, 1);
            assertTrue(blue(corner) > 200 && red(corner) < 60, () -> "the corner is blue: #" + hex(corner));

            // And halfway out is halfway along.
            var half = pixelAt(pixels, 48, 32);
            assertTrue(Math.abs(red(half) - blue(half)) < 40, () -> "halfway is a mix: #" + hex(half));
        }

        @Test
        @DisplayName("refuses a negative radius, which Blend2D would draw as something")
        void aRadiusIsNotNegative() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BlendGradient.radial(0, 0, -1, 0, 0, 0, BlendExtendMode.PAD, BlendMatrix.IDENTITY));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BlendGradient.radial(0, 0, 1, 0, 0, Double.NaN, BlendExtendMode.PAD, BlendMatrix.IDENTITY));
        }
    }

    @Nested
    @DisplayName("a conic gradient")
    class Conic {

        @Test
        @DisplayName("turns from its start angle, so opposite sides are opposite ends of the ramp")
        void itTurns() {
            var pixels = buffer(64, 64);
            try (var image = BlendImage.wrapping(pixels, 64, 64, 256);
                    var context = BlendContext.on(image);
                    var path = BlendPath.create();
                    var gradient = BlendGradient.conic(32, 32, 0, BlendExtendMode.PAD, BlendMatrix.IDENTITY)) {
                gradient.addStop(0, RED);
                gradient.addStop(0.5, RED);
                gradient.addStop(0.5, BLUE);
                gradient.addStop(1, BLUE);
                context.clearTo(0xFFFFFFFF);
                rect(path, 0, 0, 64, 64);
                context.fillPath(0, 0, path, gradient);
            }

            // A hard edge at half a turn: the two halves either side of the
            // horizontal line through the centre differ.
            var above = pixelAt(pixels, 32, 8);
            var below = pixelAt(pixels, 32, 56);
            assertTrue(
                    (red(above) > 200) != (red(below) > 200),
                    () -> "the two halves are different ends of the ramp: #" + hex(above) + " and #" + hex(below));
        }
    }

    @Nested
    @DisplayName("a gradient's own matrix")
    class Matrix {

        @Test
        @DisplayName("moves the ramp and not the shape")
        void placesTheRamp() {
            // A 0..16 ramp translated by 32: everything left of x = 32 is the
            // first stop. Without the matrix, x = 24 would be past the end.
            var pixels = buffer(64, 1);
            try (var image = BlendImage.wrapping(pixels, 64, 1, 256);
                    var context = BlendContext.on(image);
                    var path = BlendPath.create();
                    var gradient = BlendGradient.linear(
                            0, 0, 16, 0, BlendExtendMode.PAD, new BlendMatrix(1, 0, 0, 1, 32, 0))) {
                gradient.addStop(0, RED);
                gradient.addStop(1, BLUE);
                context.clearTo(0xFFFFFFFF);
                rect(path, 0, 0, 64, 1);
                context.fillPath(0, 0, path, gradient);
            }

            assertTrue(red(pixelAt(pixels, 24, 0)) > 240, "before the translated start it is the first stop");
            assertTrue(blue(pixelAt(pixels, 60, 0)) > 240, "after the translated end it is the last");
        }

        @Test
        @DisplayName("the identity crosses as no matrix at all, and a NaN is refused")
        void identityAndNaN() {
            assertTrue(BlendMatrix.IDENTITY.isIdentity());
            assertTrue(new BlendMatrix(1, 0, 0, 1, 0, 0).isIdentity());
            assertThrows(IllegalArgumentException.class, () -> new BlendMatrix(1, 0, 0, Double.NaN, 0, 0));
        }
    }

    @Nested
    @DisplayName("a composite operator")
    class CompOp {

        @Test
        @DisplayName("SRC_IN keeps the source only where the destination is")
        void sourceIn() {
            // Left half blue, right half transparent; then red everywhere under
            // SRC_IN. The left comes out red and the right stays empty.
            var pixels = buffer(64, 1);
            try (var image = BlendImage.wrapping(pixels, 64, 1, 256);
                    var context = BlendContext.on(image)) {
                context.clearTo(0x00000000);
                context.fillRect(0, 0, 32, 1, BLUE);
                context.compOp(BlendCompOp.SRC_IN);
                context.fillRect(0, 0, 64, 1, RED);
                context.compOp(BlendCompOp.SRC_OVER);
            }

            assertEquals(0xFFFF0000, pixelAt(pixels, 8, 0), "inside the destination, the source");
            assertEquals(0, pixelAt(pixels, 48, 0) >>> 24, "outside it, nothing");
        }

        @Test
        @DisplayName("save and restore carry it, so a composite cannot leak into the next fill")
        void restoreResetsIt() {
            var pixels = buffer(8, 1);
            try (var image = BlendImage.wrapping(pixels, 8, 1, 32);
                    var context = BlendContext.on(image)) {
                context.clearTo(0x00000000);
                context.save();
                context.compOp(BlendCompOp.SRC_IN);
                context.restore();
                // Under a leaked SRC_IN this would draw nothing over a
                // transparent buffer.
                context.fillRect(0, 0, 8, 1, RED);
            }

            assertEquals(0xFFFF0000, pixelAt(pixels, 4, 0));
        }
    }

    // --- helpers ------------------------------------------------------------

    private static void rect(BlendPath path, double x, double y, double w, double h) {
        path.moveTo(x, y);
        path.lineTo(x + w, y);
        path.lineTo(x + w, y + h);
        path.lineTo(x, y + h);
        path.closeSubPath();
    }

    private static ByteBuffer buffer(int width, int height) {
        return ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int pixelAt(ByteBuffer pixels, int x, int y) {
        return pixels.order(ByteOrder.LITTLE_ENDIAN).getInt(y * strideOf(pixels) + x * 4);
    }

    /// Every buffer here is 64 or 8 pixels wide, 4 bytes a pixel.
    private static int strideOf(ByteBuffer pixels) {
        return pixels.capacity() == 32 ? 32 : 256;
    }

    private static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    private static String hex(int argb) {
        return Integer.toHexString(argb);
    }
}
