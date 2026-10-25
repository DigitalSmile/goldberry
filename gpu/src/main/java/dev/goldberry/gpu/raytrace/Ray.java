package dev.goldberry.gpu.raytrace;

import java.util.Objects;

/// A half-line: from `origin`, along `direction`, which is of unit length, so
/// a distance along it is a distance in the scene.
///
/// @param origin    where it starts
/// @param direction where it goes, unit length
public record Ray(Vector3 origin, Vector3 direction) {

    /// @throws IllegalArgumentException when `direction` is not of unit length
    public Ray {
        Objects.requireNonNull(origin, "origin");
        if (Math.abs(direction.length() - 1) > 1e-4f) {
            throw new IllegalArgumentException("a ray's direction is of unit length, not " + direction.length());
        }
    }

    /// The point `distance` along it.
    public Vector3 at(float distance) {
        return origin.plus(direction.times(distance));
    }
}
