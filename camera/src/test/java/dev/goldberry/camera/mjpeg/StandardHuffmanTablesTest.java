package dev.goldberry.camera.mjpeg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The tables put into a frame that has none, checked against what an
/// independent encoder (libjpeg, through Pillow) writes as JPEG's standard
/// ones.
@DisplayName("the standard Huffman tables")
class StandardHuffmanTablesTest {

    @Test
    @DisplayName("are libjpeg's four standard tables, byte for byte")
    void matchLibjpeg() {
        var ours = MjpegFixtures.huffmanTables(prefixed(StandardHuffmanTables.segment()));
        var libjpeg = MjpegFixtures.huffmanTables(ByteBuffer.wrap(MjpegFixtures.bytes(MjpegFixtures.CARD)));
        assertAll(
                () -> assertEquals(Set.of(0x00, 0x01, 0x10, 0x11), ours.keySet(), "luma and chroma, DC and AC"),
                () -> assertEquals(libjpeg, ours));
    }

    @Test
    @DisplayName("are one DHT segment whose length counts itself and not the marker")
    void oneSegment() {
        var segment = StandardHuffmanTables.segment();
        assertAll(
                () -> assertEquals(0xFF, segment.get(0) & 0xFF),
                () -> assertEquals(0xC4, segment.get(1) & 0xFF),
                () -> assertEquals(StandardHuffmanTables.length() - 2, segment.getShort(2) & 0xFFFF),
                () -> assertEquals(2 + 2 + 4 * 17 + 12 + 162 + 12 + 162, StandardHuffmanTables.length()),
                () -> assertTrue(segment.isReadOnly()));
    }

    /// `segment` behind a start-of-image marker, as the scan of a file reads
    /// it.
    private static ByteBuffer prefixed(ByteBuffer segment) {
        var file = ByteBuffer.allocate(2 + segment.remaining() + 2);
        file.put((byte) 0xFF).put((byte) 0xD8).put(segment).put((byte) 0xFF).put((byte) 0xDA);
        return file.flip();
    }
}
