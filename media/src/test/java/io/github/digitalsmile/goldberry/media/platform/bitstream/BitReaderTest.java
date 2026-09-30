package io.github.digitalsmile.goldberry.media.platform.bitstream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("BitReader")
class BitReaderTest {

    @Test
    @DisplayName("reads fixed-width fields most significant bit first, across bytes")
    void fixedWidth() {
        var r = new BitReader(new byte[] {(byte) 0b1010_0110, (byte) 0b1100_0001});
        assertEquals(0b101, r.bits(3));
        assertTrue(!r.flag());
        assertEquals(0b0110_11, r.bits(6));
        assertEquals(10, r.position());
        r.skip(5);
        assertEquals(1, r.bit());
        assertEquals(0, r.bits(0));
    }

    @Test
    @DisplayName("a full 32-bit field comes back in an int's bits")
    void thirtyTwoBits() {
        var r = new BitReader(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFE});
        assertEquals(0xFFFF_FFFE, r.bits(32));
    }

    @ParameterizedTest(name = "{0} is ue {1}, se {2}")
    @CsvSource({"1, 0, 0", "010, 1, 1", "011, 2, -1", "00100, 3, 2", "00101, 4, -2", "0001000, 7, 4"})
    @DisplayName("reads Exp-Golomb codes, unsigned and signed")
    void expGolomb(String code, int unsigned, int signed) {
        assertEquals(unsigned, new BitReader(bytes(code)).ue());
        assertEquals(signed, new BitReader(bytes(code)).se());
    }

    @Test
    @DisplayName("agrees with the tests' BitWriter over a run of codes")
    void roundTrip() {
        var writer = new BitWriter();
        for (var i = 0; i < 300; i++) {
            writer.ue(i).se(i - 150).bits(5, i & 31);
        }
        var r = new BitReader(writer.finish());
        for (var i = 0; i < 300; i++) {
            assertEquals(i, r.ue());
            assertEquals(i - 150, r.se());
            assertEquals(i & 31, r.bits(5));
        }
    }

    @Test
    @DisplayName("running off the end is IllegalArgumentException, never a wrong value")
    void truncated() {
        var r = new BitReader(new byte[] {0});
        // Eight zeros and no terminating one: a code that never ends.
        assertThrows(IllegalArgumentException.class, r::ue);
        assertThrows(IllegalArgumentException.class, () -> new BitReader(new byte[1]).skip(9));
        assertThrows(IllegalArgumentException.class, () -> new BitReader(new byte[8]).bits(33));
    }

    @Test
    @DisplayName("unescape drops the 03 of each 00 00 03, and nothing else")
    void unescape() {
        byte[] nal = {0x67, 0, 0, 3, 1, 0, 0, 3, 3, 0, 3, 0, 0};
        assertArrayEquals(new byte[] {0, 0, 1, 0, 0, 3, 0, 3, 0, 0}, BitReader.unescape(nal, 1, nal.length - 1));
        assertThrows(IndexOutOfBoundsException.class, () -> BitReader.unescape(nal, 10, 5));
    }

    /// `code`, a string of bits, padded with ones to a byte.
    private static byte[] bytes(String code) {
        var padded = code + "1".repeat(8 - code.length() % 8);
        var out = new byte[padded.length() / 8];
        for (var i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(padded.substring(8 * i, 8 * i + 8), 2);
        }
        return out;
    }
}
