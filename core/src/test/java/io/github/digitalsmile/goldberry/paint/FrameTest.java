package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.digitalsmile.goldberry.RendererRequirement;

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
    @DisplayName("restore puts back the clip that was in force, not the whole frame")
    void restoreReturnsToTheOuterClip() {
        // The reason the export list grew a state stack (ADR-0193). `resetClip`
        // goes back to the whole frame, so a painter that used it inside an
        // existing clip -- which is what a `canvas` in a `scroll` is -- would
        // paint over the viewport's edge. save/restore is what nests.
        var buffer = PixelBuffer.allocate(new PhysicalSize(8, 8), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = new Frame(buffer, new DisplayScale(1f));
        try {
            frame.fill(0xFF000000);
            // The outer clip: the left half, as a scroll viewport would set.
            frame.clipTo(0, 0, 4, 8);

            frame.save();
            frame.clipTo(0, 0, 8, 2);   // an inner clip, as a canvas would set
            frame.restore();

            // Still confined to the outer clip. Painted after the restore, so a
            // restore that had widened the clip would let this through.
            frame.fillRect(0, 0, 8, 8, 0xFFFFFFFF);
        } finally {
            frame.end();
        }

        assertEquals(0xFFFFFFFF, pixel(buffer, 3, 7), "inside the outer clip");
        assertEquals(0xFF000000, pixel(buffer, 5, 7),
                "outside it -- the restore went back to the outer clip, not to the frame");
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

    @Test
    @DisplayName("over() is the way in from another package, and ends the same way")
    void theFactoryIsTheBoundary() {
        // `Window` lives in the shell package and this one is `paint`, so the
        // constructor it used to call is out of reach and this factory is what it
        // calls instead
        // ([ADR-0172](../../../../../../../book/src/adr/0172-a-package-is-a-role-and-the-module-is-the-fence.md)).
        // The contract that used to be kept by package-privacy -- a frame is
        // valid only until it ends -- has to be kept by the frame now.
        var buffer = PixelBuffer.allocate(new PhysicalSize(4, 4), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = Frame.over(buffer, DisplayScale.ONE);

        assertEquals(new LogicalSize(4, 4), frame.size());
        assertDoesNotThrow(() -> frame.fill(0xFF102030));
        frame.end();

        assertThrows(IllegalStateException.class, () -> frame.fill(0xFFFFFFFF),
                "a frame handed out through the factory is no more usable after ending"
                        + " than one built in its own package");
        assertDoesNotThrow(frame::end, "ending twice does nothing");
    }

    @Test
    @DisplayName("over() with a pinned worker count paints what the automatic one does")
    void pinnedWorkersPaintTheSamePixels() {
        var automatic = PixelBuffer.allocate(
                new PhysicalSize(4, 4), PixelFormat.BGRA32_PREMULTIPLIED);
        var pinned = PixelBuffer.allocate(
                new PhysicalSize(4, 4), PixelFormat.BGRA32_PREMULTIPLIED);

        var one = Frame.over(automatic, DisplayScale.ONE);
        one.fillRect(1, 1, 2, 2, 0x80204060);
        one.end();

        // Zero is synchronous, which is what a 4x4 surface gets from
        // `PaintThreads` anyway -- the point is that the three-argument factory
        // reaches the same code and not that threading changes the picture.
        var other = Frame.over(pinned, DisplayScale.ONE, 0);
        other.fillRect(1, 1, 2, 2, 0x80204060);
        other.end();

        for (var y = 0; y < 4; y++) {
            for (var x = 0; x < 4; x++) {
                assertEquals(pixel(automatic, x, y), pixel(pinned, x, y),
                        "pixel (" + x + ", " + y + ")");
            }
        }
    }

    /// The pixel at `(x, y)` as `0xAARRGGBB`. The buffer is already normalised
    /// to little-endian by [PixelBuffer].
    private static int pixel(PixelBuffer buffer, int x, int y) {
        return buffer.pixels().getInt(y * buffer.stride() + x * 4);
    }
}
