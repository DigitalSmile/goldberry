package io.github.digitalsmile.goldberry.media.platform.macos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// Reads what a VideoToolbox decoder has to know before its first picture from
/// a track's decoder configuration record: MP4's `avcC` and `hvcC`, which
/// Matroska carries unchanged as `CodecPrivate`.
///
/// The parameter sets themselves, which the format description is built from
/// (`CMVideoFormatDescriptionCreateFrom…ParameterSets` parses them, VUI colour
/// included, where a description made from the raw record leaves the colour to a
/// guess), the length of the NAL unit prefixes, and three things from the
/// sequence parameter set:
///
/// - **the reorder depth**: how many pictures can leave the decoder before one
///   that is shown ahead of them. VideoToolbox hands pictures over in decoding
///   order, so the provider holds this many back and releases them by
///   presentation time. It is what FFmpeg calls `has_b_frames`, derived the way
///   FFmpeg derives it;
/// - **the bit depth**, which picks NV12 or P010;
/// - **the chroma format**, since the frame contract is 4:2:0 only.
///
/// Only the fields up to those are parsed. Everything is bounds-checked, and a
/// malformed record is [IllegalArgumentException].
final class ParameterSets {

    /// The most pictures either codec can hold back: H.264's and HEVC's largest
    /// decoded picture buffer. The depth used when nothing says less.
    static final int MAX_REORDER = 16;

    private static final int H264_SPS_HEADER = 1;
    private static final int HEVC_NAL_VPS = 32;
    private static final int HEVC_NAL_SPS = 33;
    private static final int HEVC_NAL_PPS = 34;
    private static final int HEVC_NAL_HEADER = 2;

    /// The H.264 profiles whose SPS carries a chroma format, bit depths and
    /// scaling matrices (ITU-T H.264 §7.3.2.1.1).
    private static final Set<Integer> H264_HIGH_PROFILES =
            Set.of(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135);

    /// The profiles that are intra-only when constraint_set3 is set (§A.2.8 to
    /// §A.2.11): no picture predicts from another, so none waits for another.
    private static final Set<Integer> H264_INTRA_PROFILES = Set.of(44, 86, 100, 110, 122, 244);

    private ParameterSets() {}

    /// What the parameter sets say.
    ///
    /// @param reorderDepth how many pictures to hold back, 0 to [#MAX_REORDER]
    /// @param bitDepth     the luma bit depth
    /// @param chromaFormat `chroma_format_idc`: 0 monochrome, 1 4:2:0, 2 4:2:2,
    ///                     3 4:4:4
    record Shape(int reorderDepth, int bitDepth, int chromaFormat) {

        Shape {
            if (reorderDepth < 0 || reorderDepth > MAX_REORDER) {
                throw new IllegalArgumentException("a reorder depth of " + reorderDepth);
            }
        }

        /// Whether the provider's decoder hands this stream over as the frame
        /// contract asks: 8- or 10-bit, 4:2:0 or monochrome.
        boolean decodable() {
            return (bitDepth == 8 || bitDepth == 10) && (chromaFormat == 0 || chromaFormat == 1);
        }
    }

    /// A decoder configuration record, read.
    ///
    /// @param parameterSets    the NAL units a format description is made of, in
    ///                         the order VideoToolbox wants them: HEVC's VPS, then
    ///                         SPS, then PPS; H.264's SPS, then PPS
    /// @param nalLengthSize    the bytes of the length before each NAL unit in a
    ///                         packet: 1, 2 or 4
    /// @param shape            what the first SPS says
    record Configuration(List<byte[]> parameterSets, int nalLengthSize, Shape shape) {

        Configuration {
            parameterSets = List.copyOf(parameterSets);
            if (parameterSets.isEmpty()) {
                throw new IllegalArgumentException("a configuration with no parameter sets");
            }
            if (nalLengthSize != 1 && nalLengthSize != 2 && nalLengthSize != 4) {
                throw new IllegalArgumentException("NAL units prefixed by " + nalLengthSize + " bytes");
            }
            Objects.requireNonNull(shape, "shape");
        }
    }

    /// An H.264 stream's `avcC` (ISO/IEC 14496-15 §5.3.3.1).
    static Configuration h264(byte[] avcC) {
        if (avcC.length < 7 || avcC[0] != 1) {
            throw new IllegalArgumentException("not an avcC record");
        }
        var sets = new ArrayList<byte[]>();
        var at = 5;
        var spsCount = avcC[at++] & 0x1F;
        if (spsCount == 0) {
            throw new IllegalArgumentException("an avcC record with no SPS");
        }
        at = collect(avcC, at, spsCount, sets);
        require(avcC, at, 1);
        var ppsCount = avcC[at++] & 0xFF;
        collect(avcC, at, ppsCount, sets);
        var sps = sets.getFirst();
        if (sps.length <= H264_SPS_HEADER) {
            throw new IllegalArgumentException("an avcC record whose SPS is " + sps.length + " bytes");
        }
        var shape = h264Sps(BitReader.unescape(sps, H264_SPS_HEADER, sps.length - H264_SPS_HEADER));
        return new Configuration(sets, (avcC[4] & 0x03) + 1, shape);
    }

    /// The shape of an H.264 stream, from the payload of its SPS
    /// (ITU-T H.264 §7.3.2.1.1).
    static Shape h264Sps(byte[] rbsp) {
        var r = new BitReader(rbsp);
        var profile = r.bits(8);
        var constraints = r.bits(8);
        var level = r.bits(8);
        r.ue(); // seq_parameter_set_id
        var chromaFormat = 1;
        var bitDepth = 8;
        if (H264_HIGH_PROFILES.contains(profile)) {
            chromaFormat = r.ue();
            if (chromaFormat == 3) {
                r.skip(1); // separate_colour_plane_flag
            }
            bitDepth = r.ue() + 8;
            r.ue(); // bit_depth_chroma_minus8
            r.skip(1); // qpprime_y_zero_transform_bypass_flag
            if (r.flag()) { // seq_scaling_matrix_present_flag
                var lists = chromaFormat == 3 ? 12 : 8;
                for (var i = 0; i < lists; i++) {
                    if (r.flag()) {
                        skipScalingList(r, i < 6 ? 16 : 64);
                    }
                }
            }
        }
        r.ue(); // log2_max_frame_num_minus4
        var pocType = r.ue();
        if (pocType == 0) {
            r.ue(); // log2_max_pic_order_cnt_lsb_minus4
        } else if (pocType == 1) {
            r.skip(1); // delta_pic_order_always_zero_flag
            r.se(); // offset_for_non_ref_pic
            r.se(); // offset_for_top_to_bottom_field
            var cycle = r.ue();
            for (var i = 0; i < cycle; i++) {
                r.se(); // offset_for_ref_frame[i]
            }
        }
        var maxRefFrames = r.ue();
        r.skip(1); // gaps_in_frame_num_value_allowed_flag
        var widthMbs = r.ue() + 1;
        var heightMapUnits = r.ue() + 1;
        var frameMbsOnly = r.flag();
        if (!frameMbsOnly) {
            r.skip(1); // mb_adaptive_frame_field_flag
        }
        r.skip(1); // direct_8x8_inference_flag
        if (r.flag()) { // frame_cropping_flag
            r.ue();
            r.ue();
            r.ue();
            r.ue();
        }
        var heightMbs = (frameMbsOnly ? 1 : 2) * heightMapUnits;

        var restricted = OptionalInt.empty();
        if (r.flag()) { // vui_parameters_present_flag
            try {
                restricted = h264MaxReorder(r);
            } catch (IllegalArgumentException e) {
                // A VUI cut short, which some encoders write: fall back to the
                // level, as if it said nothing.
                restricted = OptionalInt.empty();
            }
        }

        int depth;
        if (restricted.isPresent()) {
            depth = restricted.getAsInt();
        } else if (pocType == 2 || maxRefFrames == 0) {
            // Picture order type 2 is output order equal to decoding order
            // (§8.2.1.3); no reference pictures means nothing to wait for.
            depth = 0;
        } else if (H264_INTRA_PROFILES.contains(profile) && (constraints & 0x10) != 0) {
            depth = 0;
        } else {
            // The decoded picture buffer the level allows at this size (§A.3.1,
            // Table A-1), as FFmpeg does when the SPS does not say.
            depth = maxDpbFrames(level, (constraints & 0x10) != 0, (long) widthMbs * heightMbs);
        }
        return new Shape(Math.clamp(depth, 0, MAX_REORDER), bitDepth, chromaFormat);
    }

    /// An HEVC stream's `hvcC` (ISO/IEC 14496-15 §8.3.3.1).
    static Configuration hevc(byte[] hvcC) {
        if (hvcC.length < 23 || hvcC[0] != 1) {
            throw new IllegalArgumentException("not an hvcC record");
        }
        var byType = new ArrayList<List<byte[]>>();
        for (var i = 0; i < 3; i++) {
            byType.add(new ArrayList<>());
        }
        var arrays = hvcC[22] & 0xFF;
        var at = 23;
        for (var array = 0; array < arrays; array++) {
            require(hvcC, at, 3);
            var type = hvcC[at] & 0x3F;
            var count = u16(hvcC, at + 1);
            at += 3;
            var units = type >= HEVC_NAL_VPS && type <= HEVC_NAL_PPS ? byType.get(type - HEVC_NAL_VPS) : null;
            at = collect(hvcC, at, count, units);
        }
        var spss = byType.get(HEVC_NAL_SPS - HEVC_NAL_VPS);
        if (spss.isEmpty() || spss.getFirst().length <= HEVC_NAL_HEADER) {
            throw new IllegalArgumentException("an hvcC record with no SPS");
        }
        var sps = spss.getFirst();
        var shape = hevcSps(BitReader.unescape(sps, HEVC_NAL_HEADER, sps.length - HEVC_NAL_HEADER));
        var sets = new ArrayList<byte[]>();
        byType.forEach(sets::addAll);
        return new Configuration(sets, (hvcC[21] & 0x03) + 1, shape);
    }

    /// The shape of an HEVC stream, from the payload of its SPS
    /// (ITU-T H.265 §7.3.2.2.1).
    static Shape hevcSps(byte[] rbsp) {
        var r = new BitReader(rbsp);
        r.skip(4); // sps_video_parameter_set_id
        var maxSubLayersMinus1 = r.bits(3);
        r.skip(1); // sps_temporal_id_nesting_flag
        skipProfileTierLevel(r, maxSubLayersMinus1);
        r.ue(); // sps_seq_parameter_set_id
        var chromaFormat = r.ue();
        if (chromaFormat == 3) {
            r.skip(1); // separate_colour_plane_flag
        }
        r.ue(); // pic_width_in_luma_samples
        r.ue(); // pic_height_in_luma_samples
        if (r.flag()) { // conformance_window_flag
            r.ue();
            r.ue();
            r.ue();
            r.ue();
        }
        var bitDepth = r.ue() + 8;
        r.ue(); // bit_depth_chroma_minus8
        r.ue(); // log2_max_pic_order_cnt_lsb_minus4
        var everyLayer = r.flag(); // sps_sub_layer_ordering_info_present_flag
        var reorder = 0;
        // The last entry is the highest temporal layer's, which a player decodes.
        for (var i = everyLayer ? 0 : maxSubLayersMinus1; i <= maxSubLayersMinus1; i++) {
            r.ue(); // sps_max_dec_pic_buffering_minus1
            reorder = r.ue(); // sps_max_num_reorder_pics
            r.ue(); // sps_max_latency_increase_plus1
        }
        return new Shape(Math.clamp(reorder, 0, MAX_REORDER), bitDepth, chromaFormat);
    }

    /// `max_num_reorder_frames` from an H.264 VUI (§E.1.1), or empty when the VUI
    /// has no bitstream restriction.
    private static OptionalInt h264MaxReorder(BitReader r) {
        if (r.flag()) { // aspect_ratio_info_present_flag
            if (r.bits(8) == 255) { // Extended_SAR
                r.skip(32);
            }
        }
        if (r.flag()) { // overscan_info_present_flag
            r.skip(1);
        }
        if (r.flag()) { // video_signal_type_present_flag
            r.skip(4); // video_format, video_full_range_flag
            if (r.flag()) { // colour_description_present_flag
                r.skip(24);
            }
        }
        if (r.flag()) { // chroma_loc_info_present_flag
            r.ue();
            r.ue();
        }
        if (r.flag()) { // timing_info_present_flag
            r.skip(65);
        }
        var nalHrd = r.flag();
        if (nalHrd) {
            skipHrd(r);
        }
        var vclHrd = r.flag();
        if (vclHrd) {
            skipHrd(r);
        }
        if (nalHrd || vclHrd) {
            r.skip(1); // low_delay_hrd_flag
        }
        r.skip(1); // pic_struct_present_flag
        if (!r.flag()) { // bitstream_restriction_flag
            return OptionalInt.empty();
        }
        r.skip(1); // motion_vectors_over_pic_boundaries_flag
        r.ue(); // max_bytes_per_pic_denom
        r.ue(); // max_bits_per_mb_denom
        r.ue(); // log2_max_mv_length_horizontal
        r.ue(); // log2_max_mv_length_vertical
        return OptionalInt.of(r.ue()); // max_num_reorder_frames
    }

    /// Passes over `hrd_parameters()` (§E.1.2).
    private static void skipHrd(BitReader r) {
        var count = r.ue() + 1; // cpb_cnt_minus1
        r.skip(8); // bit_rate_scale, cpb_size_scale
        for (var i = 0; i < count; i++) {
            r.ue(); // bit_rate_value_minus1
            r.ue(); // cpb_size_value_minus1
            r.skip(1); // cbr_flag
        }
        r.skip(20); // four delay lengths of five bits
    }

    /// Passes over one `scaling_list()` (§7.3.2.1.1.1).
    private static void skipScalingList(BitReader r, int size) {
        var last = 8;
        var next = 8;
        for (var j = 0; j < size; j++) {
            if (next != 0) {
                next = (last + r.se() + 256) % 256;
            }
            last = next == 0 ? last : next;
        }
    }

    /// Passes over `profile_tier_level(1, maxSubLayersMinus1)` (H.265 §7.3.3).
    private static void skipProfileTierLevel(BitReader r, int maxSubLayersMinus1) {
        // The general profile, 88 bits, and general_level_idc.
        r.skip(96);
        var profilePresent = new boolean[maxSubLayersMinus1];
        var levelPresent = new boolean[maxSubLayersMinus1];
        for (var i = 0; i < maxSubLayersMinus1; i++) {
            profilePresent[i] = r.flag();
            levelPresent[i] = r.flag();
        }
        if (maxSubLayersMinus1 > 0) {
            r.skip(2L * (8 - maxSubLayersMinus1)); // reserved_zero_2bits
        }
        for (var i = 0; i < maxSubLayersMinus1; i++) {
            if (profilePresent[i]) {
                r.skip(88);
            }
            if (levelPresent[i]) {
                r.skip(8);
            }
        }
    }

    /// The frames the decoded picture buffer holds at `level` for a picture of
    /// `macroblocks` macroblocks: MaxDpbMbs of Table A-1 over the picture's size,
    /// at most [#MAX_REORDER]. An unknown level allows the most.
    static int maxDpbFrames(int level, boolean constraintSet3, long macroblocks) {
        var maxDpbMbs =
                switch (level) {
                    case 9, 10 -> 396;
                    // Level 1b is level_idc 11 with constraint_set3 in the Baseline,
                    // Main and Extended profiles.
                    case 11 -> constraintSet3 ? 396 : 900;
                    case 12, 13, 20 -> 2376;
                    case 21 -> 4752;
                    case 22, 30 -> 8100;
                    case 31 -> 18_000;
                    case 32 -> 20_480;
                    case 40, 41 -> 32_768;
                    case 42 -> 34_816;
                    case 50 -> 110_400;
                    case 51, 52 -> 184_320;
                    case 60, 61, 62 -> 696_320;
                    default -> -1;
                };
        if (maxDpbMbs < 0 || macroblocks <= 0) {
            return MAX_REORDER;
        }
        return (int) Math.min(maxDpbMbs / macroblocks, MAX_REORDER);
    }

    /// Reads `count` NAL units, each after a two-byte length, from `at`, into
    /// `units` (or past them, for null). Answers where the next field starts.
    private static int collect(byte[] record, int at, int count, @Nullable List<byte[]> units) {
        var position = at;
        for (var unit = 0; unit < count; unit++) {
            var length = u16(record, position);
            position += 2;
            require(record, position, length);
            if (units != null) {
                units.add(Arrays.copyOfRange(record, position, position + length));
            }
            position += length;
        }
        return position;
    }

    private static int u16(byte[] data, int at) {
        require(data, at, 2);
        return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
    }

    private static void require(byte[] data, int at, int length) {
        if (at < 0 || length < 0 || at + length > data.length) {
            throw new IllegalArgumentException(
                    "the record ends at byte " + data.length + ", before " + length + " bytes at " + at);
        }
    }
}
