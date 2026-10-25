package dev.goldberry.media.bitstream.h264;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.goldberry.media.bitstream.BitReader;

/// A sequence parameter set (H.264 §7.3.2.1.1), every field a decoder and
/// Vulkan Video's `StdVideoH264SequenceParameterSet` read.
///
/// @param profileIdc                   `profile_idc`
/// @param constraints                  the six `constraint_set` flags, set0 at
///                                     bit 7
/// @param levelIdc                     `level_idc`
/// @param id                           `seq_parameter_set_id`
/// @param chromaFormatIdc              `chroma_format_idc`, 1 when not coded
/// @param separateColourPlane          `separate_colour_plane_flag`
/// @param bitDepthLumaMinus8           `bit_depth_luma_minus8`
/// @param bitDepthChromaMinus8         `bit_depth_chroma_minus8`
/// @param qpprimeYZeroTransformBypass  `qpprime_y_zero_transform_bypass_flag`
/// @param scalingLists                 the lists, when
///                                     `seq_scaling_matrix_present_flag`
/// @param log2MaxFrameNumMinus4        `log2_max_frame_num_minus4`
/// @param picOrderCntType              `pic_order_cnt_type`
/// @param log2MaxPocLsbMinus4          `log2_max_pic_order_cnt_lsb_minus4`
/// @param deltaPicOrderAlwaysZero      `delta_pic_order_always_zero_flag`
/// @param offsetForNonRefPic           `offset_for_non_ref_pic`
/// @param offsetForTopToBottomField    `offset_for_top_to_bottom_field`
/// @param offsetForRefFrame            `offset_for_ref_frame[i]`
/// @param maxNumRefFrames              `max_num_ref_frames`
/// @param gapsInFrameNumAllowed        `gaps_in_frame_num_value_allowed_flag`
/// @param picWidthInMbsMinus1          `pic_width_in_mbs_minus1`
/// @param picHeightInMapUnitsMinus1    `pic_height_in_map_units_minus1`
/// @param frameMbsOnly                 `frame_mbs_only_flag`
/// @param mbAdaptiveFrameField         `mb_adaptive_frame_field_flag`
/// @param direct8x8Inference           `direct_8x8_inference_flag`
/// @param frameCropping                `frame_cropping_flag`
/// @param cropLeft                     `frame_crop_left_offset`
/// @param cropRight                    `frame_crop_right_offset`
/// @param cropTop                      `frame_crop_top_offset`
/// @param cropBottom                   `frame_crop_bottom_offset`
/// @param vui                          `vui_parameters()`, when present
public record SequenceParameterSet(
        int profileIdc,
        int constraints,
        int levelIdc,
        int id,
        int chromaFormatIdc,
        boolean separateColourPlane,
        int bitDepthLumaMinus8,
        int bitDepthChromaMinus8,
        boolean qpprimeYZeroTransformBypass,
        Optional<ScalingLists> scalingLists,
        int log2MaxFrameNumMinus4,
        int picOrderCntType,
        int log2MaxPocLsbMinus4,
        boolean deltaPicOrderAlwaysZero,
        int offsetForNonRefPic,
        int offsetForTopToBottomField,
        List<Integer> offsetForRefFrame,
        int maxNumRefFrames,
        boolean gapsInFrameNumAllowed,
        int picWidthInMbsMinus1,
        int picHeightInMapUnitsMinus1,
        boolean frameMbsOnly,
        boolean mbAdaptiveFrameField,
        boolean direct8x8Inference,
        boolean frameCropping,
        int cropLeft,
        int cropRight,
        int cropTop,
        int cropBottom,
        Optional<Vui> vui) {

    /// The profiles whose SPS codes a chroma format, bit depths and scaling
    /// matrices (§7.3.2.1.1).
    private static final Set<Integer> HIGH_PROFILES =
            Set.of(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135);

    public SequenceParameterSet {
        offsetForRefFrame = List.copyOf(offsetForRefFrame);
    }

    /// The SPS whose RBSP, after the one-byte NAL header, is `rbsp`.
    ///
    /// @throws IllegalArgumentException when it is malformed
    public static SequenceParameterSet parse(byte[] rbsp) {
        var r = new BitReader(rbsp);
        var profile = r.bits(8);
        var constraints = r.bits(8);
        var level = r.bits(8);
        var id = r.ue();
        if (id > 31) {
            throw new IllegalArgumentException("seq_parameter_set_id " + id);
        }
        var chroma = 1;
        var separate = false;
        var lumaDepth = 0;
        var chromaDepth = 0;
        var bypass = false;
        Optional<ScalingLists> lists = Optional.empty();
        if (HIGH_PROFILES.contains(profile)) {
            chroma = r.ue();
            if (chroma > 3) {
                throw new IllegalArgumentException("chroma_format_idc " + chroma);
            }
            if (chroma == 3) {
                separate = r.flag();
            }
            lumaDepth = r.ue();
            chromaDepth = r.ue();
            bypass = r.flag();
            if (r.flag()) { // seq_scaling_matrix_present_flag
                lists = Optional.of(ScalingLists.read(r, chroma != 3 ? 8 : 12, null));
            }
        }
        var frameNum = r.ue();
        var pocType = r.ue();
        if (frameNum > 12 || pocType > 2) {
            throw new IllegalArgumentException(
                    "log2_max_frame_num_minus4 " + frameNum + ", pic_order_cnt_type " + pocType);
        }
        var pocLsb = 0;
        var alwaysZero = false;
        var nonRef = 0;
        var topToBottom = 0;
        var offsets = new ArrayList<Integer>();
        if (pocType == 0) {
            pocLsb = r.ue();
            if (pocLsb > 12) {
                throw new IllegalArgumentException("log2_max_pic_order_cnt_lsb_minus4 " + pocLsb);
            }
        } else if (pocType == 1) {
            alwaysZero = r.flag();
            nonRef = r.se();
            topToBottom = r.se();
            var cycle = r.ue();
            if (cycle > 255) {
                throw new IllegalArgumentException("num_ref_frames_in_pic_order_cnt_cycle " + cycle);
            }
            for (var i = 0; i < cycle; i++) {
                offsets.add(r.se());
            }
        }
        var refFrames = r.ue();
        var gaps = r.flag();
        var width = r.ue();
        var height = r.ue();
        var frameOnly = r.flag();
        var adaptive = !frameOnly && r.flag();
        var direct = r.flag();
        var cropping = r.flag();
        var left = 0;
        var right = 0;
        var top = 0;
        var bottom = 0;
        if (cropping) {
            left = r.ue();
            right = r.ue();
            top = r.ue();
            bottom = r.ue();
        }
        Optional<Vui> vui = r.flag() ? Optional.of(Vui.read(r)) : Optional.empty();
        return new SequenceParameterSet(
                profile,
                constraints,
                level,
                id,
                chroma,
                separate,
                lumaDepth,
                chromaDepth,
                bypass,
                lists,
                frameNum,
                pocType,
                pocLsb,
                alwaysZero,
                nonRef,
                topToBottom,
                offsets,
                refFrames,
                gaps,
                width,
                height,
                frameOnly,
                adaptive,
                direct,
                cropping,
                left,
                right,
                top,
                bottom,
                vui);
    }

    /// `ChromaArrayType`.
    public int chromaArrayType() {
        return separateColourPlane ? 0 : chromaFormatIdc;
    }

    /// `MaxFrameNum`.
    public int maxFrameNum() {
        return 1 << (log2MaxFrameNumMinus4 + 4);
    }

    /// `MaxPicOrderCntLsb`.
    public int maxPocLsb() {
        return 1 << (log2MaxPocLsbMinus4 + 4);
    }

    /// The coded width in luma samples.
    public int codedWidth() {
        return (picWidthInMbsMinus1 + 1) * 16;
    }

    /// The coded height in luma samples, of a frame.
    public int codedHeight() {
        return (picHeightInMapUnitsMinus1 + 1) * 16 * (frameMbsOnly ? 1 : 2);
    }

    /// The crop's units across and down (§7.4.2.1.1): `CropUnitX` and
    /// `CropUnitY`.
    private int cropUnitX() {
        return chromaArrayType() == 0 ? 1 : chromaFormatIdc == 3 ? 1 : 2;
    }

    private int cropUnitY() {
        var frame = frameMbsOnly ? 1 : 2;
        return chromaArrayType() == 0 ? frame : (chromaFormatIdc == 1 ? 2 : 1) * frame;
    }

    /// The visible region's left edge.
    public int cropX() {
        return cropUnitX() * cropLeft;
    }

    /// The visible region's top edge.
    public int cropY() {
        return cropUnitY() * cropTop;
    }

    /// The visible width.
    public int visibleWidth() {
        return codedWidth() - cropUnitX() * (cropLeft + cropRight);
    }

    /// The visible height.
    public int visibleHeight() {
        return codedHeight() - cropUnitY() * (cropTop + cropBottom);
    }

    /// The scaling lists in force for the sequence: its own, or flat.
    public ScalingLists sequenceLists() {
        return scalingLists.orElseGet(ScalingLists::flat);
    }
}
