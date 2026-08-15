package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.backend.PixelFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The wiring between an application's coordinates and the pixels that result.
///
/// `:natives` tests what Blend2D does with a rectangle. This tests the part
/// `:core` owns: that a logical coordinate is scaled once by the context rather
/// than rounded here, that `fill` replaces where `fillRect` blends, and that a
/// frame stops being usable when it ends.
class FrameTest {

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("logical coordinates are scaled by the rasterizer, not rounded here")
    void logicalCoordinatesReachTheRasterizer() {
        // A 4x4 physical buffer at 2x is 2x2 logical. A rectangle covering the
        // logical top-left pixel must cover four physical ones.
        var buffer = PixelBuffer.allocate(new PhysicalSize(4, 4), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = new Frame(buffer, new DisplayScale(2f));
        try {
            assertEquals(new LogicalSize(2, 2), frame.size(), "logical size");
            assertEquals(new PhysicalSize(4, 4), frame.pixelSize(), "physical size");

            frame.fill(0xFF000000);
            frame.fillRect(0, 0, 1, 1, 0xFFFFFFFF);
        } finally {
            frame.end();
        }

        assertEquals(0xFFFFFFFF, pixel(buffer, 0, 0), "the logical pixel covers physical (0,0)");
        assertEquals(0xFFFFFFFF, pixel(buffer, 1, 1), "and (1,1)");
        assertEquals(0xFF000000, pixel(buffer, 2, 0), "and stops at the logical boundary");
    }

    @Test
    @DisplayName("a fractional scale antialiases rather than snapping")
    void fractionalScaleIsNotRounded() {
        // The whole reason the scale is a context transform. Rounding a logical
        // coordinate to a whole physical pixel here would move this edge by a
        // third of a logical pixel, and every border in the frame with it.
        var buffer = PixelBuffer.allocate(new PhysicalSize(4, 4), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = new Frame(buffer, new DisplayScale(1.5f));
        try {
            frame.fill(0xFF000000);
            frame.fillRect(0, 0, 1, 1, 0xFFFFFFFF);
        } finally {
            frame.end();
        }

        assertEquals(0xFFFFFFFF, pixel(buffer, 0, 0), "fully covered");
        var edge = pixel(buffer, 1, 0) & 0xFF;
        assertTrue(
                edge > 100 && edge < 160,
                () -> "the half-covered pixel should be about half lit, and it is 0x"
                        + Integer.toHexString(edge));
    }

    @Test
    @DisplayName("fill replaces so a translucent background does not accumulate")
    void fillReplaces() {
        var buffer = PixelBuffer.allocate(new PhysicalSize(1, 1), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = new Frame(buffer, DisplayScale.ONE);
        try {
            frame.fill(0x80402010);
            var once = pixel(buffer, 0, 0);
            frame.fill(0x80402010);

            assertEquals(once, pixel(buffer, 0, 0), "the same however many times it is called");
        } finally {
            frame.end();
        }
    }

    @Test
    @DisplayName("fillRect blends, so alpha means something")
    void fillRectBlends() {
        var buffer = PixelBuffer.allocate(new PhysicalSize(1, 1), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = new Frame(buffer, DisplayScale.ONE);
        try {
            frame.fill(0xFF000000);
            // Half-transparent white over black is mid grey. Writing raw pixels
            // -- which is what this did before Blend2D -- would ignore the alpha
            // and produce white.
            frame.fillRect(0, 0, 1, 1, 0x80FFFFFF);
        } finally {
            frame.end();
        }

        var grey = pixel(buffer, 0, 0) & 0xFF;
        assertTrue(
                grey > 110 && grey < 145,
                () -> "expected roughly mid grey, got 0x" + Integer.toHexString(grey));
    }

    @Test
    @DisplayName("colours are not premultiplied by the caller")
    void coloursAreStraightAlpha() {
        var buffer = PixelBuffer.allocate(new PhysicalSize(1, 1), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = new Frame(buffer, DisplayScale.ONE);
        try {
            frame.fill(0x80402010);
        } finally {
            frame.end();
        }

        // The buffer is premultiplied and the colour was not, so each channel
        // arrives scaled by the alpha: 0x40 * 0x80 / 0xFF is 0x20.
        assertEquals(0x80201008, pixel(buffer, 0, 0));
    }

    @Test
    @DisplayName("a frame is unusable once it has ended")
    void endedFramesAreRefused() {
        var buffer = PixelBuffer.allocate(new PhysicalSize(2, 2), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = new Frame(buffer, DisplayScale.ONE);
        frame.end();

        // A painter that squirrels the Frame away and draws into it later would
        // otherwise be writing through a context Blend2D has released.
        var thrown = assertThrows(IllegalStateException.class, () -> frame.fill(0xFFFFFFFF));
        assertTrue(thrown.getMessage().contains("already been presented"), thrown.getMessage());
        assertThrows(IllegalStateException.class, () -> frame.fillRect(0, 0, 1, 1, 0xFFFFFFFF));

        assertDoesNotThrow(frame::end, "ending twice does nothing");
    }

    /// The pixel at `(x, y)` as `0xAARRGGBB`. The buffer is already normalised
    /// to little-endian by [PixelBuffer].
    private static int pixel(PixelBuffer buffer, int x, int y) {
        return buffer.pixels().getInt(y * buffer.stride() + x * 4);
    }
}
