package dev.goldberry.gpu.raytrace.bvh;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.TriangleMesh;
import dev.goldberry.gpu.raytrace.tier.FaceNormals;

/// The bottom level of a scene: a [Bvh] for each distinct mesh, built once,
/// and the triangles in the order its leaves take them, laid out for the
/// shaders. Every instance of a mesh shares its tree.
///
/// **Nodes**: every mesh's [Nodes], one after another; a leaf's first item is
/// a triangle's index in [#triangleWords].
///
/// **Triangles**: twelve words a triangle, three `float4`s: the first corner
/// and, in the fourth word, the triangle's index in its mesh; the second
/// corner less the first; the third less the first. The two edges are what a
/// ray's test needs, worked out once.
///
/// **Normals**: [FaceNormals] of the meshes, in their own order, a `float4`
/// a triangle, which a hit reads from its mesh's first normal and its index.
public final class MeshLevels {

    /// Words a triangle.
    public static final int TRIANGLE_WORDS = 12;

    /// The most triangles a leaf holds.
    public static final int MAX_LEAF_TRIANGLES = 4;

    private final List<TriangleMesh> meshes;
    private final IdentityHashMap<TriangleMesh, Integer> indices;
    private final int[] roots;
    private final float[][] rootBoxes;
    private final int[] firstNormals;
    private final int[] nodes;
    private final int[] triangles;
    private final float[] normals;
    private final int depth;

    private MeshLevels(
            List<TriangleMesh> meshes,
            int[] roots,
            float[][] rootBoxes,
            int[] firstNormals,
            int[] nodes,
            int[] triangles,
            float[] normals,
            int depth) {
        this.meshes = meshes;
        this.indices = new IdentityHashMap<>();
        for (var i = 0; i < meshes.size(); i++) {
            indices.put(meshes.get(i), i);
        }
        this.roots = roots;
        this.rootBoxes = rootBoxes;
        this.firstNormals = firstNormals;
        this.nodes = nodes;
        this.triangles = triangles;
        this.normals = normals;
        this.depth = depth;
    }

    /// The bottom level of `geometry`'s distinct meshes.
    public static MeshLevels of(SceneGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        var meshes = geometry.meshes();
        var trees = meshes.stream().map(MeshLevels::tree).toList();
        var nodeCount = trees.stream().mapToInt(Bvh::nodeCount).sum();
        var triangleCount =
                meshes.stream().mapToInt(TriangleMesh::triangleCount).sum();
        var nodes = new int[nodeCount * Nodes.WORDS];
        var triangles = new int[triangleCount * TRIANGLE_WORDS];
        var roots = new int[meshes.size()];
        var rootBoxes = new float[meshes.size()][];
        var nodeBase = 0;
        var triangleBase = 0;
        var depth = 0;
        for (var m = 0; m < meshes.size(); m++) {
            var mesh = meshes.get(m);
            var tree = trees.get(m);
            roots[m] = nodeBase;
            rootBoxes[m] = new float[] {
                tree.min(0, 0), tree.min(0, 1), tree.min(0, 2), tree.max(0, 0), tree.max(0, 1), tree.max(0, 2)
            };
            Nodes.write(tree, nodes, nodeBase, triangleBase);
            for (var i = 0; i < tree.primitiveCount(); i++) {
                writeTriangle(mesh, tree.primitive(i), triangles, (triangleBase + i) * TRIANGLE_WORDS);
            }
            nodeBase += tree.nodeCount();
            triangleBase += tree.primitiveCount();
            depth = Math.max(depth, tree.depth());
        }
        var faces = FaceNormals.of(meshes);
        return new MeshLevels(
                meshes, roots, rootBoxes, faces.firstTriangles(), nodes, triangles, faces.normals(), depth);
    }

    /// A tree over `mesh`'s triangles.
    static Bvh tree(TriangleMesh mesh) {
        var count = mesh.triangleCount();
        var boxes = new float[6 * count];
        var centroids = new float[3 * count];
        for (var t = 0; t < count; t++) {
            var a = mesh.corner(t, 0);
            var b = mesh.corner(t, 1);
            var c = mesh.corner(t, 2);
            boxes[6 * t] = Math.min(a.x(), Math.min(b.x(), c.x()));
            boxes[6 * t + 1] = Math.min(a.y(), Math.min(b.y(), c.y()));
            boxes[6 * t + 2] = Math.min(a.z(), Math.min(b.z(), c.z()));
            boxes[6 * t + 3] = Math.max(a.x(), Math.max(b.x(), c.x()));
            boxes[6 * t + 4] = Math.max(a.y(), Math.max(b.y(), c.y()));
            boxes[6 * t + 5] = Math.max(a.z(), Math.max(b.z(), c.z()));
            centroids[3 * t] = (a.x() + b.x() + c.x()) / 3;
            centroids[3 * t + 1] = (a.y() + b.y() + c.y()) / 3;
            centroids[3 * t + 2] = (a.z() + b.z() + c.z()) / 3;
        }
        return BvhBuilder.build(boxes, centroids, MAX_LEAF_TRIANGLES);
    }

    private static void writeTriangle(TriangleMesh mesh, int triangle, int[] into, int at) {
        var a = mesh.corner(triangle, 0);
        var b = mesh.corner(triangle, 1);
        var c = mesh.corner(triangle, 2);
        into[at] = Float.floatToRawIntBits(a.x());
        into[at + 1] = Float.floatToRawIntBits(a.y());
        into[at + 2] = Float.floatToRawIntBits(a.z());
        into[at + 3] = triangle;
        into[at + 4] = Float.floatToRawIntBits(b.x() - a.x());
        into[at + 5] = Float.floatToRawIntBits(b.y() - a.y());
        into[at + 6] = Float.floatToRawIntBits(b.z() - a.z());
        into[at + 8] = Float.floatToRawIntBits(c.x() - a.x());
        into[at + 9] = Float.floatToRawIntBits(c.y() - a.y());
        into[at + 10] = Float.floatToRawIntBits(c.z() - a.z());
    }

    /// The distinct meshes, in [SceneGeometry#meshes]'s order.
    public List<TriangleMesh> meshes() {
        return meshes;
    }

    /// The place of `mesh` in [#meshes], the same object, or empty.
    public OptionalInt indexOf(TriangleMesh mesh) {
        var index = indices.get(mesh);
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    /// Mesh `mesh`'s root node, in [#nodeWords].
    public int root(int mesh) {
        return roots[mesh];
    }

    /// Mesh `mesh`'s box, unwidened, six floats: its low corner, then its high.
    public float[] box(int mesh) {
        return rootBoxes[mesh].clone();
    }

    /// Where mesh `mesh`'s normals start, in triangles.
    public int firstNormal(int mesh) {
        return firstNormals[mesh];
    }

    /// The deepest of the trees.
    public int depth() {
        return depth;
    }

    /// How many nodes the trees have together.
    public int nodeCount() {
        return nodes.length / Nodes.WORDS;
    }

    /// How many triangles they hold.
    public int triangleCount() {
        return triangles.length / TRIANGLE_WORDS;
    }

    /// The nodes, [Nodes#WORDS] words each: a copy.
    public int[] nodeWords() {
        return nodes.clone();
    }

    /// The triangles, [#TRIANGLE_WORDS] words each: a copy.
    public int[] triangleWords() {
        return triangles.clone();
    }

    /// The face normals, four floats a triangle: a copy.
    public float[] normals() {
        return normals.clone();
    }

    /// The nodes, little-endian, for a storage buffer.
    public ByteBuffer nodeBytes() {
        return Packing.words(nodes);
    }

    /// The triangles, little-endian.
    public ByteBuffer triangleBytes() {
        return Packing.words(triangles);
    }

    /// The normals, little-endian.
    public ByteBuffer normalBytes() {
        return Packing.floats(normals);
    }

    /// How many bytes the three take on the GPU.
    public long bytes() {
        return 4L * (nodes.length + triangles.length + normals.length);
    }

    /// Word `index` of the nodes, for [BvhTraversal].
    int node(int index) {
        return nodes[index];
    }

    /// Word `index` of the triangles, for [BvhTraversal].
    int triangle(int index) {
        return triangles[index];
    }
}
