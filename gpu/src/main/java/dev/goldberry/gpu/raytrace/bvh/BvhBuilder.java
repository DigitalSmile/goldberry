package dev.goldberry.gpu.raytrace.bvh;

import java.util.Arrays;

/// Builds a [Bvh] by a binned surface-area heuristic: at each node the
/// primitives' centroids are sorted into [#BINS] bins along the axis they
/// spread widest on, and the node splits at the bin boundary that costs least,
/// or becomes a leaf where that is cheaper still.
///
/// The cost of a split is a step down plus each side's primitives weighted by
/// the share of the node's surface its box has: the chance a ray through the
/// node meets that side. A leaf costs its primitives. A node of more than
/// `maxLeaf` primitives always splits, by its centroids' median where the bins
/// cannot part them, and one at [#MAX_DEPTH] is a leaf whatever its size, so a
/// walk of the tree never needs more than that many entries of stack.
public final class BvhBuilder {

    /// How many bins a node's centroids are sorted into.
    public static final int BINS = 16;

    /// The deepest a leaf is, the root at depth 1: what the shaders' stacks
    /// are sized for.
    public static final int MAX_DEPTH = 48;

    /// What stepping into a node costs, against a primitive's 1.
    static final float STEP = 1f;

    private final float[] boxes;
    private final float[] centroids;
    private final int maxLeaf;
    private final int[] order;
    private final float[] bounds;
    private final int[] links;
    private int nodes;
    private int depth;

    private BvhBuilder(float[] boxes, float[] centroids, int maxLeaf) {
        this.boxes = boxes;
        this.centroids = centroids;
        this.maxLeaf = maxLeaf;
        var count = centroids.length / 3;
        this.order = new int[count];
        for (var i = 0; i < count; i++) {
            order[i] = i;
        }
        var most = Math.max(1, 2 * count - 1);
        this.bounds = new float[6 * most];
        this.links = new int[2 * most];
    }

    /// A tree over primitives whose boxes are `boxes`, six floats each (the low
    /// corner then the high), and whose centroids are `centroids`, three each,
    /// with leaves of up to `maxLeaf` primitives.
    ///
    /// @throws IllegalArgumentException when there are no primitives, the two
    ///                                  arrays disagree on how many, a value is
    ///                                  not finite, a box is inside out, or
    ///                                  `maxLeaf` is below one
    public static Bvh build(float[] boxes, float[] centroids, int maxLeaf) {
        if (maxLeaf < 1) {
            throw new IllegalArgumentException("a leaf holds at least one primitive, not " + maxLeaf);
        }
        if (centroids.length == 0 || centroids.length % 3 != 0 || boxes.length != centroids.length * 2) {
            throw new IllegalArgumentException(
                    boxes.length + " box values and " + centroids.length + " centroid values");
        }
        for (var i = 0; i < boxes.length; i++) {
            if (!Float.isFinite(boxes[i])) {
                throw new IllegalArgumentException("box value " + i + " is " + boxes[i]);
            }
        }
        for (var i = 0; i < boxes.length; i += 6) {
            for (var axis = 0; axis < 3; axis++) {
                if (boxes[i + axis] > boxes[i + 3 + axis]) {
                    throw new IllegalArgumentException("box " + i / 6 + " is inside out on axis " + axis);
                }
            }
        }
        for (var i = 0; i < centroids.length; i++) {
            if (!Float.isFinite(centroids[i])) {
                throw new IllegalArgumentException("centroid value " + i + " is " + centroids[i]);
            }
        }
        var builder = new BvhBuilder(boxes, centroids, maxLeaf);
        builder.node(0, builder.order.length, 1);
        return new Bvh(
                Arrays.copyOf(builder.bounds, 6 * builder.nodes),
                Arrays.copyOf(builder.links, 2 * builder.nodes),
                builder.order,
                builder.nodes,
                builder.depth);
    }

    /// Builds the node over `order[start, end)` at `level`, and its subtree;
    /// returns its index.
    private int node(int start, int end, int level) {
        var node = nodes++;
        depth = Math.max(depth, level);
        enclose(start, end, bounds, 6 * node);
        var count = end - start;
        if (count == 1 || level >= MAX_DEPTH) {
            return leaf(node, start, count);
        }
        var split = split(node, start, end);
        if (split < 0) {
            return leaf(node, start, count);
        }
        node(start, split, level + 1);
        links[2 * node] = node(split, end, level + 1);
        links[2 * node + 1] = 0;
        return node;
    }

    private int leaf(int node, int start, int count) {
        links[2 * node] = start;
        links[2 * node + 1] = count;
        return node;
    }

    /// Partitions `order[start, end)` where it is best split and returns the
    /// first index of the right side, or -1 when the node is best a leaf.
    private int split(int node, int start, int end) {
        var count = end - start;
        var centroidBounds = new float[6];
        Arrays.fill(centroidBounds, 0, 3, Float.POSITIVE_INFINITY);
        Arrays.fill(centroidBounds, 3, 6, Float.NEGATIVE_INFINITY);
        for (var i = start; i < end; i++) {
            var c = 3 * order[i];
            for (var axis = 0; axis < 3; axis++) {
                centroidBounds[axis] = Math.min(centroidBounds[axis], centroids[c + axis]);
                centroidBounds[3 + axis] = Math.max(centroidBounds[3 + axis], centroids[c + axis]);
            }
        }
        var axis = widest(centroidBounds);
        var low = centroidBounds[axis];
        var extent = centroidBounds[3 + axis] - low;
        var area = area(bounds, 6 * node);
        if (!(extent > 0) || !(area > 0)) {
            // Every centroid in one place, or a node with no surface: the bins
            // cannot tell the primitives apart.
            return count <= maxLeaf ? -1 : median(start, end, axis);
        }

        var binCounts = new int[BINS];
        var binBounds = new float[6 * BINS];
        for (var b = 0; b < BINS; b++) {
            empty(binBounds, 6 * b);
        }
        var scale = BINS / extent;
        for (var i = start; i < end; i++) {
            var b = bin(order[i], axis, low, scale);
            binCounts[b]++;
            grow(binBounds, 6 * b, boxes, 6 * order[i]);
        }

        // The cost of each split, the left side bins [0, b] and the right the
        // rest, swept from both ends.
        var leftCost = new float[BINS - 1];
        var running = new float[6];
        empty(running, 0);
        var leftCount = 0;
        for (var b = 0; b < BINS - 1; b++) {
            leftCount += binCounts[b];
            grow(running, 0, binBounds, 6 * b);
            leftCost[b] = leftCount == 0 ? Float.POSITIVE_INFINITY : leftCount * area(running, 0);
        }
        empty(running, 0);
        var rightCount = 0;
        var best = -1;
        var bestCost = Float.POSITIVE_INFINITY;
        for (var b = BINS - 1; b > 0; b--) {
            rightCount += binCounts[b];
            grow(running, 0, binBounds, 6 * b);
            if (rightCount == 0 || rightCount == count) {
                continue;
            }
            var cost = leftCost[b - 1] + rightCount * area(running, 0);
            if (cost < bestCost) {
                bestCost = cost;
                best = b - 1;
            }
        }
        if (best < 0) {
            return count <= maxLeaf ? -1 : median(start, end, axis);
        }
        var splitCost = STEP + bestCost / area;
        if (count <= maxLeaf && count <= splitCost) {
            return -1;
        }
        return partition(start, end, axis, low, scale, best);
    }

    /// Moves the primitives in bins up to `last` to the front of `[start,
    /// end)`, and returns where the rest begin.
    private int partition(int start, int end, int axis, float low, float scale, int last) {
        var i = start;
        var j = end - 1;
        while (i <= j) {
            if (bin(order[i], axis, low, scale) <= last) {
                i++;
            } else {
                var swap = order[i];
                order[i] = order[j];
                order[j] = swap;
                j--;
            }
        }
        return i;
    }

    /// Splits `[start, end)` at its middle by the centroids along `axis`.
    private int median(int start, int end, int axis) {
        var middle = (start + end) >>> 1;
        var sorted = Arrays.stream(order, start, end)
                .boxed()
                .sorted((a, b) -> Float.compare(centroids[3 * a + axis], centroids[3 * b + axis]))
                .mapToInt(Integer::intValue)
                .toArray();
        System.arraycopy(sorted, 0, order, start, sorted.length);
        return middle;
    }

    private int bin(int primitive, int axis, float low, float scale) {
        var b = (int) ((centroids[3 * primitive + axis] - low) * scale);
        return Math.clamp(b, 0, BINS - 1);
    }

    /// Writes the box around the primitives of `order[start, end)` at `at`.
    private void enclose(int start, int end, float[] into, int at) {
        empty(into, at);
        for (var i = start; i < end; i++) {
            grow(into, at, boxes, 6 * order[i]);
        }
    }

    private static void empty(float[] box, int at) {
        Arrays.fill(box, at, at + 3, Float.POSITIVE_INFINITY);
        Arrays.fill(box, at + 3, at + 6, Float.NEGATIVE_INFINITY);
    }

    private static void grow(float[] box, int at, float[] by, int from) {
        for (var axis = 0; axis < 3; axis++) {
            box[at + axis] = Math.min(box[at + axis], by[from + axis]);
            box[at + 3 + axis] = Math.max(box[at + 3 + axis], by[from + 3 + axis]);
        }
    }

    private static int widest(float[] box) {
        var x = box[3] - box[0];
        var y = box[4] - box[1];
        var z = box[5] - box[2];
        return x >= y && x >= z ? 0 : y >= z ? 1 : 2;
    }

    /// Half a box's surface area: what the heuristic weighs, a ratio of two of
    /// them being all it reads.
    static float area(float[] box, int at) {
        var x = box[at + 3] - box[at];
        var y = box[at + 4] - box[at + 1];
        var z = box[at + 5] - box[at + 2];
        if (!(x >= 0 && y >= 0 && z >= 0)) {
            return 0;
        }
        return x * y + y * z + z * x;
    }
}
