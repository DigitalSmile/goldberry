package dev.goldberry.gpu.raytrace;

import java.util.Objects;

/// A mesh placed in a scene: what a ray hits, and what a [Hit] names.
///
/// @param mesh      the triangles, shared with every other instance of it
/// @param transform where they go
/// @param id        what a hit on it reports, from 0 to [#MAX_ID]: an
///                  application's own number for the object
/// @param mask      which rays see it: a ray tests the instances whose mask
///                  shares a bit with its own, and the toolkit's rays use all
///                  eight. From 1 to 255
public record Instance(TriangleMesh mesh, Transform transform, int id, int mask) {

    /// The largest id: an instance keeps 24 bits of it.
    public static final int MAX_ID = 0xFF_FFFF;

    /// Every ray sees an instance with this mask.
    public static final int VISIBLE = 0xFF;

    /// @throws IllegalArgumentException when `id` or `mask` is out of range
    public Instance {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(transform, "transform");
        if (id < 0 || id > MAX_ID) {
            throw new IllegalArgumentException("an instance's id is 0 to " + MAX_ID + ", not " + id);
        }
        if (mask < 1 || mask > VISIBLE) {
            throw new IllegalArgumentException("an instance's mask is 1 to 255, not " + mask);
        }
    }

    /// `mesh` at `transform`, seen by every ray, reported as `id`.
    ///
    /// @throws IllegalArgumentException when `id` is out of range
    public static Instance of(TriangleMesh mesh, Transform transform, int id) {
        return new Instance(mesh, transform, id, VISIBLE);
    }

    /// This instance, placed at `transform`: what a [RayScene#update] of a
    /// moving scene is made of.
    public Instance withTransform(Transform transform) {
        return new Instance(mesh, transform, id, mask);
    }

    /// This instance, reported as `id`.
    ///
    /// @throws IllegalArgumentException when `id` is out of range
    public Instance withId(int id) {
        return new Instance(mesh, transform, id, mask);
    }
}
