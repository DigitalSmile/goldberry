package dev.goldberry.media.bitstream.av1;

import java.util.List;

/// `cdef_params()` (§5.9.19), as Vulkan Video's `StdVideoAV1CDEF` wants it:
/// the strengths as coded, so a secondary strength of 3 stands for 4.
///
/// @param dampingMinus3  `cdef_damping_minus_3`
/// @param bits           `cdef_bits`
/// @param yPriStrength   `cdef_y_pri_strength`, `1 << cdef_bits` of them
/// @param ySecStrength   `cdef_y_sec_strength`, as coded
/// @param uvPriStrength  `cdef_uv_pri_strength`, 0 for monochrome
/// @param uvSecStrength  `cdef_uv_sec_strength`, as coded
public record Cdef(
        int dampingMinus3,
        int bits,
        List<Integer> yPriStrength,
        List<Integer> ySecStrength,
        List<Integer> uvPriStrength,
        List<Integer> uvSecStrength) {

    /// No CDEF: a single preset of zero strengths, as §5.9.19 sets it when CDEF
    /// is off.
    public static final Cdef NONE = new Cdef(0, 0, List.of(0), List.of(0), List.of(0), List.of(0));

    public Cdef {
        yPriStrength = List.copyOf(yPriStrength);
        ySecStrength = List.copyOf(ySecStrength);
        uvPriStrength = List.copyOf(uvPriStrength);
        uvSecStrength = List.copyOf(uvSecStrength);
    }
}
