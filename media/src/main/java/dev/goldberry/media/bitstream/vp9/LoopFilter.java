package dev.goldberry.media.bitstream.vp9;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.media.bitstream.BitReader;

/// `loop_filter_params()`: the deblocking filter's strength, and its
/// adjustments by reference frame and by mode.
///
/// The adjustments are kept from frame to frame: a frame sends only the ones
/// it changes, flagged in [#updateRefDelta] and [#updateModeDelta], and a frame
/// that is intra or error resilient starts again from [#DEFAULT_REF_DELTAS]
/// and [#DEFAULT_MODE_DELTAS]. [#refDeltas] and [#modeDeltas] are the values in
/// force for this frame, after its changes.
///
/// @param level           `loop_filter_level`, 0 to 63: 0 is no filtering
/// @param sharpness       `loop_filter_sharpness`, 0 to 7
/// @param deltaEnabled    `loop_filter_delta_enabled`
/// @param deltaUpdate     `loop_filter_delta_update`
/// @param updateRefDelta  `update_ref_delta`, a bit for each of intra, last,
///                        golden and altref: the deltas this frame sends
/// @param refDeltas       `loop_filter_ref_deltas` in force, four, −63 to 63
/// @param updateModeDelta `update_mode_delta`, a bit for each of the two
/// @param modeDeltas      `loop_filter_mode_deltas` in force, two
public record LoopFilter(
        int level,
        int sharpness,
        boolean deltaEnabled,
        boolean deltaUpdate,
        int updateRefDelta,
        List<Integer> refDeltas,
        int updateModeDelta,
        List<Integer> modeDeltas) {

    /// The reference deltas a frame that is intra or error resilient starts
    /// from: intra 1, last 0, golden and altref −1.
    public static final List<Integer> DEFAULT_REF_DELTAS = List.of(1, 0, -1, -1);

    /// The mode deltas it starts from.
    public static final List<Integer> DEFAULT_MODE_DELTAS = List.of(0, 0);

    public LoopFilter {
        refDeltas = List.copyOf(refDeltas);
        modeDeltas = List.copyOf(modeDeltas);
    }

    /// `loop_filter_params()`, changing the deltas in force before it.
    static LoopFilter read(BitReader r, List<Integer> refDeltas, List<Integer> modeDeltas) {
        var level = r.bits(6);
        var sharpness = r.bits(3);
        var enabled = r.flag();
        var update = enabled && r.flag();
        var refs = new ArrayList<>(refDeltas);
        var modes = new ArrayList<>(modeDeltas);
        var refMask = 0;
        var modeMask = 0;
        if (update) {
            for (var i = 0; i < refs.size(); i++) {
                if (r.flag()) {
                    refMask |= 1 << i;
                    refs.set(i, r.signMagnitude(6));
                }
            }
            for (var i = 0; i < modes.size(); i++) {
                if (r.flag()) {
                    modeMask |= 1 << i;
                    modes.set(i, r.signMagnitude(6));
                }
            }
        }
        return new LoopFilter(level, sharpness, enabled, update, refMask, refs, modeMask, modes);
    }
}
