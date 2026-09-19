package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendImageScaleFilter;

/// What `bl_image_scale` produces — ADR-0428.
///
/// The binding takes a `BLSizeI` by pointer and a filter by value, which is two
/// ways to be silently wrong: a size read at the wrong offsets gives an image of
/// a size nobody asked for, and a filter off by one resamples with a different
/// mathematics and still returns `BL_SUCCESS`. Both are checked here against
/// pixels that can be worked out by hand.
class BlendScaleTest {

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("the result is the size that was asked for")
    void theSizeCrosses() {
        // The `BLSizeI` is two ints behind a pointer, read through the layout
        // table. Swap them and a 4x2 becomes a 2x4, which is the whole of what
        // this asserts.
        try (var source = checkerboard(4, 4);
                var scaled = BlendScaledImage.scale(source, 8, 2, BlendImageScaleFilter.NEAREST)) {

            assertEquals(8, scaled.width());
            assertEquals(2, scaled.height());
            assertFalse(scaled.isClosed());
        }
    }

    @Test
    @DisplayName("nearest doubles a checkerboard into blocks of four, inventing no colours")
    void nearestInventsNothing() {
        // NEAREST is the one filter with an answer that can be written down: a
        // 2x doubling maps each source pixel onto a 2x2 block, and every output
        // pixel is one of the two input colours. A filter that is not the one
        // asked for averages the neighbours and fails this immediately.
        try (var source = checkerboard(4, 4);
                var scaled = BlendScaledImage.scale(source, 8, 8, BlendImageScaleFilter.NEAREST)) {

            var out = ByteBuffer.allocateDirect(8 * 8 * 4).order(ByteOrder.LITTLE_ENDIAN);
            scaled.copyInto(out, 32);

            for (var y = 0; y < 8; y++) {
                for (var x = 0; x < 8; x++) {
                    var pixel = out.getInt(y * 32 + x * 4);
                    var expected = ((x / 2) + (y / 2)) % 2 == 0 ? RED : BLUE;
                    assertEquals(expected, pixel, "at (" + x + ", " + y + ")");
                }
            }
        }
    }

    @Test
    @DisplayName("a smooth filter averages instead, which is what makes it a different choice")
    void bilinearAverages() {
        // The contrast that proves the filter argument reaches the library at
        // all: the same doubling under BILINEAR produces colours that are in
        // neither source pixel. If the argument were ignored, this would be
        // identical to the nearest-neighbour result above.
        try (var source = checkerboard(4, 4);
                var scaled = BlendScaledImage.scale(source, 8, 8, BlendImageScaleFilter.BILINEAR)) {

            var out = ByteBuffer.allocateDirect(8 * 8 * 4).order(ByteOrder.LITTLE_ENDIAN);
            scaled.copyInto(out, 32);

            var blended = false;
            for (var y = 0; y < 8 && !blended; y++) {
                for (var x = 0; x < 8; x++) {
                    var pixel = out.getInt(y * 32 + x * 4);
                    if (pixel != RED && pixel != BLUE) {
                        blended = true;
                        break;
                    }
                }
            }
            assertTrue(blended, "bilinear should have mixed the two colours somewhere");
        }
    }

    @Test
    @DisplayName("a size that is not positive is refused before the library is asked")
    void refusesAnEmptySize() {
        try (var source = checkerboard(4, 4)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BlendScaledImage.scale(source, 0, 4, BlendImageScaleFilter.LANCZOS));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BlendScaledImage.scale(source, 4, -1, BlendImageScaleFilter.LANCZOS));
        }
    }

    @Test
    @DisplayName("a destination too small for the result is refused rather than overrun")
    void refusesATightBuffer() {
        try (var source = checkerboard(4, 4);
                var scaled = BlendScaledImage.scale(source, 8, 8, BlendImageScaleFilter.NEAREST)) {

            var tooSmall = ByteBuffer.allocateDirect(8 * 8 * 4 - 4).order(ByteOrder.LITTLE_ENDIAN);
            assertThrows(IllegalArgumentException.class, () -> scaled.copyInto(tooSmall, 32));

            // And a heap buffer, which has no address the copy could target.
            var heap = ByteBuffer.allocate(8 * 8 * 4);
            assertThrows(IllegalArgumentException.class, () -> scaled.copyInto(heap, 32));
        }
    }

    @Test
    @DisplayName("the allocation is released, and using it afterwards is refused")
    void closes() {
        var source = checkerboard(4, 4);
        var scaled = BlendScaledImage.scale(source, 8, 8, BlendImageScaleFilter.NEAREST);
        scaled.close();

        assertTrue(scaled.isClosed());
        var out = ByteBuffer.allocateDirect(8 * 8 * 4);
        assertThrows(IllegalStateException.class, () -> scaled.copyInto(out, 32));
        // Closing twice is a no-op, not a double free.
        scaled.close();
        source.close();
    }

    /// A `size` × `size` red-and-blue checkerboard, one pixel per square.
    private static BlendImage checkerboard(int width, int height) {
        var pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                pixels.putInt(y * width * 4 + x * 4, (x + y) % 2 == 0 ? RED : BLUE);
            }
        }
        return BlendImage.wrapping(pixels, width, height, width * 4);
    }
}
