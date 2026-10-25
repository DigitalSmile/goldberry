package dev.goldberry.media.platform.vulkan.h264;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Collection;

import dev.goldberry.media.bitstream.h264.Hrd;
import dev.goldberry.media.bitstream.h264.PictureParameterSet;
import dev.goldberry.media.bitstream.h264.ScalingLists;
import dev.goldberry.media.bitstream.h264.SequenceParameterSet;
import dev.goldberry.media.bitstream.h264.Vui;
import dev.goldberry.media.platform.vulkan.ffm.Structs;
import dev.goldberry.media.platform.vulkan.ffm.VkConstants;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoH264PictureParameterSet;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoH264PpsFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoH264SequenceParameterSet;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoH264SequenceParameterSetVui;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoH264SpsFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoH264SpsVuiFlags;

/// H.264's parameter sets as Vulkan Video's `StdVideoH264*` structs, and the
/// session parameters create info that carries them.
final class H264Parameters {

    private H264Parameters() {}

    /// `VkVideoDecodeH264SessionParametersCreateInfoKHR` holding every SPS
    /// and PPS of `spss` and `ppss`.
    static MemorySegment createInfo(
            Arena arena, Collection<SequenceParameterSet> spss, Collection<PictureParameterSet> ppss) {
        var spsLayout = StdVideoH264SequenceParameterSet.LAYOUT;
        var spsArray = arena.allocate(spsLayout, Math.max(spss.size(), 1));
        var i = 0;
        for (var sps : spss) {
            sps(arena, Structs.at(spsArray, spsLayout, i++), sps);
        }
        var ppsLayout = StdVideoH264PictureParameterSet.LAYOUT;
        var ppsArray = arena.allocate(ppsLayout, Math.max(ppss.size(), 1));
        i = 0;
        for (var pps : ppss) {
            pps(arena, Structs.at(ppsArray, ppsLayout, i++), pps);
        }
        var add = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeH264SessionParametersAddInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_SESSION_PARAMETERS_ADD_INFO_KHR);
        add.set(JAVA_INT, VkStructs.VkVideoDecodeH264SessionParametersAddInfoKHR.stdSPSCount, spss.size());
        add.set(ADDRESS, VkStructs.VkVideoDecodeH264SessionParametersAddInfoKHR.pStdSPSs, spsArray);
        add.set(JAVA_INT, VkStructs.VkVideoDecodeH264SessionParametersAddInfoKHR.stdPPSCount, ppss.size());
        add.set(ADDRESS, VkStructs.VkVideoDecodeH264SessionParametersAddInfoKHR.pStdPPSs, ppsArray);
        var info = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeH264SessionParametersCreateInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_SESSION_PARAMETERS_CREATE_INFO_KHR);
        info.set(JAVA_INT, VkStructs.VkVideoDecodeH264SessionParametersCreateInfoKHR.maxStdSPSCount, spss.size());
        info.set(JAVA_INT, VkStructs.VkVideoDecodeH264SessionParametersCreateInfoKHR.maxStdPPSCount, ppss.size());
        info.set(ADDRESS, VkStructs.VkVideoDecodeH264SessionParametersCreateInfoKHR.pParametersAddInfo, add);
        return info;
    }

    /// The `StdVideoH264LevelIdc` of `level_idc`.
    static int level(int levelIdc, boolean constraintSet3) {
        return switch (levelIdc) {
            // Level 1b, by level_idc 9 or by 11 with constraint_set3, has no
            // value of its own and decodes within 1.1.
            case 10 ->
                constraintSet3 ? VkConstants.STD_VIDEO_H264_LEVEL_IDC_1_1 : VkConstants.STD_VIDEO_H264_LEVEL_IDC_1_0;
            case 9, 11 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_1_1;
            case 12 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_1_2;
            case 13 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_1_3;
            case 20 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_2_0;
            case 21 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_2_1;
            case 22 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_2_2;
            case 30 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_3_0;
            case 31 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_3_1;
            case 32 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_3_2;
            case 40 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_4_0;
            case 41 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_4_1;
            case 42 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_4_2;
            case 50 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_5_0;
            case 51 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_5_1;
            case 52 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_5_2;
            case 60 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_6_0;
            case 61 -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_6_1;
            default -> VkConstants.STD_VIDEO_H264_LEVEL_IDC_6_2;
        };
    }

    private static void sps(Arena arena, MemorySegment s, SequenceParameterSet sps) {
        var flags = StdVideoH264SequenceParameterSet.flags;
        var c = sps.constraints();
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_constraint_set0_flag, (c & 0x80) != 0);
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_constraint_set1_flag, (c & 0x40) != 0);
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_constraint_set2_flag, (c & 0x20) != 0);
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_constraint_set3_flag, (c & 0x10) != 0);
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_constraint_set4_flag, (c & 0x08) != 0);
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_constraint_set5_flag, (c & 0x04) != 0);
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_direct_8x8_inference_flag, sps.direct8x8Inference());
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_mb_adaptive_frame_field_flag, sps.mbAdaptiveFrameField());
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_frame_mbs_only_flag, sps.frameMbsOnly());
        Structs.flag(
                s, flags, StdVideoH264SpsFlags.MASK_delta_pic_order_always_zero_flag, sps.deltaPicOrderAlwaysZero());
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_separate_colour_plane_flag, sps.separateColourPlane());
        Structs.flag(
                s, flags, StdVideoH264SpsFlags.MASK_gaps_in_frame_num_value_allowed_flag, sps.gapsInFrameNumAllowed());
        Structs.flag(
                s,
                flags,
                StdVideoH264SpsFlags.MASK_qpprime_y_zero_transform_bypass_flag,
                sps.qpprimeYZeroTransformBypass());
        Structs.flag(s, flags, StdVideoH264SpsFlags.MASK_frame_cropping_flag, sps.frameCropping());
        Structs.flag(
                s,
                flags,
                StdVideoH264SpsFlags.MASK_seq_scaling_matrix_present_flag,
                sps.scalingLists().isPresent());
        Structs.flag(
                s,
                flags,
                StdVideoH264SpsFlags.MASK_vui_parameters_present_flag,
                sps.vui().isPresent());
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.profile_idc, sps.profileIdc());
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.level_idc, level(sps.levelIdc(), (c & 0x10) != 0));
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.chroma_format_idc, sps.chromaFormatIdc());
        s.set(JAVA_BYTE, StdVideoH264SequenceParameterSet.seq_parameter_set_id, (byte) sps.id());
        s.set(JAVA_BYTE, StdVideoH264SequenceParameterSet.bit_depth_luma_minus8, (byte) sps.bitDepthLumaMinus8());
        s.set(JAVA_BYTE, StdVideoH264SequenceParameterSet.bit_depth_chroma_minus8, (byte) sps.bitDepthChromaMinus8());
        s.set(JAVA_BYTE, StdVideoH264SequenceParameterSet.log2_max_frame_num_minus4, (byte)
                sps.log2MaxFrameNumMinus4());
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.pic_order_cnt_type, sps.picOrderCntType());
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.offset_for_non_ref_pic, sps.offsetForNonRefPic());
        s.set(
                JAVA_INT,
                StdVideoH264SequenceParameterSet.offset_for_top_to_bottom_field,
                sps.offsetForTopToBottomField());
        s.set(JAVA_BYTE, StdVideoH264SequenceParameterSet.log2_max_pic_order_cnt_lsb_minus4, (byte)
                sps.log2MaxPocLsbMinus4());
        var cycle = sps.offsetForRefFrame();
        s.set(JAVA_BYTE, StdVideoH264SequenceParameterSet.num_ref_frames_in_pic_order_cnt_cycle, (byte) cycle.size());
        s.set(JAVA_BYTE, StdVideoH264SequenceParameterSet.max_num_ref_frames, (byte) sps.maxNumRefFrames());
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.pic_width_in_mbs_minus1, sps.picWidthInMbsMinus1());
        s.set(
                JAVA_INT,
                StdVideoH264SequenceParameterSet.pic_height_in_map_units_minus1,
                sps.picHeightInMapUnitsMinus1());
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.frame_crop_left_offset, sps.cropLeft());
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.frame_crop_right_offset, sps.cropRight());
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.frame_crop_top_offset, sps.cropTop());
        s.set(JAVA_INT, StdVideoH264SequenceParameterSet.frame_crop_bottom_offset, sps.cropBottom());
        if (!cycle.isEmpty()) {
            var offsets = arena.allocate(JAVA_INT, cycle.size());
            for (var i = 0; i < cycle.size(); i++) {
                offsets.setAtIndex(JAVA_INT, i, cycle.get(i));
            }
            s.set(ADDRESS, StdVideoH264SequenceParameterSet.pOffsetForRefFrame, offsets);
        }
        sps.scalingLists()
                .ifPresent(lists ->
                        s.set(ADDRESS, StdVideoH264SequenceParameterSet.pScalingLists, scalingLists(arena, lists)));
        sps.vui()
                .ifPresent(vui ->
                        s.set(ADDRESS, StdVideoH264SequenceParameterSet.pSequenceParameterSetVui, vui(arena, vui)));
    }

    private static void pps(Arena arena, MemorySegment p, PictureParameterSet pps) {
        var flags = StdVideoH264PictureParameterSet.flags;
        Structs.flag(p, flags, StdVideoH264PpsFlags.MASK_transform_8x8_mode_flag, pps.transform8x8Mode());
        Structs.flag(p, flags, StdVideoH264PpsFlags.MASK_redundant_pic_cnt_present_flag, pps.redundantPicCntPresent());
        Structs.flag(p, flags, StdVideoH264PpsFlags.MASK_constrained_intra_pred_flag, pps.constrainedIntraPred());
        Structs.flag(
                p,
                flags,
                StdVideoH264PpsFlags.MASK_deblocking_filter_control_present_flag,
                pps.deblockingFilterControlPresent());
        Structs.flag(p, flags, StdVideoH264PpsFlags.MASK_weighted_pred_flag, pps.weightedPred());
        Structs.flag(
                p,
                flags,
                StdVideoH264PpsFlags.MASK_bottom_field_pic_order_in_frame_present_flag,
                pps.bottomFieldPicOrderInFramePresent());
        Structs.flag(p, flags, StdVideoH264PpsFlags.MASK_entropy_coding_mode_flag, pps.entropyCodingMode());
        Structs.flag(
                p,
                flags,
                StdVideoH264PpsFlags.MASK_pic_scaling_matrix_present_flag,
                pps.scalingLists().isPresent());
        p.set(JAVA_BYTE, StdVideoH264PictureParameterSet.seq_parameter_set_id, (byte) pps.spsId());
        p.set(JAVA_BYTE, StdVideoH264PictureParameterSet.pic_parameter_set_id, (byte) pps.id());
        p.set(JAVA_BYTE, StdVideoH264PictureParameterSet.num_ref_idx_l0_default_active_minus1, (byte)
                pps.numRefIdxL0DefaultActiveMinus1());
        p.set(JAVA_BYTE, StdVideoH264PictureParameterSet.num_ref_idx_l1_default_active_minus1, (byte)
                pps.numRefIdxL1DefaultActiveMinus1());
        p.set(JAVA_INT, StdVideoH264PictureParameterSet.weighted_bipred_idc, pps.weightedBipredIdc());
        p.set(JAVA_BYTE, StdVideoH264PictureParameterSet.pic_init_qp_minus26, (byte) pps.picInitQpMinus26());
        p.set(JAVA_BYTE, StdVideoH264PictureParameterSet.pic_init_qs_minus26, (byte) pps.picInitQsMinus26());
        p.set(JAVA_BYTE, StdVideoH264PictureParameterSet.chroma_qp_index_offset, (byte) pps.chromaQpIndexOffset());
        p.set(JAVA_BYTE, StdVideoH264PictureParameterSet.second_chroma_qp_index_offset, (byte)
                pps.secondChromaQpIndexOffset());
        pps.scalingLists()
                .ifPresent(lists ->
                        p.set(ADDRESS, StdVideoH264PictureParameterSet.pScalingLists, scalingLists(arena, lists)));
    }

    private static MemorySegment scalingLists(Arena arena, ScalingLists lists) {
        var s = Structs.allocate(arena, VkStructs.StdVideoH264ScalingLists.LAYOUT);
        // The lists go over resolved, the fall-back rules applied: none uses
        // the default by the mask's say.
        s.set(JAVA_SHORT, VkStructs.StdVideoH264ScalingLists.scaling_list_present_mask, (short) lists.present());
        for (var i = 0; i < 6; i++) {
            for (var j = 0; j < 16; j++) {
                s.set(JAVA_BYTE, VkStructs.StdVideoH264ScalingLists.ScalingList4x4 + i * 16L + j, (byte)
                        lists.lists4x4()[i][j]);
            }
            for (var j = 0; j < 64; j++) {
                s.set(JAVA_BYTE, VkStructs.StdVideoH264ScalingLists.ScalingList8x8 + i * 64L + j, (byte)
                        lists.lists8x8()[i][j]);
            }
        }
        return s;
    }

    private static MemorySegment vui(Arena arena, Vui vui) {
        var v = Structs.allocate(arena, StdVideoH264SequenceParameterSetVui.LAYOUT);
        var flags = StdVideoH264SequenceParameterSetVui.flags;
        Structs.flag(
                v, flags, StdVideoH264SpsVuiFlags.MASK_aspect_ratio_info_present_flag, vui.aspectRatioInfoPresent());
        Structs.flag(v, flags, StdVideoH264SpsVuiFlags.MASK_overscan_info_present_flag, vui.overscanInfoPresent());
        Structs.flag(v, flags, StdVideoH264SpsVuiFlags.MASK_overscan_appropriate_flag, vui.overscanAppropriate());
        Structs.flag(
                v, flags, StdVideoH264SpsVuiFlags.MASK_video_signal_type_present_flag, vui.videoSignalTypePresent());
        Structs.flag(v, flags, StdVideoH264SpsVuiFlags.MASK_video_full_range_flag, vui.fullRange());
        Structs.flag(
                v, flags, StdVideoH264SpsVuiFlags.MASK_color_description_present_flag, vui.colourDescriptionPresent());
        Structs.flag(v, flags, StdVideoH264SpsVuiFlags.MASK_chroma_loc_info_present_flag, vui.chromaLocInfoPresent());
        Structs.flag(v, flags, StdVideoH264SpsVuiFlags.MASK_timing_info_present_flag, vui.timingInfoPresent());
        Structs.flag(v, flags, StdVideoH264SpsVuiFlags.MASK_fixed_frame_rate_flag, vui.fixedFrameRate());
        Structs.flag(v, flags, StdVideoH264SpsVuiFlags.MASK_bitstream_restriction_flag, vui.bitstreamRestriction());
        Structs.flag(
                v,
                flags,
                StdVideoH264SpsVuiFlags.MASK_nal_hrd_parameters_present_flag,
                vui.nalHrd().isPresent());
        Structs.flag(
                v,
                flags,
                StdVideoH264SpsVuiFlags.MASK_vcl_hrd_parameters_present_flag,
                vui.vclHrd().isPresent());
        v.set(JAVA_INT, StdVideoH264SequenceParameterSetVui.aspect_ratio_idc, vui.aspectRatioIdc());
        v.set(JAVA_SHORT, StdVideoH264SequenceParameterSetVui.sar_width, (short) vui.sarWidth());
        v.set(JAVA_SHORT, StdVideoH264SequenceParameterSetVui.sar_height, (short) vui.sarHeight());
        v.set(JAVA_BYTE, StdVideoH264SequenceParameterSetVui.video_format, (byte) vui.videoFormat());
        v.set(JAVA_BYTE, StdVideoH264SequenceParameterSetVui.colour_primaries, (byte) vui.colourPrimaries());
        v.set(JAVA_BYTE, StdVideoH264SequenceParameterSetVui.transfer_characteristics, (byte)
                vui.transferCharacteristics());
        v.set(JAVA_BYTE, StdVideoH264SequenceParameterSetVui.matrix_coefficients, (byte) vui.matrixCoefficients());
        v.set(JAVA_INT, StdVideoH264SequenceParameterSetVui.num_units_in_tick, (int) vui.numUnitsInTick());
        v.set(JAVA_INT, StdVideoH264SequenceParameterSetVui.time_scale, (int) vui.timeScale());
        v.set(JAVA_BYTE, StdVideoH264SequenceParameterSetVui.max_num_reorder_frames, (byte)
                Math.max(vui.maxNumReorderFrames(), 0));
        v.set(JAVA_BYTE, StdVideoH264SequenceParameterSetVui.max_dec_frame_buffering, (byte)
                Math.max(vui.maxDecFrameBuffering(), 0));
        v.set(JAVA_BYTE, StdVideoH264SequenceParameterSetVui.chroma_sample_loc_type_top_field, (byte)
                vui.chromaSampleLocTypeTop());
        v.set(JAVA_BYTE, StdVideoH264SequenceParameterSetVui.chroma_sample_loc_type_bottom_field, (byte)
                vui.chromaSampleLocTypeBottom());
        vui.nalHrd()
                .or(vui::vclHrd)
                .ifPresent(hrd -> v.set(ADDRESS, StdVideoH264SequenceParameterSetVui.pHrdParameters, hrd(arena, hrd)));
        return v;
    }

    private static MemorySegment hrd(Arena arena, Hrd hrd) {
        var h = Structs.allocate(arena, VkStructs.StdVideoH264HrdParameters.LAYOUT);
        h.set(JAVA_BYTE, VkStructs.StdVideoH264HrdParameters.cpb_cnt_minus1, (byte) hrd.cpbCntMinus1());
        h.set(JAVA_BYTE, VkStructs.StdVideoH264HrdParameters.bit_rate_scale, (byte) hrd.bitRateScale());
        h.set(JAVA_BYTE, VkStructs.StdVideoH264HrdParameters.cpb_size_scale, (byte) hrd.cpbSizeScale());
        for (var i = 0; i <= hrd.cpbCntMinus1(); i++) {
            h.set(
                    JAVA_INT,
                    VkStructs.StdVideoH264HrdParameters.bit_rate_value_minus1 + 4L * i,
                    hrd.bitRateValueMinus1().get(i));
            h.set(
                    JAVA_INT,
                    VkStructs.StdVideoH264HrdParameters.cpb_size_value_minus1 + 4L * i,
                    hrd.cpbSizeValueMinus1().get(i));
            h.set(JAVA_BYTE, VkStructs.StdVideoH264HrdParameters.cbr_flag + i, (byte) ((hrd.cbr() >> i) & 1));
        }
        h.set(
                JAVA_INT,
                VkStructs.StdVideoH264HrdParameters.initial_cpb_removal_delay_length_minus1,
                hrd.initialCpbRemovalDelayLengthMinus1());
        h.set(
                JAVA_INT,
                VkStructs.StdVideoH264HrdParameters.cpb_removal_delay_length_minus1,
                hrd.cpbRemovalDelayLengthMinus1());
        h.set(
                JAVA_INT,
                VkStructs.StdVideoH264HrdParameters.dpb_output_delay_length_minus1,
                hrd.dpbOutputDelayLengthMinus1());
        h.set(JAVA_INT, VkStructs.StdVideoH264HrdParameters.time_offset_length, hrd.timeOffsetLength());
        return h;
    }
}
