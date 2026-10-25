package dev.goldberry.media.bitstream.h264;

import java.util.Map;
import java.util.Optional;

import dev.goldberry.media.bitstream.BitReader;

/// A picture parameter set (H.264 §7.3.2.2), every field Vulkan Video's
/// `StdVideoH264PictureParameterSet` reads. Slice groups (FMO), which only the
/// Baseline and Extended profiles have, are refused.
public record PictureParameterSet(
        int id,
        int spsId,
        boolean entropyCodingMode,
        boolean bottomFieldPicOrderInFramePresent,
        int numRefIdxL0DefaultActiveMinus1,
        int numRefIdxL1DefaultActiveMinus1,
        boolean weightedPred,
        int weightedBipredIdc,
        int picInitQpMinus26,
        int picInitQsMinus26,
        int chromaQpIndexOffset,
        boolean deblockingFilterControlPresent,
        boolean constrainedIntraPred,
        boolean redundantPicCntPresent,
        boolean transform8x8Mode,
        Optional<ScalingLists> scalingLists,
        int secondChromaQpIndexOffset) {

    /// The PPS whose RBSP, after the one-byte NAL header, is `rbsp`; the SPS it
    /// names is looked up in `spss` for its chroma format and scaling lists.
    ///
    /// @throws IllegalArgumentException when it is malformed, uses slice
    ///                                  groups, or names an SPS not there
    public static PictureParameterSet parse(byte[] rbsp, Map<Integer, SequenceParameterSet> spss) {
        var r = new BitReader(rbsp);
        var id = r.ue();
        var spsId = r.ue();
        if (id > 255 || spsId > 31) {
            throw new IllegalArgumentException("PPS " + id + " of SPS " + spsId);
        }
        var sps = spss.get(spsId);
        if (sps == null) {
            throw new IllegalArgumentException("a PPS of SPS " + spsId + ", which is not there");
        }
        var cabac = r.flag();
        var bottom = r.flag();
        if (r.ue() != 0) { // num_slice_groups_minus1
            throw new IllegalArgumentException("a PPS with slice groups");
        }
        var l0 = r.ue();
        var l1 = r.ue();
        if (l0 > 31 || l1 > 31) {
            throw new IllegalArgumentException("default active references " + (l0 + 1) + ", " + (l1 + 1));
        }
        var weighted = r.flag();
        var bipred = r.bits(2);
        var qp = r.se();
        var qs = r.se();
        var chromaOffset = r.se();
        var deblocking = r.flag();
        var constrained = r.flag();
        var redundant = r.flag();
        var transform8x8 = false;
        Optional<ScalingLists> lists = Optional.empty();
        var secondOffset = chromaOffset;
        if (r.moreRbspData()) {
            transform8x8 = r.flag();
            if (r.flag()) { // pic_scaling_matrix_present_flag
                var count = 6 + (sps.chromaFormatIdc() != 3 ? 2 : 6) * (transform8x8 ? 1 : 0);
                lists = Optional.of(ScalingLists.read(r, count, sps.sequenceLists()));
            }
            secondOffset = r.se();
        }
        return new PictureParameterSet(
                id,
                spsId,
                cabac,
                bottom,
                l0,
                l1,
                weighted,
                bipred,
                qp,
                qs,
                chromaOffset,
                deblocking,
                constrained,
                redundant,
                transform8x8,
                lists,
                secondOffset);
    }
}
