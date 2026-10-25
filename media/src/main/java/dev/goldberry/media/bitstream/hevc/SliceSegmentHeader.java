package dev.goldberry.media.bitstream.hevc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.goldberry.media.bitstream.BitReader;

/// The start of a slice segment header (H.265 §7.3.6.1): as far as the
/// long-term reference pictures, which is what a decoder handed whole slices
/// needs from it to place the picture in order and to name its references.
/// The rest of the header is the decoder's.
///
/// @param nal                          the NAL unit's header
/// @param firstSliceSegmentInPic       `first_slice_segment_in_pic_flag`
/// @param noOutputOfPriorPics          `no_output_of_prior_pics_flag`
/// @param ppsId                        `slice_pic_parameter_set_id`
/// @param dependentSliceSegment        `dependent_slice_segment_flag`
/// @param sliceSegmentAddress          `slice_segment_address`
/// @param sliceType                    `slice_type`: 0 B, 1 P, 2 I; −1 for a
///                                     dependent segment, which takes its
///                                     slice's
/// @param picOutput                    `pic_output_flag`, true when not coded
/// @param pocLsb                       `slice_pic_order_cnt_lsb`, 0 for an IDR
/// @param shortTermRefPicSetSps        `short_term_ref_pic_set_sps_flag`
/// @param shortTermRefPicSetIdx        `short_term_ref_pic_set_idx`
/// @param shortTermRefPicSet           the set in use: the SPS's chosen one or
///                                     the slice's own; empty for an IDR
/// @param numBitsForShortTermRefPicSet `NumBitsForSTRefPicSetInSlice`
/// @param numLongTermSps               `num_long_term_sps`
/// @param numLongTermPics              `num_long_term_pics`
/// @param longTerm                     each long-term picture, the SPS's then
///                                     the slice's
public record SliceSegmentHeader(
        NalHeader nal,
        boolean firstSliceSegmentInPic,
        boolean noOutputOfPriorPics,
        int ppsId,
        boolean dependentSliceSegment,
        int sliceSegmentAddress,
        int sliceType,
        boolean picOutput,
        int pocLsb,
        boolean shortTermRefPicSetSps,
        int shortTermRefPicSetIdx,
        Optional<ShortTermRefPicSet> shortTermRefPicSet,
        int numBitsForShortTermRefPicSet,
        int numLongTermSps,
        int numLongTermPics,
        List<LongTerm> longTerm) {

    /// One long-term reference picture of the slice (§7.4.7.1).
    ///
    /// @param pocLsb          `PocLsbLt`
    /// @param usedByCurrPic   `UsedByCurrPicLt`
    /// @param msbPresent      `delta_poc_msb_present_flag`
    /// @param deltaPocMsbCycle `DeltaPocMsbCycleLt`, accumulated as §7.4.7.1
    ///                        says
    public record LongTerm(int pocLsb, boolean usedByCurrPic, boolean msbPresent, int deltaPocMsbCycle) {}

    public SliceSegmentHeader {
        longTerm = List.copyOf(longTerm);
    }

    /// The header of the slice segment whose RBSP, after the two-byte NAL
    /// header `nal`, is `rbsp`, with the parameter sets in force.
    ///
    /// @throws IllegalArgumentException when it is malformed, or names a
    ///                                  parameter set that is not there
    public static SliceSegmentHeader parse(
            NalHeader nal,
            byte[] rbsp,
            Map<Integer, SequenceParameterSet> spss,
            Map<Integer, PictureParameterSet> ppss) {
        var r = new BitReader(rbsp);
        var first = r.flag();
        var noOutput = nal.isIrap() && r.flag();
        var ppsId = r.ue();
        var pps = ppss.get(ppsId);
        if (pps == null) {
            throw new IllegalArgumentException("a slice of PPS " + ppsId + ", which is not there");
        }
        var sps = spss.get(pps.spsId());
        if (sps == null) {
            throw new IllegalArgumentException("PPS " + ppsId + " of SPS " + pps.spsId() + ", which is not there");
        }
        var dependent = false;
        var address = 0;
        if (!first) {
            if (pps.dependentSliceSegmentsEnabled()) {
                dependent = r.flag();
            }
            address = r.bits(ceilLog2(sps.picSizeInCtbs()));
        }
        var type = -1;
        var output = true;
        var pocLsb = 0;
        var spsFlag = false;
        var index = 0;
        Optional<ShortTermRefPicSet> set = Optional.empty();
        var bits = 0;
        var numSps = 0;
        var numPics = 0;
        var longTerm = new ArrayList<LongTerm>();
        if (!dependent) {
            r.skip(pps.numExtraSliceHeaderBits()); // slice_reserved_flag
            type = r.ue();
            if (type > 2) {
                throw new IllegalArgumentException("slice_type " + type);
            }
            if (pps.outputFlagPresent()) {
                output = r.flag();
            }
            if (sps.separateColourPlane()) {
                r.skip(2); // colour_plane_id
            }
            if (!nal.isIdr()) {
                pocLsb = r.bits(sps.log2MaxPocLsbMinus4() + 4);
                spsFlag = r.flag();
                var sets = sps.shortTermRefPicSets();
                if (!spsFlag) {
                    var own = ShortTermRefPicSet.read(r, sets.size(), sets);
                    bits = own.bits();
                    set = Optional.of(own);
                } else {
                    if (sets.isEmpty()) {
                        throw new IllegalArgumentException("a slice that uses the SPS's sets, which has none");
                    }
                    if (sets.size() > 1) {
                        index = r.bits(ceilLog2(sets.size()));
                    }
                    if (index >= sets.size()) {
                        throw new IllegalArgumentException("short_term_ref_pic_set_idx " + index);
                    }
                    set = Optional.of(sets.get(index));
                }
                if (sps.longTermRefPicsPresent()) {
                    var spsCount = sps.ltRefPicPocLsbSps().size();
                    if (spsCount > 0) {
                        numSps = r.ue();
                    }
                    numPics = r.ue();
                    if (numSps > spsCount || numSps + numPics > 32) {
                        throw new IllegalArgumentException(numSps + " and " + numPics + " long-term pictures");
                    }
                    var cycle = 0;
                    for (var i = 0; i < numSps + numPics; i++) {
                        int lsb;
                        boolean used;
                        if (i < numSps) {
                            var lt = spsCount > 1 ? r.bits(ceilLog2(spsCount)) : 0;
                            lsb = sps.ltRefPicPocLsbSps().get(lt);
                            used = (sps.usedByCurrPicLtSpsFlags() & (1 << lt)) != 0;
                        } else {
                            lsb = r.bits(sps.log2MaxPocLsbMinus4() + 4);
                            used = r.flag();
                        }
                        var msb = r.flag();
                        var delta = msb ? r.ue() : 0;
                        // DeltaPocMsbCycleLt accumulates within each of the two
                        // groups, the SPS's and the slice's (7-52).
                        cycle = i == 0 || i == numSps ? delta : delta + cycle;
                        longTerm.add(new LongTerm(lsb, used, msb, cycle));
                    }
                }
            }
        }
        return new SliceSegmentHeader(
                nal, first, noOutput, ppsId, dependent, address, type, output, pocLsb, spsFlag, index, set, bits,
                numSps, numPics, longTerm);
    }

    /// `Ceil(Log2(value))`.
    static int ceilLog2(int value) {
        var bits = 0;
        while ((1L << bits) < value) {
            bits++;
        }
        return bits;
    }
}
