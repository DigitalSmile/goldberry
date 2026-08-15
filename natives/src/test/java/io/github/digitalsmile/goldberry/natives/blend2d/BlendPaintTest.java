package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What Blend2D actually put in the buffer.
///
/// Every assertion here is a pixel value that can be worked out by hand, which
/// is the only kind of check worth having on a rasterizer binding: a swapped
/// argument, a wrong enum or a colour premultiplied twice all produce a frame
/// that renders, looks nearly right, and is wrong. None of them produces an
/// error.
class BlendPaintTest {

    /// An opaque Nord background — the colour the showcase already paints.
    private static final int NORD_DARK = 0xFF2E3440;

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("the image is a view over the buffer, not a copy of it")
    void theImageBorrowsTheBuffer() {
        var pixels = buffer(4, 4);
        try (var image = BlendImage.wrapping(pixels, 4, 4, 16)) {
            var data = image.data();

            // The whole reason the external-data constructor is the only one
            // bound: if this were a copy, every frame would cost a full blit and
            // nothing painted would ever reach the compositor.
            assertEquals(
                    MemorySegment.ofBuffer(pixels).address(),
                    data.pixels(),
                    "Blend2D is pointing at the buffer it was given");
            assertEquals(16L, data.stride(), "and at the stride it was told");
            assertEquals(BlendFormat.PRGB32, data.format());
        }
    }

    @Test
    @DisplayName("an opaque fill lands in memory as BGRA")
    void opaqueFillReachesTheBuffer() {
        var pixels = buffer(2, 2);
        try (var image = BlendImage.wrapping(pixels, 2, 2, 8);
                var context = BlendContext.on(image)) {
            context.fillAll(NORD_DARK);
        }

        // Read back as a little-endian int, which recomposes the B,G,R,A bytes
        // into the 0xAARRGGBB that was asked for. That round trip is the claim
        // BlendFormat's docs make, checked rather than asserted in prose.
        for (var i = 0; i < 4; i++) {
            assertEquals(NORD_DARK, pixelAt(pixels, i * 4), "pixel " + i);
        }
    }

    @Test
    @DisplayName("a colour is premultiplied by Blend2D, not by the caller")
    void translucentFillCompositesOnce() {
        var pixels = buffer(1, 1);
        try (var image = BlendImage.wrapping(pixels, 1, 1, 4);
                var context = BlendContext.on(image)) {
            context.clearTo(0xFFFFFFFF);
            // 50% black over white. Premultiplying the style first would apply
            // the alpha twice and land near 0x3F rather than 0x7F -- a frame
            // that renders and is visibly too dark.
            context.fillAll(0x80000000);
        }

        var result = pixelAt(pixels, 0);
        assertEquals(0xFF, (result >>> 24) & 0xFF, "opaque afterwards");
        var grey = result & 0xFF;
        assertTrue(grey >= 126 && grey <= 129, () -> "expected ~0x7F, got 0x" + Integer.toHexString(grey));
    }

    @Test
    @DisplayName("clearing replaces rather than blends, so it does not accumulate")
    void clearToReplaces() {
        // 0x80402010 asked for; 0x80201008 stored. The style is straight alpha
        // and the image is premultiplied, so Blend2D multiplies each channel by
        // the alpha on the way in: 0x40 * 0x80 / 0xFF is 0x20, and so on down.
        //
        // That is the whole contract of this binding in one number. A caller who
        // premultiplied first -- which is exactly what writing these pixels by
        // hand requires -- would get 0x80100804 and a frame that is visibly too
        // dark, with nothing anywhere reporting a problem.
        var premultiplied = 0x80201008;

        var pixels = buffer(1, 1);
        try (var image = BlendImage.wrapping(pixels, 1, 1, 4);
                var context = BlendContext.on(image)) {
            // Twice. With SRC_OVER the second call would composite onto the
            // first and the alpha would climb towards opaque -- which is how a
            // translucent window slowly turns solid.
            context.clearTo(0x80402010);
            assertEquals(premultiplied, pixelAt(pixels, 0), "after the first clear");

            context.clearTo(0x80402010);
        }

        assertEquals(premultiplied, pixelAt(pixels, 0), "and unchanged after the second");
    }

    @Test
    @DisplayName("a rectangle lands where the coordinates say")
    void rectanglesArePlaced() {
        var pixels = buffer(4, 4);
        try (var image = BlendImage.wrapping(pixels, 4, 4, 16);
                var context = BlendContext.on(image)) {
            context.clearTo(0xFF000000);
            context.fillRect(1, 1, 2, 2, 0xFFFFFFFF);
        }

        assertEquals(0xFF000000, pixelAt(pixels, 0, 0, 16), "outside, top-left");
        assertEquals(0xFFFFFFFF, pixelAt(pixels, 1, 1, 16), "inside");
        assertEquals(0xFFFFFFFF, pixelAt(pixels, 2, 2, 16), "inside, far corner");
        assertEquals(0xFF000000, pixelAt(pixels, 3, 3, 16), "outside, bottom-right");
    }

    @Test
    @DisplayName("the scale transform means coordinates are logical")
    void scaleMakesCoordinatesLogical() {
        var pixels = buffer(8, 8);
        try (var image = BlendImage.wrapping(pixels, 8, 8, 32);
                var context = BlendContext.on(image, 2.0)) {
            context.clearTo(0xFF000000);
            // Four logical pixels at 2x is eight physical ones: the whole image.
            context.fillRect(0, 0, 4, 4, 0xFFFFFFFF);
        }

        assertEquals(0xFFFFFFFF, pixelAt(pixels, 7, 7, 32), "the far corner was covered");
    }

    @Test
    @DisplayName("a fractional scale antialiases the edge instead of moving it")
    void fractionalScaleAntialiases() {
        // This is the fractional-DPI claim on the paint side. One logical pixel
        // at 1.5x covers one and a half physical ones. Snapping would put the
        // edge at 1 or at 2 -- either way the rectangle is the wrong size by a
        // third. Blend2D covers the second pixel halfway instead.
        var pixels = buffer(4, 4);
        try (var image = BlendImage.wrapping(pixels, 4, 4, 16);
                var context = BlendContext.on(image, 1.5)) {
            context.clearTo(0xFF000000);
            context.fillRect(0, 0, 1, 1, 0xFFFFFFFF);
        }

        assertEquals(0xFFFFFFFF, pixelAt(pixels, 0, 0, 16), "fully inside");

        var edge = pixelAt(pixels, 1, 0, 16) & 0xFF;
        assertTrue(
                edge > 100 && edge < 160,
                () -> "the half-covered pixel should be about half lit, and it is 0x"
                        + Integer.toHexString(edge));
        assertEquals(0xFF000000, pixelAt(pixels, 2, 0, 16), "and the third is untouched");
    }

    @Test
    @DisplayName("a rectangle running off the edge is clipped, not refused")
    void overflowingRectanglesAreClipped() {
        var pixels = buffer(2, 2);
        try (var image = BlendImage.wrapping(pixels, 2, 2, 8);
                var context = BlendContext.on(image)) {
            context.clearTo(0xFF000000);
            assertDoesNotThrow(() -> context.fillRect(-100, -100, 1000, 1000, 0xFFFFFFFF));
        }

        assertEquals(0xFFFFFFFF, pixelAt(pixels, 0), "and it still painted what fits");
    }

    @Test
    @DisplayName("a stride wider than the row is respected")
    void paddedStrideIsHonoured() {
        // A compositor's surface is often padded to an alignment, so the row
        // stride exceeds width * 4. Assuming otherwise skews the whole image by
        // a few pixels per row -- the classic diagonal-tearing bug.
        var stride = 24;
        var pixels = buffer(stride / 4, 2);
        try (var image = BlendImage.wrapping(pixels, 4, 2, stride);
                var context = BlendContext.on(image)) {
            context.clearTo(0xFF112233);
        }

        assertEquals(0xFF112233, pixelAt(pixels, 3, 0, stride), "last pixel of row 0");
        assertEquals(0, pixelAt(pixels, 4, 0, stride), "the padding is untouched");
        assertEquals(0xFF112233, pixelAt(pixels, 0, 1, stride), "first pixel of row 1");
    }

    @Test
    @DisplayName("Blend2D reports which build is linked in")
    void versionIsReported() {
        var version = Blend2D.get().version();

        assertTrue(version.major() >= 0, "a major version");
        assertTrue(
                version.minor() > 0 || version.patch() > 0 || version.major() > 0,
                () -> "a version that is not all zeros, but got " + version);
        assertTrue(!version.compiler().isBlank(), () -> "a compiler string, but got " + version);
    }

    // --- helpers ------------------------------------------------------------

    /// A direct, zeroed buffer for a tightly packed `width` x `height` image.
    private static ByteBuffer buffer(int width, int height) {
        return ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    /// The pixel at a byte offset, as `0xAARRGGBB`.
    private static int pixelAt(ByteBuffer pixels, int offset) {
        return pixels.order(ByteOrder.LITTLE_ENDIAN).getInt(offset);
    }

    /// The pixel at `(x, y)`, as `0xAARRGGBB`.
    private static int pixelAt(ByteBuffer pixels, int x, int y, int stride) {
        return pixelAt(pixels, y * stride + x * 4);
    }
}
