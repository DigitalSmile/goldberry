package dev.goldberry.media.bitstream.av1;

import java.util.List;

/// `lr_params()` (§5.9.20), as Vulkan Video's `StdVideoAV1LoopRestoration`
/// wants it.
///
/// @param type      `FrameRestorationType` per plane, remapped from `lr_type`:
///                  one of the constants here
/// @param unitShift `lr_unit_shift`, with `lr_unit_extra_shift` added
/// @param uvShift   `lr_uv_shift`
public record LoopRestoration(List<Integer> type, int unitShift, int uvShift) {

    public static final int RESTORE_NONE = 0;
    public static final int RESTORE_WIENER = 1;
    public static final int RESTORE_SGRPROJ = 2;
    public static final int RESTORE_SWITCHABLE = 3;

    /// `RESTORATION_TILESIZE_MAX`.
    public static final int TILESIZE_MAX = 256;

    /// No restoration on any plane.
    public static final LoopRestoration NONE = new LoopRestoration(List.of(0, 0, 0), 0, 0);

    public LoopRestoration {
        type = List.copyOf(type);
    }

    /// `UsesLr`: whether any plane is restored.
    public boolean usesLr() {
        return type.stream().anyMatch(t -> t != RESTORE_NONE);
    }

    /// `usesChromaLr`: whether U or V is restored.
    public boolean usesChromaLr() {
        return type.get(1) != RESTORE_NONE || type.get(2) != RESTORE_NONE;
    }

    /// `LoopRestorationSize[plane]`, in samples: 32 to 256; 0 when no plane is
    /// restored, as nothing sets it then.
    public int loopRestorationSize(int plane) {
        if (!usesLr()) {
            return 0;
        }
        var luma = TILESIZE_MAX >> (2 - unitShift);
        return plane == 0 ? luma : luma >> uvShift;
    }
}
