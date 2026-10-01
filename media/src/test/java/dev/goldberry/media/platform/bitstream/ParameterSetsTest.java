package dev.goldberry.media.platform.bitstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// The configuration records of the fixtures, and made-up SPSs for the cases
/// the fixtures do not reach.
///
/// The fixtures' expected depths are FFmpeg's own `has_b_frames` for each
/// clip, as `ffprobe` reports it: the provider derives the depth as FFmpeg does.
@DisplayName("ParameterSets")
public class ParameterSetsTest {

    private static final HexFormat HEX = HexFormat.of();

    /// `clip-h264-high.mp4`'s `avcC`: High, level 1.1, B-frames in a pyramid, and a
    /// VUI whose bitstream restriction says two.
    public static final byte[] AVCC_HIGH = HEX.parseHex(
            "0164000bffe100196764000bacd9428df93011000003000100000300320f14299601" + "000668ebe112c8b0fdf8f800");

    /// `:media`'s `clip-h264-aac.mp4`: Constrained Baseline, no B-frames.
    public static final byte[] AVCC_BASELINE =
            HEX.parseHex("0142c00bffe100186742c00bda0a37e4c044000003000400000300c83c50aa8001000468ce0fc8");

    /// `clip-hevc.mp4`'s `hvcC`, the x265 SEI array taken out: Main, three
    /// B-frames in a pyramid.
    public static final byte[] HVCC_MAIN =
            HEX.parseHex("0101600000009000000000001ef000fcfdf8f800000f03a00001001840010c01ffff"
                    + "01600000030090000003000003001e959409a10001002a4201010160000003009000000300000300"
                    + "1ea0142061f26595964932bc05a02000000300200000030321a2000100074401c172b46240");

    /// `clip-hevc-10bit.mp4`'s `hvcC`, the same way: Main 10.
    public static final byte[] HVCC_MAIN10 =
            HEX.parseHex("0102200000009000000000001ef000fcfdfafa00000f03a00001001840010c01ffff"
                    + "02200000030090000003000003001e959409a10001002e4201010220000003009000000300000300"
                    + "1ea0142061f236595964932bc05a810104820000030002000003003210a2000100074401c172b46240");

    @Test
    @DisplayName("H.264 High: the VUI's reorder depth, 8-bit 4:2:0, the SPS and PPS, 4-byte lengths")
    void h264High() {
        var configuration = ParameterSets.h264(AVCC_HIGH);
        assertEquals(new ParameterSets.Shape(2, 8, 1), configuration.shape());
        assertEquals(4, configuration.nalLengthSize());
        assertEquals(2, configuration.parameterSets().size());
        assertEquals(0x67, configuration.parameterSets().get(0)[0] & 0xFF, "SPS first");
        assertEquals(0x68, configuration.parameterSets().get(1)[0] & 0xFF, "PPS second");
        assertTrue(configuration.shape().decodable());
    }

    @Test
    @DisplayName("H.264 Constrained Baseline: nothing held back")
    void h264Baseline() {
        assertEquals(0, ParameterSets.h264(AVCC_BASELINE).shape().reorderDepth());
    }

    @ParameterizedTest(name = "{0}-bit")
    @CsvSource({"8", "10"})
    @DisplayName("HEVC: VPS, SPS and PPS in that order, the SPS's reorder depth and bit depth")
    void hevc(int bitDepth) {
        var configuration = ParameterSets.hevc(bitDepth == 8 ? HVCC_MAIN : HVCC_MAIN10);
        assertEquals(new ParameterSets.Shape(2, bitDepth, 1), configuration.shape());
        assertEquals(4, configuration.nalLengthSize());
        var types = configuration.parameterSets().stream()
                .map(set -> (set[0] >> 1) & 0x3F)
                .toList();
        assertEquals(List.of(32, 33, 34), types);
    }

    @Test
    @DisplayName("an H.264 SPS with no VUI holds back what its level's picture buffer allows")
    void h264FromLevel() {
        // Main, level 4.0, 1920×1088: 32768 / (120 × 68) is four frames.
        var sps = h264Sps(77, 40, 0, 4, 120, 68, null);
        assertEquals(4, ParameterSets.h264Sps(sps).reorderDepth());
        // Level 5.1 at 1920×1088 would be 22; the buffer holds sixteen at most.
        assertEquals(
                16, ParameterSets.h264Sps(h264Sps(77, 51, 0, 4, 120, 68, null)).reorderDepth());
    }

    @Test
    @DisplayName("an H.264 SPS whose pictures cannot be reordered holds nothing back")
    void h264NoReordering() {
        // Picture order type 2: output order is decoding order.
        assertEquals(
                0, ParameterSets.h264Sps(h264Sps(77, 40, 2, 4, 120, 68, null)).reorderDepth());
        // No reference frames: no picture waits for another.
        assertEquals(
                0, ParameterSets.h264Sps(h264Sps(77, 40, 0, 0, 120, 68, null)).reorderDepth());
    }

    @Test
    @DisplayName("an H.264 VUI's bitstream restriction wins, with HRD parameters before it")
    void h264Restriction() {
        assertEquals(1, ParameterSets.h264Sps(h264Sps(77, 40, 0, 4, 120, 68, 1)).reorderDepth());
    }

    @Test
    @DisplayName("an H.264 High 4:2:2 or 12-bit stream is read, and is not decodable as the frame contract asks")
    void h264HighProfiles() {
        var shape = ParameterSets.h264Sps(h264HighSps(122, 2, 8));
        assertEquals(2, shape.chromaFormat());
        assertFalse(shape.decodable());
        var deep = ParameterSets.h264Sps(h264HighSps(110, 1, 12));
        assertEquals(12, deep.bitDepth());
        assertFalse(deep.decodable());
        assertTrue(ParameterSets.h264Sps(h264HighSps(110, 1, 10)).decodable());
    }

    @Test
    @DisplayName("the picture buffer for each level, with level 1b and an unknown level")
    void maxDpbFrames() {
        assertEquals(4, ParameterSets.maxDpbFrames(40, false, 8160));
        assertEquals(16, ParameterSets.maxDpbFrames(99, false, 8160), "an unknown level allows the most");
        assertEquals(4, ParameterSets.maxDpbFrames(11, true, 99), "level 1b: 396 / 99");
        assertEquals(9, ParameterSets.maxDpbFrames(11, false, 99), "level 1.1: 900 / 99");
        assertEquals(16, ParameterSets.maxDpbFrames(40, false, 0));
    }

    @Test
    @DisplayName("a record that is not one, or ends early, is IllegalArgumentException")
    void malformed() {
        assertThrows(IllegalArgumentException.class, () -> ParameterSets.h264(new byte[0]));
        // Annex B: a start code where a record's version would be.
        assertThrows(IllegalArgumentException.class, () -> ParameterSets.h264(HEX.parseHex("00000001674d0028")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ParameterSets.h264(Arrays.copyOf(AVCC_HIGH, 20)),
                "a truncated SPS");
        assertThrows(IllegalArgumentException.class, () -> ParameterSets.hevc(Arrays.copyOf(HVCC_MAIN, 23)));
        assertThrows(IllegalArgumentException.class, () -> ParameterSets.hevc(Arrays.copyOf(HVCC_MAIN, 60)));
    }

    @Test
    @DisplayName("a shape and a configuration check what they are given")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new ParameterSets.Shape(17, 8, 1));
        assertThrows(IllegalArgumentException.class, () -> new ParameterSets.Shape(-1, 8, 1));
        var shape = new ParameterSets.Shape(0, 8, 1);
        assertThrows(IllegalArgumentException.class, () -> new ParameterSets.Configuration(List.of(), 4, shape));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ParameterSets.Configuration(List.of(new byte[] {0x67}), 3, shape));
    }

    @Test
    @DisplayName("an H.264 SPS with picture order type 1, fields, a crop and a VUI cut short falls back to the level")
    void h264OddFields() {
        var sps = new BitWriter()
                .bits(8, 77)
                .bits(8, 0)
                .bits(8, 30)
                .ue(0)
                .ue(0)
                .ue(1) // pic_order_cnt_type 1, and its cycle
                .flag(false)
                .se(-1)
                .se(2)
                .ue(2)
                .se(3)
                .se(-3)
                .ue(4)
                .flag(false)
                .ue(44) // 45 macroblocks wide
                .ue(17) // 18 map units of fields: 36 macroblocks tall
                .flag(false) // frame_mbs_only_flag: fields
                .flag(true)
                .flag(true)
                .flag(true) // a crop
                .ue(0)
                .ue(0)
                .ue(0)
                .ue(2)
                .flag(true) // a VUI, which ends inside its sample aspect ratio
                .flag(true)
                .bits(8, 255)
                .finish();
        // Level 3.0 holds 8100 macroblocks: five 720×576 frames.
        assertEquals(new ParameterSets.Shape(5, 8, 1), ParameterSets.h264Sps(sps));
    }

    @Test
    @DisplayName("an intra-only H.264 profile holds nothing back, and 4:4:4 is read and refused")
    void h264IntraAnd444() {
        // High 10 Intra: profile 110 with constraint_set3.
        assertEquals(0, ParameterSets.h264Sps(h264HighSps(110, 0x10, 1, 10)).reorderDepth());
        var full = ParameterSets.h264Sps(h264HighSps(244, 3, 8));
        assertEquals(3, full.chromaFormat());
        assertFalse(full.decodable());
    }

    @Test
    @DisplayName("HEVC with temporal sub-layers: the highest layer's reorder depth, with or without every layer's")
    void hevcSubLayers() {
        assertEquals(
                3,
                ParameterSets.hevcSps(hevcSps(2, true, new int[] {0, 1, 3}, 1, 8, true))
                        .reorderDepth());
        assertEquals(
                2,
                ParameterSets.hevcSps(hevcSps(2, false, new int[] {2}, 1, 8, false))
                        .reorderDepth());
        var full = ParameterSets.hevcSps(hevcSps(0, true, new int[] {1}, 3, 12, false));
        assertEquals(new ParameterSets.Shape(1, 12, 3), full);
        assertFalse(full.decodable());
    }

    @Test
    @DisplayName("an hvcC with no SPS array is refused")
    void hevcWithoutSps() {
        // The header and the VPS array of HVCC_MAIN, and nothing after.
        var record = Arrays.copyOf(HVCC_MAIN, 23 + 3 + 2 + 24);
        record[22] = 1;
        assertThrows(IllegalArgumentException.class, () -> ParameterSets.hevc(record));
    }

    /// An HEVC SPS as far as the reorder depths, with every sub-layer's profile
    /// and level present so that they are passed over.
    ///
    /// @param reorders one depth per layer written: all of them, or the highest
    public static byte[] hevcSps(
            int maxSubLayersMinus1,
            boolean everyLayer,
            int[] reorders,
            int chromaFormat,
            int bitDepth,
            boolean conformanceWindow) {
        var w = new BitWriter().bits(4, 0).bits(3, maxSubLayersMinus1).flag(true);
        w.bits(32, 0x0160_0000).bits(32, 0).bits(32, 0x5D); // general profile, tier and level
        for (var i = 0; i < maxSubLayersMinus1; i++) {
            w.flag(true).flag(true);
        }
        if (maxSubLayersMinus1 > 0) {
            for (var i = maxSubLayersMinus1; i < 8; i++) {
                w.bits(2, 0);
            }
        }
        for (var i = 0; i < maxSubLayersMinus1; i++) {
            w.bits(32, 0).bits(32, 0).bits(24, 0).bits(8, 0x5D);
        }
        w.ue(0).ue(chromaFormat);
        if (chromaFormat == 3) {
            w.flag(false);
        }
        w.ue(1920).ue(1080).flag(conformanceWindow);
        if (conformanceWindow) {
            w.ue(0).ue(0).ue(0).ue(4);
        }
        w.ue(bitDepth - 8).ue(bitDepth - 8).ue(4).flag(everyLayer);
        for (var reorder : reorders) {
            w.ue(4).ue(reorder).ue(0);
        }
        return w.finish();
    }

    /// An H.264 SPS of a profile without the High fields.
    ///
    /// @param maxReorder the VUI's `max_num_reorder_frames`, behind NAL HRD
    ///                   parameters, or null for no VUI
    public static byte[] h264Sps(
            int profile, int level, int pocType, int refFrames, int widthMbs, int heightMbs, Integer maxReorder) {
        var w = new BitWriter()
                .bits(8, profile)
                .bits(8, 0)
                .bits(8, level)
                .ue(0) // seq_parameter_set_id
                .ue(0) // log2_max_frame_num_minus4
                .ue(pocType);
        if (pocType == 0) {
            w.ue(2);
        }
        w.ue(refFrames).flag(false).ue(widthMbs - 1).ue(heightMbs - 1);
        w.flag(true); // frame_mbs_only_flag
        w.flag(true); // direct_8x8_inference_flag
        w.flag(false); // frame_cropping_flag
        if (maxReorder == null) {
            w.flag(false);
        } else {
            w.flag(true); // vui_parameters_present_flag
            w.flag(true).bits(8, 255).bits(16, 1).bits(16, 1); // Extended_SAR
            w.flag(false); // overscan
            w.flag(true).bits(3, 5).flag(false).flag(true).bits(24, 0x010101); // signal and colour
            w.flag(false); // chroma location
            w.flag(true).bits(32, 1).bits(32, 50).flag(true); // timing
            w.flag(true)
                    .ue(0)
                    .bits(4, 1)
                    .bits(4, 1)
                    .ue(1000)
                    .ue(2000)
                    .flag(false)
                    .bits(20, 0); // NAL HRD
            w.flag(false); // VCL HRD
            w.flag(false); // low_delay_hrd_flag
            w.flag(false); // pic_struct_present_flag
            w.flag(true).flag(true).ue(2).ue(1).ue(16).ue(16).ue(maxReorder).ue(4); // restriction
        }
        return w.finish();
    }

    /// A High-family H.264 SPS with a chroma format and bit depth, and scaling
    /// matrices so that they are passed over too.
    public static byte[] h264HighSps(int profile, int chromaFormat, int bitDepth) {
        return h264HighSps(profile, 0, chromaFormat, bitDepth);
    }

    /// The same, with `constraints` as the constraint_set flags byte.
    public static byte[] h264HighSps(int profile, int constraints, int chromaFormat, int bitDepth) {
        var w = new BitWriter()
                .bits(8, profile)
                .bits(8, constraints)
                .bits(8, 40)
                .ue(0);
        w.ue(chromaFormat);
        if (chromaFormat == 3) {
            w.flag(false);
        }
        w.ue(bitDepth - 8).ue(bitDepth - 8).flag(false);
        w.flag(true); // seq_scaling_matrix_present_flag
        for (var i = 0; i < (chromaFormat == 3 ? 12 : 8); i++) {
            w.flag(i % 2 == 0);
            if (i % 2 == 0) {
                // A list of deltas that ends early with a zero scale.
                w.se(3).se(-8).se(-3);
            }
        }
        w.ue(0)
                .ue(0)
                .ue(2)
                .ue(4)
                .flag(false)
                .ue(119)
                .ue(67)
                .flag(true)
                .flag(true)
                .flag(false)
                .flag(false);
        return w.finish();
    }

    /// `sps` in an `avcC` with a PPS, for the provider tests.
    public static byte[] avcC(byte[] sps) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {1, sps[0], sps[1], sps[2], (byte) 0xFF, (byte) 0xE1});
        out.write((sps.length + 1) >> 8);
        out.write(sps.length + 1);
        out.write(0x67);
        out.writeBytes(sps);
        out.writeBytes(new byte[] {1, 0, 4, 0x68, (byte) 0xCE, 0x38, (byte) 0x80});
        return out.toByteArray();
    }
}
