package dev.goldberry.media.container.ebml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.io.MemoryIO;

@DisplayName("EbmlReader")
class EbmlReaderTest {

    private static EbmlReader over(String hex) {
        return new EbmlReader(new ByteReader(new MemoryIO(HexFormat.of().parseHex(hex.replace(" ", "")))));
    }

    @Test
    @DisplayName("reads IDs of one to four bytes with their marker, and sizes of one to eight without")
    void headers() {
        var ebml = over("1A45DFA3 8F" + "42 86 81 01" + "E7 4000" + "A3 10000005" + "BB 01 00000000000003");
        var header = ebml.readHeader();
        assertEquals(0x1A45DFA3L, header.id());
        assertEquals(15, header.size());
        assertEquals(5, header.dataStart());
        assertEquals(0, header.headerStart());
        assertEquals(20, header.dataEnd());

        var version = ebml.readHeader();
        assertEquals(0x4286, version.id());
        assertEquals(1, version.size());
        assertEquals(1, ebml.readUnsigned(version));

        var timestamp = ebml.readHeader();
        assertEquals(0xE7, timestamp.id());
        assertEquals(0, timestamp.size());

        var block = ebml.readHeader();
        assertEquals(0xA3, block.id());
        assertEquals(5, block.size());

        var point = ebml.readHeader();
        assertEquals(0xBB, point.id());
        assertEquals(3, point.size());
    }

    @Test
    @DisplayName("an all-ones size is unknown, which only a master element may have")
    void unknownSize() {
        var ebml = over("18538067 01FFFFFFFFFFFFFF" + "1F43B675 FF");
        var segment = ebml.readHeader();
        assertFalse(segment.sized());
        assertEquals(EbmlReader.Element.UNKNOWN_SIZE, segment.size());
        var cluster = ebml.readHeader();
        assertFalse(cluster.sized());
        assertThrows(IllegalStateException.class, cluster::dataEnd);
        var e = assertThrows(MediaException.class, () -> ebml.skip(cluster));
        assertTrue(e.error() instanceof MediaError.InvalidData);
    }

    @Test
    @DisplayName("reads the scalar payloads: unsigned, signed, float, string")
    void scalars() {
        var ebml = over(
                "D7 82 0100" + "FB 81 FF" + "4489 84 447A0000" + "4489 88 3FF0000000000000" + "4282 85 7765626D00");
        assertEquals(256, ebml.readUnsigned(ebml.readHeader()));
        assertEquals(-1, ebml.readSigned(ebml.readHeader()));
        assertEquals(1000.0, ebml.readFloat(ebml.readHeader()));
        assertEquals(1.0, ebml.readFloat(ebml.readHeader()));
        assertEquals("webm", ebml.readString(ebml.readHeader()), "a trailing NUL is not part of the string");
    }

    @Test
    @DisplayName("refuses a float of an odd size and an integer wider than eight bytes")
    void refuses() {
        var oddFloat = over("4489 83 000000");
        var e = assertThrows(MediaException.class, () -> oddFloat.readFloat(oddFloat.readHeader()));
        assertTrue(e.getMessage().contains("3 bytes"), e.getMessage());
        var wide = over("D7 89 000000000000000000");
        assertThrows(MediaException.class, () -> wide.readUnsigned(wide.readHeader()));
    }

    @Test
    @DisplayName("names the byte where a header is not one")
    void notAHeader() {
        var ebml = over("00 81");
        var e = assertThrows(MediaException.class, ebml::readHeader);
        assertTrue(e.getMessage().contains("byte 0"), e.getMessage());
    }

    @Test
    @DisplayName("the length a first byte announces")
    void lengths() {
        assertEquals(1, EbmlReader.lengthOf(0x80));
        assertEquals(1, EbmlReader.lengthOf(0xFF));
        assertEquals(2, EbmlReader.lengthOf(0x40));
        assertEquals(4, EbmlReader.lengthOf(0x1A));
        assertEquals(8, EbmlReader.lengthOf(0x01));
        assertEquals(0, EbmlReader.lengthOf(0x00));
    }
}
