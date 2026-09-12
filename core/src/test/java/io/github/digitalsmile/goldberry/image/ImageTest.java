package io.github.digitalsmile.goldberry.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;

/// An image is a value — ADR-0283.
///
/// Split in two on purpose. [Building] needs no native library at all, because
/// `ofArgb` is arithmetic and a premultiplied buffer; [Decoding] needs one,
/// because a PNG decoder is Blend2D's. Keeping them apart is what lets the first
/// half run on a machine with no `libgoldberry` — and is also the evidence that
/// the value half really is independent of the rasterizer.
class ImageTest {

    /// A 2×2 PNG with an alpha channel: opaque red and blue on the first row,
    /// opaque green and half-transparent white on the second. The same bytes
    /// `BlendDecodeTest` uses, written out rather than produced, so that the
    /// expected pixels do not come from the decoder under test.
    static final byte[] RGBA_PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAE0lEQVR42mP4z8AAQmDqPxA0AABJ"
                    + "SQl4etla2AAAAABJRU5ErkJggg==");

    /// A 2×1 PNG with no alpha channel, which decodes as `XRGB32` and has to be
    /// converted.
    static final byte[] RGB_PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAIAAAB7QOjdAAAADUlEQVR42mP4zwAE/wEHAAH/PX2M"
                    + "SQAAAABJRU5ErkJggg==");

    @Nested
    @DisplayName("built from pixels, with no rasterizer involved")
    class Building {

        @Test
        @DisplayName("the pixels come back as they went in")
        void roundTripsOpaquePixels() {
            var image = Image.ofArgb(2, 2, new int[] {0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFFFFFFF});

            assertEquals(new PhysicalSize(2, 2), image.size());
            assertEquals(0xFFFF0000, image.argb(0, 0));
            assertEquals(0xFF0000FF, image.argb(1, 0));
            assertEquals(0xFF00FF00, image.argb(0, 1));
            assertEquals(0xFFFFFFFF, image.argb(1, 1));
        }

        @Test
        @DisplayName("a fully transparent pixel stays fully transparent")
        void keepsTransparency() {
            var image = Image.ofArgb(1, 2, new int[] {0x00FFFFFF, 0xFF000000});

            // Zero, not `0x00FFFFFF`: premultiplied storage cannot tell one
            // transparent colour from another, which is exactly why `Gradient.fade`
            // exists (ADR-0277) and why nothing should read a colour out of a
            // transparent pixel and believe it.
            assertEquals(0, image.argb(0, 0));
            assertEquals(0xFF000000, image.argb(0, 1));
        }

        @Test
        @DisplayName("a translucent pixel survives the round trip to within a level")
        void roundTripsTranslucentPixels() {
            // 0x80 of white premultiplies to 0x80808080 and comes back as
            // 0x80FFFFFF. Mid-grey at the same alpha is the interesting case: it
            // does not divide evenly, and the rounding is what keeps it from
            // drifting a level darker every time it is read and written.
            var image = Image.ofArgb(1, 2, new int[] {0x80FFFFFF, 0x80808080});

            assertEquals(0x80FFFFFF, image.argb(0, 0));
            var grey = image.argb(0, 1);
            assertEquals(0x80, grey >>> 24, "the alpha is exact");
            assertTrue(
                    Math.abs((grey & 0xFF) - 0x80) <= 1,
                    "and the channel is within a level: " + Integer.toHexString(grey));
        }

        @Test
        @DisplayName("the bounds are the whole image, which is a blit's default source")
        void statesItsBounds() {
            var image = Image.ofArgb(4, 3, new int[12]);

            assertEquals(PhysicalRect.of(0, 0, 4, 3), image.bounds());
            assertTrue(image.bounds().fitsWithin(image.size()));
        }

        @Test
        @DisplayName("the pixels are handed out read-only")
        void handsOutAReadOnlyView() {
            var image = Image.ofArgb(1, 1, new int[] {0xFFFF0000});

            // An image is a value; a writable view of its pixels would make that a
            // convention rather than a fact.
            assertTrue(image.pixels().pixels().isReadOnly());
            assertThrows(
                    java.nio.ReadOnlyBufferException.class,
                    () -> image.pixels().pixels().putInt(0, 0));
        }

        @Test
        @DisplayName("a size or an array that do not agree is refused")
        void refusesMismatchedInput() {
            assertThrows(IllegalArgumentException.class, () -> Image.ofArgb(0, 4, new int[0]), "no width");
            assertThrows(IllegalArgumentException.class, () -> Image.ofArgb(2, 2, new int[3]), "three pixels for four");
            assertThrows(NullPointerException.class, () -> Image.ofArgb(1, 1, null));
        }

        @Test
        @DisplayName("a pixel outside the image is an index error, not a wrapped read")
        void refusesPixelsOutside() {
            var image = Image.ofArgb(2, 2, new int[4]);

            assertThrows(IndexOutOfBoundsException.class, () -> image.argb(2, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> image.argb(0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> image.argb(-1, 0));
        }
    }

    @Nested
    @DisplayName("decoded from bytes, which is the rasterizer's half")
    class Decoding {

        @Test
        @DisplayName("a PNG with alpha decodes to the pixels the file holds")
        void decodesRgba() {
            RendererRequirement.enforce();
            var image = Image.decode(RGBA_PNG);

            assertEquals(new PhysicalSize(2, 2), image.size());
            assertEquals(0xFFFF0000, image.argb(0, 0), "opaque red");
            assertEquals(0xFF0000FF, image.argb(1, 0), "opaque blue");
            assertEquals(0xFF00FF00, image.argb(0, 1), "opaque green");
            // Read back unpremultiplied, which is what the file said: white at
            // half alpha.
            assertEquals(0x80FFFFFF, image.argb(1, 1), "half-transparent white");
        }

        @Test
        @DisplayName("a PNG with no alpha channel decodes fully opaque")
        void decodesRgb() {
            RendererRequirement.enforce();
            var image = Image.decode(RGB_PNG);

            assertEquals(new PhysicalSize(2, 1), image.size());
            assertEquals(0xFFFF0000, image.argb(0, 0));
            assertEquals(0xFF0000FF, image.argb(1, 0));
        }

        @Test
        @DisplayName("a decoded image needs no closing, and nothing native outlives the call")
        void isAValue() {
            RendererRequirement.enforce();
            var image = Image.decode(RGBA_PNG);

            // The claim is structural rather than observable, so this is the
            // closest a test gets to it: an Image is not AutoCloseable, so there is
            // no close() for a caller to forget and no lifetime to thread through a
            // document model (ADR-0283).
            //
            // Through the class rather than `instanceof`, which javac refuses
            // outright on a final class that cannot implement it -- a stronger
            // check than this one, and not one that can be written as a test.
            assertFalse(AutoCloseable.class.isAssignableFrom(Image.class), "an image is a value, not a handle");
            assertNotNull(image.toString());
        }

        @Test
        @DisplayName("a buffer's position and limit are honoured")
        void decodesASlice() {
            RendererRequirement.enforce();
            // The bytes with eight bytes of junk in front, handed over as a
            // positioned buffer -- which is what reading one image out of a
            // container looks like.
            var padded = ByteBuffer.allocate(RGBA_PNG.length + 8);
            padded.put(new byte[8]).put(RGBA_PNG).flip();
            padded.position(8);

            assertEquals(new PhysicalSize(2, 2), Image.decode(padded).size());
        }

        @Test
        @DisplayName("a file is read and decoded")
        void decodesAFile(@TempDir Path directory) throws Exception {
            RendererRequirement.enforce();
            var file = directory.resolve("two-by-two.png");
            Files.write(file, RGBA_PNG);

            assertEquals(new PhysicalSize(2, 2), Image.decode(file).size());
        }

        @Test
        @DisplayName("bytes that are not an image are an ImageDecodeException, not a native one")
        void refusesGarbage() {
            RendererRequirement.enforce();
            var garbage = "this was never an image".getBytes(java.nio.charset.StandardCharsets.UTF_8);

            var thrown = assertThrows(ImageDecodeException.class, () -> Image.decode(garbage));
            // The whole point of the translation: an application catching a failed
            // paste names a Goldberry type. The rasterizer's report is still there
            // as the cause, for whoever is asking why rather than what.
            assertTrue(thrown.getMessage().contains("PNG, JPEG, QOI"), thrown.getMessage());
            assertNotNull(thrown.getCause(), "and the rasterizer's own code is kept");
        }

        @Test
        @DisplayName("a file that cannot be read is an I/O failure, not a decode failure")
        void separatesIoFromContent() {
            var missing = Path.of("no-such-directory-anywhere", "absent.png");

            // Two different questions -- "I could not read this" and "this is not an
            // image" -- so two different types. An application offering to retry
            // wants to know which.
            assertThrows(UncheckedIOException.class, () -> Image.decode(missing));
        }

        @Test
        @DisplayName("no bytes at all is refused before the decoder is asked")
        void refusesEmptyInput() {
            assertThrows(IllegalArgumentException.class, () -> Image.decode(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> Image.decode(ByteBuffer.allocate(0)));
        }
    }
}
