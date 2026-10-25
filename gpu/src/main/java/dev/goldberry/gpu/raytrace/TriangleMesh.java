package dev.goldberry.gpu.raytrace;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Objects;

/// Triangles a ray can hit: vertex positions, three floats each, and indices,
/// three a triangle. Immutable: the arrays are copied in, and read out through
/// read-only views.
///
/// A mesh is geometry once, however many [Instance]s place it: a ray tracer
/// builds one acceleration structure a mesh and shares it between them, so
/// two instances of the *same* mesh object cost one build.
///
/// A triangle's [Hit#primitiveId] is its index here, `indices` element `3 *
/// id` onwards, and its barycentrics weigh its second and third vertex.
public final class TriangleMesh {

    private final float[] positions;
    private final int[] indices;

    private TriangleMesh(float[] positions, int[] indices) {
        this.positions = positions;
        this.indices = indices;
    }

    /// A mesh of `positions` (x, y, z of each vertex) and `indices` (three
    /// vertices a triangle, counter-clockwise is not required: both faces are
    /// hit).
    ///
    /// @throws IllegalArgumentException when either is empty or not a whole
    ///                                  number of vertices or triangles, a
    ///                                  position is not finite, or an index is
    ///                                  outside the vertices
    public static TriangleMesh of(float[] positions, int[] indices) {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(indices, "indices");
        if (positions.length == 0 || positions.length % 3 != 0) {
            throw new IllegalArgumentException(
                    "positions are three floats a vertex, and there are " + positions.length);
        }
        if (indices.length == 0 || indices.length % 3 != 0) {
            throw new IllegalArgumentException("indices are three a triangle, and there are " + indices.length);
        }
        for (var i = 0; i < positions.length; i++) {
            if (!Float.isFinite(positions[i])) {
                throw new IllegalArgumentException("position element " + i + " is " + positions[i]);
            }
        }
        var vertices = positions.length / 3;
        for (var i = 0; i < indices.length; i++) {
            if (indices[i] < 0 || indices[i] >= vertices) {
                throw new IllegalArgumentException(
                        "index " + i + " is " + indices[i] + ", and the mesh has " + vertices + " vertices");
            }
        }
        return new TriangleMesh(positions.clone(), indices.clone());
    }

    /// How many vertices it has.
    public int vertexCount() {
        return positions.length / 3;
    }

    /// How many triangles it has.
    public int triangleCount() {
        return indices.length / 3;
    }

    /// Vertex `index`.
    ///
    /// @throws IndexOutOfBoundsException when there is no such vertex
    public Vector3 vertex(int index) {
        Objects.checkIndex(index, vertexCount());
        return new Vector3(positions[3 * index], positions[3 * index + 1], positions[3 * index + 2]);
    }

    /// Vertex `corner` (0, 1 or 2) of triangle `triangle`.
    ///
    /// @throws IndexOutOfBoundsException when there is no such triangle or
    ///                                  corner
    public Vector3 corner(int triangle, int corner) {
        Objects.checkIndex(triangle, triangleCount());
        Objects.checkIndex(corner, 3);
        return vertex(indices[3 * triangle + corner]);
    }

    /// The positions, x, y and z of each vertex: a read-only view.
    public FloatBuffer positions() {
        return FloatBuffer.wrap(positions).asReadOnlyBuffer();
    }

    /// The indices, three a triangle: a read-only view.
    public IntBuffer indices() {
        return IntBuffer.wrap(indices).asReadOnlyBuffer();
    }

    @Override
    public String toString() {
        return "TriangleMesh[" + vertexCount() + " vertices, " + triangleCount() + " triangles]";
    }
}
