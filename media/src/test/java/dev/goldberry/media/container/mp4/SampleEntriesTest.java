package dev.goldberry.media.container.mp4;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.decoder.opus.OpusHead;

/// The configuration boxes turned into what the decoders read: the records
/// are the ones in the fixtures, and a few made by hand for the shapes the
/// fixtures do not have.
@DisplayName("SampleEntries")
class SampleEntriesTest {

    private static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    @Test
    @DisplayName("dOps becomes an OpusHead: magic, version 1, the fields little-endian, family 0 without a mapping")
    void opusHeadFromDops() {
        // tone-opus.mp4's dOps: version 0, 2 channels, pre-skip 312, 48 kHz, no gain, family 0.
        var head = SampleEntries.opusHead(hex("000201380000bb80000000"), 2);
        assertEquals(19, head.length);
        assertEquals("OpusHead", new String(head, 0, 8, StandardCharsets.US_ASCII));
        assertEquals(1, head[8]);
        var parsed = OpusHead.parse(head);
        assertEquals(2, parsed.channels());
        assertEquals(312, parsed.preSkip());
        assertEquals(48_000, parsed.inputRate());
        assertEquals(0, parsed.gain());
        assertEquals(0, parsed.mappingFamily());
    }

    @Test
    @DisplayName("a surround dOps carries its stream counts and mapping over, and a gain keeps its sign")
    void opusHeadSurround() {
        // 6 channels, pre-skip 0x0138, 48 kHz, gain -256 (Q7.8: -1 dB), family 1, 4 streams, 2 coupled.
        var head = SampleEntries.opusHead(
                hex("0006" + "0138" + "0000bb80" + "ff00" + "01" + "04" + "02" + "000401020305"), 6);
        var parsed = OpusHead.parse(head);
        assertEquals(6, parsed.channels());
        assertEquals(-256, parsed.gain());
        assertEquals(1, parsed.mappingFamily());
        assertEquals(4, parsed.streams());
        assertEquals(2, parsed.coupled());
        assertArrayEquals(new byte[] {0, 4, 1, 2, 3, 5}, parsed.mapping());
    }

    @Test
    @DisplayName("a dOps cut short of its mapping is invalid data; one missing altogether is a plain header")
    void opusHeadEdges() {
        assertThrows(MediaException.class, () -> SampleEntries.opusHead(hex("000601380000bb80ff000104"), 6));
        var plain = OpusHead.parse(SampleEntries.opusHead(new byte[0], 1));
        assertEquals(1, plain.channels());
        assertEquals(0, plain.preSkip());
        assertEquals(48_000, plain.inputRate());
    }

    @Test
    @DisplayName(
            "esds: the ES, decoder config and specific info descriptors, with 4-byte lengths as FFmpeg writes them")
    void esds() {
        // clip-h264-aac.mp4's esds.
        var esds = SampleEntries.esds(hex("00000000" + "0380808025" + "000200" + "0480808017" + "4015000000"
                + "0000fa00" + "0000f1f6" + "0580808005" + "119056e500" + "068080800102"));
        assertEquals(0x40, esds.objectType());
        assertArrayEquals(hex("119056e500"), esds.specificInfo());
        assertEquals(61_942, esds.averageBitRate());
    }

    @Test
    @DisplayName("esds with one-byte lengths and an ES_Descriptor that names a URL and a dependence")
    void esdsCompact() {
        var esds = SampleEntries.esds(hex("00000000" + "03" + "1b" + "0001" + "c0" + "0002" + "02" + "4142" + "04"
                + "11" + "6b" + "15" + "000000" + "00000000" + "00000000" + "05" + "02" + "abcd"));
        assertEquals(0x6B, esds.objectType());
        assertArrayEquals(hex("abcd"), esds.specificInfo());
    }

    @Test
    @DisplayName("dfLa's blocks follow a fLaC marker")
    void flac() {
        var extradata = SampleEntries.flacExtradata(hex("00000000" + "80000022" + "1000"));
        assertArrayEquals(hex("664c6143" + "80000022" + "1000"), extradata);
        assertArrayEquals(hex("664c6143"), SampleEntries.flacExtradata(new byte[0]));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "0100000000008000, yuv420p",
        "0100000000008200, yuv420p",
        "0100000000008300, yuv420p",
        "010000000000a200, yuv420p10le",
        "010000000000a600, yuv444p10le",
        "0100000000008400, yuv422p",
        "010000000000c600, yuv444p12le",
    })
    @DisplayName("vpcC's bit depth and chroma subsampling name the pixel format")
    void vpcc(String record, String format) {
        assertEquals(Optional.of(format), SampleEntries.vpPixelFormat(hex(record + "0000")));
    }

    @Test
    @DisplayName("a vpcC of another version, or too short, says nothing")
    void vpccEdges() {
        assertEquals(Optional.empty(), SampleEntries.vpPixelFormat(hex("0000000000008200")));
        assertEquals(Optional.empty(), SampleEntries.vpPixelFormat(hex("0100")));
        assertEquals(Optional.empty(), SampleEntries.vpPixelFormat(hex("0100000000007200")));
    }

    @ParameterizedTest(name = "{0} is profile {1}")
    @CsvSource({"1190, 1", "0990, 0", "2b92, 4", "e8a1, 28", "f8e0, 38"})
    @DisplayName("the AudioSpecificConfig's object type is FFmpeg's AAC profile less one, through the escape")
    void aacProfile(String config, int profile) {
        assertEquals(OptionalInt.of(profile), SampleEntries.aacProfile(hex(config)));
    }

    @Test
    @DisplayName("no AudioSpecificConfig, no profile")
    void aacProfileEmpty() {
        assertEquals(OptionalInt.empty(), SampleEntries.aacProfile(new byte[0]));
    }

    @Test
    @DisplayName("colr, then vpcC's colour, with mdcv's peak in ten-thousandths of a nit and clli's MaxCLL")
    void colour() {
        var children = new HashMap<Integer, byte[]>();
        // nclx: primaries 9, transfer 16, matrix 9, limited range.
        children.put(BoxTypes.COLR, hex("6e636c7800090010000900"));
        // mdcv: six primaries and a white point, then 1000 nits and 0.0001.
        children.put(BoxTypes.MDCV, hex("8a48395d1d4c0bb834bc1e1b7d1a3d13" + "00989680" + "00000001"));
        children.put(BoxTypes.CLLI, hex("03e80190"));
        var colour = SampleEntries.colour(children, new byte[0]);
        assertEquals(ColorDescription.Primaries.BT2020, colour.primaries());
        assertEquals(ColorDescription.Transfer.PQ, colour.transfer());
        assertEquals(OptionalInt.of(1000), colour.masteringPeak());
        assertEquals(OptionalInt.of(1000), colour.contentPeak());

        // vpcC version 1: profile 2, level 31, 10-bit 4:2:0, primaries 9, transfer 18, matrix 9.
        var vpcC = hex("01000000021f2009120900");
        var hlg = SampleEntries.colour(Map.of(), vpcC);
        assertEquals(ColorDescription.Transfer.HLG, hlg.transfer());
        assertEquals(ColorDescription.Primaries.BT2020, hlg.primaries());
        assertEquals(ColorDescription.UNSPECIFIED, SampleEntries.colour(Map.of(), new byte[0]));
    }
}
