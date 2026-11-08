package dev.goldberry.gpu.raytrace.compute;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.BufferUsage;
import dev.goldberry.gpu.ComputePipeline;
import dev.goldberry.gpu.GpuBuffer;
import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.raytrace.LightingRequest;
import dev.goldberry.gpu.raytrace.LightingTargets;
import dev.goldberry.gpu.raytrace.RayTracing;
import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.TraceRequest;
import dev.goldberry.gpu.raytrace.tier.RayConstants;
import dev.goldberry.gpu.raytrace.tier.Tier;
import dev.goldberry.gpu.raytrace.tier.TraceOutput;
import dev.goldberry.gpu.raytrace.tier.TracedScene;
import dev.goldberry.gpu.render.BuiltInComputeShader;

/// The software tier: compute shaders on the window's own device that walk a
/// [ComputeScene]'s hierarchy of boxes. `trace.comp` writes the records or the
/// lighting into a buffer of its own, `shadows.comp` the shadow mask, and
/// `lighting.comp` a frame's lighting into its targets.
///
/// A trace that returns bytes is a frame of one compute pass and one buffer
/// readback, submitted and waited for; the output buffer grows to the largest
/// picture asked for. Confined to the device's thread, as the device is.
public final class SoftwareTier implements Tier {

    /// The most workgroups a dispatch takes along an axis: what every SDL_GPU
    /// driver allows.
    static final int MAX_GROUPS = 65_535;

    private final GpuDevice device;
    private final RayTracing.Software answer;
    private final ComputePipeline trace;
    private final ComputePipeline shadows;
    private final ComputePipeline lighting;
    private @Nullable GpuBuffer written;
    private boolean closed;

    private SoftwareTier(GpuDevice device, ComputePipeline trace, ComputePipeline shadows, ComputePipeline lighting) {
        this.device = device;
        this.answer = answerFor(device);
        this.trace = trace;
        this.shadows = shadows;
        this.lighting = lighting;
    }

    /// What the software tier is on `device`.
    public static RayTracing.Software answerFor(GpuDevice device) {
        return new RayTracing.Software(device.adapter().name());
    }

    /// A tier on `device`: its three pipelines made.
    ///
    /// @throws WrongThreadException             when called from another thread
    ///                                          than `device`'s
    /// @throws dev.goldberry.gpu.GpuException when the driver refuses a
    ///                                          pipeline
    public static SoftwareTier open(GpuDevice device) {
        Objects.requireNonNull(device, "device");
        var made = new ArrayList<ComputePipeline>(3);
        try {
            for (var shader : List.of(
                    BuiltInComputeShader.RAY_TRACE,
                    BuiltInComputeShader.RAY_SHADOWS,
                    BuiltInComputeShader.RAY_LIGHTING)) {
                made.add(device.createComputePipeline(shader.code()));
            }
            return new SoftwareTier(device, made.get(0), made.get(1), made.get(2));
        } catch (RuntimeException e) {
            made.forEach(ComputePipeline::close);
            throw e;
        }
    }

    @Override
    public RayTracing.Software answer() {
        return answer;
    }

    @Override
    public TracedScene build(SceneGeometry geometry) {
        return ComputeScene.build(device, geometry);
    }

    @Override
    public void update(TracedScene scene, SceneGeometry geometry) {
        structures(scene).update(geometry);
    }

    @Override
    public ByteBuffer trace(TracedScene scene, TraceRequest request) {
        var constants = constants(into -> RayConstants.write(into, request, TraceOutput.RECORDS));
        var bytes = TraceOutput.RECORDS.bytes(request.width(), request.height());
        return run(structures(scene), trace, constants, null, pixelGroups(request), bytes, bytes);
    }

    @Override
    public ByteBuffer traceShadows(TracedScene scene, TraceRequest request) {
        var constants = constants(into -> RayConstants.write(into, request, TraceOutput.SHADOW_MASK));
        var words = (request.pixels() + 3) / 4;
        var groups = (words + 63) / 64;
        var groupsX = (int) Math.min(groups, MAX_GROUPS);
        var groupsY = (groups + groupsX - 1) / groupsX;
        if (groupsY > MAX_GROUPS) {
            throw tooLarge(request);
        }
        var grid = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        grid.putInt(0, groupsX * BuiltInComputeShader.RAY_SHADOWS.threadsX());
        grid.putInt(4, Math.toIntExact(words));
        var bytes = TraceOutput.SHADOW_MASK.bytes(request.width(), request.height());
        return run(
                structures(scene),
                shadows,
                constants,
                grid,
                new int[] {groupsX, (int) groupsY},
                bytes,
                request.pixels());
    }

    @Override
    public ByteBuffer traceLighting(TracedScene scene, LightingRequest request) {
        var rays = request.rays();
        var constants = constants(into -> RayConstants.write(into, request));
        var bytes = TraceOutput.LIGHTING.bytes(rays.width(), rays.height());
        return run(structures(scene), trace, constants, null, pixelGroups(rays), bytes, bytes);
    }

    @Override
    public void traceLighting(GpuFrame frame, TracedScene scene, LightingRequest request, LightingTargets into) {
        requireOpen();
        if (frame.device() != device) {
            throw new IllegalArgumentException(
                    "rays are traced in software on " + device + ", and the frame is " + frame.device() + "'s");
        }
        var structures = structures(scene);
        var constants = constants(writer -> RayConstants.write(writer, request));
        var groups = pixelGroups(request.rays());
        var targets = List.of(into.shading().view(0, 0), into.reflectedIds().view(0, 0));
        frame.computePass(List.of(), targets, pass -> {
            pass.bindPipeline(lighting);
            pass.bindStorageBuffers(structures.buffers());
            pass.pushUniforms(0, constants);
            pass.dispatch(groups[0], groups[1], 1);
        });
    }

    /// A frame of one dispatch of `pipeline` into the output buffer, read back:
    /// the first `keep` of the `bytes` it writes, in a new little-endian
    /// buffer.
    private ByteBuffer run(
            ComputeScene scene,
            ComputePipeline pipeline,
            ByteBuffer constants,
            @Nullable ByteBuffer grid,
            int[] groups,
            long bytes,
            long keep) {
        requireOpen();
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(bytes + " bytes of output are past a GPU buffer's 2 GiB");
        }
        var output = written((int) bytes);
        var buffers = scene.buffers();
        try (var frame = device.beginFrame()) {
            frame.computePass(output, pass -> {
                pass.bindPipeline(pipeline);
                pass.bindStorageBuffers(buffers);
                pass.pushUniforms(0, constants);
                if (grid != null) {
                    pass.pushUniforms(1, grid);
                }
                pass.dispatch(groups[0], groups[1], 1);
            });
            try (var readback = frame.readback(output, 0, (int) bytes)) {
                frame.submit();
                var read = readback.await();
                var copy = ByteBuffer.allocate((int) keep).order(ByteOrder.LITTLE_ENDIAN);
                copy.put(0, read, 0, (int) keep);
                return copy;
            }
        }
    }

    /// The workgroups a thread a pixel takes for `request`'s picture.
    private static int[] pixelGroups(TraceRequest request) {
        var x = (request.width() + 7) / 8;
        var y = (request.height() + 7) / 8;
        if (x > MAX_GROUPS || y > MAX_GROUPS) {
            throw tooLarge(request);
        }
        return new int[] {x, y};
    }

    private static IllegalArgumentException tooLarge(TraceRequest request) {
        return new IllegalArgumentException(request.width() + "×" + request.height() + " is more than one dispatch of "
                + MAX_GROUPS + " workgroups an axis covers");
    }

    /// The rays' constants, as `writer` writes them, in a buffer of their size.
    private static ByteBuffer constants(Consumer<MemorySegment> writer) {
        var constants = ByteBuffer.allocate(RayConstants.SIZE).order(ByteOrder.nativeOrder());
        writer.accept(MemorySegment.ofBuffer(constants));
        return constants;
    }

    /// The output buffer, at least `bytes` long.
    private GpuBuffer written(int bytes) {
        var current = written;
        if (current != null && current.size() >= bytes) {
            return current;
        }
        var size = (int) Math.min(Integer.MAX_VALUE, Math.max(bytes, current == null ? 0 : 2L * current.size()));
        var grown = device.createBuffer(EnumSet.of(BufferUsage.COMPUTE_STORAGE_WRITE), size);
        if (current != null) {
            current.close();
        }
        written = grown;
        return grown;
    }

    private ComputeScene structures(TracedScene scene) {
        if (scene instanceof ComputeScene structures && structures.device() == device) {
            return structures;
        }
        throw new IllegalArgumentException(scene + " was not built in software on " + device);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this ray tracer is closed");
        }
    }

    /// Releases its pipelines and output buffer, on a device still open.
    /// Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (device.isClosed()) {
            return;
        }
        var output = written;
        written = null;
        if (output != null) {
            output.close();
        }
        trace.close();
        shadows.close();
        lighting.close();
    }
}
