package dev.goldberry.media.bitstream.av1;

import java.util.Collections;
import java.util.List;

/// `segmentation_params()` (§5.9.14), with the features a frame takes from its
/// primary reference when it does not code them: as Vulkan Video's
/// `StdVideoAV1Segmentation` wants them, and as a later frame loads them.
///
/// @param enabled         `segmentation_enabled`
/// @param updateMap       `segmentation_update_map`
/// @param temporalUpdate  `segmentation_temporal_update`
/// @param updateData      `segmentation_update_data`
/// @param featureEnabled  `FeatureEnabled`: per segment, a bit per feature
/// @param featureData     `FeatureData`, clipped to each feature's range: the
///                        eight features of segment 0, then of segment 1, …
public record Segmentation(
        boolean enabled,
        boolean updateMap,
        boolean temporalUpdate,
        boolean updateData,
        List<Integer> featureEnabled,
        List<Integer> featureData) {

    public static final int MAX_SEGMENTS = 8;
    public static final int SEG_LVL_MAX = 8;
    public static final int SEG_LVL_ALT_Q = 0;

    /// No segmentation, and no features: what `setup_past_independence()`
    /// leaves.
    public static final Segmentation NONE = new Segmentation(
            false,
            false,
            false,
            false,
            Collections.nCopies(MAX_SEGMENTS, 0),
            Collections.nCopies(MAX_SEGMENTS * SEG_LVL_MAX, 0));

    public Segmentation {
        featureEnabled = List.copyOf(featureEnabled);
        featureData = List.copyOf(featureData);
        if (featureEnabled.size() != MAX_SEGMENTS || featureData.size() != MAX_SEGMENTS * SEG_LVL_MAX) {
            throw new IllegalArgumentException(
                    featureEnabled.size() + " segments and " + featureData.size() + " feature values");
        }
    }

    /// `FeatureEnabled[segment][feature]`.
    public boolean featureEnabled(int segment, int feature) {
        return (featureEnabled.get(segment) & (1 << feature)) != 0;
    }

    /// `FeatureData[segment][feature]`.
    public int featureData(int segment, int feature) {
        return featureData.get(segment * SEG_LVL_MAX + feature);
    }

    /// `LastActiveSegId`: the highest segment with a feature enabled.
    public int lastActiveSegId() {
        for (var i = MAX_SEGMENTS - 1; i >= 0; i--) {
            if (featureEnabled.get(i) != 0) {
                return i;
            }
        }
        return 0;
    }
}
