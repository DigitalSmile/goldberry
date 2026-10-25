package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.raytrace.Hits;
import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.TraceRequest;
import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;

/// Rays traced in hardware on the [RayDevice]: a pipeline, one command
/// buffer, a device-local buffer the shaders write the hits into (records, or
/// a shadow mask packed four pixels a word), and a cached host buffer they
/// are copied to in one transfer and read back from, both grown to the
/// largest picture asked for. The shaders' scattered writes stay in the GPU's
/// own memory; what crosses the bus is one bulk copy.
///
/// Every build and every trace is one submission, waited for, so what it
/// made is ready, or read, when the call returns. Not thread-safe: its owner
/// confines it.
public final class VulkanRayTracer implements AutoCloseable {

    private final RayDevice device;
    private final RayPipeline pipeline;
    private final Commands commands;
    private @Nullable DeviceBuffer hits;
    private @Nullable DeviceBuffer readback;
    private boolean closed;

    private VulkanRayTracer(RayDevice device, RayPipeline pipeline, Commands commands) {
        this.device = device;
        this.pipeline = pipeline;
        this.commands = commands;
    }

    /// A tracer on `device`.
    ///
    /// @throws dev.goldberry.natives.vulkan.VulkanException when the driver
    ///                                                      refuses the
    ///                                                      pipeline
    public static VulkanRayTracer open(RayDevice device) {
        var pipeline = RayPipeline.create(device);
        try {
            return new VulkanRayTracer(device, pipeline, new Commands(device));
        } catch (RuntimeException e) {
            pipeline.close();
            throw e;
        }
    }

    /// The device it traces on.
    public RayDevice device() {
        return device;
    }

    /// Builds `geometry`'s acceleration structures.
    ///
    /// @throws dev.goldberry.natives.vulkan.VulkanException when the driver
    ///                                                      refuses
    public AccelerationScene build(SceneGeometry geometry) {
        requireOpen();
        return AccelerationScene.build(device, commands, geometry);
    }

    /// Rebuilds `scene`'s top level for `geometry`'s instances.
    ///
    /// @throws IllegalArgumentException                     when `geometry`
    ///                                                      places a mesh
    ///                                                      `scene` was not
    ///                                                      built with
    /// @throws dev.goldberry.natives.vulkan.VulkanException when the driver
    ///                                                      refuses
    public void update(AccelerationScene scene, SceneGeometry geometry) {
        requireOpen();
        scene.update(commands, geometry);
    }

    /// Traces `request` through `scene`, and copies the hits out: a new
    /// little-endian buffer of [Hits#RECORD_BYTES] a pixel.
    ///
    /// @throws IllegalArgumentException                     when the picture
    ///                                                      has more pixels
    ///                                                      than the device
    ///                                                      starts rays in one
    ///                                                      dispatch
    /// @throws dev.goldberry.natives.vulkan.VulkanException when the driver
    ///                                                      refuses
    public ByteBuffer trace(AccelerationScene scene, TraceRequest request) {
        var bytes = dispatch(scene, request, TraceOutput.RECORDS);
        var copy = ByteBuffer.allocate(Math.toIntExact(bytes)).order(ByteOrder.LITTLE_ENDIAN);
        MemorySegment.ofBuffer(copy).copyFrom(readback().mapped().asSlice(0, bytes));
        return copy;
    }

    /// Traces `request` through `scene` for its shadows alone, packed on the
    /// GPU: a new buffer of a byte a pixel, rows from the top with no padding,
    /// `0xFF` where the light reaches the pixel or its ray hit nothing and 0
    /// in shadow.
    ///
    /// @throws IllegalArgumentException                     as [#trace] does
    /// @throws dev.goldberry.natives.vulkan.VulkanException when the driver
    ///                                                      refuses
    public ByteBuffer traceShadows(AccelerationScene scene, TraceRequest request) {
        dispatch(scene, request, TraceOutput.SHADOW_MASK);
        var mask = ByteBuffer.allocate(request.width() * request.height());
        var into = MemorySegment.ofBuffer(mask);
        into.copyFrom(readback().mapped().asSlice(0, into.byteSize()));
        return mask;
    }

    /// Traces `request` through `scene`, writing `output` into the hits buffer
    /// and copying it to the readback buffer, and returns how many bytes it
    /// wrote.
    private long dispatch(AccelerationScene scene, TraceRequest request, TraceOutput output) {
        requireOpen();
        var pixels = request.pixels();
        if (pixels > device.limits().maxDispatch()) {
            throw new IllegalArgumentException(
                    request.width() + "×" + request.height() + " is " + pixels + " rays, and " + device.name()
                            + " starts at most " + device.limits().maxDispatch());
        }
        var bytes = output.bytes(request.width(), request.height());
        var written = hits(bytes);
        var host = readback(bytes);
        pipeline.bind(scene.topLevel(), written);
        try (var arena = Arena.ofConfined()) {
            var constants = arena.allocate(RayConstants.SIZE, Integer.BYTES);
            RayConstants.write(constants, request, output);
            commands.run(buffer -> {
                if (output.zeroed()) {
                    device.functions().cmdFillBuffer().call(buffer, written.handle(), 0, bytes, 0);
                    Barriers.filledToTrace(device, buffer);
                }
                pipeline.record(buffer, constants, request.width(), request.height());
                Barriers.tracedToCopy(device, buffer);
                copy(arena, buffer, written, host, bytes);
                Barriers.copiedToHost(device, buffer);
            });
        }
        return bytes;
    }

    /// The device-local buffer the shaders write, at least `bytes` long; a
    /// fill zeroes it for the shadow mask.
    private DeviceBuffer hits(long bytes) {
        var grown = grown(
                hits,
                bytes,
                VkConstants.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                        | VkConstants.VK_BUFFER_USAGE_TRANSFER_SRC_BIT
                        | VkConstants.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                DeviceBuffer.Memory.DEVICE_LOCAL);
        hits = grown;
        return grown;
    }

    /// The host buffer the last trace was copied to.
    private DeviceBuffer readback() {
        if (readback == null) {
            throw new IllegalStateException("nothing has been traced");
        }
        return readback;
    }

    /// The host buffer the hits are copied to, at least `bytes` long.
    private DeviceBuffer readback(long bytes) {
        var grown =
                grown(readback, bytes, VkConstants.VK_BUFFER_USAGE_TRANSFER_DST_BIT, DeviceBuffer.Memory.HOST_READBACK);
        readback = grown;
        return grown;
    }

    /// `current` when it holds `bytes`, else a new buffer at least twice its
    /// size, `current` freed.
    private DeviceBuffer grown(@Nullable DeviceBuffer current, long bytes, int usage, DeviceBuffer.Memory memory) {
        if (current != null && current.size() >= bytes) {
            return current;
        }
        if (current != null) {
            current.close();
        }
        return DeviceBuffer.create(device, Math.max(bytes, current == null ? 0 : current.size() * 2), usage, memory);
    }

    /// Records the copy of `bytes` from `from` to `to`.
    private void copy(Arena arena, MemorySegment commands, DeviceBuffer from, DeviceBuffer to, long bytes) {
        var region = Structs.allocate(arena, VkStructs.VkBufferCopy.LAYOUT);
        region.set(JAVA_LONG, VkStructs.VkBufferCopy.size, bytes);
        device.functions().cmdCopyBuffer().call(commands, from.handle(), to.handle(), 1, region);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this ray tracer is closed");
        }
    }

    /// Frees the pipeline, the commands and the hits buffer; the device stays,
    /// for the process. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (var buffer : new @Nullable DeviceBuffer[] {hits, readback}) {
            if (buffer != null) {
                buffer.close();
            }
        }
        commands.close();
        pipeline.close();
    }
}
