package dev.goldberry.media.bitstream.hevc;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.media.bitstream.BitReader;

/// `hrd_parameters()` (H.265 §E.2.2): the hypothetical reference decoder's
/// buffering, which a decoder reads past but Vulkan Video wants handed over as
/// it was coded.
///
/// @param nalPresent              `nal_hrd_parameters_present_flag`
/// @param vclPresent              `vcl_hrd_parameters_present_flag`
/// @param subPicPresent           `sub_pic_hrd_params_present_flag`
/// @param subPicInPicTiming       `sub_pic_cpb_params_in_pic_timing_sei_flag`
/// @param tickDivisorMinus2       `tick_divisor_minus2`
/// @param duCpbRemovalDelayIncrementLengthMinus1 the field of that name
/// @param dpbOutputDelayDuLengthMinus1 the field of that name
/// @param bitRateScale            `bit_rate_scale`
/// @param cpbSizeScale            `cpb_size_scale`
/// @param cpbSizeDuScale          `cpb_size_du_scale`
/// @param initialCpbRemovalDelayLengthMinus1 the field of that name
/// @param auCpbRemovalDelayLengthMinus1 the field of that name
/// @param dpbOutputDelayLengthMinus1 the field of that name
/// @param subLayers               each sub-layer's part, the lowest first
public record Hrd(
        boolean nalPresent,
        boolean vclPresent,
        boolean subPicPresent,
        boolean subPicInPicTiming,
        int tickDivisorMinus2,
        int duCpbRemovalDelayIncrementLengthMinus1,
        int dpbOutputDelayDuLengthMinus1,
        int bitRateScale,
        int cpbSizeScale,
        int cpbSizeDuScale,
        int initialCpbRemovalDelayLengthMinus1,
        int auCpbRemovalDelayLengthMinus1,
        int dpbOutputDelayLengthMinus1,
        List<SubLayer> subLayers) {

    /// One sub-layer's part.
    ///
    /// @param fixedPicRateGeneral   `fixed_pic_rate_general_flag`
    /// @param fixedPicRateWithinCvs `fixed_pic_rate_within_cvs_flag`
    /// @param elementalDurationInTcMinus1 the field of that name
    /// @param lowDelay              `low_delay_hrd_flag`
    /// @param cpbCntMinus1          `cpb_cnt_minus1`
    /// @param nal                   the NAL HRD's buffers, when present
    /// @param vcl                   the VCL HRD's buffers, when present
    public record SubLayer(
            boolean fixedPicRateGeneral,
            boolean fixedPicRateWithinCvs,
            int elementalDurationInTcMinus1,
            boolean lowDelay,
            int cpbCntMinus1,
            List<Cpb> nal,
            List<Cpb> vcl) {

        public SubLayer {
            nal = List.copyOf(nal);
            vcl = List.copyOf(vcl);
        }
    }

    /// One coded picture buffer's parameters (§E.2.3).
    public record Cpb(
            int bitRateValueMinus1,
            int cpbSizeValueMinus1,
            int cpbSizeDuValueMinus1,
            int bitRateDuValueMinus1,
            boolean cbr) {}

    public Hrd {
        subLayers = List.copyOf(subLayers);
    }

    /// `hrd_parameters(commonInfPresentFlag, maxNumSubLayersMinus1)`.
    static Hrd read(BitReader r, boolean common, int maxSubLayersMinus1) {
        var nal = false;
        var vcl = false;
        var subPic = false;
        var inPicTiming = false;
        var tickDivisor = 0;
        var duIncrement = 0;
        var duOutput = 0;
        var bitRateScale = 0;
        var cpbSizeScale = 0;
        var cpbSizeDuScale = 0;
        var initialDelay = 23;
        var auDelay = 23;
        var outputDelay = 23;
        if (common) {
            nal = r.flag();
            vcl = r.flag();
            if (nal || vcl) {
                subPic = r.flag();
                if (subPic) {
                    tickDivisor = r.bits(8);
                    duIncrement = r.bits(5);
                    inPicTiming = r.flag();
                    duOutput = r.bits(5);
                }
                bitRateScale = r.bits(4);
                cpbSizeScale = r.bits(4);
                if (subPic) {
                    cpbSizeDuScale = r.bits(4);
                }
                initialDelay = r.bits(5);
                auDelay = r.bits(5);
                outputDelay = r.bits(5);
            }
        }
        var layers = new ArrayList<SubLayer>(maxSubLayersMinus1 + 1);
        for (var i = 0; i <= maxSubLayersMinus1; i++) {
            var general = r.flag();
            var withinCvs = general || r.flag();
            var duration = 0;
            var lowDelay = false;
            if (withinCvs) {
                duration = r.ue();
            } else {
                lowDelay = r.flag();
            }
            var cpbCount = lowDelay ? 0 : r.ue();
            if (cpbCount > 31) {
                throw new IllegalArgumentException("cpb_cnt_minus1 " + cpbCount);
            }
            var nalCpbs = nal ? cpbs(r, cpbCount, subPic) : List.<Cpb>of();
            var vclCpbs = vcl ? cpbs(r, cpbCount, subPic) : List.<Cpb>of();
            layers.add(new SubLayer(general, withinCvs, duration, lowDelay, cpbCount, nalCpbs, vclCpbs));
        }
        return new Hrd(
                nal,
                vcl,
                subPic,
                inPicTiming,
                tickDivisor,
                duIncrement,
                duOutput,
                bitRateScale,
                cpbSizeScale,
                cpbSizeDuScale,
                initialDelay,
                auDelay,
                outputDelay,
                layers);
    }

    /// `sub_layer_hrd_parameters()` for `cpbCountMinus1` + 1 buffers.
    private static List<Cpb> cpbs(BitReader r, int cpbCountMinus1, boolean subPic) {
        var all = new ArrayList<Cpb>(cpbCountMinus1 + 1);
        for (var j = 0; j <= cpbCountMinus1; j++) {
            var bitRate = r.ue();
            var cpbSize = r.ue();
            var cpbSizeDu = subPic ? r.ue() : 0;
            var bitRateDu = subPic ? r.ue() : 0;
            all.add(new Cpb(bitRate, cpbSize, cpbSizeDu, bitRateDu, r.flag()));
        }
        return all;
    }
}
