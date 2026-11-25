package dev.goldberry.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.image.scalable.ScalableRasters;
import dev.goldberry.image.scalable.VectorDecoders;
import dev.goldberry.image.spi.VectorDecoder;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;

/// SVG through `:core` with a decoder of the test's own: the format routed to
/// it, the image it answers, and that image drawn at the device pixels it
/// covers.
@DisplayName("A scalable image")
class ScalableImageTest {

    private static final byte[] SVG = "<svg width=\"4\" height=\"3\"/>".getBytes(StandardCharsets.UTF_8);

    private CheckerDocument document;

    @BeforeEach
    void decoder() {
        document = new CheckerDocument(4, 3);
        VectorDecoders.use(bytes -> document);
        ScalableRasters.shared().clear();
    }

    @AfterEach
    void reset() {
        VectorDecoders.use(null);
        ScalableRasters.shared().clear();
    }

    @Nested
    @DisplayName("decoded")
    class Decoded {

        @Test
        @DisplayName("is a raster at its natural size that carries its document")
        void decodes() {
            var image = Image.decode(SVG);
            assertAll(
                    () -> assertEquals(new PhysicalSize(4, 3), image.size()),
                    () -> assertEquals(Optional.of(document), image.scalable()),
                    () -> assertTrue(CheckerDocument.isChecker(image)),
                    () -> assertEquals(List.of(new PhysicalSize(4, 3)), document.asked()),
                    () -> assertTrue(image.toString().contains("scalable"), image.toString()));
        }

        @Test
        @DisplayName("rounds a fractional natural size up")
        void roundsUp() {
            var image = Image.of(new CheckerDocument(2.5f, 1.2f));
            assertEquals(new PhysicalSize(3, 2), image.size());
        }

        @Test
        @DisplayName("is refused, naming goldberry-vector, when nothing provides a decoder")
        void noDecoder() {
            VectorDecoders.useNone();
            var thrown = assertThrows(ImageDecodeException.class, () -> Image.decode(SVG));
            assertTrue(thrown.getMessage().contains("dev.goldberry:goldberry-vector"), thrown.getMessage());
        }

        @Test
        @DisplayName("is refused when the decoder cannot draw on this machine, with its reason")
        void unavailable() {
            VectorDecoder broken = bytes -> {
                throw new IllegalStateException("libgoldberry-vector is not available: no natives");
            };
            VectorDecoders.use(broken);
            var thrown = assertThrows(ImageDecodeException.class, () -> Image.decode(SVG));
            assertTrue(thrown.getMessage().contains("no natives"), thrown.getMessage());
        }

        @Test
        @DisplayName("into memory the caller owns, in RGBA")
        void into() {
            var memory = ByteBuffer.allocateDirect(4 * 3 * 4);
            var size = Image.decodeInto(ByteBuffer.wrap(SVG), memory, 16, ChannelOrder.RGBA);
            assertEquals(new PhysicalSize(4, 3), size);
            // Pixel (1, 0) is white and (0, 0) black, whatever the order.
            assertAll(
                    () -> assertEquals(0, memory.get(0)),
                    () -> assertEquals((byte) 0xFF, memory.get(3)),
                    () -> assertEquals((byte) 0xFF, memory.get(4)));
        }

        @Test
        @DisplayName("a crop owns only its pixels")
        void cropped() {
            assertTrue(Image.decode(SVG)
                    .cropped(PhysicalRect.of(0, 0, 2, 2))
                    .scalable()
                    .isEmpty());
        }

        @Test
        @DisplayName("a resampled image owns only its pixels")
        void scaled() {
            RendererRequirement.enforce();
            assertTrue(Image.decode(SVG).scaled(8, 6).scalable().isEmpty());
        }
    }

    @Nested
    @DisplayName("drawn")
    class Drawn {

        @BeforeEach
        void renderer() {
            RendererRequirement.enforce();
        }

        @Test
        @DisplayName("at 200% from a raster of the device pixels: sharp, not its natural raster doubled")
        void doubled() {
            var image = Image.decode(SVG);
            var frame = Offscreen.of(8, 6).scale(2f).paint((f, size) -> f.drawImage(image, 0, 0, 4, 3));
            assertTrue(CheckerDocument.isChecker(frame), "the frame alternates on every device pixel");
            assertEquals(new PhysicalSize(8, 6), document.asked().getLast());
        }

        @Test
        @DisplayName("larger than its natural size, from a raster of that size")
        void larger() {
            var image = Image.decode(SVG);
            var frame = Offscreen.of(16, 12).paint((f, size) -> f.drawImage(image, 0, 0, 16, 12));
            assertTrue(CheckerDocument.isChecker(frame));
        }

        @Test
        @DisplayName("a crop of it from a raster of the whole at the crop's scale, the crop scaled with it")
        void crop() {
            var image = Image.decode(SVG);
            // Two by two natural pixels from (1, 0) into a 4 by 4 box: a raster of
            // 8 by 6 and its (2, 0, 4, 4), which starts on a black cell.
            var frame = Offscreen.of(4, 4)
                    .paint((f, size) -> f.drawImage(image, PhysicalRect.of(1, 0, 2, 2), 0, 0, 4, 4, 1));
            assertTrue(CheckerDocument.isChecker(frame));
            assertEquals(new PhysicalSize(8, 6), document.asked().getLast());
        }

        @Test
        @DisplayName("at its natural size from its own pixels, with no raster made")
        void natural() {
            var image = Image.decode(SVG);
            var frame = Offscreen.of(4, 3).paint((f, size) -> f.drawImage(image, 0, 0, 4, 3));
            assertTrue(CheckerDocument.isChecker(frame));
            assertEquals(1, document.asked().size());
        }

        @Test
        @DisplayName("from the raster kept for a size, the second time")
        void kept() {
            var image = Image.decode(SVG);
            for (var round = 0; round < 3; round++) {
                Offscreen.of(8, 6).paint((f, size) -> f.drawImage(image, 0, 0, 8, 6));
            }
            assertEquals(List.of(new PhysicalSize(4, 3), new PhysicalSize(8, 6)), document.asked());
        }

        @Test
        @DisplayName("from its natural raster, asked once, at a size its document will not draw")
        void refused() {
            document = new CheckerDocument(4, 3, 10);
            var image = Image.decode(SVG);
            for (var round = 0; round < 2; round++) {
                Offscreen.of(16, 12).paint((f, size) -> f.drawImage(image, 0, 0, 16, 12));
            }
            assertEquals(List.of(new PhysicalSize(4, 3), new PhysicalSize(16, 12)), document.asked());
        }

        @Test
        @DisplayName("and a picture that is only pixels is drawn as it always was")
        void pixels() {
            var image = Image.decode(SVG).scaled(4, 3);
            var frame = Offscreen.of(4, 3).paint((f, size) -> f.drawImage(image, 0, 0, 4, 3));
            assertTrue(CheckerDocument.isChecker(frame));
            assertEquals(1, document.asked().size());
        }
    }
}
