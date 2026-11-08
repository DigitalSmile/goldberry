package dev.goldberry.gpu.raytrace.tier;

import dev.goldberry.gpu.raytrace.SceneGeometry;

/// A scene one [Tier] built for tracing: its structures on that tier's device,
/// which [dev.goldberry.gpu.raytrace.RayScene] wraps.
public interface TracedScene extends AutoCloseable {

    /// What it was built from, or last updated to.
    SceneGeometry geometry();

    /// How much GPU memory its structures hold, in bytes.
    long structureBytes();

    /// Whether it has been closed.
    boolean isClosed();

    /// Frees its structures. Idempotent.
    @Override
    void close();
}
