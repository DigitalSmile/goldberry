package dev.goldberry.media.bitstream.hevc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.media.bitstream.BitReader;

/// A video parameter set (H.265 §7.3.2.1), as far as a single-layer decoder
/// reads one.
///
/// @param id                        `vps_video_parameter_set_id`
/// @param maxSubLayersMinus1        `vps_max_sub_layers_minus1`
/// @param temporalIdNesting         `vps_temporal_id_nesting_flag`
/// @param profileTierLevel          `profile_tier_level()`
/// @param subLayerOrderingInfoPresent `vps_sub_layer_ordering_info_present_flag`
/// @param ordering                  each sub-layer's limits, the lowest first,
///                                  filled from the highest where not coded
/// @param timingInfoPresent         `vps_timing_info_present_flag`
/// @param numUnitsInTick            `vps_num_units_in_tick`
/// @param timeScale                 `vps_time_scale`
/// @param pocProportionalToTiming   `vps_poc_proportional_to_timing_flag`
/// @param numTicksPocDiffOneMinus1  `vps_num_ticks_poc_diff_one_minus1`
/// @param hrd                       the first `hrd_parameters()`, the base
///                                  layer's, when present
public record VideoParameterSet(
        int id,
        int maxSubLayersMinus1,
        boolean temporalIdNesting,
        ProfileTierLevel profileTierLevel,
        boolean subLayerOrderingInfoPresent,
        List<SubLayerOrdering> ordering,
        boolean timingInfoPresent,
        long numUnitsInTick,
        long timeScale,
        boolean pocProportionalToTiming,
        int numTicksPocDiffOneMinus1,
        Optional<Hrd> hrd) {

    public VideoParameterSet {
        ordering = List.copyOf(ordering);
    }

    /// The VPS whose RBSP, after the two-byte NAL header, is `rbsp`.
    public static VideoParameterSet parse(byte[] rbsp) {
        var r = new BitReader(rbsp);
        var id = r.bits(4);
        r.skip(2); // vps_base_layer_internal_flag, vps_base_layer_available_flag
        r.skip(6); // vps_max_layers_minus1: the base layer is what is decoded
        var maxSubLayersMinus1 = r.bits(3);
        var nesting = r.flag();
        r.skip(16); // vps_reserved_0xffff_16bits
        var ptl = ProfileTierLevel.read(r, maxSubLayersMinus1);
        var present = r.flag();
        var ordering = ordering(r, present, maxSubLayersMinus1);
        var maxLayerId = r.bits(6);
        var layerSets = r.ue() + 1;
        if (layerSets > 1024) {
            throw new IllegalArgumentException("vps_num_layer_sets_minus1 " + (layerSets - 1));
        }
        r.skip((long) (layerSets - 1) * (maxLayerId + 1)); // layer_id_included_flag
        var timing = r.flag();
        var units = 0L;
        var scale = 0L;
        var proportional = false;
        var ticks = 0;
        Optional<Hrd> hrd = Optional.empty();
        if (timing) {
            units = r.bitsLong(32);
            scale = r.bitsLong(32);
            proportional = r.flag();
            if (proportional) {
                ticks = r.ue();
            }
            var hrdCount = r.ue();
            for (var i = 0; i < hrdCount; i++) {
                r.ue(); // hrd_layer_set_idx
                var common = i == 0 || r.flag(); // cprms_present_flag
                var read = Hrd.read(r, common, maxSubLayersMinus1);
                if (i == 0) {
                    hrd = Optional.of(read);
                }
            }
        }
        return new VideoParameterSet(
                id,
                maxSubLayersMinus1,
                nesting,
                ptl,
                present,
                ordering,
                timing,
                units,
                scale,
                proportional,
                ticks,
                hrd);
    }

    /// The sub-layer ordering loop of a VPS or an SPS: coded for every
    /// sub-layer, or for the highest alone and the rest the same.
    static List<SubLayerOrdering> ordering(BitReader r, boolean present, int maxSubLayersMinus1) {
        var all = new SubLayerOrdering[maxSubLayersMinus1 + 1];
        for (var i = present ? 0 : maxSubLayersMinus1; i <= maxSubLayersMinus1; i++) {
            var buffering = r.ue();
            var reorder = r.ue();
            var latency = r.ue();
            if (buffering > 15 || reorder > buffering) {
                throw new IllegalArgumentException(
                        "a decoded picture buffer of " + (buffering + 1) + " reordering " + reorder);
            }
            all[i] = new SubLayerOrdering(buffering, reorder, latency);
        }
        for (var i = 0; i < maxSubLayersMinus1; i++) {
            if (all[i] == null) {
                all[i] = all[maxSubLayersMinus1];
            }
        }
        return new ArrayList<>(List.of(all));
    }
}
