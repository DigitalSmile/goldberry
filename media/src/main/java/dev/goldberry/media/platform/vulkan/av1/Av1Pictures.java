package dev.goldberry.media.platform.vulkan.av1;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import dev.goldberry.media.bitstream.av1.FrameHeader;
import dev.goldberry.media.bitstream.av1.ReferenceFrame;
import dev.goldberry.media.bitstream.av1.SequenceHeader;
import dev.goldberry.media.bitstream.av1.TileGroup;
import dev.goldberry.media.platform.vulkan.ffm.Structs;
import dev.goldberry.media.platform.vulkan.ffm.VkConstants;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1CDEF;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1ColorConfig;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1ColorConfigFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1FilmGrain;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1GlobalMotion;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1LoopFilter;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1LoopFilterFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1LoopRestoration;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1Quantization;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1QuantizationFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1Segmentation;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1SequenceHeader;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1SequenceHeaderFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1TileInfo;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1TileInfoFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1TimingInfo;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoAV1TimingInfoFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeAV1PictureInfo;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeAV1PictureInfoFlags;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeAV1ReferenceInfo;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoDecodeAV1ReferenceInfoFlags;

/// AV1's headers as Vulkan Video's `StdVideoAV1*` structs: the sequence
/// header as the session parameters' create info, a frame header and its
/// tiles as the picture info, a stored frame as its slot's reference info.
final class Av1Pictures {

    private Av1Pictures() {}

    /// Where one frame lies in the bitstream buffer: the frame header OBU's
    /// offset, and each tile's offset and size, every one from the buffer's
    /// start.
    ///
    /// @param frameHeaderOffset the frame header OBU's, or the frame OBU's
    /// @param tileOffsets       each tile's
    /// @param tileSizes         each tile's bytes
    record Layout(int frameHeaderOffset, List<Integer> tileOffsets, List<Integer> tileSizes) {

        Layout {
            tileOffsets = List.copyOf(tileOffsets);
            tileSizes = List.copyOf(tileSizes);
            if (tileOffsets.size() != tileSizes.size()) {
                throw new IllegalArgumentException(tileOffsets.size() + " offsets for " + tileSizes.size() + " tiles");
            }
        }

        /// This layout with `group`'s tiles, read from the OBU at `obuOffset`.
        Layout with(TileGroup group, int obuOffset) {
            var offsets = new ArrayList<>(tileOffsets);
            var sizes = new ArrayList<>(tileSizes);
            for (var tile : group.tiles()) {
                offsets.add(obuOffset + tile.offset());
                sizes.add(tile.size());
            }
            return new Layout(frameHeaderOffset, offsets, sizes);
        }
    }

    /// `VkVideoDecodeAV1SessionParametersCreateInfoKHR` holding `sequence`.
    static MemorySegment createInfo(Arena arena, SequenceHeader sequence) {
        var s = Structs.allocate(arena, StdVideoAV1SequenceHeader.LAYOUT);
        var flags = StdVideoAV1SequenceHeader.flags;
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_still_picture, sequence.stillPicture());
        Structs.flag(
                s,
                flags,
                StdVideoAV1SequenceHeaderFlags.MASK_reduced_still_picture_header,
                sequence.reducedStillPictureHeader());
        Structs.flag(
                s, flags, StdVideoAV1SequenceHeaderFlags.MASK_use_128x128_superblock, sequence.use128x128Superblock());
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_filter_intra, sequence.enableFilterIntra());
        Structs.flag(
                s,
                flags,
                StdVideoAV1SequenceHeaderFlags.MASK_enable_intra_edge_filter,
                sequence.enableIntraEdgeFilter());
        Structs.flag(
                s,
                flags,
                StdVideoAV1SequenceHeaderFlags.MASK_enable_interintra_compound,
                sequence.enableInterintraCompound());
        Structs.flag(
                s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_masked_compound, sequence.enableMaskedCompound());
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_warped_motion, sequence.enableWarpedMotion());
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_dual_filter, sequence.enableDualFilter());
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_order_hint, sequence.enableOrderHint());
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_jnt_comp, sequence.enableJntComp());
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_ref_frame_mvs, sequence.enableRefFrameMvs());
        Structs.flag(
                s,
                flags,
                StdVideoAV1SequenceHeaderFlags.MASK_frame_id_numbers_present_flag,
                sequence.frameIdNumbersPresent());
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_superres, sequence.enableSuperres());
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_cdef, sequence.enableCdef());
        Structs.flag(s, flags, StdVideoAV1SequenceHeaderFlags.MASK_enable_restoration, sequence.enableRestoration());
        Structs.flag(
                s,
                flags,
                StdVideoAV1SequenceHeaderFlags.MASK_film_grain_params_present,
                sequence.filmGrainParamsPresent());
        Structs.flag(
                s,
                flags,
                StdVideoAV1SequenceHeaderFlags.MASK_timing_info_present_flag,
                sequence.timingInfo().isPresent());
        Structs.flag(
                s,
                flags,
                StdVideoAV1SequenceHeaderFlags.MASK_initial_display_delay_present_flag,
                sequence.initialDisplayDelayPresent());
        s.set(JAVA_INT, StdVideoAV1SequenceHeader.seq_profile, sequence.seqProfile());
        s.set(JAVA_SHORT, StdVideoAV1SequenceHeader.max_frame_width_minus_1, (short) sequence.maxFrameWidthMinus1());
        s.set(JAVA_SHORT, StdVideoAV1SequenceHeader.max_frame_height_minus_1, (short) sequence.maxFrameHeightMinus1());
        s.set(JAVA_BYTE, StdVideoAV1SequenceHeader.delta_frame_id_length_minus_2, (byte)
                sequence.deltaFrameIdLengthMinus2());
        s.set(JAVA_BYTE, StdVideoAV1SequenceHeader.additional_frame_id_length_minus_1, (byte)
                sequence.additionalFrameIdLengthMinus1());
        s.set(JAVA_BYTE, StdVideoAV1SequenceHeader.order_hint_bits_minus_1, (byte) sequence.orderHintBitsMinus1());
        s.set(JAVA_BYTE, StdVideoAV1SequenceHeader.seq_force_integer_mv, (byte) sequence.seqForceIntegerMv());
        s.set(JAVA_BYTE, StdVideoAV1SequenceHeader.seq_force_screen_content_tools, (byte)
                sequence.seqForceScreenContentTools());
        var colour = sequence.colorConfig();
        var c = Structs.allocate(arena, StdVideoAV1ColorConfig.LAYOUT);
        Structs.flag(
                c, StdVideoAV1ColorConfig.flags, StdVideoAV1ColorConfigFlags.MASK_mono_chrome, colour.monoChrome());
        Structs.flag(
                c, StdVideoAV1ColorConfig.flags, StdVideoAV1ColorConfigFlags.MASK_color_range, colour.colorRange());
        Structs.flag(
                c,
                StdVideoAV1ColorConfig.flags,
                StdVideoAV1ColorConfigFlags.MASK_separate_uv_delta_q,
                colour.separateUvDeltaQ());
        Structs.flag(
                c,
                StdVideoAV1ColorConfig.flags,
                StdVideoAV1ColorConfigFlags.MASK_color_description_present_flag,
                colour.colorDescriptionPresent());
        c.set(JAVA_BYTE, StdVideoAV1ColorConfig.BitDepth, (byte) colour.bitDepth());
        c.set(JAVA_BYTE, StdVideoAV1ColorConfig.subsampling_x, (byte) colour.subsamplingX());
        c.set(JAVA_BYTE, StdVideoAV1ColorConfig.subsampling_y, (byte) colour.subsamplingY());
        c.set(JAVA_INT, StdVideoAV1ColorConfig.color_primaries, colour.colorPrimaries());
        c.set(JAVA_INT, StdVideoAV1ColorConfig.transfer_characteristics, colour.transferCharacteristics());
        c.set(JAVA_INT, StdVideoAV1ColorConfig.matrix_coefficients, colour.matrixCoefficients());
        c.set(JAVA_INT, StdVideoAV1ColorConfig.chroma_sample_position, colour.chromaSamplePosition());
        s.set(ADDRESS, StdVideoAV1SequenceHeader.pColorConfig, c);
        sequence.timingInfo().ifPresent(timing -> {
            var t = Structs.allocate(arena, StdVideoAV1TimingInfo.LAYOUT);
            Structs.flag(
                    t,
                    StdVideoAV1TimingInfo.flags,
                    StdVideoAV1TimingInfoFlags.MASK_equal_picture_interval,
                    timing.equalPictureInterval());
            t.set(JAVA_INT, StdVideoAV1TimingInfo.num_units_in_display_tick, (int) timing.numUnitsInDisplayTick());
            t.set(JAVA_INT, StdVideoAV1TimingInfo.time_scale, (int) timing.timeScale());
            t.set(JAVA_INT, StdVideoAV1TimingInfo.num_ticks_per_picture_minus_1, (int)
                    timing.numTicksPerPictureMinus1());
            s.set(ADDRESS, StdVideoAV1SequenceHeader.pTimingInfo, t);
        });
        var info = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeAV1SessionParametersCreateInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_AV1_SESSION_PARAMETERS_CREATE_INFO_KHR);
        info.set(ADDRESS, VkStructs.VkVideoDecodeAV1SessionParametersCreateInfoKHR.pStdSequenceHeader, s);
        return info;
    }

    /// `VkVideoDecodeAV1PictureInfoKHR` for the frame `header`, laid out as
    /// `layout` says, its seven references in the buffer slots `named`.
    static MemorySegment pictureInfo(Arena arena, FrameHeader header, Layout layout, int[] named) {
        var std = Structs.allocate(arena, StdVideoDecodeAV1PictureInfo.LAYOUT);
        var flags = StdVideoDecodeAV1PictureInfo.flags;
        var size = header.frameSize();
        var segmentation = header.segmentation();
        var restoration = header.loopRestoration();
        Structs.flag(
                std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_error_resilient_mode, header.errorResilientMode());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_disable_cdf_update, header.disableCdfUpdate());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_use_superres, size.useSuperres());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeAV1PictureInfoFlags.MASK_render_and_frame_size_different,
                size.renderAndFrameSizeDifferent());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeAV1PictureInfoFlags.MASK_allow_screen_content_tools,
                header.allowScreenContentTools());
        Structs.flag(
                std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_is_filter_switchable, header.isFilterSwitchable());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_force_integer_mv, header.forceIntegerMv());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeAV1PictureInfoFlags.MASK_frame_size_override_flag,
                header.frameSizeOverride());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeAV1PictureInfoFlags.MASK_buffer_removal_time_present_flag,
                header.bufferRemovalTimePresent());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_allow_intrabc, header.allowIntrabc());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeAV1PictureInfoFlags.MASK_frame_refs_short_signaling,
                header.frameRefsShortSignaling());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeAV1PictureInfoFlags.MASK_allow_high_precision_mv,
                header.allowHighPrecisionMv());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeAV1PictureInfoFlags.MASK_is_motion_mode_switchable,
                header.isMotionModeSwitchable());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_use_ref_frame_mvs, header.useRefFrameMvs());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeAV1PictureInfoFlags.MASK_disable_frame_end_update_cdf,
                header.disableFrameEndUpdateCdf());
        Structs.flag(
                std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_allow_warped_motion, header.allowWarpedMotion());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_reduced_tx_set, header.reducedTxSet());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_reference_select, header.referenceSelect());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_skip_mode_present, header.skipModePresent());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_delta_q_present, header.deltaQPresent());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_delta_lf_present, header.deltaLfPresent());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_delta_lf_multi, header.deltaLfMulti());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_segmentation_enabled, segmentation.enabled());
        Structs.flag(
                std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_segmentation_update_map, segmentation.updateMap());
        Structs.flag(
                std,
                flags,
                StdVideoDecodeAV1PictureInfoFlags.MASK_segmentation_temporal_update,
                segmentation.temporalUpdate());
        Structs.flag(
                std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_segmentation_update_data, segmentation.updateData());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_UsesLr, restoration.usesLr());
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_usesChromaLr, restoration.usesChromaLr());
        // Grain is the built-in decoder's: a stream with it is not claimed.
        Structs.flag(std, flags, StdVideoDecodeAV1PictureInfoFlags.MASK_apply_grain, false);
        std.set(JAVA_INT, StdVideoDecodeAV1PictureInfo.frame_type, header.frameType());
        std.set(JAVA_INT, StdVideoDecodeAV1PictureInfo.current_frame_id, header.currentFrameId());
        std.set(JAVA_BYTE, StdVideoDecodeAV1PictureInfo.OrderHint, (byte) header.orderHint());
        std.set(JAVA_BYTE, StdVideoDecodeAV1PictureInfo.primary_ref_frame, (byte) header.primaryRefFrame());
        std.set(JAVA_BYTE, StdVideoDecodeAV1PictureInfo.refresh_frame_flags, (byte) header.refreshFrameFlags());
        std.set(JAVA_INT, StdVideoDecodeAV1PictureInfo.interpolation_filter, header.interpolationFilter());
        std.set(JAVA_INT, StdVideoDecodeAV1PictureInfo.TxMode, header.txMode());
        std.set(JAVA_BYTE, StdVideoDecodeAV1PictureInfo.delta_q_res, (byte) header.deltaQRes());
        std.set(JAVA_BYTE, StdVideoDecodeAV1PictureInfo.delta_lf_res, (byte) header.deltaLfRes());
        bytes(std, StdVideoDecodeAV1PictureInfo.SkipModeFrame, header.skipModeFrames());
        std.set(JAVA_BYTE, StdVideoDecodeAV1PictureInfo.coded_denom, (byte) size.codedDenom());
        bytes(std, StdVideoDecodeAV1PictureInfo.OrderHints, header.orderHints());
        var ids = header.expectedFrameIds();
        for (var i = 0; i < ids.size() && i < 8; i++) {
            std.set(JAVA_INT, StdVideoDecodeAV1PictureInfo.expectedFrameId + 4L * i, ids.get(i));
        }
        std.set(ADDRESS, StdVideoDecodeAV1PictureInfo.pTileInfo, tileInfo(arena, header));
        std.set(ADDRESS, StdVideoDecodeAV1PictureInfo.pQuantization, quantization(arena, header));
        std.set(ADDRESS, StdVideoDecodeAV1PictureInfo.pSegmentation, segmentation(arena, header));
        std.set(ADDRESS, StdVideoDecodeAV1PictureInfo.pLoopFilter, loopFilter(arena, header));
        std.set(ADDRESS, StdVideoDecodeAV1PictureInfo.pCDEF, cdef(arena, header));
        std.set(ADDRESS, StdVideoDecodeAV1PictureInfo.pLoopRestoration, loopRestoration(arena, header));
        std.set(ADDRESS, StdVideoDecodeAV1PictureInfo.pGlobalMotion, globalMotion(arena, header));
        std.set(ADDRESS, StdVideoDecodeAV1PictureInfo.pFilmGrain, Structs.allocate(arena, StdVideoAV1FilmGrain.LAYOUT));
        var info = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeAV1PictureInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_AV1_PICTURE_INFO_KHR);
        info.set(ADDRESS, VkStructs.VkVideoDecodeAV1PictureInfoKHR.pStdPictureInfo, std);
        for (var i = 0; i < named.length; i++) {
            info.set(JAVA_INT, VkStructs.VkVideoDecodeAV1PictureInfoKHR.referenceNameSlotIndices + 4L * i, named[i]);
        }
        info.set(JAVA_INT, VkStructs.VkVideoDecodeAV1PictureInfoKHR.frameHeaderOffset, layout.frameHeaderOffset());
        var tiles = layout.tileOffsets().size();
        info.set(JAVA_INT, VkStructs.VkVideoDecodeAV1PictureInfoKHR.tileCount, tiles);
        var offsets = arena.allocate(JAVA_INT, Math.max(tiles, 1));
        var sizes = arena.allocate(JAVA_INT, Math.max(tiles, 1));
        for (var i = 0; i < tiles; i++) {
            offsets.setAtIndex(JAVA_INT, i, layout.tileOffsets().get(i));
            sizes.setAtIndex(JAVA_INT, i, layout.tileSizes().get(i));
        }
        info.set(ADDRESS, VkStructs.VkVideoDecodeAV1PictureInfoKHR.pTileOffsets, offsets);
        info.set(ADDRESS, VkStructs.VkVideoDecodeAV1PictureInfoKHR.pTileSizes, sizes);
        return info;
    }

    /// `VkVideoDecodeAV1DpbSlotInfoKHR` of the frame `header`, stored with
    /// order hint `orderHint`.
    static MemorySegment slotInfo(Arena arena, FrameHeader header, int orderHint) {
        var r = Structs.allocate(arena, StdVideoDecodeAV1ReferenceInfo.LAYOUT);
        var flags = StdVideoDecodeAV1ReferenceInfo.flags;
        Structs.flag(
                r,
                flags,
                StdVideoDecodeAV1ReferenceInfoFlags.MASK_disable_frame_end_update_cdf,
                header.disableFrameEndUpdateCdf());
        Structs.flag(
                r,
                flags,
                StdVideoDecodeAV1ReferenceInfoFlags.MASK_segmentation_enabled,
                header.segmentation().enabled());
        r.set(JAVA_BYTE, StdVideoDecodeAV1ReferenceInfo.frame_type, (byte) header.frameType());
        r.set(JAVA_BYTE, StdVideoDecodeAV1ReferenceInfo.RefFrameSignBias, (byte) header.refFrameSignBias());
        r.set(JAVA_BYTE, StdVideoDecodeAV1ReferenceInfo.OrderHint, (byte) orderHint);
        bytes(r, StdVideoDecodeAV1ReferenceInfo.SavedOrderHints, header.orderHints());
        var slot = Structs.allocate(
                arena,
                VkStructs.VkVideoDecodeAV1DpbSlotInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_AV1_DPB_SLOT_INFO_KHR);
        slot.set(ADDRESS, VkStructs.VkVideoDecodeAV1DpbSlotInfoKHR.pStdReferenceInfo, r);
        return slot;
    }

    /// The slot info of the frame stored in `reference`.
    static MemorySegment slotInfo(Arena arena, ReferenceFrame reference) {
        return slotInfo(arena, reference.header(), reference.orderHint());
    }

    private static MemorySegment tileInfo(Arena arena, FrameHeader header) {
        var tiles = header.tileInfo();
        var t = Structs.allocate(arena, StdVideoAV1TileInfo.LAYOUT);
        Structs.flag(
                t,
                StdVideoAV1TileInfo.flags,
                StdVideoAV1TileInfoFlags.MASK_uniform_tile_spacing_flag,
                tiles.uniformTileSpacing());
        t.set(JAVA_BYTE, StdVideoAV1TileInfo.TileCols, (byte) tiles.tileCols());
        t.set(JAVA_BYTE, StdVideoAV1TileInfo.TileRows, (byte) tiles.tileRows());
        t.set(JAVA_SHORT, StdVideoAV1TileInfo.context_update_tile_id, (short) tiles.contextUpdateTileId());
        t.set(JAVA_BYTE, StdVideoAV1TileInfo.tile_size_bytes_minus_1, (byte) tiles.tileSizeBytesMinus1());
        t.set(ADDRESS, StdVideoAV1TileInfo.pMiColStarts, shorts(arena, tiles.miColStarts()));
        t.set(ADDRESS, StdVideoAV1TileInfo.pMiRowStarts, shorts(arena, tiles.miRowStarts()));
        t.set(ADDRESS, StdVideoAV1TileInfo.pWidthInSbsMinus1, shorts(arena, tiles.widthInSbsMinus1()));
        t.set(ADDRESS, StdVideoAV1TileInfo.pHeightInSbsMinus1, shorts(arena, tiles.heightInSbsMinus1()));
        return t;
    }

    private static MemorySegment quantization(Arena arena, FrameHeader header) {
        var q = header.quantization();
        var s = Structs.allocate(arena, StdVideoAV1Quantization.LAYOUT);
        Structs.flag(
                s, StdVideoAV1Quantization.flags, StdVideoAV1QuantizationFlags.MASK_using_qmatrix, q.usingQmatrix());
        Structs.flag(
                s, StdVideoAV1Quantization.flags, StdVideoAV1QuantizationFlags.MASK_diff_uv_delta, q.diffUvDelta());
        s.set(JAVA_BYTE, StdVideoAV1Quantization.base_q_idx, (byte) q.baseQIdx());
        s.set(JAVA_BYTE, StdVideoAV1Quantization.DeltaQYDc, (byte) q.deltaQYDc());
        s.set(JAVA_BYTE, StdVideoAV1Quantization.DeltaQUDc, (byte) q.deltaQUDc());
        s.set(JAVA_BYTE, StdVideoAV1Quantization.DeltaQUAc, (byte) q.deltaQUAc());
        s.set(JAVA_BYTE, StdVideoAV1Quantization.DeltaQVDc, (byte) q.deltaQVDc());
        s.set(JAVA_BYTE, StdVideoAV1Quantization.DeltaQVAc, (byte) q.deltaQVAc());
        s.set(JAVA_BYTE, StdVideoAV1Quantization.qm_y, (byte) q.qmY());
        s.set(JAVA_BYTE, StdVideoAV1Quantization.qm_u, (byte) q.qmU());
        s.set(JAVA_BYTE, StdVideoAV1Quantization.qm_v, (byte) q.qmV());
        return s;
    }

    private static MemorySegment segmentation(Arena arena, FrameHeader header) {
        var segmentation = header.segmentation();
        var s = Structs.allocate(arena, StdVideoAV1Segmentation.LAYOUT);
        bytes(s, StdVideoAV1Segmentation.FeatureEnabled, segmentation.featureEnabled());
        var data = segmentation.featureData();
        for (var i = 0; i < data.size(); i++) {
            s.set(JAVA_SHORT, StdVideoAV1Segmentation.FeatureData + 2L * i, (short) (int) data.get(i));
        }
        return s;
    }

    private static MemorySegment loopFilter(Arena arena, FrameHeader header) {
        var lf = header.loopFilter();
        var l = Structs.allocate(arena, StdVideoAV1LoopFilter.LAYOUT);
        Structs.flag(
                l,
                StdVideoAV1LoopFilter.flags,
                StdVideoAV1LoopFilterFlags.MASK_loop_filter_delta_enabled,
                lf.deltaEnabled());
        Structs.flag(
                l,
                StdVideoAV1LoopFilter.flags,
                StdVideoAV1LoopFilterFlags.MASK_loop_filter_delta_update,
                lf.deltaUpdate());
        bytes(l, StdVideoAV1LoopFilter.loop_filter_level, lf.level());
        l.set(JAVA_BYTE, StdVideoAV1LoopFilter.loop_filter_sharpness, (byte) lf.sharpness());
        l.set(JAVA_BYTE, StdVideoAV1LoopFilter.update_ref_delta, (byte) lf.updateRefDelta());
        bytes(l, StdVideoAV1LoopFilter.loop_filter_ref_deltas, lf.refDeltas());
        l.set(JAVA_BYTE, StdVideoAV1LoopFilter.update_mode_delta, (byte) lf.updateModeDelta());
        bytes(l, StdVideoAV1LoopFilter.loop_filter_mode_deltas, lf.modeDeltas());
        return l;
    }

    private static MemorySegment cdef(Arena arena, FrameHeader header) {
        var cdef = header.cdef();
        var c = Structs.allocate(arena, StdVideoAV1CDEF.LAYOUT);
        c.set(JAVA_BYTE, StdVideoAV1CDEF.cdef_damping_minus_3, (byte) cdef.dampingMinus3());
        c.set(JAVA_BYTE, StdVideoAV1CDEF.cdef_bits, (byte) cdef.bits());
        bytes(c, StdVideoAV1CDEF.cdef_y_pri_strength, cdef.yPriStrength());
        bytes(c, StdVideoAV1CDEF.cdef_y_sec_strength, cdef.ySecStrength());
        bytes(c, StdVideoAV1CDEF.cdef_uv_pri_strength, cdef.uvPriStrength());
        bytes(c, StdVideoAV1CDEF.cdef_uv_sec_strength, cdef.uvSecStrength());
        return c;
    }

    /// `LoopRestorationSize` as Vulkan takes it, as FFmpeg passes it: the log2
    /// of the size in samples, less 5.
    private static MemorySegment loopRestoration(Arena arena, FrameHeader header) {
        var restoration = header.loopRestoration();
        var r = Structs.allocate(arena, StdVideoAV1LoopRestoration.LAYOUT);
        for (var plane = 0; plane < 3; plane++) {
            r.set(
                    JAVA_INT,
                    StdVideoAV1LoopRestoration.FrameRestorationType + 4L * plane,
                    restoration.type().get(plane));
            var samples = restoration.loopRestorationSize(plane);
            var code = samples > 0 ? Integer.numberOfTrailingZeros(samples) - 5 : 0;
            r.set(JAVA_SHORT, StdVideoAV1LoopRestoration.LoopRestorationSize + 2L * plane, (short) Math.max(code, 0));
        }
        return r;
    }

    private static MemorySegment globalMotion(Arena arena, FrameHeader header) {
        var motion = header.globalMotion();
        var g = Structs.allocate(arena, StdVideoAV1GlobalMotion.LAYOUT);
        bytes(g, StdVideoAV1GlobalMotion.GmType, motion.type());
        for (var ref = 0; ref < motion.params().size() && ref < 8; ref++) {
            var params = motion.params().get(ref);
            for (var i = 0; i < params.size() && i < 6; i++) {
                g.set(JAVA_INT, StdVideoAV1GlobalMotion.gm_params + 24L * ref + 4L * i, params.get(i));
            }
        }
        return g;
    }

    /// `values` as bytes from `offset`.
    private static void bytes(MemorySegment struct, long offset, List<Integer> values) {
        for (var i = 0; i < values.size(); i++) {
            struct.set(JAVA_BYTE, offset + i, (byte) (int) values.get(i));
        }
    }

    /// `values` as a `uint16_t` array.
    private static MemorySegment shorts(Arena arena, List<Integer> values) {
        var array = arena.allocate(JAVA_SHORT, Math.max(values.size(), 1));
        for (var i = 0; i < values.size(); i++) {
            array.setAtIndex(JAVA_SHORT, i, (short) (int) values.get(i));
        }
        return array;
    }
}
