package dev.goldberry.media.bitstream.hevc;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.media.bitstream.BitReader;

/// A short-term reference picture set (H.265 §7.3.7, §7.4.8): the syntax as it
/// was coded, and the picture order count deltas it stands for.
///
/// A set coded by prediction from an earlier one keeps its prediction fields
/// and has its deltas worked out here, as the specification derives them, so
/// that a reader of either the syntax or the result finds both.
///
/// @param interPrediction    `inter_ref_pic_set_prediction_flag`
/// @param deltaIdxMinus1     `delta_idx_minus1`, only coded in a slice header
/// @param deltaRpsSign       `delta_rps_sign`
/// @param absDeltaRpsMinus1  `abs_delta_rps_minus1`
/// @param usedByCurrPicFlags `used_by_curr_pic_flag[j]`, bit `j`
/// @param useDeltaFlags      `use_delta_flag[j]`, bit `j`, one where not coded
/// @param deltaPocS0         `DeltaPocS0`: the negative deltas, nearest first
/// @param usedS0             `UsedByCurrPicS0`, bit `i` for `deltaPocS0[i]`
/// @param deltaPocS1         `DeltaPocS1`: the positive deltas, nearest first
/// @param usedS1             `UsedByCurrPicS1`, bit `i`
/// @param bits               the bits the set took to code: a slice header's
///                           `NumBitsForSTRefPicSetInSlice`
public record ShortTermRefPicSet(
        boolean interPrediction,
        int deltaIdxMinus1,
        boolean deltaRpsSign,
        int absDeltaRpsMinus1,
        int usedByCurrPicFlags,
        int useDeltaFlags,
        List<Integer> deltaPocS0,
        int usedS0,
        List<Integer> deltaPocS1,
        int usedS1,
        int bits) {

    /// The most pictures a set holds on either side (§7.4.8).
    public static final int MAX_PICTURES = 16;

    public ShortTermRefPicSet {
        deltaPocS0 = List.copyOf(deltaPocS0);
        deltaPocS1 = List.copyOf(deltaPocS1);
    }

    /// `NumNegativePics`.
    public int negative() {
        return deltaPocS0.size();
    }

    /// `NumPositivePics`.
    public int positive() {
        return deltaPocS1.size();
    }

    /// `NumDeltaPocs`.
    public int deltaPocs() {
        return negative() + positive();
    }

    /// `st_ref_pic_set(index)`, with `sets` the ones coded before it, from the
    /// SPS: `index` equal to their count is a slice header's own set.
    static ShortTermRefPicSet read(BitReader r, int index, List<ShortTermRefPicSet> sets) {
        var start = r.position();
        var inter = index != 0 && r.flag();
        if (inter) {
            var deltaIdxMinus1 = index == sets.size() ? r.ue() : 0;
            var referenceIndex = index - (deltaIdxMinus1 + 1);
            if (referenceIndex < 0 || referenceIndex >= sets.size()) {
                throw new IllegalArgumentException("a reference picture set predicted from set " + referenceIndex);
            }
            var reference = sets.get(referenceIndex);
            var sign = r.flag();
            var absMinus1 = r.ue();
            if (absMinus1 > (1 << 15) - 1) {
                throw new IllegalArgumentException("abs_delta_rps_minus1 " + absMinus1);
            }
            var deltaRps = (sign ? -1 : 1) * (absMinus1 + 1);
            var count = reference.deltaPocs() + 1;
            var used = 0;
            var useDelta = 0;
            for (var j = 0; j < count; j++) {
                var usedFlag = r.flag();
                if (usedFlag) {
                    used |= 1 << j;
                }
                if (usedFlag || r.flag()) {
                    useDelta |= 1 << j;
                }
            }
            var s0 = new ArrayList<Integer>();
            var s1 = new ArrayList<Integer>();
            var usedS0 = 0;
            var usedS1 = 0;
            var refNegative = reference.negative();
            var refAll = reference.deltaPocs();
            for (var j = reference.positive() - 1; j >= 0; j--) {
                var poc = reference.deltaPocS1().get(j) + deltaRps;
                if (poc < 0 && bit(useDelta, refNegative + j)) {
                    usedS0 |= bit(used, refNegative + j) ? 1 << s0.size() : 0;
                    s0.add(poc);
                }
            }
            if (deltaRps < 0 && bit(useDelta, refAll)) {
                usedS0 |= bit(used, refAll) ? 1 << s0.size() : 0;
                s0.add(deltaRps);
            }
            for (var j = 0; j < refNegative; j++) {
                var poc = reference.deltaPocS0().get(j) + deltaRps;
                if (poc < 0 && bit(useDelta, j)) {
                    usedS0 |= bit(used, j) ? 1 << s0.size() : 0;
                    s0.add(poc);
                }
            }
            for (var j = refNegative - 1; j >= 0; j--) {
                var poc = reference.deltaPocS0().get(j) + deltaRps;
                if (poc > 0 && bit(useDelta, j)) {
                    usedS1 |= bit(used, j) ? 1 << s1.size() : 0;
                    s1.add(poc);
                }
            }
            if (deltaRps > 0 && bit(useDelta, refAll)) {
                usedS1 |= bit(used, refAll) ? 1 << s1.size() : 0;
                s1.add(deltaRps);
            }
            for (var j = 0; j < reference.positive(); j++) {
                var poc = reference.deltaPocS1().get(j) + deltaRps;
                if (poc > 0 && bit(useDelta, refNegative + j)) {
                    usedS1 |= bit(used, refNegative + j) ? 1 << s1.size() : 0;
                    s1.add(poc);
                }
            }
            if (s0.size() > MAX_PICTURES || s1.size() > MAX_PICTURES) {
                throw new IllegalArgumentException(
                        "a predicted reference picture set of " + s0.size() + " and " + s1.size() + " pictures");
            }
            return new ShortTermRefPicSet(
                    true, deltaIdxMinus1, sign, absMinus1, used, useDelta, s0, usedS0, s1, usedS1, (int)
                            (r.position() - start));
        }
        var negative = r.ue();
        var positive = r.ue();
        if (negative > MAX_PICTURES || positive > MAX_PICTURES) {
            throw new IllegalArgumentException(
                    "a reference picture set of " + negative + " and " + positive + " pictures");
        }
        var s0 = new ArrayList<Integer>(negative);
        var s1 = new ArrayList<Integer>(positive);
        var usedS0 = 0;
        var usedS1 = 0;
        var poc = 0;
        for (var i = 0; i < negative; i++) {
            poc -= r.ue() + 1; // delta_poc_s0_minus1
            s0.add(poc);
            if (r.flag()) { // used_by_curr_pic_s0_flag
                usedS0 |= 1 << i;
            }
        }
        poc = 0;
        for (var i = 0; i < positive; i++) {
            poc += r.ue() + 1; // delta_poc_s1_minus1
            s1.add(poc);
            if (r.flag()) { // used_by_curr_pic_s1_flag
                usedS1 |= 1 << i;
            }
        }
        return new ShortTermRefPicSet(false, 0, false, 0, 0, 0, s0, usedS0, s1, usedS1, (int) (r.position() - start));
    }

    private static boolean bit(int flags, int index) {
        return (flags & (1 << index)) != 0;
    }
}
