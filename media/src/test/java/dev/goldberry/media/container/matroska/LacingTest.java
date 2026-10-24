package dev.goldberry.media.container.matroska;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaException;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.io.MemoryIO;

@DisplayName("Lacing")
class LacingTest {

    private static ByteReader over(String hex) {
        return new ByteReader(new MemoryIO(HexFormat.of().parseHex(hex.replace(" ", ""))));
    }

    @Test
    @DisplayName("the lacing bits of a block's flags")
    void flags() {
        assertEquals(Lacing.NONE, Lacing.of(0x80));
        assertEquals(Lacing.XIPH, Lacing.of(0x02));
        assertEquals(Lacing.FIXED, Lacing.of(0x04));
        assertEquals(Lacing.EBML, Lacing.of(0x86));
    }

    @Test
    @DisplayName("no lacing: one frame to the end of the payload")
    void none() {
        var in = over("0102030405");
        assertArrayEquals(new long[] {5}, Lacing.read(in, Lacing.NONE, 5));
    }

    @Test
    @DisplayName("Xiph lacing: sizes as runs of 255 and a remainder, the last frame taking the rest")
    void xiph() {
        // Three frames: 300 bytes (255 + 45), 10 bytes, and whatever is left.
        var header = "02 FF2D 0A";
        var in = over(header + "00".repeat(300 + 10 + 7));
        assertArrayEquals(new long[] {300, 10, 7}, Lacing.read(in, Lacing.XIPH, 4 + 317));
    }

    @Test
    @DisplayName("EBML lacing: the first size as a vint, the next as signed differences")
    void ebml() {
        // Three frames: 20, then 20 + (0x80|0x3F = 63 - 63 = 0) = 20, then the rest.
        var header = "02 94 BF";
        var in = over(header + "00".repeat(20 + 20 + 3));
        assertArrayEquals(new long[] {20, 20, 3}, Lacing.read(in, Lacing.EBML, 3 + 43));
        // A negative difference: 0xBE is 62 - 63 = -1. Three frames, so that
        // two sizes are coded and the third takes the rest.
        var shrinking = over("02 94 BE" + "00".repeat(20 + 19 + 1));
        assertArrayEquals(new long[] {20, 19, 1}, Lacing.read(shrinking, Lacing.EBML, 3 + 40));
    }

    @Test
    @DisplayName("fixed lacing: the payload split into equal frames")
    void fixed() {
        var in = over("03" + "00".repeat(40));
        assertArrayEquals(new long[] {10, 10, 10, 10}, Lacing.read(in, Lacing.FIXED, 41));
        var uneven = over("02" + "00".repeat(10));
        assertThrows(MediaException.class, () -> Lacing.read(uneven, Lacing.FIXED, 11));
    }

    @Test
    @DisplayName("refuses sizes that name more bytes than the block holds")
    void overrun() {
        var in = over("01 FF2D 0000");
        assertThrows(MediaException.class, () -> Lacing.read(in, Lacing.XIPH, 5));
    }
}
