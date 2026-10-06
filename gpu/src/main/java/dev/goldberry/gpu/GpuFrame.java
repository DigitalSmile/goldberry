package dev.goldberry.gpu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.gpu.SdlGpuBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuDepthTarget;
import dev.goldberry.natives.sdl.gpu.SdlGpuLoad;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTarget;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.natives.sdl.gpu.SdlGpuTextureView;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;
import dev.goldberry.render.model.PhysicalRect;

/// One frame of GPU work: a command buffer, recorded into pass by pass, then
/// submitted once.
///
/// ```java
/// try (var frame = device.beginFrame()) {
///     frame.copyPass(copy -> copy.upload(vertices, 0, mesh));
///     frame.renderPass(target, Load.clear(0, 0, 0, 1), DepthTarget.clear(depth), pass -> {
///         pass.bindPipeline(pipeline);
///         pass.bindVertexBuffer(0, vertices);
///         pass.pushVertexUniforms(0, transform);
///         pass.draw(36);
///     });
///     var pixels = frame.readback(target);
///     frame.submit();
///     return pixels.awaitPixels();
/// }
/// ```
///
/// **Passes are scoped.** A pass is open only while its body runs and is ended
/// when the body returns or throws, so a pass cannot be left open, and one
/// kept past its body throws on use. Passes do not nest.
///
/// **Closing discards.** A frame closed without [#submit] -- because its
/// body threw, say -- is cancelled: nothing it recorded reaches the GPU, and its
/// readbacks fail. Closing a submitted frame does nothing, so
/// try-with-resources is always right.
///
/// **One thread**, the device's, like everything else here.
public final class GpuFrame implements AutoCloseable {

    private enum State {
        RECORDING,
        SUBMITTED,
        DISCARDED
    }

    private final GpuDevice device;
    private final SdlGpuCommandBuffer commands;
    private final List<Readback> readbacks = new ArrayList<>();
    private State state = State.RECORDING;
    private @Nullable String openPass;

    GpuFrame(GpuDevice device, SdlGpuCommandBuffer commands) {
        this.device = device;
        this.commands = commands;
    }

    /// The device it records for.
    public GpuDevice device() {
        return device;
    }

    /// Records a copy pass: uploads into textures and buffers. `body` runs now,
    /// and the pass ends when it returns.
    ///
    /// @throws IllegalStateException when the frame is finished or a pass is open
    /// @throws GpuException          when the driver cannot begin the pass
    public void copyPass(Consumer<CopyPass> body) {
        Objects.requireNonNull(body, "body");
        requireRecording("copyPass");
        var pass = GpuDevice.call(commands::beginCopyPass);
        var copy = new CopyPass(device, pass);
        openPass = "a copy pass";
        try (pass) {
            body.accept(copy);
        } finally {
            copy.end();
            openPass = null;
        }
    }

    /// Records a render pass into `target`, which first does what `load` says.
    /// `body` runs now, and the pass ends when it returns.
    ///
    /// @throws IllegalArgumentException when `target` is not a colour target
    ///                                  of this device, or is closed
    /// @throws IllegalStateException    when the frame is finished or a pass is
    ///                                  open
    /// @throws GpuException             when the driver cannot begin the pass
    public void renderPass(RenderTarget target, Load load, Consumer<RenderPass> body) {
        recordRenderPass(target, load, null, body);
    }

    /// Records a render pass into `target` that tests and writes depth in
    /// `depth`, which is the target's size. `body` runs now, and the pass ends
    /// when it returns.
    ///
    /// @throws IllegalArgumentException when `target` is not a colour target of
    ///                                  this device, `depth` is another size or
    ///                                  device's, or either is closed
    /// @throws IllegalStateException    when the frame is finished or a pass is
    ///                                  open
    /// @throws GpuException             when the driver cannot begin the pass
    public void renderPass(RenderTarget target, Load load, DepthTarget depth, Consumer<RenderPass> body) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(depth, "depth");
        recordRenderPass(target, load, depth, body);
    }

    /// Records a render pass into `target`, a multisampled texture, whose
    /// samples are resolved into `resolveTo` when the pass ends: one level of
    /// one layer of a single-sampled texture of the target's format and size.
    /// The target's own contents are then undefined. `body` runs now, with a
    /// pipeline of the target's sample count, and the pass ends when it
    /// returns.
    ///
    /// @throws IllegalArgumentException when `target` has one sample or is not
    ///                                  a colour target, `resolveTo` is
    ///                                  multisampled or of another format or
    ///                                  size, or anything is another device's
    ///                                  or closed
    /// @throws IllegalStateException    when the frame is finished or a pass is
    ///                                  open
    /// @throws GpuException             when the driver cannot begin the pass
    public void renderPass(GpuTexture target, Load load, TextureView resolveTo, Consumer<RenderPass> body) {
        recordResolvingPass(target, load, resolveTo, null, body);
    }

    /// [#renderPass(GpuTexture, Load, TextureView, Consumer)], testing depth in
    /// `depth`, which has the target's sample count.
    ///
    /// @throws IllegalArgumentException as that method, or when `depth` has
    ///                                  another sample count
    /// @throws IllegalStateException    as that method
    /// @throws GpuException             as that method
    public void renderPass(
            GpuTexture target, Load load, TextureView resolveTo, DepthTarget depth, Consumer<RenderPass> body) {
        recordResolvingPass(target, load, resolveTo, Objects.requireNonNull(depth, "depth"), body);
    }

    private void recordResolvingPass(
            GpuTexture target,
            Load load,
            TextureView resolveTo,
            @Nullable DepthTarget depth,
            Consumer<RenderPass> body) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(resolveTo, "resolveTo");
        Objects.requireNonNull(body, "body");
        requireRecording("renderPass");
        var sdlTarget = requireColorTarget(target);
        var sdlResolve = new SdlGpuTextureView(resolveTo.texture().sdl(device), resolveTo.level(), resolveTo.layer());
        var sdlLoad = sdlLoad(load);
        var sdlDepth = depth == null ? null : sdlDepth(depth);
        var pass = GpuDevice.call(() -> commands.beginRenderPass(sdlTarget, sdlLoad, sdlResolve, sdlDepth));
        var render = new RenderPass(device, pass, Optional.of(target));
        openPass = "a render pass";
        try (pass) {
            body.accept(render);
        } finally {
            render.end();
            openPass = null;
        }
    }

    /// Records a compute pass that writes `buffer`, made with
    /// [BufferUsage#COMPUTE_STORAGE_WRITE]. `body` runs now, and the pass ends
    /// when it returns.
    ///
    /// @throws IllegalArgumentException as [#computePass(List, List, Consumer)]
    /// @throws IllegalStateException    as [#computePass(List, List, Consumer)]
    /// @throws GpuException             as [#computePass(List, List, Consumer)]
    public void computePass(GpuBuffer buffer, Consumer<ComputePass> body) {
        computePass(List.of(Objects.requireNonNull(buffer, "buffer")), List.of(), body);
    }

    /// Records a compute pass that writes `writtenBuffers` and
    /// `writtenTextures`, one level of one layer each, which were made with
    /// the compute-write usage; the pipeline bound in it declares as many of
    /// each. `body` runs now, binds a pipeline and its read-only storage, and
    /// dispatches; the pass ends when the body returns.
    ///
    /// @throws IllegalArgumentException when a buffer or texture was not made
    ///                                  to be written by compute, or is another
    ///                                  device's or closed
    /// @throws IllegalStateException    when the frame is finished or a pass is
    ///                                  open
    /// @throws GpuException             when the driver cannot begin the pass
    public void computePass(
            List<GpuBuffer> writtenBuffers, List<TextureView> writtenTextures, Consumer<ComputePass> body) {
        Objects.requireNonNull(writtenBuffers, "writtenBuffers");
        Objects.requireNonNull(writtenTextures, "writtenTextures");
        Objects.requireNonNull(body, "body");
        requireRecording("computePass");
        var buffers = new ArrayList<SdlGpuBuffer>(writtenBuffers.size());
        for (var buffer : writtenBuffers) {
            buffers.add(buffer.sdl(device));
        }
        var views = new ArrayList<SdlGpuTextureView>(writtenTextures.size());
        for (var view : writtenTextures) {
            views.add(new SdlGpuTextureView(view.texture().sdl(device), view.level(), view.layer()));
        }
        var pass = GpuDevice.call(() -> commands.beginComputePass(buffers, views));
        var compute = new ComputePass(device, pass);
        openPass = "a compute pass";
        try (pass) {
            body.accept(compute);
        } finally {
            compute.end();
            openPass = null;
        }
    }

    /// Records a render pass with no colour target that tests and writes depth
    /// in `depth`: a shadow map's pass, drawn with a [PipelineSpec#depthOnly]
    /// pipeline. `body` runs now, and the pass ends when it returns.
    ///
    /// @throws IllegalArgumentException when `depth` is another device's or
    ///                                  closed
    /// @throws IllegalStateException    when the frame is finished or a pass is
    ///                                  open
    /// @throws GpuException             when the driver cannot begin the pass
    public void renderPass(DepthTarget depth, Consumer<RenderPass> body) {
        Objects.requireNonNull(depth, "depth");
        recordRenderPass(null, Load.keep(), depth, body);
    }

    private void recordRenderPass(
            @Nullable RenderTarget target, Load load, @Nullable DepthTarget depth, Consumer<RenderPass> body) {
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(body, "body");
        requireRecording("renderPass");
        var sdlLoad = sdlLoad(load);
        SdlGpuCommandBuffer.RenderPass pass;
        if (target == null) {
            var sdlDepth = sdlDepth(Objects.requireNonNull(depth));
            pass = GpuDevice.call(() -> commands.beginRenderPass(sdlDepth));
        } else {
            var sdlTarget = sdlTarget(target);
            pass = depth == null
                    ? GpuDevice.call(() -> commands.beginRenderPass(sdlTarget, sdlLoad))
                    : GpuDevice.call(() -> commands.beginRenderPass(sdlTarget, sdlLoad, sdlDepth(depth)));
        }
        var render = new RenderPass(device, pass, Optional.ofNullable(target));
        openPass = "a render pass";
        try (pass) {
            body.accept(render);
        } finally {
            render.end();
            openPass = null;
        }
    }

    private static SdlGpuLoad sdlLoad(Load load) {
        return switch (load) {
            case Load.Keep _ -> SdlGpuLoad.keep();
            case Load.Clear(var red, var green, var blue, var alpha) -> SdlGpuLoad.clear(red, green, blue, alpha);
            case Load.DontCare _ -> new SdlGpuLoad.DontCare();
        };
    }

    private SdlGpuTarget sdlTarget(RenderTarget target) {
        return switch (target) {
            case GpuTexture texture -> requireColorTarget(texture);
            case TextureView(var texture, var level, var layer) ->
                new SdlGpuTextureView(requireColorTarget(texture), level, layer);
        };
    }

    private SdlGpuTexture requireColorTarget(GpuTexture texture) {
        var sdl = texture.sdl(device);
        if (!texture.usages().contains(TextureUsage.COLOR_TARGET)) {
            throw new IllegalArgumentException(texture + " was not made to be rendered into");
        }
        return sdl;
    }

    /// Records the filling of every mip level of `texture` below the first from
    /// the level above it, each a box filter of the one before: what a texture
    /// uploaded at full size needs before a mipmapped sampler reads it. Outside
    /// any pass.
    ///
    /// @throws IllegalArgumentException when the texture has one level, was not
    ///                                  made as [TextureSpec#renderTarget] (both
    ///                                  sampled and a colour target, which the
    ///                                  drivers' blits need), or is another
    ///                                  device's or closed
    /// @throws IllegalStateException    when the frame is finished or a pass is
    ///                                  open
    public void generateMipmaps(GpuTexture texture) {
        requireRecording("generateMipmaps");
        commands.generateMipmaps(texture.sdl(device));
    }

    private SdlGpuDepthTarget sdlDepth(DepthTarget depth) {
        return new SdlGpuDepthTarget(depth.texture().sdl(device), depth.clear(), depth.clearDepth());
    }

    /// Records a copy of the whole of `source` into memory the CPU reads once
    /// the frame is submitted: [Readback#await].
    ///
    /// @throws IllegalArgumentException when `source` is a depth texture, or is
    ///                                  another device's or closed
    /// @throws IllegalStateException    when the frame is finished or a pass is
    ///                                  open
    public Readback readback(GpuTexture source) {
        return readback(source, new PhysicalRect(0, 0, source.width(), source.height()));
    }

    /// Records a copy of the whole of `view`, one level of one layer, into
    /// memory the CPU reads once the frame is submitted: [Readback#await].
    ///
    /// @throws IllegalArgumentException as [#readback(GpuTexture)]
    /// @throws IllegalStateException    as [#readback(GpuTexture)]
    public Readback readback(TextureView view) {
        return readback(view, new PhysicalRect(0, 0, view.width(), view.height()));
    }

    /// Records a copy of `region` of `view`, in the level's texels, into memory
    /// the CPU reads once the frame is submitted: [Readback#await].
    ///
    /// @throws IllegalArgumentException as [#readback(GpuTexture, PhysicalRect)]
    /// @throws IllegalStateException    as [#readback(GpuTexture, PhysicalRect)]
    public Readback readback(TextureView view, PhysicalRect region) {
        Objects.requireNonNull(view, "view");
        return readback(view.texture(), view.level(), view.layer(), region);
    }

    /// Records a copy of `region` of `source` into memory the CPU reads once
    /// the frame is submitted: [Readback#await]. Recorded in a copy pass of its
    /// own, so after what the frame has drawn so far.
    ///
    /// A depth texture is read back only when it was made to be sampled: its
    /// bytes are then [TextureFormat#D32_FLOAT] floats or
    /// [TextureFormat#D16_UNORM] shorts.
    ///
    /// @throws IllegalArgumentException when `source` is a depth texture that
    ///                                  is not sampled, or is another device's
    ///                                  or closed, or `region` is empty or
    ///                                  outside it
    /// @throws IllegalStateException    when the frame is finished or a pass is
    ///                                  open
    /// @throws GpuException             when the driver refuses the memory
    public Readback readback(GpuTexture source, PhysicalRect region) {
        return readback(source, 0, 0, region);
    }

    private Readback readback(GpuTexture source, int level, int layer, PhysicalRect region) {
        requireRecording("readback");
        var texture = source.sdl(device);
        if (source.format().isDepth() && !source.usages().contains(TextureUsage.SAMPLER)) {
            throw new IllegalArgumentException(source + " is a depth texture made only to be tested, not read back");
        }
        if (region.isEmpty() || !source.view(level, layer).contains(region)) {
            throw new IllegalArgumentException(region + " is empty or outside " + source.view(level, layer));
        }
        var sdlRegion = new SdlGpuRegion(region.x(), region.y(), region.width(), region.height());
        var bytes = texture.byteSize(sdlRegion);
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(region + " of " + source + " is past SDL's 2 GiB");
        }
        var sdlDevice = device.sdl();
        var transfer = GpuDevice.call(() -> sdlDevice.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, (int) bytes));
        try (var pass = GpuDevice.call(commands::beginCopyPass)) {
            pass.download(texture, level, layer, sdlRegion, transfer, 0);
        } catch (RuntimeException e) {
            transfer.close();
            throw e;
        }
        var readback = new Readback(device, transfer, source.format(), region.width(), region.height());
        readbacks.add(readback);
        return readback;
    }

    /// Begins a debug group named `name`, runs `body`, and ends it: the name a
    /// frame capture in Xcode, RenderDoc or PIX shows the commands recorded in
    /// `body` under. Allowed inside a pass's body as well as between passes.
    ///
    /// @throws IllegalStateException when the frame is finished
    public void debugGroup(String name, Runnable body) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(body, "body");
        requireOpen("debugGroup");
        commands.pushDebugGroup(name);
        try {
            body.run();
        } finally {
            if (!commands.isFinished()) {
                commands.popDebugGroup();
            }
        }
    }

    /// Marks this point of the frame with `text`, for a frame capture.
    ///
    /// @throws IllegalStateException when the frame is finished
    public void debugLabel(String text) {
        Objects.requireNonNull(text, "text");
        requireOpen("debugLabel");
        commands.insertDebugLabel(text);
    }

    /// Submits the frame: the GPU runs what it recorded, and its readbacks can
    /// be awaited. Does not wait for the GPU.
    ///
    /// @throws IllegalStateException when the frame is finished, a pass is open,
    ///                               or it is called inside a debug group
    /// @throws GpuException          when the driver refuses; the frame is
    ///                               discarded and its readbacks fail
    public void submit() {
        requireRecording("submit");
        try {
            if (readbacks.isEmpty()) {
                commands.submit();
            } else {
                var fence = new SharedFence(commands.submitWithFence(), readbacks.size());
                for (var readback : readbacks) {
                    readback.submitted(fence);
                }
            }
            state = State.SUBMITTED;
        } catch (SdlException e) {
            discardReadbacks();
            state = State.DISCARDED;
            throw GpuException.of(e);
        }
    }

    /// Whether [#submit] has run.
    public boolean isSubmitted() {
        return state == State.SUBMITTED;
    }

    /// Whether it can still be recorded into.
    public boolean isRecording() {
        return state == State.RECORDING;
    }

    /// Discards the frame if it was not submitted: nothing it recorded runs,
    /// and its readbacks fail. Does nothing once submitted or discarded.
    ///
    /// @throws IllegalStateException when called inside one of its own passes
    @Override
    public void close() {
        device.requireThread();
        if (state != State.RECORDING) {
            return;
        }
        if (openPass != null) {
            throw new IllegalStateException("a frame cannot be closed inside " + openPass + " of its own");
        }
        state = State.DISCARDED;
        discardReadbacks();
        if (!commands.isFinished()) {
            GpuDevice.run(commands::cancel);
        }
    }

    private void discardReadbacks() {
        for (var readback : readbacks) {
            readback.discarded();
        }
    }

    private void requireOpen(String operation) {
        device.requireThread();
        if (state != State.RECORDING) {
            throw new IllegalStateException(
                    operation + " on a frame already " + (state == State.SUBMITTED ? "submitted" : "discarded"));
        }
    }

    private void requireRecording(String operation) {
        requireOpen(operation);
        if (openPass != null) {
            throw new IllegalStateException(operation + " inside " + openPass + ": passes do not nest");
        }
        device.sdl();
    }
}
