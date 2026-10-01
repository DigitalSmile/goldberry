package dev.goldberry.gpu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuDepthTarget;
import dev.goldberry.natives.sdl.gpu.SdlGpuLoad;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTarget;
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
        Objects.requireNonNull(depth, "depth");
        recordRenderPass(target, load, depth, body);
    }

    private void recordRenderPass(
            RenderTarget target, Load load, @Nullable DepthTarget depth, Consumer<RenderPass> body) {
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(body, "body");
        requireRecording("renderPass");
        var sdlTarget = sdlTarget(target);
        var sdlLoad =
                switch (load) {
                    case Load.Keep _ -> SdlGpuLoad.keep();
                    case Load.Clear(var red, var green, var blue, var alpha) ->
                        SdlGpuLoad.clear(red, green, blue, alpha);
                    case Load.DontCare _ -> new SdlGpuLoad.DontCare();
                };
        var pass = depth == null
                ? GpuDevice.call(() -> commands.beginRenderPass(sdlTarget, sdlLoad))
                : GpuDevice.call(() -> commands.beginRenderPass(sdlTarget, sdlLoad, sdlDepth(depth)));
        var render = new RenderPass(device, pass, target);
        openPass = "a render pass";
        try (pass) {
            body.accept(render);
        } finally {
            render.end();
            openPass = null;
        }
    }

    private SdlGpuTarget sdlTarget(RenderTarget target) {
        return switch (target) {
            case GpuTexture texture -> {
                var sdl = texture.sdl(device);
                if (!texture.usages().contains(TextureUsage.COLOR_TARGET)) {
                    throw new IllegalArgumentException(texture + " was not made to be rendered into");
                }
                yield sdl;
            }
        };
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

    /// Records a copy of `region` of `source` into memory the CPU reads once
    /// the frame is submitted: [Readback#await]. Recorded in a copy pass of its
    /// own, so after what the frame has drawn so far.
    ///
    /// @throws IllegalArgumentException when `source` is a depth texture, or is
    ///                                  another device's or closed, or `region`
    ///                                  is empty or outside it
    /// @throws IllegalStateException    when the frame is finished or a pass is
    ///                                  open
    /// @throws GpuException             when the driver refuses the memory
    public Readback readback(GpuTexture source, PhysicalRect region) {
        requireRecording("readback");
        var texture = source.sdl(device);
        if (source.format().isDepth()) {
            throw new IllegalArgumentException(source + " is a depth texture, which is not read back");
        }
        if (region.isEmpty() || !source.contains(region)) {
            throw new IllegalArgumentException(region + " is empty or outside " + source);
        }
        var sdlRegion = new SdlGpuRegion(region.x(), region.y(), region.width(), region.height());
        var bytes = texture.byteSize(sdlRegion);
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(region + " of " + source + " is past SDL's 2 GiB");
        }
        var sdlDevice = device.sdl();
        var transfer = GpuDevice.call(() -> sdlDevice.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, (int) bytes));
        try (var pass = GpuDevice.call(commands::beginCopyPass)) {
            pass.download(texture, sdlRegion, transfer, 0);
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
