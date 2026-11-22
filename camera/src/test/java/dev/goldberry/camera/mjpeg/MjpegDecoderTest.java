package dev.goldberry.camera.mjpeg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.image.Image;
import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.natives.NativeLibraryRequirement;

/// Motion JPEG frames decoded by the rasterizer's decoder into the pool: the
/// card's bars in BGRA, a frame without tables given the standard ones, a bad
/// frame refused without touching a picture handed out.
@DisplayName("a Motion JPEG decoder")
class MjpegDecoderTest {

    /// What the decoder's rounding and the encoder's quantisation leave of a
    /// flat bar, away from its edges.
    private static final int TOLERANCE = 12;

    @BeforeEach
    void library() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("decode a frame into an opaque BGRA picture of its own size, bars in place")
    void decodes() {
        var decoder = new MjpegDecoder();
        var picture = decoder.decode(MjpegFixtures.lent(MjpegFixtures.CARD), 42);
        assertAll(
                () -> assertEquals(64, picture.width()),
                () -> assertEquals(48, picture.height()),
                () -> assertEquals(64 * 4, picture.stride()),
                () -> assertTrue(picture.opaque()),
                () -> assertEquals(42, picture.ptsNanos()),
                // The middle of the white, yellow, cyan and blue bars, as B, G, R.
                () -> assertPixel(picture, 4, 10, 235, 235, 235),
                () -> assertPixel(picture, 13, 10, 16, 235, 235),
                () -> assertPixel(picture, 22, 10, 235, 235, 16),
                () -> assertPixel(picture, 59, 10, 235, 16, 16),
                () -> assertEquals(1, decoder.decoded()),
                () -> assertEquals(0, decoder.givenTables()));
    }

    @Test
    @DisplayName("give a frame without Huffman tables the standard ones, and decode it as the frame with them")
    void givesTables() {
        var bare = MjpegFixtures.lent(MjpegFixtures.CARD_WITHOUT_TABLES);
        assertThrows(
                ImageDecodeException.class,
                () -> Image.decode(bare.duplicate()),
                "the rasterizer refuses a frame without tables by itself");
        var decoder = new MjpegDecoder();
        var withTables = bytes(decoder.decode(MjpegFixtures.lent(MjpegFixtures.CARD), 0));
        var given = bytes(decoder.decode(bare, 0));
        assertAll(
                () -> assertTrue(Arrays.equals(withTables, given), "the same pixels"),
                () -> assertEquals(1, decoder.givenTables()),
                () -> assertEquals(0, bare.position(), "the lent frame is left as it was"));
    }

    @Test
    @DisplayName("hand out three slots in turn, and keep a refused frame from taking one")
    void slots() {
        var decoder = new MjpegDecoder();
        var first = decoder.decode(MjpegFixtures.lent(MjpegFixtures.CARD), 1);
        var card = MjpegFixtures.bytes(MjpegFixtures.CARD);
        var cut = ByteBuffer.allocateDirect(card.length / 2).put(0, card, 0, card.length / 2);
        var garbage = ByteBuffer.allocateDirect(64);
        assertAll(
                () -> assertThrows(ImageDecodeException.class, () -> decoder.decode(garbage, 2)),
                () -> assertThrows(ImageDecodeException.class, () -> decoder.decode(cut, 2)));
        var second = decoder.decode(MjpegFixtures.lent(MjpegFixtures.CARD), 3);
        var third = decoder.decode(MjpegFixtures.lent(MjpegFixtures.CARD), 4);
        var fourth = decoder.decode(MjpegFixtures.lent(MjpegFixtures.CARD), 5);
        assertAll(
                () -> assertNotEquals(address(first), address(second)),
                () -> assertNotEquals(address(second), address(third)),
                () -> assertNotEquals(address(first), address(third)),
                () -> assertEquals(address(first), address(fourth), "the fourth reuses the first slot"),
                () -> assertEquals(4, decoder.decoded()));
    }

    @Test
    @DisplayName("decode a frame of a new size into a pool of its own, leaving the last picture whole")
    void resizes() {
        var decoder = new MjpegDecoder();
        var large = decoder.decode(MjpegFixtures.lent(MjpegFixtures.CARD), 0);
        var before = bytes(large);
        var small = decoder.decode(MjpegFixtures.lent(MjpegFixtures.SMALL_CARD), 0);
        assertAll(
                () -> assertEquals(32, small.width()),
                () -> assertEquals(24, small.height()),
                () -> assertTrue(Arrays.equals(before, bytes(large))));
    }

    private static void assertPixel(VideoPicture picture, int x, int y, int blue, int green, int red) {
        var pixels = picture.pixels();
        var at = y * picture.stride() + x * 4;
        var actual = new int[] {
            pixels.get(at) & 0xFF, pixels.get(at + 1) & 0xFF, pixels.get(at + 2) & 0xFF, pixels.get(at + 3) & 0xFF
        };
        var expected = new int[] {blue, green, red, 255};
        for (var channel = 0; channel < 4; channel++) {
            assertTrue(
                    Math.abs(actual[channel] - expected[channel]) <= TOLERANCE,
                    () -> "(" + x + ", " + y + ") is " + Arrays.toString(actual) + ", not near "
                            + Arrays.toString(expected));
        }
    }

    private static byte[] bytes(VideoPicture picture) {
        var out = new byte[picture.stride() * picture.height()];
        picture.pixels().get(0, out);
        return out;
    }

    private static long address(VideoPicture picture) {
        return MemorySegment.ofBuffer(picture.pixels()).address();
    }
}
