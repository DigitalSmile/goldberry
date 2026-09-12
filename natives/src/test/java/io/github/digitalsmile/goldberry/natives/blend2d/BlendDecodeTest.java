package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;

/// The three symbols decoding needed — `bl_image_init`,
/// `bl_image_read_from_data` and `bl_image_convert`.
///
/// [BlendLayerTest] says why a test like this exists at all: an export list has
/// linked a symbol in and left it **local** three times over, which is present
/// to `nm` and absent to `nm -D`, and only a run against a real library tells
/// them apart. So every assertion below is a pixel that can be worked out by
/// hand from the bytes at the top of the file.
///
/// The two images are written out as base64 rather than read from a resource,
/// and are two rows and two columns so that every byte of them is accounted for:
/// a decode test whose expected pixels came out of the decoder it is testing
/// would pass no matter what the decoder did.
class BlendDecodeTest {

    /// A 2×2 PNG with an alpha channel: opaque red and blue on the first row,
    /// opaque green and half-transparent white on the second.
    private static final byte[] RGBA_PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAE0lEQVR42mP4z8AAQmDqPxA0AABJ"
                    + "SQl4etla2AAAAABJRU5ErkJggg==");

    /// A 2×1 PNG with **no** alpha channel — colour type 2, three bytes a pixel.
    /// This is the one that decodes to `XRGB32` and has to be converted.
    private static final byte[] RGB_PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAIAAAB7QOjdAAAADUlEQVR42mP4zwAE/wEHAAH/PX2M"
                    + "SQAAAABJRU5ErkJggg==");

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    private static ByteBuffer buffer(int width, int height) {
        return ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
    }

    /// A pixel as `0xAARRGGBB`, read the way the memory is laid out: premultiplied
    /// BGRA, little-endian, so one `getInt` is one pixel.
    private static int pixel(ByteBuffer pixels, int stride, int x, int y) {
        return pixels.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt(y * stride + x * 4);
    }

    @Test
    @DisplayName("a PNG decodes to the size the file says, not one anybody passed in")
    void readsTheSizeFromTheFile() {
        // The whole reason Blend2D is allowed to allocate here: nothing on the
        // Java side could have known this was 2x2 before the decoder said so.
        try (var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGBA_PNG))) {
            assertEquals(2, decoded.width());
            assertEquals(2, decoded.height());
        }
    }

    @Test
    @DisplayName("the decoded pixels are premultiplied BGRA, in the order the file wrote them")
    void decodesPixels() {
        var pixels = buffer(2, 2);
        try (var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGBA_PNG))) {
            decoded.copyInto(pixels, 8);
        }

        assertEquals(0xFFFF0000, pixel(pixels, 8, 0, 0), "opaque red");
        assertEquals(0xFF0000FF, pixel(pixels, 8, 1, 0), "opaque blue");
        assertEquals(0xFF00FF00, pixel(pixels, 8, 0, 1), "opaque green");
        // White at alpha 128, premultiplied: 255 * 128 / 255 is 128 in every
        // channel. Unpremultiplied it would have been 0x80FFFFFF, and the
        // difference is exactly what `Frame` must not be handed.
        assertEquals(0x80808080, pixel(pixels, 8, 1, 1), "half-transparent white, premultiplied");
    }

    @Test
    @DisplayName("a PNG with no alpha channel is converted rather than left as XRGB32")
    void convertsOpaqueImages() {
        // Colour type 2 decodes to BL_FORMAT_XRGB32, whose alpha byte is
        // undefined rather than 0xFF. Without `bl_image_convert` this would blit
        // as whatever that byte happened to be -- including invisible.
        var pixels = buffer(2, 1);
        try (var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGB_PNG))) {
            assertEquals(2, decoded.width());
            assertEquals(1, decoded.height());
            decoded.copyInto(pixels, 8);
        }

        assertEquals(0xFFFF0000, pixel(pixels, 8, 0, 0), "red, fully opaque");
        assertEquals(0xFF0000FF, pixel(pixels, 8, 1, 0), "blue, fully opaque");
    }

    @Test
    @DisplayName("a copy honours a destination stride wider than the image")
    void copiesIntoAPaddedBuffer() {
        // Blend2D pads a row to its own alignment and a caller's buffer is
        // whatever the caller allocated, so the copy is row by row and neither
        // stride may be assumed to be the other.
        var stride = 32;
        var pixels = ByteBuffer.allocateDirect(stride * 2).order(ByteOrder.nativeOrder());
        try (var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGBA_PNG))) {
            decoded.copyInto(pixels, stride);
        }

        assertEquals(0xFFFF0000, pixel(pixels, stride, 0, 0));
        assertEquals(0xFF00FF00, pixel(pixels, stride, 0, 1), "the second row starts one stride in");
        assertEquals(0, pixel(pixels, stride, 2, 0), "and the padding after a row is untouched");
    }

    @Test
    @DisplayName("a decoded image can be drawn, which is the point of decoding one")
    void blitsWhatItDecoded() {
        var pixels = buffer(2, 2);
        try (var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGBA_PNG))) {
            decoded.copyInto(pixels, 8);
        }

        // Decoded pixels are ordinary pixels: they go back through
        // BlendImage.wrapping like a layer's, and the handle Blend2D allocated is
        // already gone by the time anything is drawn (ADR-0283).
        //
        // At its own size and one pixel in, so the four pixels are exact. A 2x2
        // image drawn into 4x4 would be *interpolated* -- Blend2D's scaled blit
        // filters rather than replicating -- and an exact assertion on a
        // stretched image would be an assertion about its filter.
        var targetPixels = buffer(4, 4);
        try (var source = BlendImage.wrapping(pixels, 2, 2, 8);
                var target = BlendImage.wrapping(targetPixels, 4, 4, 16)) {
            try (var context = BlendContext.on(target, 1.0, 0)) {
                context.blitScaled(1, 1, 2, 2, source);
            }
        }

        assertEquals(0xFFFF0000, pixel(targetPixels, 16, 1, 1), "red, one pixel in from the corner");
        assertEquals(0xFF0000FF, pixel(targetPixels, 16, 2, 1), "blue beside it");
        assertEquals(0xFF00FF00, pixel(targetPixels, 16, 1, 2), "green below it");
        assertEquals(0, pixel(targetPixels, 16, 0, 0), "and nothing where the image is not");
    }

    @Test
    @DisplayName("a source rectangle blits a part of the image")
    void blitsACrop() {
        var pixels = buffer(2, 2);
        try (var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGBA_PNG))) {
            decoded.copyInto(pixels, 8);
        }

        // The top-right pixel only -- blue -- stretched over the whole target.
        // The first BLRectI to cross the boundary in either direction, so a wrong
        // layout row would show up here as some other colour.
        var targetPixels = buffer(4, 4);
        try (var source = BlendImage.wrapping(pixels, 2, 2, 8);
                var target = BlendImage.wrapping(targetPixels, 4, 4, 16)) {
            try (var context = BlendContext.on(target, 1.0, 0)) {
                context.blitScaled(0, 0, 4, 4, source, 1, 0, 1, 1);
            }
        }

        for (var y = 0; y < 4; y++) {
            for (var x = 0; x < 4; x++) {
                assertEquals(0xFF0000FF, pixel(targetPixels, 16, x, y), "only the blue pixel was asked for");
            }
        }
    }

    @Test
    @DisplayName("bytes no codec recognises are reported rather than drawn")
    void refusesGarbage() {
        var garbage = ByteBuffer.wrap(
                "this is not an image, and never was".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var thrown = assertThrows(BlendException.class, () -> BlendDecodedImage.decode(garbage));
        assertTrue(
                thrown.getMessage().contains("bl_image_read_from_data"),
                "the report names the call that failed: " + thrown.getMessage());
        assertNotEquals(0, thrown.result(), "and carries Blend2D's own code");
    }

    @Test
    @DisplayName("an empty buffer is refused before Blend2D is asked")
    void refusesNothing() {
        var thrown =
                assertThrows(IllegalArgumentException.class, () -> BlendDecodedImage.decode(ByteBuffer.allocate(0)));
        assertTrue(thrown.getMessage().contains("nothing to decode"), thrown.getMessage());
    }

    @Test
    @DisplayName("a heap buffer of encoded bytes is fine, unlike a heap buffer of pixels")
    void acceptsHeapEncodedBytes() {
        // `ByteBuffer.wrap` is a heap buffer, and every test above uses one: the
        // encoded bytes are read once during the call, so copying them costs a
        // file's length and not a frame. Pixels are the opposite case and
        // BlendImage refuses them.
        try (var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGBA_PNG))) {
            assertEquals(2, decoded.width());
        }

        var pixels = buffer(2, 2);
        try (var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGBA_PNG))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> decoded.copyInto(ByteBuffer.allocate(pixels.capacity()), 8),
                    "a heap destination has no address to copy into");
        }
    }

    @Test
    @DisplayName("a closed decode refuses to be copied out of")
    void refusesUseAfterClose() {
        var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGBA_PNG));
        decoded.close();
        assertTrue(decoded.isClosed());
        // Idempotent, like every other wrapper here.
        decoded.close();
        assertThrows(IllegalStateException.class, () -> decoded.copyInto(buffer(2, 2), 8));
    }

    @Test
    @DisplayName("a destination too small for the image is refused rather than overrun")
    void refusesASmallDestination() {
        try (var decoded = BlendDecodedImage.decode(ByteBuffer.wrap(RGBA_PNG))) {
            assertThrows(IllegalArgumentException.class, () -> decoded.copyInto(buffer(2, 1), 8), "one row short");
            assertThrows(IllegalArgumentException.class, () -> decoded.copyInto(buffer(2, 2), 4), "stride too narrow");
        }
    }
}
