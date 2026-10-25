package dev.goldberry.media.bitstream.av1;

import java.util.List;

/// `loop_filter_params()` (§5.9.11), with the deltas a frame takes from its
/// primary reference when it does not code them: as Vulkan Video's
/// `StdVideoAV1LoopFilter` wants them, and as a later frame loads them.
///
/// @param level           `loop_filter_level`: luma vertical, luma
///                        horizontal, U, V
/// @param sharpness       `loop_filter_sharpness`
/// @param deltaEnabled    `loop_filter_delta_enabled`
/// @param deltaUpdate     `loop_filter_delta_update`
/// @param updateRefDelta  `update_ref_delta`: a bit per reference
/// @param refDeltas       `loop_filter_ref_deltas`, by reference name from
///                        `INTRA_FRAME`
/// @param updateModeDelta `update_mode_delta`: a bit per mode
/// @param modeDeltas      `loop_filter_mode_deltas`
public record LoopFilter(
        List<Integer> level,
        int sharpness,
        boolean deltaEnabled,
        boolean deltaUpdate,
        int updateRefDelta,
        List<Integer> refDeltas,
        int updateModeDelta,
        List<Integer> modeDeltas) {

    /// The reference deltas `setup_past_independence()` sets.
    public static final List<Integer> DEFAULT_REF_DELTAS = List.of(1, 0, 0, 0, -1, 0, -1, -1);

    /// The mode deltas `setup_past_independence()` sets.
    public static final List<Integer> DEFAULT_MODE_DELTAS = List.of(0, 0);

    public LoopFilter {
        level = List.copyOf(level);
        refDeltas = List.copyOf(refDeltas);
        modeDeltas = List.copyOf(modeDeltas);
    }
}
