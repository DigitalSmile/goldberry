package dev.goldberry.gpu.raytrace;

/// Where one pixel's primary ray first met the scene.
///
/// @param instanceId  the [Instance#id] of what it hit
/// @param primitiveId the triangle's index in its mesh
/// @param distance    how far along the ray
/// @param u           the triangle's second vertex's barycentric weight
/// @param v           its third vertex's; the first's is `1 - u - v`
/// @param lit         whether the light reaches the point, which is true when
///                    no shadow ray was asked for
public record Hit(int instanceId, int primitiveId, float distance, float u, float v, boolean lit) {

    /// The first vertex's barycentric weight.
    public float w() {
        return 1 - u - v;
    }
}
