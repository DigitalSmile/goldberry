package dev.goldberry.media.bitstream.hevc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.media.bitstream.BitReader;

/// A sequence parameter set (H.265 §7.3.2.2), every field a decoder of the
/// base layer and Vulkan Video's `StdVideoH265SequenceParameterSet` read, with
/// the range extension's flags. A stream with the multilayer, 3D or screen
/// content extensions is refused: none of them is a profile this reads.
///
/// @param vpsId                         `sps_video_parameter_set_id`
/// @param maxSubLayersMinus1            `sps_max_sub_layers_minus1`
/// @param temporalIdNesting             `sps_temporal_id_nesting_flag`
/// @param profileTierLevel              `profile_tier_level()`
/// @param id                            `sps_seq_parameter_set_id`
/// @param chromaFormatIdc               `chroma_format_idc`
/// @param separateColourPlane           `separate_colour_plane_flag`
/// @param width                         `pic_width_in_luma_samples`
/// @param height                        `pic_height_in_luma_samples`
/// @param conformanceWindow             `conformance_window_flag`
/// @param confWinLeft                   `conf_win_left_offset`
/// @param confWinRight                  `conf_win_right_offset`
/// @param confWinTop                    `conf_win_top_offset`
/// @param confWinBottom                 `conf_win_bottom_offset`
/// @param bitDepthLumaMinus8            `bit_depth_luma_minus8`
/// @param bitDepthChromaMinus8          `bit_depth_chroma_minus8`
/// @param log2MaxPocLsbMinus4           `log2_max_pic_order_cnt_lsb_minus4`
/// @param subLayerOrderingInfoPresent   `sps_sub_layer_ordering_info_present_flag`
/// @param ordering                      each sub-layer's limits
/// @param log2MinLumaCodingBlockSizeMinus3     the field of that name
/// @param log2DiffMaxMinLumaCodingBlockSize    the field of that name
/// @param log2MinLumaTransformBlockSizeMinus2  the field of that name
/// @param log2DiffMaxMinLumaTransformBlockSize the field of that name
/// @param maxTransformHierarchyDepthInter      the field of that name
/// @param maxTransformHierarchyDepthIntra      the field of that name
/// @param scalingListEnabled            `scaling_list_enabled_flag`
/// @param scalingListDataPresent        `sps_scaling_list_data_present_flag`
/// @param scalingLists                  the lists, when enabled: the coded
///                                      ones or the defaults
/// @param ampEnabled                    `amp_enabled_flag`
/// @param saoEnabled                    `sample_adaptive_offset_enabled_flag`
/// @param pcmEnabled                    `pcm_enabled_flag`
/// @param pcmBitDepthLumaMinus1         `pcm_sample_bit_depth_luma_minus1`
/// @param pcmBitDepthChromaMinus1       `pcm_sample_bit_depth_chroma_minus1`
/// @param log2MinPcmLumaCodingBlockSizeMinus3  the field of that name
/// @param log2DiffMaxMinPcmLumaCodingBlockSize the field of that name
/// @param pcmLoopFilterDisabled         `pcm_loop_filter_disabled_flag`
/// @param shortTermRefPicSets           `st_ref_pic_set(i)` for every `i`
/// @param longTermRefPicsPresent        `long_term_ref_pics_present_flag`
/// @param ltRefPicPocLsbSps             `lt_ref_pic_poc_lsb_sps[i]`
/// @param usedByCurrPicLtSpsFlags       `used_by_curr_pic_lt_sps_flag[i]`, bit `i`
/// @param temporalMvpEnabled            `sps_temporal_mvp_enabled_flag`
/// @param strongIntraSmoothingEnabled   `strong_intra_smoothing_enabled_flag`
/// @param vui                           `vui_parameters()`, when present
/// @param extensionPresent              `sps_extension_present_flag`
/// @param rangeExtension                `sps_range_extension_flag`
/// @param rangeExtensionFlags           the range extension's nine flags, from
///                                      `transform_skip_rotation_enabled_flag`
///                                      at bit 8 to
///                                      `cabac_bypass_alignment_enabled_flag` at
///                                      bit 0, in coded order
public record SequenceParameterSet(
        int vpsId,
        int maxSubLayersMinus1,
        boolean temporalIdNesting,
        ProfileTierLevel profileTierLevel,
        int id,
        int chromaFormatIdc,
        boolean separateColourPlane,
        int width,
        int height,
        boolean conformanceWindow,
        int confWinLeft,
        int confWinRight,
        int confWinTop,
        int confWinBottom,
        int bitDepthLumaMinus8,
        int bitDepthChromaMinus8,
        int log2MaxPocLsbMinus4,
        boolean subLayerOrderingInfoPresent,
        List<SubLayerOrdering> ordering,
        int log2MinLumaCodingBlockSizeMinus3,
        int log2DiffMaxMinLumaCodingBlockSize,
        int log2MinLumaTransformBlockSizeMinus2,
        int log2DiffMaxMinLumaTransformBlockSize,
        int maxTransformHierarchyDepthInter,
        int maxTransformHierarchyDepthIntra,
        boolean scalingListEnabled,
        boolean scalingListDataPresent,
        Optional<ScalingLists> scalingLists,
        boolean ampEnabled,
        boolean saoEnabled,
        boolean pcmEnabled,
        int pcmBitDepthLumaMinus1,
        int pcmBitDepthChromaMinus1,
        int log2MinPcmLumaCodingBlockSizeMinus3,
        int log2DiffMaxMinPcmLumaCodingBlockSize,
        boolean pcmLoopFilterDisabled,
        List<ShortTermRefPicSet> shortTermRefPicSets,
        boolean longTermRefPicsPresent,
        List<Integer> ltRefPicPocLsbSps,
        int usedByCurrPicLtSpsFlags,
        boolean temporalMvpEnabled,
        boolean strongIntraSmoothingEnabled,
        Optional<Vui> vui,
        boolean extensionPresent,
        boolean rangeExtension,
        int rangeExtensionFlags) {

    /// The most short-term sets an SPS codes (§7.4.3.2.1).
    public static final int MAX_SHORT_TERM_SETS = 64;

    /// The most long-term pictures an SPS lists.
    public static final int MAX_LONG_TERM_SPS = 32;

    public SequenceParameterSet {
        ordering = List.copyOf(ordering);
        shortTermRefPicSets = List.copyOf(shortTermRefPicSets);
        ltRefPicPocLsbSps = List.copyOf(ltRefPicPocLsbSps);
    }

    /// The SPS whose RBSP, after the two-byte NAL header, is `rbsp`.
    ///
    /// @throws IllegalArgumentException when it is malformed, or uses an
    ///                                  extension this does not read
    public static SequenceParameterSet parse(byte[] rbsp) {
        var r = new BitReader(rbsp);
        var vps = r.bits(4);
        var maxSubLayersMinus1 = r.bits(3);
        if (maxSubLayersMinus1 > 6) {
            throw new IllegalArgumentException("sps_max_sub_layers_minus1 " + maxSubLayersMinus1);
        }
        var nesting = r.flag();
        var ptl = ProfileTierLevel.read(r, maxSubLayersMinus1);
        var id = r.ue();
        if (id > 15) {
            throw new IllegalArgumentException("sps_seq_parameter_set_id " + id);
        }
        var chroma = r.ue();
        if (chroma > 3) {
            throw new IllegalArgumentException("chroma_format_idc " + chroma);
        }
        var separate = chroma == 3 && r.flag();
        var width = r.ue();
        var height = r.ue();
        var window = r.flag();
        var left = 0;
        var right = 0;
        var top = 0;
        var bottom = 0;
        if (window) {
            left = r.ue();
            right = r.ue();
            top = r.ue();
            bottom = r.ue();
        }
        var lumaDepth = r.ue();
        var chromaDepth = r.ue();
        var pocLsb = r.ue();
        if (lumaDepth > 8 || chromaDepth > 8 || pocLsb > 12) {
            throw new IllegalArgumentException(
                    "bit depths " + (lumaDepth + 8) + " and " + (chromaDepth + 8) + ", POC LSBs " + (pocLsb + 4));
        }
        var present = r.flag();
        var ordering = VideoParameterSet.ordering(r, present, maxSubLayersMinus1);
        var minCb = r.ue();
        var diffCb = r.ue();
        var minTb = r.ue();
        var diffTb = r.ue();
        var depthInter = r.ue();
        var depthIntra = r.ue();
        var scalingEnabled = r.flag();
        var scalingPresent = false;
        Optional<ScalingLists> lists = Optional.empty();
        if (scalingEnabled) {
            scalingPresent = r.flag();
            lists = Optional.of(scalingPresent ? ScalingLists.read(r) : ScalingLists.defaults());
        }
        var amp = r.flag();
        var sao = r.flag();
        var pcm = r.flag();
        var pcmLuma = 0;
        var pcmChroma = 0;
        var pcmMin = 0;
        var pcmDiff = 0;
        var pcmLoop = false;
        if (pcm) {
            pcmLuma = r.bits(4);
            pcmChroma = r.bits(4);
            pcmMin = r.ue();
            pcmDiff = r.ue();
            pcmLoop = r.flag();
        }
        var setCount = r.ue();
        if (setCount > MAX_SHORT_TERM_SETS) {
            throw new IllegalArgumentException("num_short_term_ref_pic_sets " + setCount);
        }
        var sets = new ArrayList<ShortTermRefPicSet>(setCount);
        for (var i = 0; i < setCount; i++) {
            sets.add(ShortTermRefPicSet.read(r, i, sets));
        }
        var longTerm = r.flag();
        var ltLsbs = new ArrayList<Integer>();
        var ltUsed = 0;
        if (longTerm) {
            var count = r.ue();
            if (count > MAX_LONG_TERM_SPS) {
                throw new IllegalArgumentException("num_long_term_ref_pics_sps " + count);
            }
            for (var i = 0; i < count; i++) {
                ltLsbs.add(r.bits(pocLsb + 4));
                if (r.flag()) {
                    ltUsed |= 1 << i;
                }
            }
        }
        var tmvp = r.flag();
        var smoothing = r.flag();
        Optional<Vui> vui = r.flag() ? Optional.of(Vui.read(r, maxSubLayersMinus1)) : Optional.empty();
        var extension = r.flag();
        var range = false;
        var rangeFlags = 0;
        if (extension) {
            range = r.flag();
            var multilayer = r.flag();
            var threeD = r.flag();
            var scc = r.flag();
            r.skip(4); // sps_extension_4bits
            if (range) {
                rangeFlags = r.bits(9);
            }
            if (multilayer || threeD || scc) {
                throw new IllegalArgumentException("an SPS with the "
                        + (multilayer ? "multilayer" : threeD ? "3D" : "screen content") + " extension");
            }
        }
        return new SequenceParameterSet(
                vps,
                maxSubLayersMinus1,
                nesting,
                ptl,
                id,
                chroma,
                separate,
                width,
                height,
                window,
                left,
                right,
                top,
                bottom,
                lumaDepth,
                chromaDepth,
                pocLsb,
                present,
                ordering,
                minCb,
                diffCb,
                minTb,
                diffTb,
                depthInter,
                depthIntra,
                scalingEnabled,
                scalingPresent,
                lists,
                amp,
                sao,
                pcm,
                pcmLuma,
                pcmChroma,
                pcmMin,
                pcmDiff,
                pcmLoop,
                sets,
                longTerm,
                ltLsbs,
                ltUsed,
                tmvp,
                smoothing,
                vui,
                extension,
                range,
                rangeFlags);
    }

    /// `MaxPicOrderCntLsb`.
    public int maxPocLsb() {
        return 1 << (log2MaxPocLsbMinus4 + 4);
    }

    /// `CtbLog2SizeY`.
    public int ctbLog2Size() {
        return log2MinLumaCodingBlockSizeMinus3 + 3 + log2DiffMaxMinLumaCodingBlockSize;
    }

    /// `PicSizeInCtbsY`.
    public int picSizeInCtbs() {
        var ctb = 1 << ctbLog2Size();
        return ((width + ctb - 1) / ctb) * ((height + ctb - 1) / ctb);
    }

    /// The highest sub-layer's limits: what a player decoding every layer
    /// keeps to.
    public SubLayerOrdering highest() {
        return ordering.get(maxSubLayersMinus1);
    }

    /// The visible width: the coded width less the conformance window.
    public int visibleWidth() {
        var unit = chromaFormatIdc == 1 || chromaFormatIdc == 2 ? 2 : 1;
        return width - unit * (confWinLeft + confWinRight);
    }

    /// The visible height.
    public int visibleHeight() {
        var unit = chromaFormatIdc == 1 ? 2 : 1;
        return height - unit * (confWinTop + confWinBottom);
    }
}
