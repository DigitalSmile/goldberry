package dev.goldberry.gpu.raytrace.bvh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The binned surface-area builder, on boxes made up for it: every primitive
/// in one leaf, every box around what it holds, the cheaper split taken, and
/// the depth bounded whatever the input.
@DisplayName("the BVH builder")
class BvhBuilderTest {

    /// `count` boxes of side `size` at random places in a cube of side 100.
    private static float[][] scattered(int count, float size, long seed) {
        var random = new Random(seed);
        var boxes = new float[6 * count];
        var centroids = new float[3 * count];
        for (var i = 0; i < count; i++) {
            for (var axis = 0; axis < 3; axis++) {
                var centre = random.nextFloat() * 100;
                centroids[3 * i + axis] = centre;
                boxes[6 * i + axis] = centre - size / 2;
                boxes[6 * i + 3 + axis] = centre + size / 2;
            }
        }
        return new float[][] {boxes, centroids};
    }

    /// Asserts what holds of every tree: each primitive in exactly one leaf,
    /// each leaf at most `maxLeaf` (or at the depth limit), each branch's box
    /// around both its children's, each leaf's around its primitives', and
    /// the nodes laid out depth first.
    private static void assertWellFormed(Bvh bvh, float[] boxes, int maxLeaf) {
        var count = boxes.length / 6;
        assertEquals(count, bvh.primitiveCount());
        assertTrue(bvh.nodeCount() <= 2 * count - 1, bvh.nodeCount() + " nodes for " + count);
        assertTrue(bvh.depth() <= BvhBuilder.MAX_DEPTH, "depth " + bvh.depth());
        var seen = new BitSet(count);
        for (var i = 0; i < count; i++) {
            var primitive = bvh.primitive(i);
            assertFalse(seen.get(primitive), "primitive " + primitive + " twice");
            seen.set(primitive);
        }
        var covered = new BitSet(count);
        walk(bvh, 0, 1, boxes, maxLeaf, covered);
        assertEquals(count, covered.cardinality(), "every place in the order is in one leaf");
    }

    private static int walk(Bvh bvh, int node, int level, float[] boxes, int maxLeaf, BitSet covered) {
        if (bvh.isLeaf(node)) {
            var count = bvh.count(node);
            assertTrue(count >= 1, "leaf " + node + " is empty");
            assertTrue(count <= maxLeaf || level == BvhBuilder.MAX_DEPTH, "leaf " + node + " holds " + count);
            for (var i = bvh.first(node); i < bvh.first(node) + count; i++) {
                assertFalse(covered.get(i), "place " + i + " in two leaves");
                covered.set(i);
                assertContains(bvh, node, boxes, 6 * bvh.primitive(i));
            }
            return node + 1;
        }
        var left = node + 1;
        var right = bvh.right(node);
        assertContainsNode(bvh, node, left);
        assertContainsNode(bvh, node, right);
        var afterLeft = walk(bvh, left, level + 1, boxes, maxLeaf, covered);
        assertEquals(afterLeft, right, "the right child follows the left subtree");
        return walk(bvh, right, level + 1, boxes, maxLeaf, covered);
    }

    private static void assertContains(Bvh bvh, int node, float[] boxes, int at) {
        for (var axis = 0; axis < 3; axis++) {
            assertTrue(bvh.min(node, axis) <= boxes[at + axis], "node " + node + " on axis " + axis);
            assertTrue(bvh.max(node, axis) >= boxes[at + 3 + axis], "node " + node + " on axis " + axis);
        }
    }

    private static void assertContainsNode(Bvh bvh, int parent, int child) {
        for (var axis = 0; axis < 3; axis++) {
            assertTrue(bvh.min(parent, axis) <= bvh.min(child, axis), parent + " around " + child);
            assertTrue(bvh.max(parent, axis) >= bvh.max(child, axis), parent + " around " + child);
        }
    }

    @Test
    @DisplayName("puts every primitive in one leaf of at most four, inside every box above it")
    void wellFormed() {
        for (var count : new int[] {1, 2, 3, 4, 5, 17, 1000, 20_000}) {
            var input = scattered(count, 0.5f, count);
            var bvh = BvhBuilder.build(input[0], input[1], 4);
            assertWellFormed(bvh, input[0], 4);
        }
    }

    @Test
    @DisplayName("is a single leaf for one primitive, and for a few that splitting would not help")
    void leaves() {
        var one = scattered(1, 1, 1);
        var single = BvhBuilder.build(one[0], one[1], 4);
        assertEquals(1, single.nodeCount());
        assertTrue(single.isLeaf(0));
        assertEquals(1, single.depth());

        // Four boxes that overlap almost entirely: either side of any split
        // costs as much as the whole, so the four stay together.
        var boxes = new float[] {
            0, 0, 0, 10, 10, 10, 0.1f, 0, 0, 10.1f, 10, 10, 0.2f, 0, 0, 10.2f, 10, 10, 0.3f, 0, 0, 10.3f, 10, 10
        };
        var centroids = new float[] {5, 5, 5, 5.1f, 5, 5, 5.2f, 5, 5, 5.3f, 5, 5};
        var together = BvhBuilder.build(boxes, centroids, 4);
        assertEquals(1, together.nodeCount(), together.toString());
    }

    @Test
    @DisplayName("splits two far clusters apart at the root, where the surface heuristic says to")
    void splitsClusters() {
        var count = 64;
        var boxes = new float[6 * count];
        var centroids = new float[3 * count];
        var random = new Random(7);
        for (var i = 0; i < count; i++) {
            var x = (i < count / 2 ? 0 : 1000) + random.nextFloat();
            var y = random.nextFloat();
            var z = random.nextFloat();
            centroids[3 * i] = x;
            centroids[3 * i + 1] = y;
            centroids[3 * i + 2] = z;
            System.arraycopy(
                    new float[] {x - 0.1f, y - 0.1f, z - 0.1f, x + 0.1f, y + 0.1f, z + 0.1f}, 0, boxes, 6 * i, 6);
        }
        var bvh = BvhBuilder.build(boxes, centroids, 4);
        assertWellFormed(bvh, boxes, 4);
        var left = 1;
        var right = bvh.right(0);
        assertTrue(bvh.max(left, 0) < 500, "the left child is one cluster: " + bvh.max(left, 0));
        assertTrue(bvh.min(right, 0) > 500, "the right child the other: " + bvh.min(right, 0));
    }

    @Test
    @DisplayName("builds a tree that costs less than a leaf, on a scene where that is true")
    void costsLessThanALeaf() {
        var input = scattered(5000, 0.5f, 3);
        var bvh = BvhBuilder.build(input[0], input[1], 4);
        var cost = cost(bvh, 0) / area(bvh, 0);
        assertTrue(cost < 100, "the expected cost of a ray through the root is " + cost + " primitives, of 5000");
    }

    /// The surface-area cost of the subtree at `node`, unnormalised: its
    /// area times what a ray entering it pays.
    private static double cost(Bvh bvh, int node) {
        if (bvh.isLeaf(node)) {
            return area(bvh, node) * bvh.count(node);
        }
        return area(bvh, node) * BvhBuilder.STEP + cost(bvh, node + 1) + cost(bvh, bvh.right(node));
    }

    private static double area(Bvh bvh, int node) {
        var x = bvh.max(node, 0) - bvh.min(node, 0);
        var y = bvh.max(node, 1) - bvh.min(node, 1);
        var z = bvh.max(node, 2) - bvh.min(node, 2);
        return x * y + y * z + z * x;
    }

    @Test
    @DisplayName("splits primitives with one centroid by their median, so a leaf still holds four")
    void sameCentroid() {
        var count = 37;
        var boxes = new float[6 * count];
        var centroids = new float[3 * count];
        for (var i = 0; i < count; i++) {
            System.arraycopy(new float[] {-1, -1, -1, 1, 1, 1}, 0, boxes, 6 * i, 6);
        }
        var bvh = BvhBuilder.build(boxes, centroids, 4);
        assertWellFormed(bvh, boxes, 4);
        assertTrue(bvh.depth() <= 7, "a median split halves: depth " + bvh.depth());
    }

    @Test
    @DisplayName("stops at the depth limit on input made to be deep, holding every primitive")
    void boundedDepth() {
        // Boxes growing geometrically along x: the heuristic peels off the
        // largest each time, which would be a tree as deep as there are boxes.
        var count = 120;
        var boxes = new float[6 * count];
        var centroids = new float[3 * count];
        for (var i = 0; i < count; i++) {
            var x = (float) Math.pow(1.7, i);
            centroids[3 * i] = x;
            System.arraycopy(new float[] {x - x * 0.01f, 0, 0, x + x * 0.01f, 1, 1}, 0, boxes, 6 * i, 6);
        }
        var bvh = BvhBuilder.build(boxes, centroids, 1);
        assertWellFormed(bvh, boxes, 1);
        assertTrue(bvh.depth() <= BvhBuilder.MAX_DEPTH);
    }

    @Test
    @DisplayName("refuses no primitives, arrays that disagree, values that are not finite and inside-out boxes")
    void refusals() {
        assertThrows(IllegalArgumentException.class, () -> BvhBuilder.build(new float[0], new float[0], 4));
        assertThrows(IllegalArgumentException.class, () -> BvhBuilder.build(new float[6], new float[6], 4));
        assertThrows(IllegalArgumentException.class, () -> BvhBuilder.build(new float[6], new float[3], 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> BvhBuilder.build(new float[] {0, 0, 0, Float.NaN, 1, 1}, new float[3], 4));
        assertThrows(
                IllegalArgumentException.class,
                () -> BvhBuilder.build(new float[] {0, 0, 0, 1, 1, 1}, new float[] {0, Float.POSITIVE_INFINITY, 0}, 4));
        assertThrows(
                IllegalArgumentException.class,
                () -> BvhBuilder.build(new float[] {1, 0, 0, 0, 1, 1}, new float[3], 4));
    }
}
