package dev.goldberry.gpu.raytrace;

import dev.goldberry.gpu.raytrace.vulkan.AccelerationScene;

/// A [SceneGeometry] built for tracing by a [RayTracer]: its acceleration
/// structures, on the tracer's device. Traced by that tracer alone, as often
/// as wanted; built again when the geometry or an instance's transform
/// changes.
///
/// Close it when done: its structures are GPU memory.
public final class RayScene implements AutoCloseable {

    private final RayTracer owner;
    private final AccelerationScene structures;

    RayScene(RayTracer owner, AccelerationScene structures) {
        this.owner = owner;
        this.structures = structures;
    }

    /// What it was built from.
    public SceneGeometry geometry() {
        return structures.geometry();
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
