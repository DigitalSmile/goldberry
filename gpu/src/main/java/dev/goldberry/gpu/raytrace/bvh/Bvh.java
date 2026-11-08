package dev.goldberry.gpu.raytrace.bvh;

import java.util.Objects;

/// A bounding volume hierarchy over some primitives, as [BvhBuilder] made it:
/// nodes flattened depth first, so a branch's left child follows it and its
/// right child is named, and the primitives in the order the leaves take
/// them.
///
/// A node is a box and two numbers. A branch's are its right child's index and
/// 0; a leaf's are the place in [#primitive] of its first primitive and its
/// count, at least one.
public final class Bvh {

    private final float[] bounds;
    private final int[] links;
    private final int[] order;
    private final int nodes;
    private final int depth;

    Bvh(float[] bounds, int[] links, int[] order, int nodes, int depth) {
        this.bounds = bounds;
        this.links = links;
        this.order = order;
        this.nodes = nodes;
        this.depth = depth;
    }

    /// How many nodes it has: at most twice its primitives, less one.
    public int nodeCount() {
        return nodes;
    }

    /// How many primitives it holds.
    public int primitiveCount() {
        return order.length;
    }

    /// The most nodes from the root to a leaf, the root and the leaf counted.
    public int depth() {
        return depth;
    }

    /// Whether node `node` is a leaf.
    public boolean isLeaf(int node) {
        return links[2 * Objects.checkIndex(node, nodes) + 1] > 0;
    }

    /// A branch's right child; its left is `node + 1`.
    ///
    /// @throws IllegalArgumentException when `node` is a leaf
    public int right(int node) {
        if (isLeaf(node)) {
            throw new IllegalArgumentException("node " + node + " is a leaf");
        }
        return links[2 * node];
    }

    /// Where a leaf's primitives start in [#primitive].
    ///
    /// @throws IllegalArgumentException when `node` is a branch
    public int first(int node) {
        if (!isLeaf(node)) {
            throw new IllegalArgumentException("node " + node + " is a branch");
        }
        return links[2 * node];
    }

    /// How many primitives a leaf holds; 0 for a branch.
    public int count(int node) {
        return Math.max(0, links[2 * Objects.checkIndex(node, nodes) + 1]);
    }

    /// The low corner of node `node`'s box on `axis` (0, 1 or 2).
    public float min(int node, int axis) {
        return bounds[6 * Objects.checkIndex(node, nodes) + Objects.checkIndex(axis, 3)];
    }

    /// The high corner of node `node`'s box on `axis`.
    public float max(int node, int axis) {
        return bounds[6 * Objects.checkIndex(node, nodes) + 3 + Objects.checkIndex(axis, 3)];
    }

    /// The primitive at `index` of the leaves' order: an index into what the
    /// tree was built over.
    public int primitive(int index) {
        return order[Objects.checkIndex(index, order.length)];
    }

    @Override
    public String toString() {
        return "Bvh[" + order.length + " primitives, " + nodes + " nodes, depth " + depth + "]";
    }
}
