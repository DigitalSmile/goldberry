package dev.goldberry.gpu.raytrace.vulkan;

import java.util.List;

import dev.goldberry.gpu.raytrace.TriangleMesh;

/// Each triangle's normal in object space, for the closest-hit shader: the
/// scene's distinct meshes one after another, a `float4` a triangle, and where
/// each mesh's start.
///
/// A normal is the cross product of the triangle's two edges from its first
/// corner, normalised, so it points out of a counter-clockwise face. Which way
/// it points does not matter to the shader, which turns it to face the ray;
/// a triangle of no area has the y axis.
final class FaceNormals {

    /// Bytes a triangle's normal takes.
    static final int BYTES = 4 * Float.BYTES;

    private final float[] normals;
    private final int[] firstTriangles;

    private FaceNormals(float[] normals, int[] firstTriangles) {
        this.normals = normals;
        this.firstTriangles = firstTriangles;
    }

    /// The normals of `meshes`, in order.
    static FaceNormals of(List<TriangleMesh> meshes) {
        var firsts = new int[meshes.size()];
        var total = 0;
        for (var i = 0; i < meshes.size(); i++) {
            firsts[i] = total;
            total += meshes.get(i).triangleCount();
        }
        var normals = new float[total * 4];
        var at = 0;
        for (var mesh : meshes) {
            for (var t = 0; t < mesh.triangleCount(); t++) {
                var a = mesh.corner(t, 0);
                var b = mesh.corner(t, 1);
                var c = mesh.corner(t, 2);
                var n = b.minus(a).cross(c.minus(a));
                var length = n.length();
                if (length > 0) {
                    normals[at] = n.x() / length;
                    normals[at + 1] = n.y() / length;
                    normals[at + 2] = n.z() / length;
                } else {
                    normals[at + 1] = 1;
                }
                at += 4;
            }
        }
        return new FaceNormals(normals, firsts);
    }

    /// Four floats a triangle, the fourth 0: a copy.
    float[] normals() {
        return normals.clone();
    }

    /// Where each mesh's normals start, in triangles: a copy.
    int[] firstTriangles() {
        return firstTriangles.clone();
    }

    /// How many triangles they cover.
    int triangles() {
        return normals.length / 4;
    }
}
