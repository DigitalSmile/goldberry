package dev.goldberry.media.bitstream.vp9;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.media.bitstream.BitReader;

/// The features of the eight segments: `FeatureEnabled` and `FeatureData`,
/// with the sign applied. A frame that updates the segmentation data sends
/// them all; every other frame keeps the last ones sent, and a frame that is
/// intra or error resilient starts again from [#NONE].
///
/// @param enabled each segment's enabled features, a bit for each of
///                [#ALT_Q], [#ALT_L], [#REF_FRAME] and [#SKIP], as Vulkan's
///                `FeatureEnabled` holds them
/// @param data    each segment's four values, 0 for a feature not enabled
public record SegmentFeatures(List<Integer> enabled, List<List<Integer>> data) {

    /// The segments.
    public static final int MAX_SEGMENTS = 8;

    /// The features of a segment.
    public static final int SEG_LVL_MAX = 4;

    /// A quantizer index, or an offset to the frame's: −255 to 255.
    public static final int ALT_Q = 0;

    /// A loop filter level, or an offset to the frame's: −63 to 63.
    public static final int ALT_L = 1;

    /// The reference frame every block of the segment uses: 0 to 3.
    public static final int REF_FRAME = 2;

    /// Blocks with no residual, and no data.
    public static final int SKIP = 3;

    /// The bits of each feature's value.
    static final List<Integer> BITS = List.of(8, 6, 2, 0);

    /// Whether each feature's value has a sign.
    static final List<Boolean> SIGNED = List.of(true, true, false, false);

    /// No feature in any segment.
    public static final SegmentFeatures NONE = new SegmentFeatures(
            List.of(0, 0, 0, 0, 0, 0, 0, 0),
            List.of(zeros(), zeros(), zeros(), zeros(), zeros(), zeros(), zeros(), zeros()));

    public SegmentFeatures {
        enabled = List.copyOf(enabled);
        data = data.stream().map(List::copyOf).toList();
        if (enabled.size() != MAX_SEGMENTS || data.size() != MAX_SEGMENTS) {
            throw new IllegalArgumentException(enabled.size() + " and " + data.size() + " segments");
        }
    }

    /// Whether `feature` of `segment` is enabled.
    public boolean enabled(int segment, int feature) {
        return (enabled.get(segment) & (1 << feature)) != 0;
    }

    /// The value of `feature` of `segment`.
    public int value(int segment, int feature) {
        return data.get(segment).get(feature);
    }

    /// Whether any segment has any feature.
    public boolean any() {
        return enabled.stream().anyMatch(mask -> mask != 0);
    }

    /// The features a frame's `segmentation_params()` sends, read from `r`.
    static SegmentFeatures read(BitReader r) {
        var enabled = new ArrayList<Integer>(MAX_SEGMENTS);
        var data = new ArrayList<List<Integer>>(MAX_SEGMENTS);
        for (var i = 0; i < MAX_SEGMENTS; i++) {
            var mask = 0;
            var values = new ArrayList<Integer>(SEG_LVL_MAX);
            for (var j = 0; j < SEG_LVL_MAX; j++) {
                var value = 0;
                if (r.flag()) {
                    mask |= 1 << j;
                    value = r.bits(BITS.get(j));
                    if (SIGNED.get(j) && r.flag()) {
                        value = -value;
                    }
                }
                values.add(value);
            }
            enabled.add(mask);
            data.add(values);
        }
        return new SegmentFeatures(enabled, data);
    }

    private static List<Integer> zeros() {
        return List.of(0, 0, 0, 0);
    }
}
