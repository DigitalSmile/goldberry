package dev.goldberry.media.platform.bitstream;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Length-prefixed packets rewritten with start codes, as a decoder that reads
/// Annex B wants them.
@DisplayName("AnnexB")
class AnnexBTest {

    private static final HexFormat HEX = HexFormat.of();

    private static byte[] rewrite(String packet, int lengthSize, List<byte[]> sets, boolean keyframe) {
        try (var arena = Arena.ofConfined()) {
            var in = arena.allocateFrom(JAVA_BYTE, HEX.parseHex(packet));
            var out = arena.allocate(AnnexB.size(in, lengthSize, sets, keyframe));
            var written = AnnexB.rewrite(in, lengthSize, sets, keyframe, out);
            assertEquals(out.byteSize(), written);
            return out.toArray(JAVA_BYTE);
        }
    }

    @Test
    @DisplayName("replaces each four-byte length with a start code")
    void fourByteLengths() {
        assertArrayEquals(
                HEX.parseHex("00000001" + "4101" + "00000001" + "419a0203"),
                rewrite("00000002" + "4101" + "00000004" + "419a0203", 4, List.of(), false));
    }

    @Test
    @DisplayName("reads one- and two-byte lengths too")
    void shorterLengths() {
        assertArrayEquals(HEX.parseHex("00000001" + "41aa"), rewrite("02" + "41aa", 1, List.of(), false));
        assertArrayEquals(HEX.parseHex("00000001" + "41aabb"), rewrite("0003" + "41aabb", 2, List.of(), false));
    }

    @Test
    @DisplayName("puts the parameter sets before a keyframe, in order, and only before a keyframe")
    void parameterSetsBeforeAKeyframe() {
        var sets = List.of(HEX.parseHex("6764"), HEX.parseHex("68ee"));
        assertArrayEquals(
                HEX.parseHex("00000001" + "6764" + "00000001" + "68ee" + "00000001" + "6588"),
                rewrite("00000002" + "6588", 4, sets, true));
        assertArrayEquals(HEX.parseHex("00000001" + "4188"), rewrite("00000002" + "4188", 4, sets, false));
    }

    @Test
    @DisplayName("the parameter sets of a real avcC come first, as the configuration lists them")
    void realConfiguration() {
        var configuration = ParameterSets.h264(ParameterSetsTest.AVCC_HIGH);
        var out = rewrite("00000002" + "6588", configuration.nalLengthSize(), configuration.parameterSets(), true);
        var sps = configuration.parameterSets().getFirst();
        assertEquals(0x67, out[4] & 0xff, "an SPS first");
        assertEquals(sps.length + 4 + configuration.parameterSets().get(1).length + 4 + 6, out.length);
    }

    @Test
    @DisplayName("refuses a length that runs past the packet, and a target too small")
    void refusesMalformed() {
        assertThrows(IllegalArgumentException.class, () -> rewrite("00000009" + "4101", 4, List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> rewrite("0000", 4, List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> rewrite("024101", 3, List.of(), false));
        try (var arena = Arena.ofConfined()) {
            var in = arena.allocateFrom(JAVA_BYTE, HEX.parseHex("000000024101"));
            assertThrows(
                    IllegalArgumentException.class, () -> AnnexB.rewrite(in, 4, List.of(), false, arena.allocate(5)));
        }
    }

    @Test
    @DisplayName("an empty packet is empty, and a keyframe's is its parameter sets alone")
    void empty() {
        assertEquals(0, rewrite("", 4, List.of(), false).length);
        assertArrayEquals(HEX.parseHex("00000001" + "6764"), rewrite("", 4, List.of(HEX.parseHex("6764")), true));
    }

    @Test
    @DisplayName("knows a stream already in Annex B by its start code, three bytes or four")
    void recognisesAnnexB() {
        try (var arena = Arena.ofConfined()) {
            assertTrue(AnnexB.startsWithStartCode(arena.allocateFrom(JAVA_BYTE, HEX.parseHex("00000001674d"))));
            assertTrue(AnnexB.startsWithStartCode(arena.allocateFrom(JAVA_BYTE, HEX.parseHex("000001674d"))));
            assertFalse(AnnexB.startsWithStartCode(arena.allocateFrom(JAVA_BYTE, HEX.parseHex("0000000267"))));
            assertFalse(AnnexB.startsWithStartCode(MemorySegment.NULL));
        }
    }
}
