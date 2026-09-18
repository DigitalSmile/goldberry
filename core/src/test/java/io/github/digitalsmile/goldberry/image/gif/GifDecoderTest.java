package io.github.digitalsmile.goldberry.image.gif;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.image.ImageDecodeException;

/// What a **malformed** GIF comes back as.
///
/// [AnimationTest] covers the files that are right, with a writer that builds
/// them; this covers the files that are wrong, which are what a decoder handed
/// bytes off a disk or a clipboard is actually going to meet.
///
/// The contract is one type: `GifDecoder` answers [GifFormatException] and
/// `Image` answers `ImageDecodeException`, whatever the file did. Two ways out of
/// it were open until the 2026-09-18 review (C12) — a sub-block length that ran
/// past the end of the buffer came out as `IllegalArgumentException` from
/// `ByteBuffer.position`, and a header claiming 65535×65535 came out as
/// `ArithmeticException` from `Math.multiplyExact` — and an application catching
/// what the javadoc promised caught neither.
class GifDecoderTest {

    /// A header and a four-colour global table, which is where every GIF starts.
    private static Bytes gif(int width, int height) {
        var out = new Bytes();
        out.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
        out.short16(width);
        out.short16(height);
        out.byte8(0x80 | 0x01);
        out.byte8(0);
        out.byte8(0);
        for (var i = 0; i < 4; i++) {
            out.byte8(0);
            out.byte8(0);
            out.byte8(0);
        }
        return out;
    }

    @Nested
    @DisplayName("a file that ends in the middle of something")
    class Truncated {

        @Test
        @DisplayName("a sub-block longer than what is left is a format error, not an argument error")
        void aSubBlockPastTheEnd() {
            // A comment extension whose first sub-block claims 200 bytes and is
            // followed by three. `ByteBuffer.position` past the limit throws
            // IllegalArgumentException, which is neither of the two types the
            // decoder used to translate.
            var bytes = gif(2, 2);
            bytes.byte8(0x21);
            bytes.byte8(0xFE);
            bytes.byte8(200);
            bytes.write(new byte[] {1, 2, 3});

            var thrown = assertThrows(
                    GifFormatException.class, () -> GifDecoder.decode(ByteBuffer.wrap(bytes.toByteArray())));
            assertTrue(thrown.getMessage().contains("ends in the middle"), thrown.getMessage());
        }

        @Test
        @DisplayName("and Image answers for it in its own vocabulary")
        void andImageTranslatesIt() {
            var bytes = gif(2, 2);
            bytes.byte8(0x21);
            bytes.byte8(0xFE);
            bytes.byte8(200);
            bytes.write(new byte[] {1, 2, 3});

            assertThrows(ImageDecodeException.class, () -> Image.decode(ByteBuffer.wrap(bytes.toByteArray())));
        }

        @Test
        @DisplayName("a file that stops after its header is a format error")
        void nothingAfterTheHeader() {
            assertThrows(
                    GifFormatException.class,
                    () -> GifDecoder.decode(ByteBuffer.wrap(gif(2, 2).toByteArray())));
        }
    }

    @Nested
    @DisplayName("a file that claims more than it could hold")
    class TooLarge {

        @Test
        @DisplayName("a 65535x65535 logical screen is refused in the format's own words")
        void theLargestScreenTwoShortsCanName() {
            // Four billion pixels, sixteen gigabytes of int[]. The multiplication
            // overflowed an int and threw ArithmeticException before this was
            // checked; the message now says what the file claimed.
            var thrown = assertThrows(
                    GifFormatException.class,
                    () -> GifDecoder.decode(ByteBuffer.wrap(gif(65535, 65535).toByteArray())));

            assertTrue(thrown.getMessage().contains("65535x65535"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("pixels"), thrown.getMessage());
        }

        @Test
        @DisplayName("and so is every frame of it")
        void theSameForASequence() {
            assertThrows(
                    GifFormatException.class,
                    () -> GifDecoder.decodeAll(ByteBuffer.wrap(gif(65535, 65535).toByteArray())));
        }

        @Test
        @DisplayName("and Image answers for it in its own vocabulary")
        void andImageTranslatesIt() {
            assertThrows(
                    ImageDecodeException.class,
                    () -> Image.decode(ByteBuffer.wrap(gif(65535, 65535).toByteArray())));
        }
    }

    /// A little-endian byte sink, which is what every multi-byte field in a GIF
    /// is written as.
    private static final class Bytes {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void byte8(int value) {
            out.write(value & 0xFF);
        }

        void short16(int value) {
            byte8(value);
            byte8(value >> 8);
        }

        void write(byte[] bytes) {
            out.writeBytes(bytes);
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
