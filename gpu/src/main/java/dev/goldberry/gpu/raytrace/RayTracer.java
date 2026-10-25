package dev.goldberry.gpu.raytrace;

import java.util.Objects;
import java.util.Optional;

import dev.goldberry.gpu.GpuException;
import dev.goldberry.gpu.raytrace.vulkan.AccelerationScene;
import dev.goldberry.gpu.raytrace.vulkan.RayDevice;
import dev.goldberry.gpu.raytrace.vulkan.VulkanRayTracer;
import dev.goldberry.natives.vulkan.VulkanException;

/// Traces rays through a scene in hardware, and hands back what each pixel's
/// ray hit and whether the light reaches it.
///
/// SDL's GPU has no ray tracing, so this runs on a Vulkan device of its own
/// beside it: the scene is built there, rays are traced there, and the
/// [Hits] come back through this process's memory. From there they go into
/// SDL's textures and buffers with a [dev.goldberry.gpu.CopyPass] — a
/// [ShadowMask] into an `R8_UNORM` texture, [Lighting]'s planes into an
/// `R8G8B8A8_UNORM` and an `R32_FLOAT` one, [Hits#records()] into a storage
/// buffer — where a renderer's own shaders read them. Every call returns once
/// the GPU is done, so a trace costs its copy back as well as its rays: 24
/// bytes a pixel for [#trace], eight for [#traceLighting], one for
/// [#traceShadows].
///
/// ```java
/// try (var tracer = RayTracer.open(RayTracingMode.AUTO).orElseThrow();
///         var scene = tracer.build(SceneGeometry.of(Instance.of(mesh, Transform.IDENTITY, 7)))) {
///     var shadows = tracer.traceShadows(scene, TraceRequest.of(camera, 640, 360).withShadowsToward(sun));
///     frame.copyPass(pass -> pass.upload(shadowTexture, shadows.pixels()));
/// }
/// ```
///
/// A scene whose instances move is [RayScene#update]d rather than built
/// again: its meshes' structures stay, and only the one over the instances is
/// rebuilt.
///
/// Confined to the thread that opened it, as a [dev.goldberry.gpu.GpuDevice]
/// is: any other throws [WrongThreadException].
public final class RayTracer implements AutoCloseable {

    private final VulkanRayTracer vulkan;
    private final Thread owner;
    private final RayTracing.Hardware device;
    private boolean closed;

    private RayTracer(VulkanRayTracer vulkan) {
        this.vulkan = vulkan;
        this.owner = Thread.currentThread();
        this.device = new RayTracing.Hardware(vulkan.device().name());
    }

    /// Whether rays can be traced in this process, and on what. Looks for a
    /// device once a process; `goldberry.raytracing=off` answers unavailable
    /// without looking.
    public static RayTracing availability() {
        if (RayTracingMode.effective(RayTracingMode.AUTO) == RayTracingMode.OFF) {
            return new RayTracing.Unavailable(RayTracingMode.PROPERTY + " is off");
        }
        return switch (RayDevice.probe()) {
            case RayDevice.Probe.Ready(var ready) -> new RayTracing.Hardware(ready.name());
            case RayDevice.Probe.Refused(var reason) -> new RayTracing.Unavailable(reason);
        };
    }

    /// A tracer for `mode`, as `goldberry.raytracing` overrides it: empty for
    /// `OFF`, and for `AUTO` where there is no ray tracing.
    ///
    /// @throws UnsupportedOperationException when `HARDWARE` is asked and there
    ///                                       is none; the message says why
    /// @throws GpuException                  when the device refuses the
    ///                                       tracer's pipeline
    public static Optional<RayTracer> open(RayTracingMode mode) {
        Objects.requireNonNull(mode, "mode");
        var effective = RayTracingMode.effective(mode);
        if (effective == RayTracingMode.OFF) {
            return Optional.empty();
        }
        return switch (RayDevice.probe()) {
            case RayDevice.Probe.Ready(var ready) -> Optional.of(open(ready));
            case RayDevice.Probe.Refused(var reason) -> {
                if (effective == RayTracingMode.HARDWARE) {
                    throw new UnsupportedOperationException("no hardware ray tracing: " + reason);
                }
                yield Optional.empty();
            }
        };
    }

    private static RayTracer open(RayDevice device) {
        try {
            return new RayTracer(VulkanRayTracer.open(device));
        } catch (VulkanException e) {
            throw new GpuException("the ray-tracing pipeline was refused on " + device.name(), e);
        }
    }

    /// The device it traces on.
    public RayTracing.Hardware device() {
        return device;
    }

    /// Builds `geometry` for tracing: an acceleration structure for each
    /// distinct mesh and one over the instances.
    ///
    /// @throws WrongThreadException when called from another thread than its
    ///                              owner's
    /// @throws GpuException         when the device refuses, out of memory
    ///                              among the reasons
    public RayScene build(SceneGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        requireOpen();
        try {
            return new RayScene(this, vulkan.build(geometry));
        } catch (VulkanException e) {
            throw new GpuException("the scene could not be built on " + device.device(), e);
        }
    }

    /// Traces `request` through `scene`: a ray through each pixel's centre,
    /// and a shadow ray from each hit where the request asks for them.
    ///
    /// @throws IllegalArgumentException when `scene` was built by another
    ///                                  tracer, or the picture has more pixels
    ///                                  than the device starts in one dispatch
    /// @throws IllegalStateException    when `scene` is closed
    /// @throws WrongThreadException     when called from another thread than
    ///                                  its owner's
    /// @throws GpuException             when the device refuses
    public Hits trace(RayScene scene, TraceRequest request) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(request, "request");
        requireOpen();
        var structures = scene.structuresFor(this);
        try {
            return Hits.of(request.width(), request.height(), vulkan.trace(structures, request));
        } catch (VulkanException e) {
            throw new GpuException("rays could not be traced on " + device.device(), e);
        }
    }

    /// Traces `request` through `scene` for its shadows alone: a primary ray
    /// through each pixel's centre and a shadow ray from each hit, with the
    /// mask packed on the GPU, so a byte a pixel comes back rather than a
    /// record.
    ///
    /// @throws IllegalArgumentException when `request` asks for no shadows,
    ///                                  `scene` was built by another tracer,
    ///                                  or the picture has more pixels than
    ///                                  the device starts in one dispatch
    /// @throws IllegalStateException    when `scene` is closed
    /// @throws WrongThreadException     when called from another thread than
    ///                                  its owner's
    /// @throws GpuException             when the device refuses
    public ShadowMask traceShadows(RayScene scene, TraceRequest request) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(request, "request");
        requireOpen();
        if (request.towardLight().isEmpty()) {
            throw new IllegalArgumentException("a shadow mask needs a light: ask withShadowsToward(direction)");
        }
        var structures = scene.structuresFor(this);
        try {
            return ShadowMask.of(request.width(), request.height(), vulkan.traceShadows(structures, request));
        } catch (VulkanException e) {
            throw new GpuException("shadows could not be traced on " + device.device(), e);
        }
    }

    /// Traces `request` through `scene` for its lighting: from each primary
    /// hit a shadow ray, the occlusion rays and a reflection ray, packed on the
    /// GPU into [Lighting]'s two planes, eight bytes a pixel.
    ///
    /// @throws IllegalArgumentException when `scene` was built by another
    ///                                  tracer, or the picture has more pixels
    ///                                  than the device starts in one dispatch
    /// @throws IllegalStateException    when `scene` is closed
    /// @throws WrongThreadException     when called from another thread than
    ///                                  its owner's
    /// @throws GpuException             when the device refuses
    public Lighting traceLighting(RayScene scene, LightingRequest request) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(request, "request");
        requireOpen();
        var structures = scene.structuresFor(this);
        try {
            var rays = request.rays();
            return Lighting.of(rays.width(), rays.height(), vulkan.traceLighting(structures, request));
        } catch (VulkanException e) {
            throw new GpuException("the lighting could not be traced on " + device.device(), e);
        }
    }

    /// Rebuilds `scene`'s structure over its instances for `geometry`, which
    /// places none but the meshes it was built with.
    void update(RayScene scene, AccelerationScene structures, SceneGeometry geometry) {
        requireOpen();
        try {
            vulkan.update(structures, geometry);
        } catch (VulkanException e) {
            throw new GpuException(scene + " could not be updated on " + device.device(), e);
        }
    }

    /// Whether it has been closed.
    public boolean isClosed() {
        return closed;
    }

    void requireThread() {
        if (Thread.currentThread() != owner) {
            throw new WrongThreadException(this + " belongs to thread " + owner.getName() + ", and was used from "
                    + Thread.currentThread().getName());
        }
    }

    private void requireOpen() {
        requireThread();
        if (closed) {
            throw new IllegalStateException(this + " is closed");
        }
    }

    /// Frees its pipeline and buffers. Scenes it built are closed with it in
    /// effect: they can no longer be traced, and should be closed first.
    /// Idempotent.
    ///
    /// @throws WrongThreadException when called from another thread than its
    ///                              owner's
    @Override
    public void close() {
        requireThread();
        if (closed) {
            return;
        }
        closed = true;
        vulkan.close();
    }

    @Override
    public String toString() {
        return "RayTracer[" + device.device() + (closed ? ", closed]" : "]");
    }
}
