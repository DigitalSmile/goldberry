package dev.goldberry.example.gpu;

/// The few 4x4 matrices the GPU screen's scenes need, row-major, which is how
/// their vertex shaders read the floats: a point is a column, multiplied on the
/// right.
///
/// An application's own, as a renderer's maths is: `:gpu` hands a renderer a
/// device and a frame, and leaves the scene to it.
public final class Matrices {

    private Matrices() {}

    /// `a` times `b`: `b` applied first.
    public static float[] multiply(float[] a, float[] b) {
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
    public static float[] rotateY(double angle) {
        var c = (float) Math.cos(angle);
        var s = (float) Math.sin(angle);
        return new float[] {c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
    }

    /// A turn of `angle` radians about x.
    public static float[] rotateX(double angle) {
        var c = (float) Math.cos(angle);
        var s = (float) Math.sin(angle);
        return new float[] {1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
    }

    /// A move by `(x, y, z)`.
    public static float[] translate(float x, float y, float z) {
        return new float[] {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
    }

    /// A right-handed perspective onto SDL's clip space: y up, and depth from 0
    /// at `near` to 1 at `far`.
    public static float[] perspective(double fovY, double aspect, double near, double far) {
        var f = (float) (1 / Math.tan(fovY / 2));
        var depth = (float) (far / (near - far));
        var offset = (float) (near * far / (near - far));
        return new float[] {f / (float) aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, depth, offset, 0, 0, -1, 0};
    }

    /// A uniform scale by `factor`.
    public static float[] scale(float factor) {
        return new float[] {factor, 0, 0, 0, 0, factor, 0, 0, 0, 0, factor, 0, 0, 0, 0, 1};
    }

    /// The view from `eye` toward `target`, `y` up, right-handed: the camera
    /// looks down its own -z.
    public static float[] lookAt(float[] eye, float[] target) {
        var f = normalized(minus(target, eye));
        var r = normalized(cross(f, new float[] {0, 1, 0}));
        var u = cross(r, f);
        return new float[] {
            r[0], r[1], r[2], -dot(r, eye), u[0], u[1], u[2], -dot(u, eye), -f[0], -f[1], -f[2], dot(f, eye), 0, 0, 0, 1
        };
    }

    private static float[] minus(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] normalized(float[] v) {
        var length = (float) Math.sqrt(dot(v, v));
        return new float[] {v[0] / length, v[1] / length, v[2] / length};
    }
}
