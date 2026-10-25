package dev.goldberry.media.bitstream.hevc;

import java.util.Arrays;

import dev.goldberry.media.bitstream.BitReader;

/// A `scaling_list_data()` (H.265 §7.3.4), resolved: every list its
/// coefficients in coded order, the up-right diagonal scan, which is the order
/// Vulkan Video's `StdVideoH265ScalingLists` takes them in.
///
/// @param lists4x4   six lists of 16
/// @param lists8x8   six lists of 64
/// @param lists16x16 six lists of 64, upsampled by the decoder
/// @param lists32x32 two lists of 64, for matrix 0 and matrix 3
/// @param dc16x16    the six 16×16 lists' DC coefficients
/// @param dc32x32    the two 32×32 lists' DC coefficients
@SuppressWarnings("ArrayRecordComponent")
public record ScalingLists(
        int[][] lists4x4, int[][] lists8x8, int[][] lists16x16, int[][] lists32x32, int[] dc16x16, int[] dc32x32) {

    /// Table 7-6's default for the 8×8 and larger intra lists, in coded order.
    private static final int[] DEFAULT_INTRA = {
        16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 17, 16, 17, 16, 17, 18, 17, 18, 18, 17, 18, 21, 19, 20, 21, 20, 19, 21,
        24, 22, 22, 24, 24, 22, 22, 24, 25, 25, 27, 30, 27, 25, 25, 29, 31, 35, 35, 31, 29, 36, 41, 44, 41, 36, 47, 54,
        54, 47, 65, 70, 65, 88, 88, 115
    };

    /// Table 7-6's default for the 8×8 and larger inter lists, in coded order.
    private static final int[] DEFAULT_INTER = {
        16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 20, 20, 20, 20, 20, 20, 20,
        24, 24, 24, 24, 24, 24, 24, 24, 25, 25, 25, 25, 25, 25, 25, 28, 28, 28, 28, 28, 28, 33, 33, 33, 33, 33, 41, 41,
        41, 41, 54, 54, 54, 71, 71, 91
    };

    /// The lists a stream with `scaling_list_enabled_flag` and no list data of
    /// its own uses: every list its default (§7.4.5).
    public static ScalingLists defaults() {
        var all = new int[4][6][];
        var dc = new int[4][6];
        for (var size = 0; size < 4; size++) {
            for (var matrix = 0; matrix < 6; matrix++) {
                all[size][matrix] = defaultList(size, matrix);
                dc[size][matrix] = 16;
            }
        }
        return of(all, dc);
    }

    private static int[] defaultList(int sizeId, int matrixId) {
        if (sizeId == 0) {
            var flat = new int[16];
            Arrays.fill(flat, 16);
            return flat;
        }
        return (matrixId < 3 ? DEFAULT_INTRA : DEFAULT_INTER).clone();
    }

    /// `scaling_list_data()`.
    static ScalingLists read(BitReader r) {
        var all = new int[4][6][];
        var dc = new int[4][6];
        for (var size = 0; size < 4; size++) {
            var step = size == 3 ? 3 : 1;
            for (var matrix = 0; matrix < 6; matrix += step) {
                var count = Math.min(64, 1 << (4 + (size << 1)));
                if (!r.flag()) { // scaling_list_pred_mode_flag
                    var delta = r.ue(); // scaling_list_pred_matrix_id_delta
                    if (delta == 0) {
                        all[size][matrix] = defaultList(size, matrix);
                        dc[size][matrix] = 16;
                    } else {
                        var reference = matrix - delta * step;
                        if (reference < 0) {
                            throw new IllegalArgumentException("a scaling list predicted from list " + reference);
                        }
                        all[size][matrix] = all[size][reference].clone();
                        dc[size][matrix] = dc[size][reference];
                    }
                } else {
                    var next = 8;
                    if (size > 1) {
                        next = r.se() + 8; // scaling_list_dc_coef_minus8
                        dc[size][matrix] = next;
                    }
                    var list = new int[count];
                    for (var i = 0; i < count; i++) {
                        next = (next + r.se() + 256) % 256; // scaling_list_delta_coef
                        list[i] = next;
                    }
                    all[size][matrix] = list;
                    if (size <= 1) {
                        dc[size][matrix] = 16;
                    }
                }
            }
        }
        // The 32×32 chroma lists are not coded; they copy the 16×16 ones'
        // (§7.4.5, for ChromaArrayType 3).
        for (var matrix : new int[] {1, 2, 4, 5}) {
            all[3][matrix] = all[2][matrix].clone();
            dc[3][matrix] = dc[2][matrix];
        }
        return of(all, dc);
    }

    private static ScalingLists of(int[][][] all, int[][] dc) {
        return new ScalingLists(
                all[0], all[1], all[2], new int[][] {all[3][0], all[3][3]}, dc[2].clone(), new int[] {dc[3][0], dc[3][3]
                });
    }
}
