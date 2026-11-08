package dev.goldberry.gpu.raytrace;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuException;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.raytrace.compute.SoftwareTier;
import dev.goldberry.gpu.raytrace.tier.Tier;
import dev.goldberry.gpu.raytrace.tier.TracedScene;
import dev.goldberry.gpu.raytrace.vulkan.HardwareTier;
import dev.goldberry.gpu.raytrace.vulkan.RayDevice;
import dev.goldberry.natives.vulkan.VulkanException;

/// Traces rays through a scene, and hands back what each pixel's ray hit and
/// whether the light reaches it, in hardware or in software: one API either
/// way, and the same answers.
///
/// **In hardware**, SDL's GPU having no ray tracing, it runs on a Vulkan device
/// of its own beside SDL's: the scene is built there, rays are traced there,
/// and what they found comes back through this process's memory. **In
/// software** it runs on the window's own device, any GPU SDL drives: the scene
/// is a hierarchy of boxes built on the CPU, and compute shaders walk it.
/// [#open(GpuDevice, RayTracingMode)] picks; [#device()] says which it got.
///
/// What a trace finds goes into SDL's textures and buffers with a
/// [dev.goldberry.gpu.CopyPass] — a [ShadowMask] into an `R8_UNORM` texture,
/// [Lighting]'s planes into an `R8G8B8A8_UNORM` and an `R32_FLOAT` one,
/// [Hits#records()] into a storage buffer — where a renderer's own shaders read
/// them. Every such call returns once the GPU is done, so a trace costs its
/// copy back as well as its rays: 24 bytes a pixel for [#trace], eight for
/// [#traceLighting], one for [#traceShadows]. A frame's lighting can skip the
/// trip instead: [#traceLighting(GpuFrame, RayScene, LightingRequest, LightingTargets)]
/// records it into two textures, which the software tier writes where they
/// are.
///
/// ```java
/// try (var tracer = RayTracer.open(device, RayTracingMode.AUTO).orElseThrow();
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
/// Confined to the thread that opened it, as a [GpuDevice] is: any other
/// throws [WrongThreadException].
public final class RayTracer implements AutoCloseable {

    private final Tier tier;
    private final Thread owner;
    private final RayTracing.Available device;
    private boolean closed;

    private RayTracer(Tier tier) {
        this.tier = tier;
        this.owner = Thread.currentThread();
        this.device = tier.answer();
    }

    /// Whether rays can be traced in hardware in this process, and on what.
    /// Looks for a device once a process; `goldberry.raytracing=off` answers
    /// unavailable without looking. The software tier needs the window's
    /// device: [#availability(GpuDevice, RayTracingMode)] answers for both.
    public static RayTracing availability() {
        if (RayTracingMode.effective(RayTracingMode.AUTO) == RayTracingMode.OFF) {
            return new RayTracing.Unavailable(RayTracingMode.PROPERTY + " is off");
        }
        return switch (RayDevice.probe()) {
            case RayDevice.Probe.Ready(var ready) -> new RayTracing.Hardware(ready.name());
            case RayDevice.Probe.Refused(var reason) -> new RayTracing.Unavailable(reason);
        };
    }

    /// What [#open(GpuDevice, RayTracingMode)] would trace with on `device` for
    /// `mode`, as `goldberry.raytracing` overrides it, without opening
    /// anything: [RayTracing.Unavailable] for `OFF`, and for `HARDWARE` where
    /// there is none.
    public static RayTracing availability(GpuDevice device, RayTracingMode mode) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(mode, "mode");
        return switch (RayTracingMode.effective(mode)) {
            case OFF ->
                new RayTracing.Unavailable(
                        mode == RayTracingMode.OFF ? "ray tracing is off" : RayTracingMode.PROPERTY + " is off");
            case HARDWARE -> availability();
            case SOFTWARE -> SoftwareTier.answerFor(device);
            case AUTO ->
                switch (RayDevice.probe()) {
                    case RayDevice.Probe.Ready(var ready) when !ready.isCpu() -> new RayTracing.Hardware(ready.name());
                    case RayDevice.Probe.Ready _, RayDevice.Probe.Refused _ -> SoftwareTier.answerFor(device);
                };
        };
    }

    /// A tracer in hardware for `mode`, as `goldberry.raytracing` overrides it:
    /// empty for `OFF`, and for `AUTO` where there is no hardware ray tracing.
    /// The software tier needs the window's device, which
    /// [#open(GpuDevice, RayTracingMode)] takes.
    ///
    /// @throws UnsupportedOperationException when `HARDWARE` is asked and there
    ///                                       is none; the message says why
    /// @throws IllegalArgumentException      when `SOFTWARE` is asked: it
    ///                                       traces on a device this is not
    ///                                       given
    /// @throws GpuException                  when the device refuses the
    ///                                       tracer's pipeline
    public static Optional<RayTracer> open(RayTracingMode mode) {
        Objects.requireNonNull(mode, "mode");
        return switch (RayTracingMode.effective(mode)) {
            case OFF -> Optional.empty();
            case SOFTWARE ->
                throw new IllegalArgumentException(
                        "rays are traced in software on the window's device: open(device, mode)");
            case HARDWARE -> Optional.of(hardware(true).orElseThrow());
            case AUTO -> hardware(false);
        };
    }

    /// A tracer for `mode`, as `goldberry.raytracing` overrides it, on
    /// `device` where it traces in software: empty for `OFF`. `AUTO` traces in
    /// hardware where its device is a GPU, and in software otherwise — a
    /// "hardware" tracer on the CPU, as lavapipe is, is slower than compute
    /// shaders on any GPU.
    ///
    /// @throws UnsupportedOperationException when `HARDWARE` is asked and there
    ///                                       is none; the message says why
    /// @throws WrongThreadException          when called from another thread
    ///                                       than `device`'s
    /// @throws GpuException                  when a device refuses the
    ///                                       tracer's pipelines
    public static Optional<RayTracer> open(GpuDevice device, RayTracingMode mode) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(mode, "mode");
        return switch (RayTracingMode.effective(mode)) {
            case OFF -> Optional.empty();
            case HARDWARE -> Optional.of(hardware(true).orElseThrow());
            case SOFTWARE -> Optional.of(software(device));
            case AUTO ->
                switch (RayDevice.probe()) {
                    case RayDevice.Probe.Ready(var ready) when !ready.isCpu() -> Optional.of(open(ready));
                    case RayDevice.Probe.Ready _, RayDevice.Probe.Refused _ -> Optional.of(software(device));
                };
        };
    }

    /// The hardware tracer, or empty where there is none, or a refusal that
    /// says why when `required`.
    private static Optional<RayTracer> hardware(boolean required) {
        return switch (RayDevice.probe()) {
            case RayDevice.Probe.Ready(var ready) -> Optional.of(open(ready));
            case RayDevice.Probe.Refused(var reason) -> {
                if (required) {
                    throw new UnsupportedOperationException("no hardware ray tracing: " + reason);
                }
                yield Optional.empty();
            }
        };
    }

    private static RayTracer open(RayDevice device) {
        try {
            return new RayTracer(HardwareTier.open(device));
        } catch (VulkanException e) {
            throw new GpuException("the ray-tracing pipeline was refused on " + device.name(), e);
        }
    }

    private static RayTracer software(GpuDevice device) {
        return new RayTracer(SoftwareTier.open(device));
    }

    /// Which tier it traces with, and on what device.
    public RayTracing.Available device() {
        return device;
    }

    /// Builds `geometry` for tracing: a structure for each distinct mesh and
    /// one over the instances.
    ///
    /// @throws WrongThreadException when called from another thread than its
    ///                              owner's
    /// @throws GpuException         when the device refuses, out of memory
    ///                              among the reasons
    public RayScene build(SceneGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        requireOpen();
        return new RayScene(this, call("the scene could not be built", () -> tier.build(geometry)));
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
        ByteBuffer records = call("rays could not be traced", () -> tier.trace(structures, request));
        return Hits.of(request.width(), request.height(), records);
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
        ByteBuffer mask = call("shadows could not be traced", () -> tier.traceShadows(structures, request));
        return ShadowMask.of(request.width(), request.height(), mask);
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
        var rays = request.rays();
        ByteBuffer planes = call("the lighting could not be traced", () -> tier.traceLighting(structures, request));
        return Lighting.of(rays.width(), rays.height(), planes);
    }

    /// Records `request`'s lighting through `scene` into `into`, in `frame`:
    /// what [#traceLighting(RayScene, LightingRequest)] returns, kept on the
    /// GPU for the frame's own passes to sample. The software tier dispatches
    /// into the textures where they are; the hardware tier traces now, on its
    /// own device, and records the planes' upload.
    ///
    /// @throws IllegalArgumentException when `scene` was built by another
    ///                                  tracer, the textures are not the
    ///                                  picture's size or `frame`'s device's,
    ///                                  or the picture has more pixels than the
    ///                                  device starts in one dispatch
    /// @throws IllegalStateException    when `scene` is closed, or `frame` is
    ///                                  finished or inside a pass
    /// @throws WrongThreadException     when called from another thread than
    ///                                  its owner's
    /// @throws GpuException             when a device refuses
    public void traceLighting(GpuFrame frame, RayScene scene, LightingRequest request, LightingTargets into) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(into, "into");
        requireOpen();
        var rays = request.rays();
        if (into.width() != rays.width() || into.height() != rays.height()) {
            throw new IllegalArgumentException("the targets are " + into.width() + "×" + into.height()
                    + ", and the rays " + rays.width() + "×" + rays.height());
        }
        if (into.shading().device() != frame.device()) {
            throw new IllegalArgumentException(
                    into + " belongs to " + into.shading().device() + ", and the frame to " + frame.device());
        }
        var structures = scene.structuresFor(this);
        call("the lighting could not be traced", () -> {
            tier.traceLighting(frame, structures, request, into);
            return null;
        });
    }

    /// Rebuilds `scene`'s structure over its instances for `geometry`, which
    /// places none but the meshes it was built with.
    void update(RayScene scene, TracedScene structures, SceneGeometry geometry) {
        requireOpen();
        call(scene + " could not be updated", () -> {
            tier.update(structures, geometry);
            return null;
        });
    }

    /// `body`'s answer, a Vulkan failure worded as `what` on this tracer's
    /// device.
    private <T> T call(String what, Supplier<T> body) {
        try {
            return body.get();
        } catch (VulkanException e) {
            throw new GpuException(what + " on " + device.device(), e);
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

    /// Frees its pipelines and buffers. Scenes it built are closed with it in
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
        tier.close();
    }

    @Override
    public String toString() {
        var tierName = switch (device) {
            case RayTracing.Hardware _ -> "hardware";
            case RayTracing.Software _ -> "software";
        };
        return "RayTracer[" + tierName + ", " + device.device() + (closed ? ", closed]" : "]");
    }
}
