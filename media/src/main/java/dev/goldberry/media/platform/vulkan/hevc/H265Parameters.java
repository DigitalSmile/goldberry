package dev.goldberry.media.platform.vulkan.hevc;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Collection;
import java.util.List;

import dev.goldberry.media.bitstream.hevc.Hrd;
import dev.goldberry.media.bitstream.hevc.PictureParameterSet;
import dev.goldberry.media.bitstream.hevc.ProfileTierLevel;
import dev.goldberry.media.bitstream.hevc.ScalingLists;
import dev.goldberry.media.bitstream.hevc.SequenceParameterSet;
import dev.goldberry.media.bitstream.hevc.ShortTermRefPicSet;
import dev.goldberry.media.bitstream.hevc.SubLayerOrdering;
import dev.goldberry.media.bitstream.hevc.VideoParameterSet;
import dev.goldberry.media.bitstream.hevc.Vui;
import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265DecPicBufMgr;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265HrdFlags;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265HrdParameters;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265LongTermRefPicsSps;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265PictureParameterSet;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265PpsFlags;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265ProfileTierLevel;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265ProfileTierLevelFlags;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265ScalingLists;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265SequenceParameterSet;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265SequenceParameterSetVui;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265ShortTermRefPicSet;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265ShortTermRefPicSetFlags;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265SpsFlags;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265SpsVuiFlags;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265SubLayerHrdParameters;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265VideoParameterSet;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoH265VpsFlags;

/// HEVC's parameter sets as Vulkan Video's `StdVideoH265*` structs, and the
/// session parameters create info that carries them.
final class H265Parameters {

    /// The sub-layers a `StdVideoH265DecPicBufMgr` and an HRD hold.
    private static final int SUB_LAYERS = 7;

    private H265Parameters() {}

    /// `VkVideoDecodeH265SessionParametersCreateInfoKHR` holding every VPS,
    /// SPS and PPS given.
    static MemorySegment createInfo(
            Arena arena,
            Collection<VideoParameterSet> vpss,
            Collection<SequenceParameterSet> spss,
            Collection<PictureParameterSet> ppss) {
        var vpsLayout = StdVideoH265VideoParameterSet.LAYOUT;
        var vpsArray = arena.allocate(vpsLayout, Math.max(vpss.size(), 1));
        var i = 0;
        for (var vps : vpss) {
            vps(arena, Structs.at(vpsArray, vpsLayout, i++), vps);
        }
        var spsLayout = StdVideoH265SequenceParameterSet.LAYOUT;
        var spsArray = arena.allocate(spsLayout, Math.max(spss.size(), 1));
        i = 0;
        for (var sps : spss) {
            sps(arena, Structs.at(spsArray, spsLayout, i++), sps);
        }
        var ppsLayout = StdVideoH265PictureParameterSet.LAYOUT;
        var ppsArray = arena.allocate(ppsLayout, Math.max(ppss.size(), 1));
        i = 0;
        for (var pps : ppss) {
            var sps = spss.stream().filter(s -> s.id() == pps.spsId()).findFirst();
            pps(
                    arena,
                    Structs.at(ppsArray, ppsLayout, i++),
                    pps,
                    sps.map(SequenceParameterSet::vpsId).orElse(0));
        }
        var add = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeH265SessionParametersAddInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_SESSION_PARAMETERS_ADD_INFO_KHR);
        add.set(JAVA_INT, VkStructs.VkVideoDecodeH265SessionParametersAddInfoKHR.stdVPSCount, vpss.size());
        add.set(ADDRESS, VkStructs.VkVideoDecodeH265SessionParametersAddInfoKHR.pStdVPSs, vpsArray);
        add.set(JAVA_INT, VkStructs.VkVideoDecodeH265SessionParametersAddInfoKHR.stdSPSCount, spss.size());
        add.set(ADDRESS, VkStructs.VkVideoDecodeH265SessionParametersAddInfoKHR.pStdSPSs, spsArray);
        add.set(JAVA_INT, VkStructs.VkVideoDecodeH265SessionParametersAddInfoKHR.stdPPSCount, ppss.size());
        add.set(ADDRESS, VkStructs.VkVideoDecodeH265SessionParametersAddInfoKHR.pStdPPSs, ppsArray);
        var info = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeH265SessionParametersCreateInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_SESSION_PARAMETERS_CREATE_INFO_KHR);
        info.set(JAVA_INT, VkStructs.VkVideoDecodeH265SessionParametersCreateInfoKHR.maxStdVPSCount, vpss.size());
        info.set(JAVA_INT, VkStructs.VkVideoDecodeH265SessionParametersCreateInfoKHR.maxStdSPSCount, spss.size());
        info.set(JAVA_INT, VkStructs.VkVideoDecodeH265SessionParametersCreateInfoKHR.maxStdPPSCount, ppss.size());
        info.set(ADDRESS, VkStructs.VkVideoDecodeH265SessionParametersCreateInfoKHR.pParametersAddInfo, add);
        return info;
    }

    /// The `StdVideoH265LevelIdc` of `general_level_idc`, which is thirty
    /// times the level.
    static int level(int levelIdc) {
        return switch (levelIdc) {
            case 30 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_1_0;
            case 60 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_2_0;
            case 63 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_2_1;
            case 90 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_3_0;
            case 93 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_3_1;
            case 120 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_4_0;
            case 123 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_4_1;
            case 150 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_5_0;
            case 153 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_5_1;
            case 156 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_5_2;
            case 180 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_6_0;
            case 183 -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_6_1;
            default -> VkConstants.STD_VIDEO_H265_LEVEL_IDC_6_2;
        };
    }

    private static void vps(Arena arena, MemorySegment v, VideoParameterSet vps) {
        var flags = StdVideoH265VideoParameterSet.flags;
        Structs.flag(v, flags, StdVideoH265VpsFlags.MASK_vps_temporal_id_nesting_flag, vps.temporalIdNesting());
        Structs.flag(
                v,
                flags,
                StdVideoH265VpsFlags.MASK_vps_sub_layer_ordering_info_present_flag,
                vps.subLayerOrderingInfoPresent());
        Structs.flag(v, flags, StdVideoH265VpsFlags.MASK_vps_timing_info_present_flag, vps.timingInfoPresent());
        Structs.flag(
                v, flags, StdVideoH265VpsFlags.MASK_vps_poc_proportional_to_timing_flag, vps.pocProportionalToTiming());
        v.set(JAVA_BYTE, StdVideoH265VideoParameterSet.vps_video_parameter_set_id, (byte) vps.id());
        v.set(JAVA_BYTE, StdVideoH265VideoParameterSet.vps_max_sub_layers_minus1, (byte) vps.maxSubLayersMinus1());
        v.set(JAVA_INT, StdVideoH265VideoParameterSet.vps_num_units_in_tick, (int) vps.numUnitsInTick());
        v.set(JAVA_INT, StdVideoH265VideoParameterSet.vps_time_scale, (int) vps.timeScale());
        v.set(
                JAVA_INT,
                StdVideoH265VideoParameterSet.vps_num_ticks_poc_diff_one_minus1,
                vps.numTicksPocDiffOneMinus1());
        v.set(ADDRESS, StdVideoH265VideoParameterSet.pDecPicBufMgr, bufferManager(arena, vps.ordering()));
        v.set(
                ADDRESS,
                StdVideoH265VideoParameterSet.pProfileTierLevel,
                profileTierLevel(arena, vps.profileTierLevel()));
        vps.hrd()
                .ifPresent(hrd -> v.set(
                        ADDRESS,
                        StdVideoH265VideoParameterSet.pHrdParameters,
                        hrd(arena, hrd, vps.maxSubLayersMinus1())));
    }

    private static void sps(Arena arena, MemorySegment s, SequenceParameterSet sps) {
        var flags = StdVideoH265SequenceParameterSet.flags;
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_sps_temporal_id_nesting_flag, sps.temporalIdNesting());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_separate_colour_plane_flag, sps.separateColourPlane());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_conformance_window_flag, sps.conformanceWindow());
        Structs.flag(
                s,
                flags,
                StdVideoH265SpsFlags.MASK_sps_sub_layer_ordering_info_present_flag,
                sps.subLayerOrderingInfoPresent());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_scaling_list_enabled_flag, sps.scalingListEnabled());
        Structs.flag(
                s, flags, StdVideoH265SpsFlags.MASK_sps_scaling_list_data_present_flag, sps.scalingListDataPresent());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_amp_enabled_flag, sps.ampEnabled());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_sample_adaptive_offset_enabled_flag, sps.saoEnabled());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_pcm_enabled_flag, sps.pcmEnabled());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_pcm_loop_filter_disabled_flag, sps.pcmLoopFilterDisabled());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_long_term_ref_pics_present_flag, sps.longTermRefPicsPresent());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_sps_temporal_mvp_enabled_flag, sps.temporalMvpEnabled());
        Structs.flag(
                s,
                flags,
                StdVideoH265SpsFlags.MASK_strong_intra_smoothing_enabled_flag,
                sps.strongIntraSmoothingEnabled());
        Structs.flag(
                s,
                flags,
                StdVideoH265SpsFlags.MASK_vui_parameters_present_flag,
                sps.vui().isPresent());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_sps_extension_present_flag, sps.extensionPresent());
        Structs.flag(s, flags, StdVideoH265SpsFlags.MASK_sps_range_extension_flag, sps.rangeExtension());
        // The range extension's nine flags, the first read the highest bit.
        var range = sps.rangeExtensionFlags();
        int[] masks = {
            StdVideoH265SpsFlags.MASK_transform_skip_rotation_enabled_flag,
            StdVideoH265SpsFlags.MASK_transform_skip_context_enabled_flag,
            StdVideoH265SpsFlags.MASK_implicit_rdpcm_enabled_flag,
            StdVideoH265SpsFlags.MASK_explicit_rdpcm_enabled_flag,
            StdVideoH265SpsFlags.MASK_extended_precision_processing_flag,
            StdVideoH265SpsFlags.MASK_intra_smoothing_disabled_flag,
            StdVideoH265SpsFlags.MASK_high_precision_offsets_enabled_flag,
            StdVideoH265SpsFlags.MASK_persistent_rice_adaptation_enabled_flag,
            StdVideoH265SpsFlags.MASK_cabac_bypass_alignment_enabled_flag
        };
        for (var bit = 0; bit < masks.length; bit++) {
            Structs.flag(s, flags, masks[bit], (range >> (masks.length - 1 - bit) & 1) != 0);
        }
        s.set(JAVA_INT, StdVideoH265SequenceParameterSet.chroma_format_idc, sps.chromaFormatIdc());
        s.set(JAVA_INT, StdVideoH265SequenceParameterSet.pic_width_in_luma_samples, sps.width());
        s.set(JAVA_INT, StdVideoH265SequenceParameterSet.pic_height_in_luma_samples, sps.height());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.sps_video_parameter_set_id, (byte) sps.vpsId());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.sps_max_sub_layers_minus1, (byte) sps.maxSubLayersMinus1());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.sps_seq_parameter_set_id, (byte) sps.id());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.bit_depth_luma_minus8, (byte) sps.bitDepthLumaMinus8());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.bit_depth_chroma_minus8, (byte) sps.bitDepthChromaMinus8());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.log2_max_pic_order_cnt_lsb_minus4, (byte)
                sps.log2MaxPocLsbMinus4());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.log2_min_luma_coding_block_size_minus3, (byte)
                sps.log2MinLumaCodingBlockSizeMinus3());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.log2_diff_max_min_luma_coding_block_size, (byte)
                sps.log2DiffMaxMinLumaCodingBlockSize());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.log2_min_luma_transform_block_size_minus2, (byte)
                sps.log2MinLumaTransformBlockSizeMinus2());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.log2_diff_max_min_luma_transform_block_size, (byte)
                sps.log2DiffMaxMinLumaTransformBlockSize());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.max_transform_hierarchy_depth_inter, (byte)
                sps.maxTransformHierarchyDepthInter());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.max_transform_hierarchy_depth_intra, (byte)
                sps.maxTransformHierarchyDepthIntra());
        var sets = sps.shortTermRefPicSets();
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.num_short_term_ref_pic_sets, (byte) sets.size());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.num_long_term_ref_pics_sps, (byte)
                sps.ltRefPicPocLsbSps().size());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.pcm_sample_bit_depth_luma_minus1, (byte)
                sps.pcmBitDepthLumaMinus1());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.pcm_sample_bit_depth_chroma_minus1, (byte)
                sps.pcmBitDepthChromaMinus1());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.log2_min_pcm_luma_coding_block_size_minus3, (byte)
                sps.log2MinPcmLumaCodingBlockSizeMinus3());
        s.set(JAVA_BYTE, StdVideoH265SequenceParameterSet.log2_diff_max_min_pcm_luma_coding_block_size, (byte)
                sps.log2DiffMaxMinPcmLumaCodingBlockSize());
        s.set(JAVA_INT, StdVideoH265SequenceParameterSet.conf_win_left_offset, sps.confWinLeft());
        s.set(JAVA_INT, StdVideoH265SequenceParameterSet.conf_win_right_offset, sps.confWinRight());
        s.set(JAVA_INT, StdVideoH265SequenceParameterSet.conf_win_top_offset, sps.confWinTop());
        s.set(JAVA_INT, StdVideoH265SequenceParameterSet.conf_win_bottom_offset, sps.confWinBottom());
        s.set(
                ADDRESS,
                StdVideoH265SequenceParameterSet.pProfileTierLevel,
                profileTierLevel(arena, sps.profileTierLevel()));
        s.set(ADDRESS, StdVideoH265SequenceParameterSet.pDecPicBufMgr, bufferManager(arena, sps.ordering()));
        if (sps.scalingListEnabled()) {
            var lists = sps.scalingLists().orElseGet(ScalingLists::defaults);
            s.set(ADDRESS, StdVideoH265SequenceParameterSet.pScalingLists, scalingLists(arena, lists));
        }
        if (!sets.isEmpty()) {
            var layout = StdVideoH265ShortTermRefPicSet.LAYOUT;
            var array = arena.allocate(layout, sets.size());
            for (var i = 0; i < sets.size(); i++) {
                shortTermSet(Structs.at(array, layout, i), sets.get(i));
            }
            s.set(ADDRESS, StdVideoH265SequenceParameterSet.pShortTermRefPicSet, array);
        }
        if (sps.longTermRefPicsPresent()) {
            var longTerm = Structs.allocate(arena, StdVideoH265LongTermRefPicsSps.LAYOUT);
            longTerm.set(
                    JAVA_INT,
                    StdVideoH265LongTermRefPicsSps.used_by_curr_pic_lt_sps_flag,
                    sps.usedByCurrPicLtSpsFlags());
            var lsbs = sps.ltRefPicPocLsbSps();
            for (var i = 0; i < lsbs.size(); i++) {
                longTerm.set(JAVA_INT, StdVideoH265LongTermRefPicsSps.lt_ref_pic_poc_lsb_sps + 4L * i, lsbs.get(i));
            }
            s.set(ADDRESS, StdVideoH265SequenceParameterSet.pLongTermRefPicsSps, longTerm);
        }
        sps.vui()
                .ifPresent(vui -> s.set(
                        ADDRESS,
                        StdVideoH265SequenceParameterSet.pSequenceParameterSetVui,
                        vui(arena, vui, sps.maxSubLayersMinus1())));
    }

    private static void pps(Arena arena, MemorySegment p, PictureParameterSet pps, int vpsId) {
        var flags = StdVideoH265PictureParameterSet.flags;
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_dependent_slice_segments_enabled_flag,
                pps.dependentSliceSegmentsEnabled());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_output_flag_present_flag, pps.outputFlagPresent());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_sign_data_hiding_enabled_flag, pps.signDataHidingEnabled());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_cabac_init_present_flag, pps.cabacInitPresent());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_constrained_intra_pred_flag, pps.constrainedIntraPred());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_transform_skip_enabled_flag, pps.transformSkipEnabled());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_cu_qp_delta_enabled_flag, pps.cuQpDeltaEnabled());
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_pps_slice_chroma_qp_offsets_present_flag,
                pps.sliceChromaQpOffsetsPresent());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_weighted_pred_flag, pps.weightedPred());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_weighted_bipred_flag, pps.weightedBipred());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_transquant_bypass_enabled_flag, pps.transquantBypassEnabled());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_tiles_enabled_flag, pps.tilesEnabled());
        Structs.flag(
                p, flags, StdVideoH265PpsFlags.MASK_entropy_coding_sync_enabled_flag, pps.entropyCodingSyncEnabled());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_uniform_spacing_flag, pps.uniformSpacing());
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_loop_filter_across_tiles_enabled_flag,
                pps.loopFilterAcrossTilesEnabled());
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_pps_loop_filter_across_slices_enabled_flag,
                pps.loopFilterAcrossSlicesEnabled());
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_deblocking_filter_control_present_flag,
                pps.deblockingFilterControlPresent());
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_deblocking_filter_override_enabled_flag,
                pps.deblockingFilterOverrideEnabled());
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_pps_deblocking_filter_disabled_flag,
                pps.deblockingFilterDisabled());
        Structs.flag(
                p, flags, StdVideoH265PpsFlags.MASK_pps_scaling_list_data_present_flag, pps.scalingListDataPresent());
        Structs.flag(
                p, flags, StdVideoH265PpsFlags.MASK_lists_modification_present_flag, pps.listsModificationPresent());
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_slice_segment_header_extension_present_flag,
                pps.sliceSegmentHeaderExtensionPresent());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_pps_extension_present_flag, pps.extensionPresent());
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_cross_component_prediction_enabled_flag,
                pps.crossComponentPredictionEnabled());
        Structs.flag(
                p,
                flags,
                StdVideoH265PpsFlags.MASK_chroma_qp_offset_list_enabled_flag,
                pps.chromaQpOffsetListEnabled());
        Structs.flag(p, flags, StdVideoH265PpsFlags.MASK_pps_range_extension_flag, pps.rangeExtension());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.pps_pic_parameter_set_id, (byte) pps.id());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.pps_seq_parameter_set_id, (byte) pps.spsId());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.sps_video_parameter_set_id, (byte) vpsId);
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.num_extra_slice_header_bits, (byte)
                pps.numExtraSliceHeaderBits());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.num_ref_idx_l0_default_active_minus1, (byte)
                pps.numRefIdxL0DefaultActiveMinus1());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.num_ref_idx_l1_default_active_minus1, (byte)
                pps.numRefIdxL1DefaultActiveMinus1());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.init_qp_minus26, (byte) pps.initQpMinus26());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.diff_cu_qp_delta_depth, (byte) pps.diffCuQpDeltaDepth());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.pps_cb_qp_offset, (byte) pps.cbQpOffset());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.pps_cr_qp_offset, (byte) pps.crQpOffset());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.pps_beta_offset_div2, (byte) pps.betaOffsetDiv2());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.pps_tc_offset_div2, (byte) pps.tcOffsetDiv2());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.log2_parallel_merge_level_minus2, (byte)
                pps.log2ParallelMergeLevelMinus2());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.log2_max_transform_skip_block_size_minus2, (byte)
                pps.log2MaxTransformSkipBlockSizeMinus2());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.diff_cu_chroma_qp_offset_depth, (byte)
                pps.diffCuChromaQpOffsetDepth());
        var cb = pps.cbQpOffsetList();
        var cr = pps.crQpOffsetList();
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.chroma_qp_offset_list_len_minus1, (byte)
                Math.max(cb.size() - 1, 0));
        for (var i = 0; i < cb.size(); i++) {
            p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.cb_qp_offset_list + i, (byte) (int) cb.get(i));
            p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.cr_qp_offset_list + i, (byte) (int) cr.get(i));
        }
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.log2_sao_offset_scale_luma, (byte)
                pps.log2SaoOffsetScaleLuma());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.log2_sao_offset_scale_chroma, (byte)
                pps.log2SaoOffsetScaleChroma());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.num_tile_columns_minus1, (byte) pps.numTileColumnsMinus1());
        p.set(JAVA_BYTE, StdVideoH265PictureParameterSet.num_tile_rows_minus1, (byte) pps.numTileRowsMinus1());
        var columns = pps.columnWidthMinus1();
        for (var i = 0; i < columns.size(); i++) {
            p.set(JAVA_SHORT, StdVideoH265PictureParameterSet.column_width_minus1 + 2L * i, (short)
                    (int) columns.get(i));
        }
        var rows = pps.rowHeightMinus1();
        for (var i = 0; i < rows.size(); i++) {
            p.set(JAVA_SHORT, StdVideoH265PictureParameterSet.row_height_minus1 + 2L * i, (short) (int) rows.get(i));
        }
        pps.scalingLists()
                .ifPresent(lists ->
                        p.set(ADDRESS, StdVideoH265PictureParameterSet.pScalingLists, scalingLists(arena, lists)));
    }

    private static MemorySegment profileTierLevel(Arena arena, ProfileTierLevel ptl) {
        var p = Structs.allocate(arena, StdVideoH265ProfileTierLevel.LAYOUT);
        var flags = StdVideoH265ProfileTierLevel.flags;
        Structs.flag(p, flags, StdVideoH265ProfileTierLevelFlags.MASK_general_tier_flag, ptl.tier());
        Structs.flag(
                p,
                flags,
                StdVideoH265ProfileTierLevelFlags.MASK_general_progressive_source_flag,
                ptl.progressiveSource());
        Structs.flag(
                p,
                flags,
                StdVideoH265ProfileTierLevelFlags.MASK_general_interlaced_source_flag,
                ptl.interlacedSource());
        Structs.flag(
                p,
                flags,
                StdVideoH265ProfileTierLevelFlags.MASK_general_non_packed_constraint_flag,
                ptl.nonPackedConstraint());
        Structs.flag(
                p,
                flags,
                StdVideoH265ProfileTierLevelFlags.MASK_general_frame_only_constraint_flag,
                ptl.frameOnlyConstraint());
        p.set(JAVA_INT, StdVideoH265ProfileTierLevel.general_profile_idc, ptl.profileIdc());
        p.set(JAVA_INT, StdVideoH265ProfileTierLevel.general_level_idc, level(ptl.levelIdc()));
        return p;
    }

    /// Each sub-layer's buffering, the highest coded one's repeated above it.
    private static MemorySegment bufferManager(Arena arena, List<SubLayerOrdering> ordering) {
        var m = Structs.allocate(arena, StdVideoH265DecPicBufMgr.LAYOUT);
        for (var i = 0; i < SUB_LAYERS && !ordering.isEmpty(); i++) {
            var layer = ordering.get(Math.min(i, ordering.size() - 1));
            m.set(
                    JAVA_INT,
                    StdVideoH265DecPicBufMgr.max_latency_increase_plus1 + 4L * i,
                    layer.maxLatencyIncreasePlus1());
            m.set(JAVA_BYTE, StdVideoH265DecPicBufMgr.max_dec_pic_buffering_minus1 + i, (byte)
                    layer.maxDecPicBufferingMinus1());
            m.set(JAVA_BYTE, StdVideoH265DecPicBufMgr.max_num_reorder_pics + i, (byte) layer.maxNumReorderPics());
        }
        return m;
    }

    /// A short-term set as Vulkan takes it: the deltas resolved, as
    /// `delta_poc_s0_minus1` and `delta_poc_s1_minus1` code them, whether the
    /// set was coded by prediction or not.
    static void shortTermSet(MemorySegment s, ShortTermRefPicSet set) {
        var flags = StdVideoH265ShortTermRefPicSet.flags;
        Structs.flag(
                s,
                flags,
                StdVideoH265ShortTermRefPicSetFlags.MASK_inter_ref_pic_set_prediction_flag,
                set.interPrediction());
        Structs.flag(s, flags, StdVideoH265ShortTermRefPicSetFlags.MASK_delta_rps_sign, set.deltaRpsSign());
        s.set(JAVA_INT, StdVideoH265ShortTermRefPicSet.delta_idx_minus1, set.deltaIdxMinus1());
        s.set(JAVA_SHORT, StdVideoH265ShortTermRefPicSet.use_delta_flag, (short) set.useDeltaFlags());
        s.set(JAVA_SHORT, StdVideoH265ShortTermRefPicSet.abs_delta_rps_minus1, (short) set.absDeltaRpsMinus1());
        s.set(JAVA_SHORT, StdVideoH265ShortTermRefPicSet.used_by_curr_pic_flag, (short) set.usedByCurrPicFlags());
        s.set(JAVA_SHORT, StdVideoH265ShortTermRefPicSet.used_by_curr_pic_s0_flag, (short) set.usedS0());
        s.set(JAVA_SHORT, StdVideoH265ShortTermRefPicSet.used_by_curr_pic_s1_flag, (short) set.usedS1());
        s.set(JAVA_BYTE, StdVideoH265ShortTermRefPicSet.num_negative_pics, (byte) set.negative());
        s.set(JAVA_BYTE, StdVideoH265ShortTermRefPicSet.num_positive_pics, (byte) set.positive());
        var previous = 0;
        for (var i = 0; i < set.negative(); i++) {
            int delta = set.deltaPocS0().get(i);
            s.set(JAVA_SHORT, StdVideoH265ShortTermRefPicSet.delta_poc_s0_minus1 + 2L * i, (short)
                    (previous - delta - 1));
            previous = delta;
        }
        previous = 0;
        for (var i = 0; i < set.positive(); i++) {
            var delta = set.deltaPocS1().get(i);
            s.set(JAVA_SHORT, StdVideoH265ShortTermRefPicSet.delta_poc_s1_minus1 + 2L * i, (short)
                    (delta - previous - 1));
            previous = delta;
        }
    }

    private static MemorySegment scalingLists(Arena arena, ScalingLists lists) {
        var s = Structs.allocate(arena, StdVideoH265ScalingLists.LAYOUT);
        copy(s, StdVideoH265ScalingLists.ScalingList4x4, lists.lists4x4(), 16);
        copy(s, StdVideoH265ScalingLists.ScalingList8x8, lists.lists8x8(), 64);
        copy(s, StdVideoH265ScalingLists.ScalingList16x16, lists.lists16x16(), 64);
        copy(s, StdVideoH265ScalingLists.ScalingList32x32, lists.lists32x32(), 64);
        for (var i = 0; i < lists.dc16x16().length; i++) {
            s.set(JAVA_BYTE, StdVideoH265ScalingLists.ScalingListDCCoef16x16 + i, (byte) lists.dc16x16()[i]);
        }
        for (var i = 0; i < lists.dc32x32().length; i++) {
            s.set(JAVA_BYTE, StdVideoH265ScalingLists.ScalingListDCCoef32x32 + i, (byte) lists.dc32x32()[i]);
        }
        return s;
    }

    private static void copy(MemorySegment s, long offset, int[][] lists, int size) {
        for (var i = 0; i < lists.length; i++) {
            for (var j = 0; j < size; j++) {
                s.set(JAVA_BYTE, offset + (long) i * size + j, (byte) lists[i][j]);
            }
        }
    }

    private static MemorySegment vui(Arena arena, Vui vui, int maxSubLayersMinus1) {
        var v = Structs.allocate(arena, StdVideoH265SequenceParameterSetVui.LAYOUT);
        var flags = StdVideoH265SequenceParameterSetVui.flags;
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_aspect_ratio_info_present_flag, vui.aspectRatioIdc() != 0);
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_overscan_info_present_flag, vui.overscanInfoPresent());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_overscan_appropriate_flag, vui.overscanAppropriate());
        Structs.flag(
                v, flags, StdVideoH265SpsVuiFlags.MASK_video_signal_type_present_flag, vui.videoSignalTypePresent());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_video_full_range_flag, vui.fullRange());
        Structs.flag(
                v, flags, StdVideoH265SpsVuiFlags.MASK_colour_description_present_flag, vui.colourDescriptionPresent());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_chroma_loc_info_present_flag, vui.chromaLocInfoPresent());
        Structs.flag(
                v, flags, StdVideoH265SpsVuiFlags.MASK_neutral_chroma_indication_flag, vui.neutralChromaIndication());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_field_seq_flag, vui.fieldSeq());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_frame_field_info_present_flag, vui.frameFieldInfoPresent());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_default_display_window_flag, vui.defaultDisplayWindow());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_vui_timing_info_present_flag, vui.timingInfoPresent());
        Structs.flag(
                v,
                flags,
                StdVideoH265SpsVuiFlags.MASK_vui_poc_proportional_to_timing_flag,
                vui.pocProportionalToTiming());
        Structs.flag(
                v,
                flags,
                StdVideoH265SpsVuiFlags.MASK_vui_hrd_parameters_present_flag,
                vui.hrd().isPresent());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_bitstream_restriction_flag, vui.bitstreamRestriction());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_tiles_fixed_structure_flag, vui.tilesFixedStructure());
        Structs.flag(
                v,
                flags,
                StdVideoH265SpsVuiFlags.MASK_motion_vectors_over_pic_boundaries_flag,
                vui.motionVectorsOverPicBoundaries());
        Structs.flag(v, flags, StdVideoH265SpsVuiFlags.MASK_restricted_ref_pic_lists_flag, vui.restrictedRefPicLists());
        v.set(JAVA_INT, StdVideoH265SequenceParameterSetVui.aspect_ratio_idc, vui.aspectRatioIdc());
        v.set(JAVA_SHORT, StdVideoH265SequenceParameterSetVui.sar_width, (short) vui.sarWidth());
        v.set(JAVA_SHORT, StdVideoH265SequenceParameterSetVui.sar_height, (short) vui.sarHeight());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.video_format, (byte) vui.videoFormat());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.colour_primaries, (byte) vui.colourPrimaries());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.transfer_characteristics, (byte)
                vui.transferCharacteristics());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.matrix_coeffs, (byte) vui.matrixCoefficients());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.chroma_sample_loc_type_top_field, (byte)
                vui.chromaSampleLocTypeTop());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.chroma_sample_loc_type_bottom_field, (byte)
                vui.chromaSampleLocTypeBottom());
        var window = vui.defaultDisplayWindowOffsets();
        if (window.length == 4) {
            v.set(JAVA_SHORT, StdVideoH265SequenceParameterSetVui.def_disp_win_left_offset, (short) window[0]);
            v.set(JAVA_SHORT, StdVideoH265SequenceParameterSetVui.def_disp_win_right_offset, (short) window[1]);
            v.set(JAVA_SHORT, StdVideoH265SequenceParameterSetVui.def_disp_win_top_offset, (short) window[2]);
            v.set(JAVA_SHORT, StdVideoH265SequenceParameterSetVui.def_disp_win_bottom_offset, (short) window[3]);
        }
        v.set(JAVA_INT, StdVideoH265SequenceParameterSetVui.vui_num_units_in_tick, (int) vui.numUnitsInTick());
        v.set(JAVA_INT, StdVideoH265SequenceParameterSetVui.vui_time_scale, (int) vui.timeScale());
        v.set(
                JAVA_INT,
                StdVideoH265SequenceParameterSetVui.vui_num_ticks_poc_diff_one_minus1,
                vui.numTicksPocDiffOneMinus1());
        v.set(JAVA_SHORT, StdVideoH265SequenceParameterSetVui.min_spatial_segmentation_idc, (short)
                vui.minSpatialSegmentationIdc());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.max_bytes_per_pic_denom, (byte) vui.maxBytesPerPicDenom());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.max_bits_per_min_cu_denom, (byte)
                vui.maxBitsPerMinCuDenom());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.log2_max_mv_length_horizontal, (byte)
                vui.log2MaxMvLengthHorizontal());
        v.set(JAVA_BYTE, StdVideoH265SequenceParameterSetVui.log2_max_mv_length_vertical, (byte)
                vui.log2MaxMvLengthVertical());
        vui.hrd()
                .ifPresent(hrd -> v.set(
                        ADDRESS,
                        StdVideoH265SequenceParameterSetVui.pHrdParameters,
                        hrd(arena, hrd, maxSubLayersMinus1)));
        return v;
    }

    private static MemorySegment hrd(Arena arena, Hrd hrd, int maxSubLayersMinus1) {
        var h = Structs.allocate(arena, StdVideoH265HrdParameters.LAYOUT);
        var flags = StdVideoH265HrdParameters.flags;
        Structs.flag(h, flags, StdVideoH265HrdFlags.MASK_nal_hrd_parameters_present_flag, hrd.nalPresent());
        Structs.flag(h, flags, StdVideoH265HrdFlags.MASK_vcl_hrd_parameters_present_flag, hrd.vclPresent());
        Structs.flag(h, flags, StdVideoH265HrdFlags.MASK_sub_pic_hrd_params_present_flag, hrd.subPicPresent());
        Structs.flag(
                h, flags, StdVideoH265HrdFlags.MASK_sub_pic_cpb_params_in_pic_timing_sei_flag, hrd.subPicInPicTiming());
        h.set(JAVA_BYTE, StdVideoH265HrdParameters.tick_divisor_minus2, (byte) hrd.tickDivisorMinus2());
        h.set(JAVA_BYTE, StdVideoH265HrdParameters.du_cpb_removal_delay_increment_length_minus1, (byte)
                hrd.duCpbRemovalDelayIncrementLengthMinus1());
        h.set(JAVA_BYTE, StdVideoH265HrdParameters.dpb_output_delay_du_length_minus1, (byte)
                hrd.dpbOutputDelayDuLengthMinus1());
        h.set(JAVA_BYTE, StdVideoH265HrdParameters.bit_rate_scale, (byte) hrd.bitRateScale());
        h.set(JAVA_BYTE, StdVideoH265HrdParameters.cpb_size_scale, (byte) hrd.cpbSizeScale());
        h.set(JAVA_BYTE, StdVideoH265HrdParameters.cpb_size_du_scale, (byte) hrd.cpbSizeDuScale());
        h.set(JAVA_BYTE, StdVideoH265HrdParameters.initial_cpb_removal_delay_length_minus1, (byte)
                hrd.initialCpbRemovalDelayLengthMinus1());
        h.set(JAVA_BYTE, StdVideoH265HrdParameters.au_cpb_removal_delay_length_minus1, (byte)
                hrd.auCpbRemovalDelayLengthMinus1());
        h.set(JAVA_BYTE, StdVideoH265HrdParameters.dpb_output_delay_length_minus1, (byte)
                hrd.dpbOutputDelayLengthMinus1());
        var layers = hrd.subLayers();
        var count = Math.min(layers.size(), Math.min(maxSubLayersMinus1 + 1, SUB_LAYERS));
        var general = 0;
        var withinCvs = 0;
        var lowDelay = 0;
        var subLayout = StdVideoH265SubLayerHrdParameters.LAYOUT;
        var nal = arena.allocate(subLayout, Math.max(count, 1));
        var vcl = arena.allocate(subLayout, Math.max(count, 1));
        for (var i = 0; i < count; i++) {
            var layer = layers.get(i);
            general |= layer.fixedPicRateGeneral() ? 1 << i : 0;
            withinCvs |= layer.fixedPicRateWithinCvs() ? 1 << i : 0;
            lowDelay |= layer.lowDelay() ? 1 << i : 0;
            h.set(JAVA_BYTE, StdVideoH265HrdParameters.cpb_cnt_minus1 + i, (byte) layer.cpbCntMinus1());
            h.set(JAVA_SHORT, StdVideoH265HrdParameters.elemental_duration_in_tc_minus1 + 2L * i, (short)
                    layer.elementalDurationInTcMinus1());
            subLayer(Structs.at(nal, subLayout, i), layer.nal());
            subLayer(Structs.at(vcl, subLayout, i), layer.vcl());
        }
        Structs.bits(
                h,
                flags,
                StdVideoH265HrdFlags.SHIFT_fixed_pic_rate_general_flag,
                StdVideoH265HrdFlags.MASK_fixed_pic_rate_general_flag,
                general);
        Structs.bits(
                h,
                flags,
                StdVideoH265HrdFlags.SHIFT_fixed_pic_rate_within_cvs_flag,
                StdVideoH265HrdFlags.MASK_fixed_pic_rate_within_cvs_flag,
                withinCvs);
        Structs.bits(
                h,
                flags,
                StdVideoH265HrdFlags.SHIFT_low_delay_hrd_flag,
                StdVideoH265HrdFlags.MASK_low_delay_hrd_flag,
                lowDelay);
        if (hrd.nalPresent()) {
            h.set(ADDRESS, StdVideoH265HrdParameters.pSubLayerHrdParametersNal, nal);
        }
        if (hrd.vclPresent()) {
            h.set(ADDRESS, StdVideoH265HrdParameters.pSubLayerHrdParametersVcl, vcl);
        }
        return h;
    }

    private static void subLayer(MemorySegment s, List<Hrd.Cpb> cpbs) {
        var cbr = 0;
        for (var i = 0; i < cpbs.size() && i < 32; i++) {
            var cpb = cpbs.get(i);
            s.set(JAVA_INT, StdVideoH265SubLayerHrdParameters.bit_rate_value_minus1 + 4L * i, cpb.bitRateValueMinus1());
            s.set(JAVA_INT, StdVideoH265SubLayerHrdParameters.cpb_size_value_minus1 + 4L * i, cpb.cpbSizeValueMinus1());
            s.set(
                    JAVA_INT,
                    StdVideoH265SubLayerHrdParameters.cpb_size_du_value_minus1 + 4L * i,
                    cpb.cpbSizeDuValueMinus1());
            s.set(
                    JAVA_INT,
                    StdVideoH265SubLayerHrdParameters.bit_rate_du_value_minus1 + 4L * i,
                    cpb.bitRateDuValueMinus1());
            cbr |= cpb.cbr() ? 1 << i : 0;
        }
        s.set(JAVA_INT, StdVideoH265SubLayerHrdParameters.cbr_flag, cbr);
    }
}
