package dev.goldberry.gpu.raytrace;

import java.util.Objects;

import dev.goldberry.gpu.raytrace.vulkan.AccelerationScene;

/// A [SceneGeometry] built for tracing by a [RayTracer]: its acceleration
/// structures, on the tracer's device. Traced by that tracer alone, as often
/// as wanted.
///
/// Each distinct mesh has a structure of its own, built once and compacted,
/// and one more structure is over the instances. When instances move, come or
/// go, [#update] rebuilds that one alone, which is what a frame of a moving
/// scene costs; a new mesh, or one whose triangles changed, is a new scene.
///
/// Close it when done: its structures are GPU memory.
public final class RayScene implements AutoCloseable {

    private final RayTracer owner;
    private final AccelerationScene structures;

    RayScene(RayTracer owner, AccelerationScene structures) {
        this.owner = owner;
        this.structures = structures;
    }

    /// What it was built from, or last updated to.
    public SceneGeometry geometry() {
        return structures.geometry();
    }

    /// Places `geometry`'s instances instead of the ones it has: their
    /// transforms, ids and masks, and how many there are. The meshes' own
    /// structures stay as they were built, so every mesh `geometry` places
    /// must be one this scene was built with, the same object.
    ///
    /// @throws IllegalArgumentException when `geometry` places a mesh the
    ///                                  scene was not built with
    /// @throws IllegalStateException    when it is closed
    /// @throws WrongThreadException     when called from a thread other than
    ///                                  its tracer's
    /// @throws dev.goldberry.gpu.GpuException when the device refuses
    public void update(SceneGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        owner.update(this, structuresFor(owner), geometry);
    }

    /// How much GPU memory its structures hold, in bytes: the meshes'
    /// compacted, and the one over the instances.
    public long structureBytes() {
        return structures.structureBytes();
    }

    /// Whether it has been closed, or its tracer has.
    public boolean isClosed() {
        return structures.isClosed() || owner.isClosed();
    }

    /// The structures, for `user`, which must be the tracer that built them.
    AccelerationScene structuresFor(RayTracer user) {
        if (user != owner) {
            throw new IllegalArgumentException(this + " was built by another ray tracer");
        }
        if (isClosed()) {
            throw new IllegalStateException(this + " is closed");
        }
        return structures;
    }

    /// Frees its structures. Idempotent.
    ///
    /// @throws WrongThreadException when called from a thread other than its
    ///                              tracer's
    @Override
    public void close() {
        owner.requireThread();
        structures.close();
    }

    @Override
    public String toString() {
        return "RayScene[" + geometry().instances().size() + " instances, "
                + geometry().triangleCount() + " triangles" + (isClosed() ? ", closed]" : "]");
    }
}
