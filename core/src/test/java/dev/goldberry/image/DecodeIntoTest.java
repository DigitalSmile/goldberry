package dev.goldberry.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.RendererRequirement;
import dev.goldberry.render.model.PhysicalSize;

/// Decoding into the caller's memory, in the caller's channel order.
///
/// Asserted against [Image#decode(ByteBuffer)] of the same bytes: what lands in
/// the caller's buffer has to be the image's own premultiplied pixels, byte for
/// byte in BGRA and with red and blue exchanged in RGBA. The fixtures are the
/// codec suite's, one picture in every format.
@DisplayName("decoding into memory the caller owns")
class DecodeIntoTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static ByteBuffer fixture(String name) {
        try (var in = DecodeIntoTest.class.getResourceAsStream("/image/" + name)) {
            assertNotNull(in, "the fixture /image/" + name + " is missing");
            return ByteBuffer.wrap(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// The byte at `offset` of `buffer`, unsigned.
    private static int at(ByteBuffer buffer, int offset) {
        return buffer.get(offset) & 0xFF;
    }

    /// Every pixel of `into`, at `stride`, against `expected` in `order`.
    private static void assertPixels(Image expected, ByteBuffer into, int stride, ChannelOrder order) {
        var source = expected.pixels().pixels();
        var sourceStride = expected.pixels().stride();
        for (var y = 0; y < expected.height(); y++) {
            for (var x = 0; x < expected.width(); x++) {
                var from = y * sourceStride + x * 4;
                var to = y * stride + x * 4;
                var b = at(source, from);
                var g = at(source, from + 1);
                var r = at(source, from + 2);
                var a = at(source, from + 3);
                var want = order == ChannelOrder.BGRA ? new int[] {b, g, r, a} : new int[] {r, g, b, a};
                for (var c = 0; c < 4; c++) {
                    assertEquals(want[c], at(into, to + c), "channel " + c + " of the pixel at " + x + "," + y);
                }
            }
        }
    }

    @Nested
    @DisplayName("every format")
    class Formats {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"pattern.png", "pattern.qoi", "pattern.gif", "pattern.webp", "noise.png"})
        @DisplayName("lands as the decoded image, in BGRA")
        void bgra(String name) {
            var expected = Image.decode(fixture(name));
            var into = ByteBuffer.allocateDirect(expected.width() * expected.height() * 4);

            var size = Image.decodeInto(fixture(name), into, expected.width() * 4, ChannelOrder.BGRA);

            assertEquals(expected.size(), size);
            assertPixels(expected, into, expected.width() * 4, ChannelOrder.BGRA);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"pattern.png", "pattern.qoi", "pattern.gif", "pattern.webp", "noise.png"})
        @DisplayName("lands with red and blue exchanged, in RGBA")
        void rgba(String name) {
            var expected = Image.decode(fixture(name));
            var into = ByteBuffer.allocateDirect(expected.width() * expected.height() * 4);

            Image.decodeInto(fixture(name), into, expected.width() * 4, ChannelOrder.RGBA);

            assertPixels(expected, into, expected.width() * 4, ChannelOrder.RGBA);
        }
    }

    @Nested
    @DisplayName("the memory")
    class Memory {

        @Test
        @DisplayName("is asked for once, with the decoded size")
        void askedWithTheSize() {
            var asked = new AtomicReference<PhysicalSize>();
            var size = Image.decodeInto(fixture("pattern.png"), ChannelOrder.RGBA, decoded -> {
                assertTrue(asked.compareAndSet(null, decoded), "asked once");
                return PixelTarget.packed(ByteBuffer.allocateDirect(decoded.width() * decoded.height() * 4), 32);
            });

            assertSame(size, asked.get());
            assertEquals(new PhysicalSize(32, 32), size);
        }

        @Test
        @DisplayName("may have padded rows, and the padding is left alone")
        void stride() {
            var expected = Image.decode(fixture("pattern.png"));
            var stride = expected.width() * 4 + 12;
            var into = ByteBuffer.allocateDirect(stride * expected.height());
            for (var i = 0; i < into.capacity(); i++) {
                into.put(i, (byte) 0x5A);
            }

            Image.decodeInto(fixture("pattern.png"), into, stride, ChannelOrder.RGBA);

            assertPixels(expected, into, stride, ChannelOrder.RGBA);
            for (var y = 0; y < expected.height() - 1; y++) {
                for (var pad = expected.width() * 4; pad < stride; pad++) {
                    assertEquals(0x5A, at(into, y * stride + pad), "row " + y + " padding byte " + pad);
                }
            }
        }

        @Test
        @DisplayName("starts at the buffer's position")
        void position() {
            var expected = Image.decode(fixture("pattern.gif"));
            var whole = ByteBuffer.allocateDirect(16 + expected.width() * expected.height() * 4);
            var into = whole.position(16).slice();

            Image.decodeInto(fixture("pattern.gif"), whole.position(16), expected.width() * 4, ChannelOrder.BGRA);

            assertEquals(0, at(whole, 0));
            assertPixels(expected, into, expected.width() * 4, ChannelOrder.BGRA);
        }

        @Test
        @DisplayName("too small is refused before anything is written")
        void tooSmall() {
            var into = ByteBuffer.allocateDirect(32 * 31 * 4);
            var error = assertThrows(
                    IllegalArgumentException.class,
                    () -> Image.decodeInto(fixture("pattern.png"), into, 32 * 4, ChannelOrder.RGBA));
            assertTrue(error.getMessage().contains("32x32"), error.getMessage());
            for (var i = 0; i < into.capacity(); i++) {
                assertEquals(0, into.get(i), "byte " + i);
            }
        }

        @Test
        @DisplayName("too narrow a stride is refused")
        void narrowStride() {
            var into = ByteBuffer.allocateDirect(64 * 64 * 4);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Image.decodeInto(fixture("pattern.png"), into, 31 * 4, ChannelOrder.BGRA));
        }

        @Test
        @DisplayName("on the heap is refused")
        void heap() {
            assertThrows(IllegalArgumentException.class, () -> new PixelTarget(ByteBuffer.allocate(16), 4));
        }

        @Test
        @DisplayName("is sized by bytesFor: no padding after the last row")
        void bytesFor() {
            var target = new PixelTarget(ByteBuffer.allocateDirect(1).order(ByteOrder.LITTLE_ENDIAN), 40);
            assertEquals(40L * 2 + 32, target.bytesFor(new PhysicalSize(8, 3)));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("bytes that are no image are the decoder's error")
        void notAnImage() {
            var into = ByteBuffer.allocateDirect(64);
            assertThrows(
                    ImageDecodeException.class,
                    () -> Image.decodeInto(ByteBuffer.wrap(new byte[] {1, 2, 3, 4}), into, 16, ChannelOrder.RGBA));
        }

        @Test
        @DisplayName("no bytes at all are refused")
        void empty() {
            var into = ByteBuffer.allocateDirect(64);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Image.decodeInto(ByteBuffer.allocate(0), into, 16, ChannelOrder.RGBA));
        }
    }

    @Test
    @DisplayName("the swap exchanges red and blue and keeps green and alpha")
    void swap() {
        assertEquals(0x80332211, Decoding.swapRedAndBlue(0x80112233));
        assertEquals(0xFF00FF00, Decoding.swapRedAndBlue(0xFF00FF00));
    }
}
