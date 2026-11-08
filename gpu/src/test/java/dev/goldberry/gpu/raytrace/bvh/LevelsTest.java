package dev.goldberry.gpu.raytrace.bvh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.raytrace.Instance;
import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.Transform;
import dev.goldberry.gpu.raytrace.TriangleMesh;
import dev.goldberry.gpu.raytrace.Vector3;

/// The two levels' layouts, word for word: what the shaders read.
@DisplayName("a scene's two levels, as the shaders read them")
class LevelsTest {

    private static final TriangleMesh QUAD =
            TriangleMesh.of(new float[] {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0}, new int[] {0, 1, 2, 0, 2, 3});

    private static final TriangleMesh TRIANGLE =
            TriangleMesh.of(new float[] {0, 0, 0, 2, 0, 0, 0, 3, 0}, new int[] {0, 1, 2});

    @Test
    @DisplayName("lays each mesh's tree after the last, its leaves naming its triangles in the triangles' buffer")
    void meshes() {
        var geometry = SceneGeometry.of(
                Instance.of(QUAD, Transform.IDENTITY, 1),
                Instance.of(TRIANGLE, Transform.IDENTITY, 2),
                Instance.of(QUAD, Transform.translation(new Vector3(5, 0, 0)), 3));
        var levels = MeshLevels.of(geometry);
        assertEquals(2, levels.meshes().size(), "two distinct meshes");
        assertEquals(3, levels.triangleCount());
        assertEquals(0, levels.root(0));
        assertEquals(levels.nodeCount() - 1, levels.root(1), "the triangle's tree is one leaf, after the quad's");
        assertEquals(0, levels.firstNormal(0));
        assertEquals(2, levels.firstNormal(1));

        var nodes = levels.nodeWords();
        var triangleRoot = levels.root(1) * Nodes.WORDS;
        assertEquals(Nodes.LEAF | 1, nodes[triangleRoot + 7]);
        assertEquals(2, nodes[triangleRoot + 3], "its one triangle follows the quad's two");

        var triangles = levels.triangleWords();
        var at = 2 * MeshLevels.TRIANGLE_WORDS;
        assertEquals(0, triangles[at + 3], "the triangle is 0 of its mesh");
        assertEquals(2f, Float.intBitsToFloat(triangles[at + 4]), "the first edge, x");
        assertEquals(3f, Float.intBitsToFloat(triangles[at + 9]), "the second edge, y");
        var quadTriangles = new HashSet<Integer>();
        quadTriangles.add(triangles[3]);
        quadTriangles.add(triangles[MeshLevels.TRIANGLE_WORDS + 3]);
        assertEquals(Set.of(0, 1), quadTriangles, "both of the quad's, each once");

        assertEquals(0f, levels.normals()[8], "the triangle's normal is +z: x");
        assertEquals(1f, Math.abs(levels.normals()[10]), "the triangle's normal is +z: z");
        assertEquals(ByteOrder.LITTLE_ENDIAN, levels.nodeBytes().order());
        assertEquals(nodes[3], levels.nodeBytes().getInt(12));
        assertEquals(4L * (nodes.length + triangles.length + levels.normals().length), levels.bytes());
    }

    @Test
    @DisplayName("widens every box by a few units in the last place")
    void widened() {
        var levels = MeshLevels.of(SceneGeometry.of(Instance.of(TRIANGLE, Transform.IDENTITY, 1)));
        var nodes = levels.nodeWords();
        assertTrue(Float.intBitsToFloat(nodes[0]) < 0, "the low corner moved below 0");
        assertTrue(Float.intBitsToFloat(nodes[4]) > 2, "the high corner past 2");
        assertTrue(Float.intBitsToFloat(nodes[4]) - 2 < 1e-5f, "by a few units in the last place");
        assertTrue(Float.intBitsToFloat(nodes[6]) > 0, "a flat box is given thickness");
    }

    @Test
    @DisplayName(
            "places the instances with their inverse transforms, mesh roots, normals and ids, in the leaves' order")
    void instances() {
        var moved = Transform.scaling(2).then(Transform.translation(new Vector3(10, 0, 0)));
        var geometry = SceneGeometry.of(
                Instance.of(QUAD, Transform.IDENTITY, 7), new Instance(TRIANGLE, moved, Instance.MAX_ID, 0x81));
        var meshes = MeshLevels.of(geometry);
        var top = InstanceLevel.of(meshes, geometry);
        assertEquals(2, top.instanceCount());
        var words = top.instanceWords();
        var ids = new HashSet<Integer>();
        for (var i = 0; i < 2; i++) {
            var at = i * InstanceLevel.INSTANCE_WORDS;
            var idMask = words[at + 14];
            ids.add(idMask & Instance.MAX_ID);
            if ((idMask & Instance.MAX_ID) == Instance.MAX_ID) {
                assertEquals(0x81, idMask >>> 24, "the mask in the top byte");
                assertEquals(meshes.root(1), words[at + 12]);
                assertEquals(meshes.firstNormal(1), words[at + 13]);
                assertEquals(0.5f, Float.intBitsToFloat(words[at]), "the inverse scales by a half");
                assertEquals(-5f, Float.intBitsToFloat(words[at + 3]), "and moves back by 10, halved");
            } else {
                assertEquals(0xFF, idMask >>> 24);
                assertEquals(meshes.root(0), words[at + 12]);
            }
            assertEquals(0, words[at + 15]);
        }
        assertEquals(Set.of(7, Instance.MAX_ID), ids);
        var root = top.nodeWords();
        assertEquals(-0.0001f, Float.intBitsToFloat(root[0]), 1e-3, "the root's box starts at the quad");
        assertEquals(14f, Float.intBitsToFloat(root[4]), 1e-3, "and ends at the scaled triangle: 10 + 2 × 2");
    }

    @Test
    @DisplayName("leaves out an instance that flattens its mesh, and is an empty leaf when every one does")
    void flattened() {
        var flat = Transform.scaling(1, 0, 1);
        var geometry = SceneGeometry.of(Instance.of(QUAD, flat, 1), Instance.of(QUAD, Transform.IDENTITY, 2));
        var meshes = MeshLevels.of(geometry);
        assertEquals(1, InstanceLevel.of(meshes, geometry).instanceCount());

        var none = SceneGeometry.of(Instance.of(QUAD, flat, 1));
        var empty = InstanceLevel.of(MeshLevels.of(none), none);
        assertEquals(0, empty.instanceCount());
        assertEquals(1, empty.nodeCount());
        assertEquals(Nodes.LEAF, empty.nodeWords()[7], "a leaf of nothing");
        assertEquals(64, empty.instanceBytes().remaining(), "a buffer of one empty instance, for the GPU");
    }

    @Test
    @DisplayName("refuses an instance of a mesh it was not built with, however alike")
    void foreignMesh() {
        var geometry = SceneGeometry.of(Instance.of(QUAD, Transform.IDENTITY, 1));
        var meshes = MeshLevels.of(geometry);
        var twin = TriangleMesh.of(new float[] {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0}, new int[] {0, 1, 2, 0, 2, 3});
        assertThrows(
                IllegalArgumentException.class,
                () -> InstanceLevel.of(meshes, SceneGeometry.of(Instance.of(twin, Transform.IDENTITY, 1))));
        assertFalse(meshes.indexOf(twin).isPresent());
    }

    @Test
    @DisplayName("inverts a transform in double precision, and has none for one that flattens")
    void inverse() {
        var transform = Transform.rotationY(0.7)
                .then(Transform.scaling(2, 3, 0.5f))
                .then(Transform.rotationX(-0.3))
                .then(Transform.translation(new Vector3(4, -2, 9)));
        var inverse = Affine.inverse(transform).orElseThrow();
        var back = Transform.ofRowMajor(inverse);
        var round = transform.then(back);
        assertArrayEquals(Transform.IDENTITY.toRowMajor(), round.toRowMajor(), 1e-5f);
        var mirrored = Affine.inverse(Transform.scaling(-1, 1, 1)).orElseThrow();
        assertEquals(-1f, mirrored[0]);
        assertTrue(Affine.inverse(Transform.scaling(0)).isEmpty());
        assertTrue(Affine.inverse(Transform.scaling(1, 1, 0)).isEmpty());
    }
}
