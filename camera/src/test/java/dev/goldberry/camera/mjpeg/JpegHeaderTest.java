package dev.goldberry.camera.mjpeg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.image.ImageDecodeException;

@DisplayName("a JPEG's header")
class JpegHeaderTest {

    @Test
    @DisplayName("finds the first scan, and whether Huffman tables come before it")
    void scan() {
        var card = ByteBuffer.wrap(MjpegFixtures.bytes(MjpegFixtures.CARD));
        var bare = ByteBuffer.wrap(MjpegFixtures.bytes(MjpegFixtures.CARD_WITHOUT_TABLES));
        var withTables = JpegHeader.read(card);
        var without = JpegHeader.read(bare);
        assertAll(
                () -> assertTrue(withTables.definesHuffmanTables()),
                () -> assertFalse(without.definesHuffmanTables()),
                () -> assertEquals(0xDA, card.get(withTables.scanOffset() + 1) & 0xFF),
                () -> assertEquals(0xDA, bare.get(without.scanOffset() + 1) & 0xFF),
                () -> assertEquals(
                        withTables.scanOffset() - without.scanOffset(),
                        card.remaining() - bare.remaining(),
                        "only the tables were cut"),
                () -> assertEquals(0, card.position(), "the buffer is left as it was"));
    }

    @Test
    @DisplayName("counts from the buffer's position, and reads fill bytes before a marker")
    void positionAndFill() {
        var card = MjpegFixtures.bytes(MjpegFixtures.CARD);
        var at = JpegHeader.read(ByteBuffer.wrap(card)).scanOffset();
        // Three bytes of something else ahead, and two fill bytes before the scan.
        var shifted = new byte[3 + card.length + 2];
        System.arraycopy(card, 0, shifted, 3, at);
        shifted[3 + at] = (byte) 0xFF;
        shifted[3 + at + 1] = (byte) 0xFF;
        System.arraycopy(card, at, shifted, 3 + at + 2, card.length - at);
        var header = JpegHeader.read(ByteBuffer.wrap(shifted).position(3));
        assertEquals(new JpegHeader(at, true), header);
    }

    @Test
    @DisplayName("refuses bytes that are not a JPEG, or end before a scan")
    void refuses() {
        var card = MjpegFixtures.bytes(MjpegFixtures.CARD);
        var at = JpegHeader.read(ByteBuffer.wrap(card)).scanOffset();
        var broken = card.clone();
        broken[2] = 0x00;
        assertAll(
                () -> assertThrows(ImageDecodeException.class, () -> JpegHeader.read(ByteBuffer.allocate(0))),
                () -> assertThrows(
                        ImageDecodeException.class,
                        () -> JpegHeader.read(ByteBuffer.wrap(new byte[] {(byte) 0x89, 'P', 'N', 'G'}))),
                () -> assertThrows(
                        ImageDecodeException.class, () -> JpegHeader.read(ByteBuffer.wrap(Arrays.copyOf(card, at)))),
                () -> assertThrows(
                        ImageDecodeException.class, () -> JpegHeader.read(ByteBuffer.wrap(Arrays.copyOf(card, 21)))),
                () -> assertThrows(ImageDecodeException.class, () -> JpegHeader.read(ByteBuffer.wrap(broken))),
                () -> assertThrows(
                        ImageDecodeException.class,
                        () -> JpegHeader.read(ByteBuffer.wrap(new byte[] {
                            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, (byte) 0xFF, (byte) 0xDA
                        }))));
    }
}
