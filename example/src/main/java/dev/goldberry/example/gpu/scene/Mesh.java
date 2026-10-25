package dev.goldberry.example.gpu.scene;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.IntStream;

import dev.goldberry.gpu.raytrace.TriangleMesh;

/// A solid as the room's two renderers take it: triangles of a position and a
/// normal a vertex, three vertices a triangle with none shared, for the
/// rasterizer's vertex buffer; and the same positions as a [TriangleMesh], for
/// the ray tracer.
///
/// Each triangle is counter-clockwise seen from outside.
final class Mesh {

    /// Floats a vertex: a position, then a normal.
    static final int FLOATS = 6;

    /// Bytes a vertex.
    static final int STRIDE = FLOATS * Float.BYTES;

    private final float[] vertices;
    private final TriangleMesh traced;

    private Mesh(float[] vertices) {
        this.vertices = vertices;
        var count = vertices.length / FLOATS;
        var positions = new float[count * 3];
        for (var i = 0; i < count; i++) {
            System.arraycopy(vertices, i * FLOATS, positions, i * 3, 3);
        }
        this.traced = TriangleMesh.of(positions, IntStream.range(0, count).toArray());
    }

    /// How many vertices it is drawn with.
    int vertexCount() {
        return vertices.length / FLOATS;
    }

    /// The vertex buffer's bytes.
    ByteBuffer bytes() {
        var bytes = ByteBuffer.allocate(vertices.length * Float.BYTES).order(ByteOrder.nativeOrder());
        bytes.asFloatBuffer().put(vertices);
        return bytes;
    }

    /// The same triangles, for the ray tracer.
    TriangleMesh traced() {
        return traced;
    }

    /// A unit cube centred on the origin, a normal a face.
    static Mesh cube() {
        var out = new float[36 * FLOATS];
        var at = 0;
        for (var face = 0; face < 6; face++) {
            var axis = face / 2;
            var sign = face % 2 == 0 ? 1f : -1f;
            var u = (axis + 1) % 3;
            var v = (axis + 2) % 3;
            float[][] around = sign > 0
                    ? new float[][] {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}
                    : new float[][] {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            for (var index : new int[] {0, 1, 2, 0, 2, 3}) {
                var vertex = new float[FLOATS];
                vertex[axis] = sign * 0.5f;
                vertex[u] = around[index][0] * 0.5f;
                vertex[v] = around[index][1] * 0.5f;
                vertex[3 + axis] = sign;
                System.arraycopy(vertex, 0, out, at, FLOATS);
                at += FLOATS;
            }
        }
        return new Mesh(out);
    }

    /// A square of side `size` in the `y = 0` plane, facing up.
    static Mesh floor(float size) {
        var h = size / 2;
        float[][] corners = {{-h, h}, {h, h}, {h, -h}, {-h, -h}};
        var out = new float[6 * FLOATS];
        var at = 0;
        for (var index : new int[] {0, 1, 2, 0, 2, 3}) {
            out[at] = corners[index][0];
            out[at + 2] = corners[index][1];
            out[at + 4] = 1;
            at += FLOATS;
        }
        return new Mesh(out);
    }

    /// A sphere of radius 0.5 centred on the origin, of `slices` around and
    /// `stacks` from pole to pole, with smooth normals.
    static Mesh sphere(int slices, int stacks) {
        var triangles = slices * (stacks - 1) * 2;
        var out = new float[triangles * 3 * FLOATS];
        var at = 0;
        for (var stack = 0; stack < stacks; stack++) {
            for (var slice = 0; slice < slices; slice++) {
                var a = point(slice, stack, slices, stacks);
                var b = point(slice, stack + 1, slices, stacks);
                var c = point(slice + 1, stack + 1, slices, stacks);
                var d = point(slice + 1, stack, slices, stacks);
                // The pole rows are one triangle a slice; the rest two.
                if (stack != 0) {
                    at = put(out, at, a, d, b);
                }
                if (stack != stacks - 1) {
                    at = put(out, at, d, c, b);
                }
            }
        }
        return new Mesh(out);
    }

    private static float[] point(int slice, int stack, int slices, int stacks) {
        var theta = Math.PI * stack / stacks;
        var phi = 2 * Math.PI * slice / slices;
        return new float[] {
            (float) (Math.sin(theta) * Math.cos(phi)),
            (float) Math.cos(theta),
            (float) (Math.sin(theta) * Math.sin(phi))
        };
    }

    private static int put(float[] out, int at, float[]... corners) {
        var next = at;
        for (var unit : corners) {
            for (var i = 0; i < 3; i++) {
                out[next + i] = unit[i] * 0.5f;
                out[next + 3 + i] = unit[i];
            }
            next += FLOATS;
        }
        return next;
    }
}
