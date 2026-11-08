package dev.goldberry.gpu.raytrace.tier;

import java.nio.ByteBuffer;

import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.raytrace.LightingRequest;
import dev.goldberry.gpu.raytrace.LightingTargets;
import dev.goldberry.gpu.raytrace.RayTracing;
import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.TraceRequest;

/// One way of tracing rays behind [dev.goldberry.gpu.raytrace.RayTracer]: in
/// hardware on a Vulkan device of the toolkit's own, or in software on the
/// window's device. Both write what they trace in the layouts of
/// [dev.goldberry.gpu.raytrace.Hits], [dev.goldberry.gpu.raytrace.ShadowMask]
/// and [dev.goldberry.gpu.raytrace.Lighting], so one test holds both.
///
/// Every call but the frame's returns once the GPU is done. The ray tracer
/// checks the thread, that it is open, and that a scene is its own, before it
/// calls; a tier's failures are its own exceptions, which the ray tracer
/// words.
public interface Tier extends AutoCloseable {

    /// Which tier it is, and on what device.
    RayTracing.Available answer();

    /// Builds `geometry`'s structures.
    TracedScene build(SceneGeometry geometry);

    /// Places `geometry`'s instances in `scene`, whose meshes they are.
    ///
    /// @throws IllegalArgumentException when `geometry` places a mesh `scene`
    ///                                  was not built with
    void update(TracedScene scene, SceneGeometry geometry);

    /// A [dev.goldberry.gpu.raytrace.Hits] record a pixel.
    ///
    /// @throws IllegalArgumentException when the picture is larger than the
    ///                                  device traces in one dispatch
    ByteBuffer trace(TracedScene scene, TraceRequest request);

    /// A shadow mask's byte a pixel, rows with no padding, exactly the
    /// picture's size.
    ///
    /// @throws IllegalArgumentException as [#trace]
    ByteBuffer traceShadows(TracedScene scene, TraceRequest request);

    /// The two planes of a [dev.goldberry.gpu.raytrace.Lighting].
    ///
    /// @throws IllegalArgumentException as [#trace]
    ByteBuffer traceLighting(TracedScene scene, LightingRequest request);

    /// Records the lighting into `into`'s textures, in `frame`: what the GPU
    /// runs when the frame is submitted.
    ///
    /// @throws IllegalArgumentException as [#trace]
    void traceLighting(GpuFrame frame, TracedScene scene, LightingRequest request, LightingTargets into);

    /// Frees what it made. Idempotent.
    @Override
    void close();
}
