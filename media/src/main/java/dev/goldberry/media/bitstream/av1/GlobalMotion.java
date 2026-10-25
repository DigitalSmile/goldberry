package dev.goldberry.media.bitstream.av1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// `global_motion_params()` (§5.9.24): the warp of each reference, worked out
/// from what is coded against the primary reference's, as Vulkan Video's
/// `StdVideoAV1GlobalMotion` wants it.
///
/// @param type   `GmType` by reference name from `INTRA_FRAME`: one of the
///               constants here
/// @param params `gm_params` by reference name, six each, in
///               `WARPEDMODEL_PREC_BITS` fixed point
public record GlobalMotion(List<Integer> type, List<List<Integer>> params) {

    public static final int IDENTITY = 0;
    public static final int TRANSLATION = 1;
    public static final int ROTZOOM = 2;
    public static final int AFFINE = 3;

    /// `WARPEDMODEL_PREC_BITS`.
    public static final int WARPEDMODEL_PREC_BITS = 16;

    /// The parameters of the identity warp.
    public static final List<Integer> IDENTITY_PARAMS =
            List.of(0, 0, 1 << WARPEDMODEL_PREC_BITS, 0, 0, 1 << WARPEDMODEL_PREC_BITS);

    /// Every reference unwarped: what an intra frame has, and what
    /// `setup_past_independence()` gives the next frame to code against.
    public static final GlobalMotion DEFAULT = new GlobalMotion(
            Collections.nCopies(References.NUM_REF_FRAMES, IDENTITY),
            Collections.nCopies(References.NUM_REF_FRAMES, IDENTITY_PARAMS));

    public GlobalMotion {
        type = List.copyOf(type);
        var copies = new ArrayList<List<Integer>>(params.size());
        for (var each : params) {
            copies.add(List.copyOf(each));
        }
        params = List.copyOf(copies);
    }

    /// `gm_params[ref][i]`.
    public int params(int ref, int i) {
        return params.get(ref).get(i);
    }
}
