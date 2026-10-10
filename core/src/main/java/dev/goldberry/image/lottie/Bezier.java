package dev.goldberry.image.lottie;

/// One Lottie path: vertices, and each vertex's in and out tangent relative to
/// it, open or closed.
///
/// The segment from vertex `k` to vertex `k + 1` is the cubic through
/// `v[k] + out[k]` and `v[k + 1] + in[k + 1]`; a closed path has one more,
/// from the last vertex back to the first.
///
/// Immutable once made. The arrays are interleaved `x, y` pairs.
final class Bezier {

    /// No vertices: nothing to draw.
    static final Bezier EMPTY = new Bezier(new double[0], new double[0], new double[0], false);

    private final double[] vertices;
    private final double[] in;
    private final double[] out;
    private final boolean closed;

    /// @param vertices `x, y` per vertex
    /// @param in       each vertex's in tangent, relative to it
    /// @param out      each vertex's out tangent, relative to it
    Bezier(double[] vertices, double[] in, double[] out, boolean closed) {
        if (in.length != vertices.length || out.length != vertices.length || vertices.length % 2 != 0) {
            throw new IllegalArgumentException("a path has as many tangents as vertices, two numbers each");
        }
        this.vertices = vertices.clone();
        this.in = in.clone();
        this.out = out.clone();
        this.closed = closed;
    }

    int size() {
        return vertices.length / 2;
    }

    boolean closed() {
        return closed;
    }

    double x(int index) {
        return vertices[index * 2];
    }

    double y(int index) {
        return vertices[index * 2 + 1];
    }

    double inX(int index) {
        return in[index * 2];
    }

    double inY(int index) {
        return in[index * 2 + 1];
    }

    double outX(int index) {
        return out[index * 2];
    }

    double outY(int index) {
        return out[index * 2 + 1];
    }

    /// Whether `other` can be blended with this: the same number of vertices.
    boolean matches(Bezier other) {
        return vertices.length == other.vertices.length;
    }

    /// This path `t` of the way to `other`, vertex by vertex.
    Bezier towards(Bezier other, double t) {
        var v = new double[vertices.length];
        var i = new double[vertices.length];
        var o = new double[vertices.length];
        for (var k = 0; k < vertices.length; k++) {
            v[k] = vertices[k] + (other.vertices[k] - vertices[k]) * t;
            i[k] = in[k] + (other.in[k] - in[k]) * t;
            o[k] = out[k] + (other.out[k] - out[k]) * t;
        }
        return new Bezier(v, i, o, closed);
    }
}
