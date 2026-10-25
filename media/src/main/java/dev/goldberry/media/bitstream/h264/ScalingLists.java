package dev.goldberry.media.bitstream.h264;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.bitstream.BitReader;

/// The scaling lists of an SPS or a PPS (H.264 §7.3.2.1.1.1, §7.4.2.1.1),
/// resolved by the fall-back rules: every list's 16 or 64 values in coded
/// order, the zig-zag scan, which is the order Vulkan Video's
/// `StdVideoH264ScalingLists` takes them in, with what was coded as a mask.
///
/// @param present   bit `i` for each `scaling_list_present_flag[i]`
/// @param lists4x4  six lists of 16
/// @param lists8x8  six lists of 64; two are coded unless 4:4:4
@SuppressWarnings("ArrayRecordComponent")
public record ScalingLists(int present, int[][] lists4x4, int[][] lists8x8) {

    /// Table 7-3's `Default_4x4_Intra`, in zig-zag order.
    static final int[] DEFAULT_4X4_INTRA = {6, 13, 13, 20, 20, 20, 28, 28, 28, 28, 32, 32, 32, 37, 37, 42};

    /// Table 7-3's `Default_4x4_Inter`.
    static final int[] DEFAULT_4X4_INTER = {10, 14, 14, 20, 20, 20, 24, 24, 24, 24, 27, 27, 27, 30, 30, 34};

    /// Table 7-4's `Default_8x8_Intra`, in zig-zag order.
    static final int[] DEFAULT_8X8_INTRA = {
        6, 10, 10, 13, 11, 13, 16, 16, 16, 16, 18, 18, 18, 18, 18, 23, 23, 23, 23, 23, 23, 25, 25, 25, 25, 25, 25, 25,
        27, 27, 27, 27, 27, 27, 27, 27, 29, 29, 29, 29, 29, 29, 29, 31, 31, 31, 31, 31, 31, 33, 33, 33, 33, 33, 36, 36,
        36, 36, 38, 38, 38, 40, 40, 42
    };

    /// Table 7-4's `Default_8x8_Inter`.
    static final int[] DEFAULT_8X8_INTER = {
        9, 13, 13, 15, 13, 15, 17, 17, 17, 17, 19, 19, 19, 19, 19, 21, 21, 21, 21, 21, 21, 22, 22, 22, 22, 22, 22, 22,
        24, 24, 24, 24, 24, 24, 24, 24, 25, 25, 25, 25, 25, 25, 25, 27, 27, 27, 27, 27, 27, 28, 28, 28, 28, 28, 30, 30,
        30, 30, 32, 32, 32, 33, 33, 35
    };

    /// Every list flat, 16: a stream with no scaling matrix.
    public static ScalingLists flat() {
        var four = new int[6][];
        var eight = new int[6][];
        for (var i = 0; i < 6; i++) {
            four[i] = filled(16, 16);
            eight[i] = filled(64, 16);
        }
        return new ScalingLists(0, four, eight);
    }

    private static int[] filled(int length, int value) {
        var list = new int[length];
        Arrays.fill(list, value);
        return list;
    }

    /// The lists coded after `seq_scaling_matrix_present_flag` or
    /// `pic_scaling_matrix_present_flag`, `count` of them (8, or 12 for 4:4:4,
    /// or 6 for a PPS without `transform_8x8_mode_flag`), resolved by fall-back
    /// rule A for an SPS (`sequence` null) or B for a PPS, which falls back to
    /// the SPS's own.
    static ScalingLists read(BitReader r, int count, @Nullable ScalingLists sequence) {
        var four = new int[6][];
        var eight = new int[6][];
        var present = 0;
        for (var i = 0; i < 12; i++) {
            var coded = i < count && r.flag();
            if (coded) {
                present |= 1 << i;
            }
            var size = i < 6 ? 16 : 64;
            int[] list;
            if (coded) {
                list = new int[size];
                var useDefault = list(r, list);
                if (useDefault) {
                    list = defaultFor(i);
                }
            } else {
                list = fallBack(i, four, eight, sequence);
            }
            if (i < 6) {
                four[i] = list;
            } else {
                eight[i - 6] = list;
            }
        }
        return new ScalingLists(present, four, eight);
    }

    /// One `scaling_list()`: true when it says to use the default list.
    private static boolean list(BitReader r, int[] list) {
        var last = 8;
        var next = 8;
        for (var j = 0; j < list.length; j++) {
            if (next != 0) {
                var delta = r.se();
                next = (last + delta + 256) % 256;
                if (j == 0 && next == 0) {
                    return true;
                }
            }
            list[j] = next == 0 ? last : next;
            last = list[j];
        }
        return false;
    }

    private static int[] defaultFor(int i) {
        return switch (i) {
            case 0, 1, 2 -> DEFAULT_4X4_INTRA.clone();
            case 3, 4, 5 -> DEFAULT_4X4_INTER.clone();
            case 6, 8, 10 -> DEFAULT_8X8_INTRA.clone();
            default -> DEFAULT_8X8_INTER.clone();
        };
    }

    /// Fall-back rules A and B (Table 7-2): the first list of each kind falls
    /// to the default or the sequence's, the others to the one before them.
    private static int[] fallBack(int i, int[][] four, int[][] eight, @Nullable ScalingLists sequence) {
        return switch (i) {
            case 0 -> sequence == null ? DEFAULT_4X4_INTRA.clone() : sequence.lists4x4()[0].clone();
            case 3 -> sequence == null ? DEFAULT_4X4_INTER.clone() : sequence.lists4x4()[3].clone();
            case 6 -> sequence == null ? DEFAULT_8X8_INTRA.clone() : sequence.lists8x8()[0].clone();
            case 7 -> sequence == null ? DEFAULT_8X8_INTER.clone() : sequence.lists8x8()[1].clone();
            case 1, 2, 4, 5 -> four[i - 1].clone();
            default -> eight[i - 6 - 2].clone();
        };
    }
}
