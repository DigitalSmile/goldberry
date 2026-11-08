package dev.goldberry.gpu.raytrace.vulkan;

import java.nio.ByteBuffer;
import java.util.Objects;

import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.raytrace.LightingRequest;
import dev.goldberry.gpu.raytrace.LightingTargets;
import dev.goldberry.gpu.raytrace.RayTracing;
import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.TraceRequest;
import dev.goldberry.gpu.raytrace.tier.Tier;
import dev.goldberry.gpu.raytrace.tier.TracedScene;

/// The hardware tier: a [VulkanRayTracer] on the [RayDevice], its scenes
/// [AccelerationScene]s. A frame's lighting is traced at once, on this device,
/// and its planes uploaded into the frame's textures: SDL's device and this one
/// share no memory.
///
/// Its failures are [dev.goldberry.natives.vulkan.VulkanException]s.
public final class HardwareTier implements Tier {

    private final RayDevice device;
    private final VulkanRayTracer tracer;
    private final RayTracing.Hardware answer;

    private HardwareTier(RayDevice device, VulkanRayTracer tracer) {
        this.device = device;
        this.tracer = tracer;
        this.answer = new RayTracing.Hardware(device.name());
    }

    /// A tier on `device`.
    ///
    /// @throws dev.goldberry.natives.vulkan.VulkanException when the driver
    ///                                                      refuses the
    ///                                                      pipeline
    public static HardwareTier open(RayDevice device) {
        Objects.requireNonNull(device, "device");
        return new HardwareTier(device, VulkanRayTracer.open(device));
    }

    /// The device it traces on.
    public RayDevice device() {
        return device;
    }

    @Override
    public RayTracing.Hardware answer() {
        return answer;
    }

    @Override
    public TracedScene build(SceneGeometry geometry) {
        return tracer.build(geometry);
    }

    @Override
    public void update(TracedScene scene, SceneGeometry geometry) {
        tracer.update(structures(scene), geometry);
    }

    @Override
    public ByteBuffer trace(TracedScene scene, TraceRequest request) {
        return tracer.trace(structures(scene), request);
    }

    @Override
    public ByteBuffer traceShadows(TracedScene scene, TraceRequest request) {
        return tracer.traceShadows(structures(scene), request);
    }

    @Override
    public ByteBuffer traceLighting(TracedScene scene, LightingRequest request) {
        return tracer.traceLighting(structures(scene), request);
    }

    @Override
    public void traceLighting(GpuFrame frame, TracedScene scene, LightingRequest request, LightingTargets into) {
        var planes = traceLighting(scene, request);
        var pixels = request.rays().pixels();
        var shading = planes.slice(0, Math.toIntExact(pixels * 4));
        var ids = planes.slice(Math.toIntExact(pixels * 4), Math.toIntExact(pixels * Float.BYTES));
        frame.copyPass(pass -> {
            pass.upload(into.shading(), shading);
            pass.upload(into.reflectedIds(), ids);
        });
    }

    private static AccelerationScene structures(TracedScene scene) {
        if (scene instanceof AccelerationScene structures) {
            return structures;
        }
        throw new IllegalArgumentException(scene + " was not built in hardware");
    }

    @Override
    public void close() {
        tracer.close();
    }
}
