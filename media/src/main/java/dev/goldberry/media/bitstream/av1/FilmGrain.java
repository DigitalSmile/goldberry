package dev.goldberry.media.bitstream.av1;

import java.util.List;

/// `film_grain_params()` (§5.9.30): the grain to add to a shown frame, as
/// Vulkan Video's `StdVideoAV1FilmGrain` wants it. A frame that takes its
/// grain from a reference (`update_grain` clear) has that reference's
/// parameters here, with its own `grain_seed`, as `load_grain_params()` makes
/// them.
///
/// @param applyGrain             `apply_grain`; when clear, every other value
///                               is zero
/// @param grainSeed              `grain_seed`
/// @param updateGrain            `update_grain`
/// @param filmGrainParamsRefIdx  `film_grain_params_ref_idx`, the reference
///                               the parameters came from when not updated
/// @param pointYValue            `point_y_value`, `num_y_points` of them
/// @param pointYScaling          `point_y_scaling`
/// @param chromaScalingFromLuma  `chroma_scaling_from_luma`
/// @param pointCbValue           `point_cb_value`
/// @param pointCbScaling         `point_cb_scaling`
/// @param pointCrValue           `point_cr_value`
/// @param pointCrScaling         `point_cr_scaling`
/// @param grainScalingMinus8     `grain_scaling_minus_8`
/// @param arCoeffLag             `ar_coeff_lag`
/// @param arCoeffsYPlus128       `ar_coeffs_y_plus_128`, as coded: 0 to 255
/// @param arCoeffsCbPlus128      `ar_coeffs_cb_plus_128`
/// @param arCoeffsCrPlus128      `ar_coeffs_cr_plus_128`
/// @param arCoeffShiftMinus6     `ar_coeff_shift_minus_6`
/// @param grainScaleShift        `grain_scale_shift`
/// @param cbMult                 `cb_mult`
/// @param cbLumaMult             `cb_luma_mult`
/// @param cbOffset               `cb_offset`
/// @param crMult                 `cr_mult`
/// @param crLumaMult             `cr_luma_mult`
/// @param crOffset               `cr_offset`
/// @param overlapFlag            `overlap_flag`
/// @param clipToRestrictedRange  `clip_to_restricted_range`
public record FilmGrain(
        boolean applyGrain,
        int grainSeed,
        boolean updateGrain,
        int filmGrainParamsRefIdx,
        List<Integer> pointYValue,
        List<Integer> pointYScaling,
        boolean chromaScalingFromLuma,
        List<Integer> pointCbValue,
        List<Integer> pointCbScaling,
        List<Integer> pointCrValue,
        List<Integer> pointCrScaling,
        int grainScalingMinus8,
        int arCoeffLag,
        List<Integer> arCoeffsYPlus128,
        List<Integer> arCoeffsCbPlus128,
        List<Integer> arCoeffsCrPlus128,
        int arCoeffShiftMinus6,
        int grainScaleShift,
        int cbMult,
        int cbLumaMult,
        int cbOffset,
        int crMult,
        int crLumaMult,
        int crOffset,
        boolean overlapFlag,
        boolean clipToRestrictedRange) {

    /// No grain: what `reset_grain_params()` leaves.
    public static final FilmGrain NONE = new FilmGrain(
            false, 0, false, 0, List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), 0, 0,
            List.of(), List.of(), List.of(), 0, 0, 0, 0, 0, 0, 0, 0, false, false);

    public FilmGrain {
        pointYValue = List.copyOf(pointYValue);
        pointYScaling = List.copyOf(pointYScaling);
        pointCbValue = List.copyOf(pointCbValue);
        pointCbScaling = List.copyOf(pointCbScaling);
        pointCrValue = List.copyOf(pointCrValue);
        pointCrScaling = List.copyOf(pointCrScaling);
        arCoeffsYPlus128 = List.copyOf(arCoeffsYPlus128);
        arCoeffsCbPlus128 = List.copyOf(arCoeffsCbPlus128);
        arCoeffsCrPlus128 = List.copyOf(arCoeffsCrPlus128);
    }

    /// These parameters with `grain_seed` `seed` and `update_grain` clear,
    /// taken from reference `refIdx`: what `load_grain_params()` gives a frame
    /// that applies grain and does not update it. Its `apply_grain` stays set,
    /// as libaom and dav1d keep it.
    FilmGrain loadedFrom(int refIdx, int seed) {
        return new FilmGrain(
                true,
                seed,
                false,
                refIdx,
                pointYValue,
                pointYScaling,
                chromaScalingFromLuma,
                pointCbValue,
                pointCbScaling,
                pointCrValue,
                pointCrScaling,
                grainScalingMinus8,
                arCoeffLag,
                arCoeffsYPlus128,
                arCoeffsCbPlus128,
                arCoeffsCrPlus128,
                arCoeffShiftMinus6,
                grainScaleShift,
                cbMult,
                cbLumaMult,
                cbOffset,
                crMult,
                crLumaMult,
                crOffset,
                overlapFlag,
                clipToRestrictedRange);
    }
}
