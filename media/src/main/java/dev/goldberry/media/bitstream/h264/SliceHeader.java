package dev.goldberry.media.bitstream.h264;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.goldberry.media.bitstream.BitReader;

/// A slice header (H.264 §7.3.3) as far as `dec_ref_pic_marking()`: what a
/// decoder handed whole slices needs to place the picture in order, mark the
/// references, and name them. The rest is the decoder's.
///
/// @param nal                         the NAL unit's header
/// @param firstMbInSlice              `first_mb_in_slice`
/// @param sliceType                   `slice_type` mod 5: 0 P, 1 B, 2 I, 3 SP,
///                                    4 SI
/// @param ppsId                       `pic_parameter_set_id`
/// @param frameNum                    `frame_num`
/// @param fieldPic                    `field_pic_flag`
/// @param bottomField                 `bottom_field_flag`
/// @param idrPicId                    `idr_pic_id`
/// @param pocLsb                      `pic_order_cnt_lsb`
/// @param deltaPocBottom              `delta_pic_order_cnt_bottom`
/// @param deltaPoc0                   `delta_pic_order_cnt[0]`
/// @param deltaPoc1                   `delta_pic_order_cnt[1]`
/// @param noOutputOfPriorPics         `no_output_of_prior_pics_flag`
/// @param longTermReference           `long_term_reference_flag`
/// @param adaptiveRefPicMarking       `adaptive_ref_pic_marking_mode_flag`
/// @param operations                  each `memory_management_control_operation`
///                                    with its arguments, in order
/// @param bits                        the bits read, to the end of
///                                    `dec_ref_pic_marking()`
public record SliceHeader(
        NalHeader nal,
        int firstMbInSlice,
        int sliceType,
        int ppsId,
        int frameNum,
        boolean fieldPic,
        boolean bottomField,
        int idrPicId,
        int pocLsb,
        int deltaPocBottom,
        int deltaPoc0,
        int deltaPoc1,
        boolean noOutputOfPriorPics,
        boolean longTermReference,
        boolean adaptiveRefPicMarking,
        List<MemoryManagement> operations,
        long bits) {

    /// One `memory_management_control_operation` (§7.4.3.3).
    ///
    /// @param operation           1 to 6
    /// @param differenceOfPicNums `difference_of_pic_nums_minus1` + 1
    /// @param longTermPicNum      `long_term_pic_num`
    /// @param longTermFrameIdx    `long_term_frame_idx`
    /// @param maxLongTermFrameIdxPlus1 `max_long_term_frame_idx_plus1`
    public record MemoryManagement(
            int operation,
            int differenceOfPicNums,
            int longTermPicNum,
            int longTermFrameIdx,
            int maxLongTermFrameIdxPlus1) {}

    public SliceHeader {
        operations = List.copyOf(operations);
    }

    /// Whether it is an I or SI slice.
    public boolean intra() {
        return sliceType == 2 || sliceType == 4;
    }

    /// The header of the slice whose RBSP, after the one-byte NAL header
    /// `nal`, is `rbsp`.
    ///
    /// @throws IllegalArgumentException when it is malformed or names a
    ///                                  parameter set not there
    public static SliceHeader parse(
            NalHeader nal,
            byte[] rbsp,
            Map<Integer, SequenceParameterSet> spss,
            Map<Integer, PictureParameterSet> ppss) {
        var r = new BitReader(rbsp);
        var firstMb = r.ue();
        var type = r.ue();
        if (type > 9) {
            throw new IllegalArgumentException("slice_type " + type);
        }
        type %= 5;
        var ppsId = r.ue();
        var pps = ppss.get(ppsId);
        if (pps == null) {
            throw new IllegalArgumentException("a slice of PPS " + ppsId + ", which is not there");
        }
        var sps = spss.get(pps.spsId());
        if (sps == null) {
            throw new IllegalArgumentException("PPS " + ppsId + " of SPS " + pps.spsId() + ", which is not there");
        }
        if (sps.separateColourPlane()) {
            r.skip(2); // colour_plane_id
        }
        var frameNum = r.bits(sps.log2MaxFrameNumMinus4() + 4);
        var field = false;
        var bottom = false;
        if (!sps.frameMbsOnly()) {
            field = r.flag();
            if (field) {
                bottom = r.flag();
            }
        }
        var idr = nal.isIdr();
        var idrPicId = idr ? r.ue() : 0;
        var lsb = 0;
        var deltaBottom = 0;
        var delta0 = 0;
        var delta1 = 0;
        if (sps.picOrderCntType() == 0) {
            lsb = r.bits(sps.log2MaxPocLsbMinus4() + 4);
            if (pps.bottomFieldPicOrderInFramePresent() && !field) {
                deltaBottom = r.se();
            }
        }
        if (sps.picOrderCntType() == 1 && !sps.deltaPicOrderAlwaysZero()) {
            delta0 = r.se();
            if (pps.bottomFieldPicOrderInFramePresent() && !field) {
                delta1 = r.se();
            }
        }
        if (pps.redundantPicCntPresent()) {
            r.ue(); // redundant_pic_cnt
        }
        var b = type == 1;
        var p = type == 0 || type == 3;
        if (b) {
            r.skip(1); // direct_spatial_mv_pred_flag
        }
        var l0 = pps.numRefIdxL0DefaultActiveMinus1();
        var l1 = pps.numRefIdxL1DefaultActiveMinus1();
        if (p || b) {
            if (r.flag()) { // num_ref_idx_active_override_flag
                l0 = r.ue();
                if (b) {
                    l1 = r.ue();
                }
            }
        }
        if (type != 2 && type != 4) {
            skipModifications(r);
            if (b) {
                skipModifications(r);
            }
        }
        if ((pps.weightedPred() && p) || (pps.weightedBipredIdc() == 1 && b)) {
            r.ue(); // luma_log2_weight_denom
            var chroma = sps.chromaArrayType() != 0;
            if (chroma) {
                r.ue(); // chroma_log2_weight_denom
            }
            skipWeights(r, l0, chroma);
            if (b) {
                skipWeights(r, l1, chroma);
            }
        }
        var noOutput = false;
        var longTerm = false;
        var adaptive = false;
        var operations = new ArrayList<MemoryManagement>();
        if (nal.refIdc() != 0) {
            if (idr) {
                noOutput = r.flag();
                longTerm = r.flag();
            } else {
                adaptive = r.flag();
                if (adaptive) {
                    for (var operation = r.ue(); operation != 0; operation = r.ue()) {
                        if (operation > 6 || operations.size() > 66) {
                            throw new IllegalArgumentException("memory_management_control_operation " + operation);
                        }
                        var difference = operation == 1 || operation == 3 ? r.ue() + 1 : 0;
                        var picNum = operation == 2 ? r.ue() : 0;
                        var frameIdx = operation == 3 || operation == 6 ? r.ue() : 0;
                        var max = operation == 4 ? r.ue() : 0;
                        operations.add(new MemoryManagement(operation, difference, picNum, frameIdx, max));
                    }
                }
            }
        }
        return new SliceHeader(
                nal,
                firstMb,
                type,
                ppsId,
                frameNum,
                field,
                bottom,
                idrPicId,
                lsb,
                deltaBottom,
                delta0,
                delta1,
                noOutput,
                longTerm,
                adaptive,
                operations,
                r.position());
    }

    /// Passes over one `ref_pic_list_modification()` list.
    private static void skipModifications(BitReader r) {
        if (!r.flag()) {
            return;
        }
        for (var count = 0; ; count++) {
            var idc = r.ue();
            if (idc == 3) {
                return;
            }
            if (idc > 5 || count > 64) {
                throw new IllegalArgumentException("modification_of_pic_nums_idc " + idc);
            }
            r.ue();
        }
    }

    /// Passes over one list of `pred_weight_table()`.
    private static void skipWeights(BitReader r, int activeMinus1, boolean chroma) {
        for (var i = 0; i <= activeMinus1; i++) {
            if (r.flag()) { // luma_weight_flag
                r.se();
                r.se();
            }
            if (chroma && r.flag()) { // chroma_weight_flag
                for (var j = 0; j < 2; j++) {
                    r.se();
                    r.se();
                }
            }
        }
    }
}
