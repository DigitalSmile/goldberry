package dev.goldberry.gpu.raytrace;

/// Where an [Instance] puts its mesh: an affine transform, the top three rows
/// of a 4×4 matrix, which is what a ray tracer's instance holds. A point `p`
/// becomes `(m00 p.x + m01 p.y + m02 p.z + m03, …)`.
///
/// Transforms compose with [#then]: `a.then(b)` applies `a` first.
///
/// @param m00 row 0, column 0
/// @param m01 row 0, column 1
/// @param m02 row 0, column 2
/// @param m03 row 0, column 3: the translation along x
/// @param m10 row 1, column 0
/// @param m11 row 1, column 1
/// @param m12 row 1, column 2
/// @param m13 row 1, column 3: the translation along y
/// @param m20 row 2, column 0
/// @param m21 row 2, column 1
/// @param m22 row 2, column 2
/// @param m23 row 2, column 3: the translation along z
public record Transform(
        float m00,
        float m01,
        float m02,
        float m03,
        float m10,
        float m11,
        float m12,
        float m13,
        float m20,
        float m21,
        float m22,
        float m23) {

    /// Leaves every point where it is.
    public static final Transform IDENTITY = new Transform(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0);

    /// @throws IllegalArgumentException when an element is not finite
    public Transform {
        for (var element : new float[] {m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23}) {
            if (!Float.isFinite(element)) {
                throw new IllegalArgumentException("a transform's elements are finite, and one is " + element);
            }
        }
    }

    /// The transform of `rowMajor`'s twelve elements, row by row.
    ///
    /// @throws IllegalArgumentException when there are not twelve, or one is
    ///                                  not finite
    public static Transform ofRowMajor(float... rowMajor) {
        if (rowMajor.length != 12) {
            throw new IllegalArgumentException("a 3×4 transform has 12 elements, not " + rowMajor.length);
        }
        var m = rowMajor;
        return new Transform(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], m[10], m[11]);
    }

    /// A move by `offset`.
    public static Transform translation(Vector3 offset) {
        return new Transform(1, 0, 0, offset.x(), 0, 1, 0, offset.y(), 0, 0, 1, offset.z());
    }

    /// A scale by `factor` about the origin on every axis.
    public static Transform scaling(float factor) {
        return scaling(factor, factor, factor);
    }

    /// A scale about the origin by `x`, `y` and `z` along each axis.
    public static Transform scaling(float x, float y, float z) {
        return new Transform(x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0);
    }

    /// A turn of `radians` about the x axis, counter-clockwise looking down it.
    public static Transform rotationX(double radians) {
        var c = (float) Math.cos(radians);
        var s = (float) Math.sin(radians);
        return new Transform(1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0);
    }

    /// A turn of `radians` about the y axis, counter-clockwise looking down it.
    public static Transform rotationY(double radians) {
        var c = (float) Math.cos(radians);
        var s = (float) Math.sin(radians);
        return new Transform(c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0);
    }

    /// A turn of `radians` about the z axis, counter-clockwise looking down it.
    public static Transform rotationZ(double radians) {
        var c = (float) Math.cos(radians);
        var s = (float) Math.sin(radians);
        return new Transform(c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0);
    }

    /// This, then `next`: a point moved by this and then by `next`.
    public Transform then(Transform next) {
        var n = next;
        return new Transform(
                n.m00 * m00 + n.m01 * m10 + n.m02 * m20,
                n.m00 * m01 + n.m01 * m11 + n.m02 * m21,
                n.m00 * m02 + n.m01 * m12 + n.m02 * m22,
                n.m00 * m03 + n.m01 * m13 + n.m02 * m23 + n.m03,
                n.m10 * m00 + n.m11 * m10 + n.m12 * m20,
                n.m10 * m01 + n.m11 * m11 + n.m12 * m21,
                n.m10 * m02 + n.m11 * m12 + n.m12 * m22,
                n.m10 * m03 + n.m11 * m13 + n.m12 * m23 + n.m13,
                n.m20 * m00 + n.m21 * m10 + n.m22 * m20,
                n.m20 * m01 + n.m21 * m11 + n.m22 * m21,
                n.m20 * m02 + n.m21 * m12 + n.m22 * m22,
                n.m20 * m03 + n.m21 * m13 + n.m22 * m23 + n.m23);
    }

    /// Where `point` goes.
    public Vector3 apply(Vector3 point) {
        return new Vector3(
                m00 * point.x() + m01 * point.y() + m02 * point.z() + m03,
                m10 * point.x() + m11 * point.y() + m12 * point.z() + m13,
                m20 * point.x() + m21 * point.y() + m22 * point.z() + m23);
    }

    /// The twelve elements, row by row: the layout of a ray tracer's instance.
    public float[] toRowMajor() {
        return new float[] {m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23};
    }
}
