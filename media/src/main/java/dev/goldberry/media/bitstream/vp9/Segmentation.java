package dev.goldberry.media.bitstream.vp9;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.media.bitstream.BitReader;

/// `segmentation_params()`: whether the frame's blocks are put in segments,
/// how their segment ids are coded, and what each segment changes.
///
/// The probabilities are this frame's, sent with a new segment map, and 255
/// when not sent. [#absOrDeltaUpdate] and [#features] are kept from frame to
/// frame like the loop filter's deltas: they are the values in force for this
/// frame, sent by it when [#updateData] is set.
///
/// @param enabled          `segmentation_enabled`
/// @param updateMap        `segmentation_update_map`: the frame codes a
///                         segment id for every block
/// @param treeProbs        `segmentation_tree_probs`, seven
/// @param temporalUpdate   `segmentation_temporal_update`: the ids are coded
///                         as changes to the previous frame's
/// @param predProbs        `segmentation_pred_prob`, three, all 255 unless
///                         [#temporalUpdate]
/// @param updateData       `segmentation_update_data`: the frame sends the
///                         features
/// @param absOrDeltaUpdate `segmentation_abs_or_delta_update` in force: the
///                         features' values replace the frame's quantizer and
///                         filter level, rather than adjust them
/// @param features         `FeatureEnabled` and `FeatureData` in force
public record Segmentation(
        boolean enabled,
        boolean updateMap,
        List<Integer> treeProbs,
        boolean temporalUpdate,
        List<Integer> predProbs,
        boolean updateData,
        boolean absOrDeltaUpdate,
        SegmentFeatures features) {

    /// The tree's probabilities.
    public static final int TREE_PROBS = 7;

    /// The prediction's probabilities.
    public static final int PRED_PROBS = 3;

    /// A probability that is not sent.
    public static final int MAX_PROB = 255;

    private static final List<Integer> UNSENT_TREE = List.of(255, 255, 255, 255, 255, 255, 255);
    private static final List<Integer> UNSENT_PRED = List.of(255, 255, 255);

    public Segmentation {
        treeProbs = List.copyOf(treeProbs);
        predProbs = List.copyOf(predProbs);
    }

    /// `segmentation_params()`, with the data in force before it.
    static Segmentation read(BitReader r, boolean absOrDeltaUpdate, SegmentFeatures features) {
        if (!r.flag()) {
            return new Segmentation(false, false, UNSENT_TREE, false, UNSENT_PRED, false, absOrDeltaUpdate, features);
        }
        var updateMap = r.flag();
        var tree = UNSENT_TREE;
        var temporal = false;
        var pred = UNSENT_PRED;
        if (updateMap) {
            tree = probs(r, TREE_PROBS);
            temporal = r.flag();
            if (temporal) {
                pred = probs(r, PRED_PROBS);
            }
        }
        var updateData = r.flag();
        if (updateData) {
            absOrDeltaUpdate = r.flag();
            features = SegmentFeatures.read(r);
        }
        return new Segmentation(true, updateMap, tree, temporal, pred, updateData, absOrDeltaUpdate, features);
    }

    /// `read_prob()` `count` times: a flag, then eight bits when it is set.
    private static List<Integer> probs(BitReader r, int count) {
        var probs = new ArrayList<Integer>(count);
        for (var i = 0; i < count; i++) {
            probs.add(r.flag() ? r.bits(8) : MAX_PROB);
        }
        return probs;
    }
}
