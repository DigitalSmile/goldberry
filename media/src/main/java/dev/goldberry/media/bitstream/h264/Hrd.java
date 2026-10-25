package dev.goldberry.media.bitstream.h264;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.media.bitstream.BitReader;

/// `hrd_parameters()` (H.264 §E.1.2), as Vulkan Video's
/// `StdVideoH264HrdParameters` takes it.
///
/// @param cpbCntMinus1                       `cpb_cnt_minus1`
/// @param bitRateScale                       `bit_rate_scale`
/// @param cpbSizeScale                       `cpb_size_scale`
/// @param bitRateValueMinus1                 each buffer's `bit_rate_value_minus1`
/// @param cpbSizeValueMinus1                 each buffer's `cpb_size_value_minus1`
/// @param cbr                                each buffer's `cbr_flag`, bit `i`
/// @param initialCpbRemovalDelayLengthMinus1 the field of that name
/// @param cpbRemovalDelayLengthMinus1        the field of that name
/// @param dpbOutputDelayLengthMinus1         the field of that name
/// @param timeOffsetLength                   `time_offset_length`
public record Hrd(
        int cpbCntMinus1,
        int bitRateScale,
        int cpbSizeScale,
        List<Integer> bitRateValueMinus1,
        List<Integer> cpbSizeValueMinus1,
        int cbr,
        int initialCpbRemovalDelayLengthMinus1,
        int cpbRemovalDelayLengthMinus1,
        int dpbOutputDelayLengthMinus1,
        int timeOffsetLength) {

    public Hrd {
        bitRateValueMinus1 = List.copyOf(bitRateValueMinus1);
        cpbSizeValueMinus1 = List.copyOf(cpbSizeValueMinus1);
    }

    static Hrd read(BitReader r) {
        var count = r.ue();
        if (count > 31) {
            throw new IllegalArgumentException("cpb_cnt_minus1 " + count);
        }
        var bitRateScale = r.bits(4);
        var cpbSizeScale = r.bits(4);
        var bitRates = new ArrayList<Integer>();
        var sizes = new ArrayList<Integer>();
        var cbr = 0;
        for (var i = 0; i <= count; i++) {
            bitRates.add(r.ue());
            sizes.add(r.ue());
            if (r.flag()) {
                cbr |= 1 << i;
            }
        }
        return new Hrd(
                count, bitRateScale, cpbSizeScale, bitRates, sizes, cbr, r.bits(5), r.bits(5), r.bits(5), r.bits(5));
    }
}
