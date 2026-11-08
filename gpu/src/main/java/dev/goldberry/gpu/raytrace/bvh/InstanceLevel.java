package dev.goldberry.gpu.raytrace.bvh;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;

import dev.goldberry.gpu.raytrace.Instance;
import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.Transform;

/// The top level of a scene: a [Bvh] over its instances' boxes in the world,
/// and the instances in the order its leaves take them, laid out for the
/// shaders. Rebuilt by every update; the meshes' trees in [MeshLevels] stay.
///
/// **Nodes**: [Nodes]; a leaf's first item is an instance's index in
/// [#instanceWords].
///
/// **Instances**: sixteen words an instance, four `float4`s: the three rows
/// of the transform from the world into its mesh's space, then as unsigned
/// words its mesh's root node, its mesh's first normal, its id in the low 24
/// bits with its mask in the high 8, and 0.
///
/// An instance whose transform flattens its mesh has no inverse and is left
/// out: no ray can hit it.
public final class InstanceLevel {

    /// Words an instance.
    public static final int INSTANCE_WORDS = 16;

    /// The most instances a leaf holds.
    public static final int MAX_LEAF_INSTANCES = 2;

    private final SceneGeometry geometry;
    private final int[] nodes;
    private final int[] instances;
    private final int depth;

    private InstanceLevel(SceneGeometry geometry, int[] nodes, int[] instances, int depth) {
        this.geometry = geometry;
        this.nodes = nodes;
        this.instances = instances;
        this.depth = depth;
    }

    /// The top level of `geometry` over `meshes`.
    ///
    /// @throws IllegalArgumentException when `geometry` places a mesh `meshes`
    ///                                  was not built from, the same object
    public static InstanceLevel of(MeshLevels meshes, SceneGeometry geometry) {
        Objects.requireNonNull(meshes, "meshes");
        Objects.requireNonNull(geometry, "geometry");
        var placed = new ArrayList<Placed>(geometry.instances().size());
        for (var instance : geometry.instances()) {
            var mesh = meshes.indexOf(instance.mesh())
                    .orElseThrow(() -> new IllegalArgumentException(
                            instance.mesh() + " is not one of the meshes this scene was built with"));
            Affine.inverse(instance.transform())
                    .ifPresent(inverse ->
                            placed.add(new Placed(instance, mesh, inverse, worldBox(meshes, mesh, instance))));
        }
        if (placed.isEmpty()) {
            var nodes = new int[Nodes.WORDS];
            Nodes.writeEmpty(nodes, 0);
            return new InstanceLevel(geometry, nodes, new int[0], 1);
        }
        var boxes = new float[6 * placed.size()];
        var centroids = new float[3 * placed.size()];
        for (var i = 0; i < placed.size(); i++) {
            var box = placed.get(i).box;
            System.arraycopy(box, 0, boxes, 6 * i, 6);
            for (var axis = 0; axis < 3; axis++) {
                centroids[3 * i + axis] = (box[axis] + box[3 + axis]) / 2;
            }
        }
        var tree = BvhBuilder.build(boxes, centroids, MAX_LEAF_INSTANCES);
        var nodes = new int[tree.nodeCount() * Nodes.WORDS];
        Nodes.write(tree, nodes, 0, 0);
        var instances = new int[placed.size() * INSTANCE_WORDS];
        for (var i = 0; i < tree.primitiveCount(); i++) {
            var entry = placed.get(tree.primitive(i));
            var at = i * INSTANCE_WORDS;
            for (var w = 0; w < 12; w++) {
                instances[at + w] = Float.floatToRawIntBits(entry.inverse[w]);
            }
            instances[at + 12] = meshes.root(entry.mesh);
            instances[at + 13] = meshes.firstNormal(entry.mesh);
            instances[at + 14] = (entry.instance.id() & Instance.MAX_ID) | (entry.instance.mask() << 24);
        }
        return new InstanceLevel(geometry, nodes, instances, tree.depth());
    }

    /// An instance with an inverse: its mesh's place in the bottom level, the
    /// transform into its space, row-major, and its box in the world.
    private static final class Placed {

        final Instance instance;
        final int mesh;
        final float[] inverse;
        final float[] box;

        Placed(Instance instance, int mesh, float[] inverse, float[] box) {
            this.instance = instance;
            this.mesh = mesh;
            this.inverse = inverse;
            this.box = box;
        }
    }

    /// The box around `instance`'s mesh where it places it: the mesh's box's
    /// eight corners, transformed.
    private static float[] worldBox(MeshLevels meshes, int mesh, Instance instance) {
        var local = meshes.box(mesh);
        var box = new float[] {
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        var t = instance.transform();
        for (var corner = 0; corner < 8; corner++) {
            var x = (corner & 1) == 0 ? local[0] : local[3];
            var y = (corner & 2) == 0 ? local[1] : local[4];
            var z = (corner & 4) == 0 ? local[2] : local[5];
            var world = apply(t, x, y, z);
            for (var axis = 0; axis < 3; axis++) {
                box[axis] = Math.min(box[axis], world[axis]);
                box[3 + axis] = Math.max(box[3 + axis], world[axis]);
            }
        }
        return box;
    }

    private static float[] apply(Transform t, float x, float y, float z) {
        return new float[] {
            t.m00() * x + t.m01() * y + t.m02() * z + t.m03(),
            t.m10() * x + t.m11() * y + t.m12() * z + t.m13(),
            t.m20() * x + t.m21() * y + t.m22() * z + t.m23()
        };
    }

    /// What it was built from.
    public SceneGeometry geometry() {
        return geometry;
    }

    /// How many instances it places: those of [#geometry] that have an
    /// inverse.
    public int instanceCount() {
        return instances.length / INSTANCE_WORDS;
    }

    /// How many nodes its tree has.
    public int nodeCount() {
        return nodes.length / Nodes.WORDS;
    }

    /// The deepest leaf.
    public int depth() {
        return depth;
    }

    /// The nodes, [Nodes#WORDS] words each: a copy.
    public int[] nodeWords() {
        return nodes.clone();
    }

    /// The instances, [#INSTANCE_WORDS] words each: a copy.
    public int[] instanceWords() {
        return instances.clone();
    }

    /// The nodes, little-endian, for a storage buffer.
    public ByteBuffer nodeBytes() {
        return Packing.words(nodes);
    }

    /// The instances, little-endian; one empty instance when there are none.
    public ByteBuffer instanceBytes() {
        return Packing.words(instances.length == 0 ? new int[INSTANCE_WORDS] : instances);
    }

    /// How many bytes the two take on the GPU.
    public long bytes() {
        return 4L * (nodes.length + Math.max(INSTANCE_WORDS, instances.length));
    }

    /// Word `index` of the nodes, for [BvhTraversal].
    int node(int index) {
        return nodes[index];
    }

    /// Word `index` of the instances, for [BvhTraversal].
    int instance(int index) {
        return instances[index];
    }
}
