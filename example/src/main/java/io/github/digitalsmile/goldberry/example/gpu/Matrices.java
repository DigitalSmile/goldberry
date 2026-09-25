package io.github.digitalsmile.goldberry.example.gpu;

/// The few 4x4 matrices a cube needs, row-major, which is how `cube.vert`
/// reads its floats: a point is a column, multiplied on the right.
///
/// An application's own, as a renderer's maths is: `:gpu` hands a renderer a
/// device and a frame, and leaves the scene to it.
final class Matrices {

    private Matrices() {}

    /// `a` times `b`: `b` applied first.
    static float[] multiply(float[] a, float[] b) {
        var out = new float[16];
        for (var row = 0; row < 4; row++) {
            for (var column = 0; column < 4; column++) {
                var sum = 0f;
                for (var k = 0; k < 4; k++) {
                    sum += a[row * 4 + k] * b[k * 4 + column];
                }
                out[row * 4 + column] = sum;
            }
        }
        return out;
    }

    /// A turn of `angle` radians about y.
    static float[] rotateY(double angle) {
        var c = (float) Math.cos(angle);
        var s = (float) Math.sin(angle);
        return new float[] {c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
    }

    /// A turn of `angle` radians about x.
    static float[] rotateX(double angle) {
        var c = (float) Math.cos(angle);
        var s = (float) Math.sin(angle);
        return new float[] {1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
    }

    /// A move by `(x, y, z)`.
    static float[] translate(float x, float y, float z) {
        return new float[] {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
    }

    /// A right-handed perspective onto SDL's clip space: y up, and depth from 0
    /// at `near` to 1 at `far`.
    static float[] perspective(double fovY, double aspect, double near, double far) {
        var f = (float) (1 / Math.tan(fovY / 2));
        var depth = (float) (far / (near - far));
        var offset = (float) (near * far / (near - far));
        return new float[] {f / (float) aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, depth, offset, 0, 0, -1, 0};
    }
}
