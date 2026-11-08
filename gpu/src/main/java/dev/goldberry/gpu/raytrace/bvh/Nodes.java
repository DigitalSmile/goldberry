package dev.goldberry.gpu.raytrace.bvh;

import java.util.Arrays;

/// How a [Bvh] is laid out for the shaders: eight 32-bit words a node, two
/// `float4`s. The first is the box's low corner and a link, the second its
/// high corner and a leaf's count:
///
/// | Word | Branch | Leaf |
/// |------|--------|------|
/// | 0–2 | low corner | low corner |
/// | 3 | the right child's index | the first item's index |
/// | 4–6 | high corner | high corner |
/// | 7 | 0 | [#LEAF] or'd with the count |
///
/// A branch's left child follows it. Each box is widened by a few units in the
/// last place of its largest coordinate, so the rounding of a ray's walk never
/// refuses a box the ray grazes.
public final class Nodes {

    /// Words a node.
    public static final int WORDS = 8;

    /// The bit of word 7 that makes a node a leaf.
    public static final int LEAF = 0x8000_0000;

    /// How many units in the last place a box is widened by on each side.
    static final int WIDENING_ULPS = 4;

    private Nodes() {}

    /// Writes `bvh`'s nodes into `into` from node `base`, each branch's right
    /// child moved by `base` and each leaf's first item by `firstItem`.
    static void write(Bvh bvh, int[] into, int base, int firstItem) {
        for (var node = 0; node < bvh.nodeCount(); node++) {
            var at = (base + node) * WORDS;
            var largest = 0f;
            for (var axis = 0; axis < 3; axis++) {
                largest = Math.max(largest, Math.max(Math.abs(bvh.min(node, axis)), Math.abs(bvh.max(node, axis))));
            }
            var widening = WIDENING_ULPS * Math.ulp(largest);
            for (var axis = 0; axis < 3; axis++) {
                into[at + axis] = Float.floatToRawIntBits(bvh.min(node, axis) - widening);
                into[at + 4 + axis] = Float.floatToRawIntBits(bvh.max(node, axis) + widening);
            }
            if (bvh.isLeaf(node)) {
                into[at + 3] = firstItem + bvh.first(node);
                into[at + 7] = LEAF | bvh.count(node);
            } else {
                into[at + 3] = base + bvh.right(node);
                into[at + 7] = 0;
            }
        }
    }

    /// Writes a leaf of no items over an empty box at node `node`: the top
    /// level of a scene whose every instance is flat.
    static void writeEmpty(int[] into, int node) {
        var at = node * WORDS;
        Arrays.fill(into, at, at + WORDS, 0);
        into[at + 7] = LEAF;
    }
}
