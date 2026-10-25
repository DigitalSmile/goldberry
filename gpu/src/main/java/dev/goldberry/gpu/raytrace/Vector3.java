package dev.goldberry.gpu.raytrace;

/// A point or a direction in a scene, in single precision as the GPU holds it.
///
/// @param x across
/// @param y up
/// @param z toward the viewer, in a right-handed scene
public record Vector3(float x, float y, float z) {

    /// The origin.
    public static final Vector3 ZERO = new Vector3(0, 0, 0);

    /// Up: `+y`.
    public static final Vector3 UP = new Vector3(0, 1, 0);

    /// @throws IllegalArgumentException when a component is not finite
    public Vector3 {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("a vector's components are finite: (" + x + ", " + y + ", " + z + ")");
        }
    }

    /// This plus `other`.
    public Vector3 plus(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    /// This minus `other`.
    public Vector3 minus(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    /// This scaled by `factor`.
    public Vector3 times(float factor) {
        return new Vector3(x * factor, y * factor, z * factor);
    }

    /// The dot product.
    public float dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /// The cross product, `this × other`.
    public Vector3 cross(Vector3 other) {
        return new Vector3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    /// The length.
    public float length() {
        return (float) Math.sqrt(dot(this));
    }

    /// This at unit length.
    ///
    /// @throws IllegalArgumentException when this has no length to scale
    public Vector3 normalized() {
        var length = length();
        if (length == 0) {
            throw new IllegalArgumentException("the zero vector has no direction");
        }
        return times(1 / length);
    }
}
