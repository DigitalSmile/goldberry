package dev.goldberry.natives.sdl.gpu;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.layout.Layouts;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.calls.SdlGpuCommandCalls;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBufferUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuIndexSize;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuSampleCount;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureType;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;

/// Commands recorded for a [SdlGpuDevice], then submitted or cancelled, once.
///
/// SDL's rules are checked here, before SDL is called, because SDL's answer to
/// breaking one is an assertion in a debug build and undefined behaviour in a
/// release one:
///
/// - one pass at a time: nothing is recorded while a [CopyPass] or a
///   [RenderPass] is open;
/// - a draw has a pipeline bound, for the target's format and its depth
///   target's, a texture bound to every sampler slot the pipeline's fragment
///   shader declares, a buffer bound to every vertex slot it reads, and, for an
///   indexed draw, an index buffer;
/// - debug groups are balanced, and one begun in a pass ends in it;
/// - a command buffer is used once: after [#submit], [#submitWithFence] or
///   [#cancel], every method throws;
/// - a transfer buffer is unmapped while a copy pass uses it;
/// - resources are the same device's, regions lie inside their texture, and
///   the bytes fit the transfer buffer.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlGpuCommandBuffer {

    private enum State {
        RECORDING,
        IN_COPY_PASS,
        IN_RENDER_PASS,
        IN_COMPUTE_PASS,
        FINISHED
    }

    private final SdlGpuDevice device;
    private final MemorySegment handle;
    private State state = State.RECORDING;
    private int debugDepth;

    SdlGpuCommandBuffer(SdlGpuDevice device, MemorySegment handle) {
        this.device = device;
        this.handle = handle;
    }

    /// Records a render pass that clears `target` to a colour and keeps it.
    ///
    /// The components are the texture format's: for an `UNORM` format, 0 to 1.
    ///
    /// @throws IllegalArgumentException when `target` is not a colour target of
    ///                                  this device
    /// @throws SdlException             when SDL cannot begin the pass
    public void clear(SdlGpuTarget target, float red, float green, float blue, float alpha) {
        beginRenderPass(target, SdlGpuLoad.clear(red, green, blue, alpha)).close();
    }

    /// Begins a render pass into `target`, which first does what `load` says.
    /// Nothing else is recorded until the pass is closed.
    ///
    /// @throws IllegalArgumentException when `target` is not a colour target of
    ///                                  this device
    /// @throws SdlException             when SDL cannot begin the pass
    public RenderPass beginRenderPass(SdlGpuTarget target, SdlGpuLoad load) {
        return beginPass(target, load, null);
    }

    /// Begins a render pass into `target` that tests and writes depth in
    /// `depth`, which must be as large as the target. Each first does what its
    /// load says. Nothing else is recorded until the pass is closed.
    ///
    /// @throws IllegalArgumentException when `target` is not a colour target of
    ///                                  this device, or `depth` is another
    ///                                  device's or another size
    /// @throws SdlException             when SDL cannot begin the pass
    public RenderPass beginRenderPass(SdlGpuTarget target, SdlGpuLoad load, SdlGpuDepthTarget depth) {
        return beginPass(target, load, Objects.requireNonNull(depth, "depth"));
    }

    /// Begins a render pass into the multisampled `target` whose samples are
    /// resolved into `resolveTo`, one level of one layer of a single-sampled
    /// texture of the target's format and size, when the pass ends; the
    /// target's own contents are then undefined. With a `depth`, which must
    /// have the target's sample count, or null for none.
    ///
    /// @throws IllegalArgumentException when `target` has one sample,
    ///                                  `resolveTo` has more than one or
    ///                                  another format or size, or either is
    ///                                  not a colour target of this device
    /// @throws SdlException             when SDL cannot begin the pass
    public RenderPass beginRenderPass(
            SdlGpuTexture target, SdlGpuLoad load, SdlGpuTextureView resolveTo, @Nullable SdlGpuDepthTarget depth) {
        Objects.requireNonNull(resolveTo, "resolveTo");
        if (target.sampleCount() == SdlGpuSampleCount.ONE) {
            throw new IllegalArgumentException(target + " has one sample, and nothing to resolve");
        }
        var destination = resolveTo.texture();
        requireColorTarget(destination);
        if (destination.sampleCount() != SdlGpuSampleCount.ONE) {
            throw new IllegalArgumentException(destination + " is multisampled, and a resolve lands on one sample");
        }
        if (destination.format() != target.format()
                || resolveTo.width() != target.width()
                || resolveTo.height() != target.height()) {
            throw new IllegalArgumentException(resolveTo + " is not the format and size of " + target);
        }
        return beginPass(target, load, depth, resolveTo);
    }

    private RenderPass beginPass(@Nullable SdlGpuTarget target, SdlGpuLoad load, @Nullable SdlGpuDepthTarget depth) {
        return beginPass(target, load, depth, null);
    }

    /// Begins a render pass with no colour target that tests and writes depth
    /// in `depth`: a shadow map. Only a pipeline with no colour target draws in
    /// it. Nothing else is recorded until the pass is closed.
    ///
    /// @throws IllegalArgumentException when `depth` is another device's
    /// @throws SdlException             when SDL cannot begin the pass
    public RenderPass beginRenderPass(SdlGpuDepthTarget depth) {
        return beginPass(null, SdlGpuLoad.keep(), Objects.requireNonNull(depth, "depth"));
    }

    private RenderPass beginPass(
            @Nullable SdlGpuTarget target,
            SdlGpuLoad load,
            @Nullable SdlGpuDepthTarget depth,
            @Nullable SdlGpuTextureView resolveTo) {
        requireState(State.RECORDING, "beginRenderPass");
        var resolved = target == null ? null : colorTarget(target);
        if (resolved == null && depth == null) {
            throw new IllegalArgumentException("a render pass needs a colour target or a depth target");
        }
        var info = Layouts.SDL_GPU_COLOR_TARGET_INFO;
        var color = Layouts.SDL_FCOLOR;
        var clearColor = info.offsetOf("clear_color");
        try (var arena = Arena.ofConfined()) {
            var colorTarget = MemorySegment.NULL;
            if (resolved != null) {
                colorTarget = arena.allocate(info.layout());
                colorTarget.set(ADDRESS, info.offsetOf("texture"), resolved.handle());
                colorTarget.set(JAVA_INT, info.offsetOf("mip_level"), resolved.level());
                colorTarget.set(JAVA_INT, info.offsetOf("layer_or_depth_plane"), resolved.layer());
                var loadOp = switch (load) {
                    case SdlGpuLoad.Keep _ -> SdlGpuCommandCalls.LOADOP_LOAD;
                    case SdlGpuLoad.DontCare _ -> SdlGpuCommandCalls.LOADOP_DONT_CARE;
                    case SdlGpuLoad.Clear(var red, var green, var blue, var alpha) -> {
                        colorTarget.set(JAVA_FLOAT, clearColor + color.offsetOf("r"), red);
                        colorTarget.set(JAVA_FLOAT, clearColor + color.offsetOf("g"), green);
                        colorTarget.set(JAVA_FLOAT, clearColor + color.offsetOf("b"), blue);
                        colorTarget.set(JAVA_FLOAT, clearColor + color.offsetOf("a"), alpha);
                        yield SdlGpuCommandCalls.LOADOP_CLEAR;
                    }
                };
                colorTarget.set(JAVA_INT, info.offsetOf("load_op"), loadOp);
                if (resolveTo == null) {
                    colorTarget.set(JAVA_INT, info.offsetOf("store_op"), SdlGpuCommandCalls.STOREOP_STORE);
                } else {
                    colorTarget.set(JAVA_INT, info.offsetOf("store_op"), SdlGpuCommandCalls.STOREOP_RESOLVE);
                    colorTarget.set(
                            ADDRESS,
                            info.offsetOf("resolve_texture"),
                            resolveTo.texture().handle());
                    colorTarget.set(JAVA_INT, info.offsetOf("resolve_mip_level"), resolveTo.level());
                    colorTarget.set(JAVA_INT, info.offsetOf("resolve_layer"), resolveTo.layer());
                    colorTarget.set(JAVA_BOOLEAN, info.offsetOf("cycle_resolve_texture"), false);
                }
                colorTarget.set(JAVA_BOOLEAN, info.offsetOf("cycle"), false);
            }
            var width = resolved != null ? resolved.width() : depth.texture().width();
            var height = resolved != null ? resolved.height() : depth.texture().height();
            var samples =
                    resolved != null ? resolved.samples() : depth.texture().sampleCount();
            if (depth != null && resolved != null && depth.texture().sampleCount() != samples) {
                throw new IllegalArgumentException(
                        depth.texture() + " has not the " + samples.samples() + " samples of the colour target");
            }
            var depthTarget = depth == null ? MemorySegment.NULL : depthTarget(arena, width, height, depth);
            var pass = device.calls()
                    .commands()
                    .beginGPURenderPass()
                    .call(handle, colorTarget, resolved == null ? 0 : 1, depthTarget);
            if (MemorySegment.NULL.equals(pass)) {
                throw new SdlException("SDL_BeginGPURenderPass", Sdl.get().lastError());
            }
            state = State.IN_RENDER_PASS;
            return new RenderPass(
                    pass,
                    width,
                    height,
                    resolved == null ? Optional.empty() : resolved.format(),
                    resolved == null,
                    samples,
                    depth == null
                            ? Optional.empty()
                            : Optional.of(depth.texture().format()));
        }
    }

    /// The `SDL_GPUDepthStencilTargetInfo` for `depth`, which must be `width`
    /// by `height`, the pass's size. Stencil is neither loaded nor kept: the
    /// depth formats here have none.
    private MemorySegment depthTarget(Arena arena, int width, int height, SdlGpuDepthTarget depth) {
        var texture = depth.texture();
        requireOwn(texture);
        if (texture.width() != width || texture.height() != height) {
            throw new IllegalArgumentException(texture + " is not the size of the pass, " + width + "x" + height);
        }
        var info = Layouts.SDL_GPU_DEPTH_STENCIL_TARGET_INFO;
        var segment = arena.allocate(info.layout());
        segment.set(ADDRESS, info.offsetOf("texture"), texture.handle());
        segment.set(JAVA_FLOAT, info.offsetOf("clear_depth"), depth.clearDepth());
        segment.set(
                JAVA_INT,
                info.offsetOf("load_op"),
                depth.clear() ? SdlGpuCommandCalls.LOADOP_CLEAR : SdlGpuCommandCalls.LOADOP_LOAD);
        segment.set(JAVA_INT, info.offsetOf("store_op"), SdlGpuCommandCalls.STOREOP_STORE);
        segment.set(JAVA_INT, info.offsetOf("stencil_load_op"), SdlGpuCommandCalls.LOADOP_DONT_CARE);
        segment.set(JAVA_INT, info.offsetOf("stencil_store_op"), SdlGpuCommandCalls.STOREOP_DONT_CARE);
        segment.set(JAVA_BOOLEAN, info.offsetOf("cycle"), false);
        return segment;
    }

    /// Waits for `window`'s next swapchain texture. It is presented when this
    /// command buffer is submitted.
    ///
    /// Empty when the window has none to give, which is not a failure: SDL's
    /// answer for a minimised or occluded window. The command buffer should
    /// still be submitted, so what it uploads still lands.
    ///
    /// @throws IllegalArgumentException when `window` was claimed by another
    ///                                  device
    /// @throws SdlException             when SDL fails
    public Optional<SdlGpuSwapchainTexture> acquireSwapchainTexture(SdlGpuWindow window) {
        requireState(State.RECORDING, "acquireSwapchainTexture");
        requireOwn(window);
        try (var arena = Arena.ofConfined()) {
            var texture = arena.allocate(ADDRESS);
            var width = arena.allocate(JAVA_INT);
            var height = arena.allocate(JAVA_INT);
            if (!device.calls()
                    .swapchain()
                    .waitAndAcquireGPUSwapchainTexture()
                    .call(handle, window.pointer(), texture, width, height)) {
                throw new SdlException(
                        "SDL_WaitAndAcquireGPUSwapchainTexture", Sdl.get().lastError());
            }
            var acquired = texture.get(ADDRESS, 0);
            if (MemorySegment.NULL.equals(acquired)) {
                return Optional.empty();
            }
            return Optional.of(new SdlGpuSwapchainTexture(
                    this, acquired, width.get(JAVA_INT, 0), height.get(JAVA_INT, 0), window.textureFormat()));
        }
    }

    /// Records a copy of `sourceRegion` of `source` into `destinationRegion` of
    /// `destination`, scaled with `filter` when the two differ in size. Outside
    /// any pass, as SDL requires.
    ///
    /// @throws IllegalArgumentException when a texture is not usable so, or a
    ///                                  region lies outside its texture
    public void blit(
            SdlGpuTexture source,
            SdlGpuRegion sourceRegion,
            SdlGpuTarget destination,
            SdlGpuRegion destinationRegion,
            SdlGpuFilter filter) {
        requireState(State.RECORDING, "blit");
        requireOwn(source);
        if (!source.usages().contains(SdlGpuTextureUsage.SAMPLER)) {
            throw new IllegalArgumentException(source + " cannot be sampled, so cannot be blitted from");
        }
        if (!sourceRegion.fitsIn(source.width(), source.height())) {
            throw new IllegalArgumentException(sourceRegion + " is outside " + source);
        }
        if (!destinationRegion.fitsIn(destination.width(), destination.height())) {
            throw new IllegalArgumentException(destinationRegion + " is outside " + destination);
        }
        var resolved = colorTarget(destination);
        var info = Layouts.SDL_GPU_BLIT_INFO;
        try (var arena = Arena.ofConfined()) {
            var blit = arena.allocate(info.layout());
            blitRegion(blit, info.offsetOf("source"), source.handle(), sourceRegion);
            blitRegion(blit, info.offsetOf("destination"), resolved.handle(), destinationRegion);
            var region = Layouts.SDL_GPU_BLIT_REGION;
            var at = info.offsetOf("destination");
            blit.set(JAVA_INT, at + region.offsetOf("mip_level"), resolved.level());
            blit.set(JAVA_INT, at + region.offsetOf("layer_or_depth_plane"), resolved.layer());
            blit.set(JAVA_INT, info.offsetOf("load_op"), SdlGpuCommandCalls.LOADOP_LOAD);
            blit.set(JAVA_INT, info.offsetOf("flip_mode"), SdlGpuCommandCalls.FLIP_NONE);
            blit.set(JAVA_INT, info.offsetOf("filter"), filter.value());
            blit.set(JAVA_BOOLEAN, info.offsetOf("cycle"), false);
            device.calls().commands().blitGPUTexture().call(handle, blit);
        }
    }

    private static void blitRegion(MemorySegment blit, long offset, MemorySegment texture, SdlGpuRegion region) {
        var layout = Layouts.SDL_GPU_BLIT_REGION;
        blit.set(ADDRESS, offset + layout.offsetOf("texture"), texture);
        blit.set(JAVA_INT, offset + layout.offsetOf("x"), region.x());
        blit.set(JAVA_INT, offset + layout.offsetOf("y"), region.y());
        blit.set(JAVA_INT, offset + layout.offsetOf("w"), region.width());
        blit.set(JAVA_INT, offset + layout.offsetOf("h"), region.height());
    }

    /// Records the filling of every mip level of `texture` below the first
    /// from the level above it. Outside any pass, as SDL requires.
    ///
    /// @throws IllegalArgumentException when the texture has one level, is not
    ///                                  both sampled and a colour target (which
    ///                                  the drivers' blits need), or is another
    ///                                  device's
    public void generateMipmaps(SdlGpuTexture texture) {
        requireState(State.RECORDING, "generateMipmaps");
        requireOwn(texture);
        if (texture.mipLevels() <= 1) {
            throw new IllegalArgumentException(texture + " has one mip level, so none to generate");
        }
        if (!texture.usages().containsAll(EnumSet.of(SdlGpuTextureUsage.SAMPLER, SdlGpuTextureUsage.COLOR_TARGET))) {
            throw new IllegalArgumentException(
                    texture + " must be sampled and a colour target for its mip levels to be generated");
        }
        device.calls().compute().generateMipmapsForGPUTexture().call(handle, texture.handle());
    }

    /// A target this command buffer may draw into: its handle, the level and
    /// layer, its size, and its format where known.
    private record ResolvedTarget(
            MemorySegment handle,
            int level,
            int layer,
            int width,
            int height,
            Optional<SdlGpuTextureFormat> format,
            SdlGpuSampleCount samples) {}

    private ResolvedTarget colorTarget(SdlGpuTarget target) {
        return switch (target) {
            case SdlGpuTexture texture -> {
                requireColorTarget(texture);
                yield new ResolvedTarget(
                        texture.handle(),
                        0,
                        0,
                        texture.width(),
                        texture.height(),
                        Optional.of(texture.format()),
                        texture.sampleCount());
            }
            case SdlGpuTextureView(var texture, var level, var layer) -> {
                requireColorTarget(texture);
                yield new ResolvedTarget(
                        texture.handle(),
                        level,
                        layer,
                        texture.levelWidth(level),
                        texture.levelHeight(level),
                        Optional.of(texture.format()),
                        texture.sampleCount());
            }
            case SdlGpuSwapchainTexture swapchain ->
                new ResolvedTarget(
                        swapchain.handle(this),
                        0,
                        0,
                        swapchain.width(),
                        swapchain.height(),
                        swapchain.format(),
                        SdlGpuSampleCount.ONE);
        };
    }

    private void requireColorTarget(SdlGpuTexture texture) {
        requireOwn(texture);
        if (!texture.usages().contains(SdlGpuTextureUsage.COLOR_TARGET)) {
            throw new IllegalArgumentException(texture + " is not a colour target");
        }
    }

    /// Begins a copy pass. Nothing else is recorded until it is closed.
    ///
    /// @throws SdlException when SDL cannot begin the pass
    public CopyPass beginCopyPass() {
        requireState(State.RECORDING, "beginCopyPass");
        var pass = device.calls().commands().beginGPUCopyPass().call(handle);
        if (MemorySegment.NULL.equals(pass)) {
            throw new SdlException("SDL_BeginGPUCopyPass", Sdl.get().lastError());
        }
        state = State.IN_COPY_PASS;
        return new CopyPass(pass);
    }

    /// Begins a debug group named `name`: what a frame capture shows the
    /// commands recorded until [#popDebugGroup] under. Allowed inside a pass,
    /// and then it must end in the same pass, as Metal scopes it to the pass.
    ///
    /// @throws IllegalStateException when the buffer is finished
    public void pushDebugGroup(String name) {
        requireRecordingOrInPass("pushDebugGroup");
        try (var arena = Arena.ofConfined()) {
            device.calls().debug().pushGPUDebugGroup().call(handle, arena.allocateFrom(name));
        }
        debugDepth++;
    }

    /// Ends the debug group begun last.
    ///
    /// @throws IllegalStateException when no group is open, or the one open
    ///                               was begun outside the pass now open
    public void popDebugGroup() {
        requireRecordingOrInPass("popDebugGroup");
        if (debugDepth <= openPassDebugDepth) {
            throw new IllegalStateException(
                    debugDepth == 0
                            ? "popDebugGroup with no debug group open"
                            : "popDebugGroup inside a pass, of a group begun outside it");
        }
        device.calls().debug().popGPUDebugGroup().call(handle);
        debugDepth--;
    }

    /// Marks this point of the command stream with `text`, for a frame capture.
    ///
    /// @throws IllegalStateException when the buffer is finished
    public void insertDebugLabel(String text) {
        requireRecordingOrInPass("insertDebugLabel");
        try (var arena = Arena.ofConfined()) {
            device.calls().debug().insertGPUDebugLabel().call(handle, arena.allocateFrom(text));
        }
    }

    /// How many debug groups are open.
    public int debugDepth() {
        return debugDepth;
    }

    /// Submits the commands.
    ///
    /// @throws IllegalStateException when a debug group is still open
    /// @throws SdlException          when SDL refuses
    public void submit() {
        requireState(State.RECORDING, "submit");
        requireNoDebugGroup("submit");
        state = State.FINISHED;
        if (!device.calls().commands().submitGPUCommandBuffer().call(handle)) {
            throw new SdlException("SDL_SubmitGPUCommandBuffer", Sdl.get().lastError());
        }
    }

    /// Submits the commands and returns a fence that signals when the GPU has
    /// finished them. The caller closes the fence.
    ///
    /// @throws IllegalStateException when a debug group is still open
    /// @throws SdlException          when SDL refuses
    public SdlGpuFence submitWithFence() {
        requireState(State.RECORDING, "submitWithFence");
        requireNoDebugGroup("submitWithFence");
        state = State.FINISHED;
        var fence = device.calls()
                .commands()
                .submitGPUCommandBufferAndAcquireFence()
                .call(handle);
        if (MemorySegment.NULL.equals(fence)) {
            throw new SdlException(
                    "SDL_SubmitGPUCommandBufferAndAcquireFence", Sdl.get().lastError());
        }
        return new SdlGpuFence(device, fence);
    }

    /// Discards the commands. An open copy pass must be closed first, as SDL
    /// requires.
    ///
    /// @throws SdlException when SDL refuses
    public void cancel() {
        requireState(State.RECORDING, "cancel");
        state = State.FINISHED;
        if (!device.calls().commands().cancelGPUCommandBuffer().call(handle)) {
            throw new SdlException("SDL_CancelGPUCommandBuffer", Sdl.get().lastError());
        }
    }

    /// Whether the buffer has been submitted or cancelled.
    public boolean isFinished() {
        return state == State.FINISHED;
    }

    private void requireState(State wanted, String operation) {
        device.handle();
        if (state != wanted) {
            throw new IllegalStateException(operation + " on a command buffer that is "
                    + switch (state) {
                        case RECORDING -> "recording";
                        case IN_COPY_PASS -> "in a copy pass";
                        case IN_RENDER_PASS -> "in a render pass";
                        case IN_COMPUTE_PASS -> "in a compute pass";
                        case FINISHED -> "already submitted or cancelled";
                    });
        }
    }

    /// The debug depth at which the open pass began; 0 outside a pass, so a
    /// group can be popped down to none.
    private int openPassDebugDepth;

    private void requireRecordingOrInPass(String operation) {
        device.handle();
        if (state == State.FINISHED) {
            throw new IllegalStateException(operation + " on a command buffer that is already submitted or cancelled");
        }
    }

    private void requireNoDebugGroup(String operation) {
        if (debugDepth != 0) {
            throw new IllegalStateException(
                    operation + " with " + debugDepth + " debug group" + (debugDepth == 1 ? "" : "s") + " open");
        }
    }

    private void requireOwn(SdlGpuResource resource) {
        if (resource.device() != device) {
            throw new IllegalArgumentException(resource + " belongs to another device");
        }
    }

    /// A render pass: the pipeline, viewport, scissor, sampled textures, vertex
    /// and index buffers, uniforms and draws recorded into one target. Closing
    /// it ends the pass, and the command buffer records again.
    public final class RenderPass implements AutoCloseable {

        private final MemorySegment pass;
        private final int width;
        private final int height;
        private final Optional<SdlGpuTextureFormat> targetFormat;
        private final boolean depthOnly;
        private final SdlGpuSampleCount samples;
        private final Optional<SdlGpuTextureFormat> depthFormat;
        private final int debugDepthAtBegin;
        private final BitSet boundVertexSlots = new BitSet();
        private @Nullable SdlGpuGraphicsPipeline pipeline;
        private int boundSamplers;
        private int boundVertexSamplers;
        private int boundVertexStorageBuffers;
        private int boundFragmentStorageBuffers;
        private int boundFragmentStorageTextures;
        private boolean indexBound;
        private boolean ended;

        private RenderPass(
                MemorySegment pass,
                int width,
                int height,
                Optional<SdlGpuTextureFormat> targetFormat,
                boolean depthOnly,
                SdlGpuSampleCount samples,
                Optional<SdlGpuTextureFormat> depthFormat) {
            this.pass = pass;
            this.width = width;
            this.height = height;
            this.targetFormat = targetFormat;
            this.depthOnly = depthOnly;
            this.samples = samples;
            this.depthFormat = depthFormat;
            this.debugDepthAtBegin = debugDepth;
            openPassDebugDepth = debugDepth;
        }

        /// The width of what the pass draws into, in pixels.
        public int width() {
            return width;
        }

        /// The height of what the pass draws into, in pixels.
        public int height() {
            return height;
        }

        /// Whether the pass has no colour target.
        public boolean isDepthOnly() {
            return depthOnly;
        }

        /// How many samples the pass's targets have.
        public SdlGpuSampleCount samples() {
            return samples;
        }

        /// Binds the pipeline the next draws use. Its samplers and vertex
        /// buffers are bound after it, and again after the next.
        ///
        /// @throws IllegalArgumentException when it is another device's, for
        ///                                  another format than the target's,
        ///                                  draws colour in a pass with no
        ///                                  colour target (or the reverse), or
        ///                                  tests depth this pass has no target
        ///                                  for (or the reverse)
        public void bindPipeline(SdlGpuGraphicsPipeline bound) {
            requireOpen();
            requireOwn(bound);
            if (depthOnly != bound.isDepthOnly()) {
                throw new IllegalArgumentException(
                        depthOnly
                                ? bound + " draws colour, and this pass has no colour target"
                                : bound + " writes depth alone, and this pass has a colour target");
            }
            if (targetFormat.isPresent()
                    && bound.targetFormat().isPresent()
                    && targetFormat.get() != bound.targetFormat().get()) {
                throw new IllegalArgumentException(
                        bound + " draws " + bound.targetFormat().get() + ", and the target is " + targetFormat.get());
            }
            if (bound.sampleCount() != samples) {
                throw new IllegalArgumentException(
                        bound + " draws " + bound.sampleCount().samples() + " samples, and this pass's targets have "
                                + samples.samples());
            }
            if (!bound.depthFormat().equals(depthFormat)) {
                throw new IllegalArgumentException(bound + " tests depth in "
                        + bound.depthFormat().map(Object::toString).orElse("nothing") + ", and this pass has "
                        + depthFormat
                                .map(format -> "a " + format + " depth target")
                                .orElse("no depth target"));
            }
            device.calls().renderPass().bindGPUGraphicsPipeline().call(pass, bound.handle());
            pipeline = bound;
            boundSamplers = 0;
            boundVertexSamplers = 0;
            boundVertexStorageBuffers = 0;
            boundFragmentStorageBuffers = 0;
            boundFragmentStorageTextures = 0;
            boundVertexSlots.clear();
        }

        /// Binds `textures`, each read with `sampler`, to the vertex shader's
        /// sampler slots from 0: every slot the bound pipeline's vertex shader
        /// declares, and no more.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException when the count is not the shader's,
        ///                                  or a texture cannot be sampled
        public void bindVertexSamplers(SdlGpuSampler sampler, SdlGpuTexture... textures) {
            bindVertexSamplers(bindings(sampler, textures));
        }

        /// Binds each texture with its own sampler to the vertex shader's
        /// sampler slots from 0, in order: every slot the bound pipeline's
        /// vertex shader declares, and no more.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException when the count is not the shader's,
        ///                                  or a texture cannot be sampled
        public void bindVertexSamplers(List<SdlGpuSamplerBinding> slots) {
            requireOpen();
            var bound = requirePipeline("bindVertexSamplers");
            var shader = bound.description().vertex();
            if (slots.size() != shader.samplers()) {
                throw new IllegalArgumentException(
                        shader + " samples " + shader.samplers() + " textures, not " + slots.size());
            }
            try (var arena = Arena.ofConfined()) {
                var bindings = samplerBindings(arena, slots);
                device.calls().renderPass().bindGPUVertexSamplers().call(pass, 0, bindings, slots.size());
            }
            boundVertexSamplers = slots.size();
        }

        /// Binds `buffers` to the vertex shader's storage-buffer slots from 0:
        /// every slot it declares, and no more.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException when the count is not the shader's,
        ///                                  or a buffer was not made to be read
        ///                                  as storage by a draw
        public void bindVertexStorageBuffers(SdlGpuBuffer... buffers) {
            requireOpen();
            var bound = requirePipeline("bindVertexStorageBuffers");
            var shader = bound.description().vertex();
            if (buffers.length != shader.storageBuffers()) {
                throw new IllegalArgumentException(
                        shader + " reads " + shader.storageBuffers() + " storage buffers, not " + buffers.length);
            }
            try (var arena = Arena.ofConfined()) {
                var handles = storageBuffers(arena, SdlGpuBufferUsage.GRAPHICS_STORAGE_READ, buffers);
                device.calls().renderPass().bindGPUVertexStorageBuffers().call(pass, 0, handles, buffers.length);
            }
            boundVertexStorageBuffers = buffers.length;
        }

        /// Binds `buffers` to the fragment shader's storage-buffer slots from
        /// 0: every slot it declares, and no more.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException as [#bindVertexStorageBuffers]
        public void bindFragmentStorageBuffers(SdlGpuBuffer... buffers) {
            requireOpen();
            var bound = requirePipeline("bindFragmentStorageBuffers");
            var shader = bound.description().fragment();
            if (buffers.length != shader.storageBuffers()) {
                throw new IllegalArgumentException(
                        shader + " reads " + shader.storageBuffers() + " storage buffers, not " + buffers.length);
            }
            try (var arena = Arena.ofConfined()) {
                var handles = storageBuffers(arena, SdlGpuBufferUsage.GRAPHICS_STORAGE_READ, buffers);
                device.calls().renderPass().bindGPUFragmentStorageBuffers().call(pass, 0, handles, buffers.length);
            }
            boundFragmentStorageBuffers = buffers.length;
        }

        /// Binds `textures` to the fragment shader's storage-texture slots from
        /// 0: every slot it declares, and no more. Read texel by texel, with no
        /// sampler.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException when the count is not the shader's,
        ///                                  or a texture was not made to be read
        ///                                  as storage by a draw
        public void bindFragmentStorageTextures(SdlGpuTexture... textures) {
            requireOpen();
            var bound = requirePipeline("bindFragmentStorageTextures");
            var shader = bound.description().fragment();
            if (textures.length != shader.storageTextures()) {
                throw new IllegalArgumentException(
                        shader + " reads " + shader.storageTextures() + " storage textures, not " + textures.length);
            }
            try (var arena = Arena.ofConfined()) {
                var handles = storageTextures(arena, SdlGpuTextureUsage.GRAPHICS_STORAGE_READ, textures);
                device.calls().renderPass().bindGPUFragmentStorageTextures().call(pass, 0, handles, textures.length);
            }
            boundFragmentStorageTextures = textures.length;
        }

        /// Maps clip space onto `x, y, width, height` of the target, in pixels.
        public void setViewport(float x, float y, float width, float height) {
            requireOpen();
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("viewport " + width + "x" + height);
            }
            var layout = Layouts.SDL_GPU_VIEWPORT;
            try (var arena = Arena.ofConfined()) {
                var viewport = arena.allocate(layout.layout());
                viewport.set(JAVA_FLOAT, layout.offsetOf("x"), x);
                viewport.set(JAVA_FLOAT, layout.offsetOf("y"), y);
                viewport.set(JAVA_FLOAT, layout.offsetOf("w"), width);
                viewport.set(JAVA_FLOAT, layout.offsetOf("h"), height);
                viewport.set(JAVA_FLOAT, layout.offsetOf("min_depth"), 0f);
                viewport.set(JAVA_FLOAT, layout.offsetOf("max_depth"), 1f);
                device.calls().renderPass().setGPUViewport().call(pass, viewport);
            }
        }

        /// Draws nothing outside `region` of the target: the clip rectangle of a GPU
        /// layer, which is drawn scissored to its clip in paint order.
        ///
        /// @throws IllegalArgumentException when it lies outside the target
        public void setScissor(SdlGpuRegion region) {
            requireOpen();
            if (!region.fitsIn(width, height)) {
                throw new IllegalArgumentException(region + " is outside the " + width + "x" + height + " target");
            }
            var layout = Layouts.SDL_RECT;
            try (var arena = Arena.ofConfined()) {
                var rect = arena.allocate(layout.layout());
                rect.set(JAVA_INT, layout.offsetOf("x"), region.x());
                rect.set(JAVA_INT, layout.offsetOf("y"), region.y());
                rect.set(JAVA_INT, layout.offsetOf("w"), region.width());
                rect.set(JAVA_INT, layout.offsetOf("h"), region.height());
                device.calls().renderPass().setGPUScissor().call(pass, rect);
            }
        }

        /// Binds `textures`, each read with `sampler`, to the fragment shader's
        /// slots from 0: every slot the bound pipeline declares, and no more.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException when the count is not the pipeline's,
        ///                                  or a texture cannot be sampled
        public void bindFragmentSamplers(SdlGpuSampler sampler, SdlGpuTexture... textures) {
            bindFragmentSamplers(bindings(sampler, textures));
        }

        /// Binds each texture with its own sampler to the fragment shader's
        /// slots from 0, in order: every slot the bound pipeline declares, and
        /// no more.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException when the count is not the pipeline's,
        ///                                  or a texture cannot be sampled
        public void bindFragmentSamplers(List<SdlGpuSamplerBinding> slots) {
            requireOpen();
            var bound = requirePipeline("bindFragmentSamplers");
            if (slots.size() != bound.samplers()) {
                throw new IllegalArgumentException(
                        bound + " samples " + bound.samplers() + " textures, not " + slots.size());
            }
            try (var arena = Arena.ofConfined()) {
                var bindings = samplerBindings(arena, slots);
                device.calls().renderPass().bindGPUFragmentSamplers().call(pass, 0, bindings, slots.size());
            }
            boundSamplers = slots.size();
        }

        /// Binds `buffer`, from byte `offset`, to vertex slot `slot` of the bound
        /// pipeline.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException when the pipeline reads no such slot,
        ///                                  the buffer is not a vertex buffer of
        ///                                  this device, or the offset is outside it
        public void bindVertexBuffer(int slot, SdlGpuBuffer buffer, int offset) {
            requireOpen();
            var bound = requirePipeline("bindVertexBuffer");
            requireOwn(buffer);
            if (bound.description().vertexBuffers().stream().noneMatch(declared -> declared.slot() == slot)) {
                throw new IllegalArgumentException(bound + " reads no vertex buffer in slot " + slot);
            }
            if (!buffer.usages().contains(SdlGpuBufferUsage.VERTEX)) {
                throw new IllegalArgumentException(buffer + " is not a vertex buffer");
            }
            if (offset < 0 || offset >= buffer.size()) {
                throw new IllegalArgumentException("offset " + offset + " is outside " + buffer);
            }
            try (var arena = Arena.ofConfined()) {
                device.calls().buffers().bindGPUVertexBuffers().call(pass, slot, binding(arena, buffer, offset), 1);
            }
            boundVertexSlots.set(slot);
        }

        /// Binds `buffer`, from byte `offset`, as the index buffer of the
        /// indexed draws that follow, each index `size` wide.
        ///
        /// @throws IllegalArgumentException when the buffer is not an index
        ///                                  buffer of this device, or the offset
        ///                                  is outside it or not a multiple of
        ///                                  the index size
        public void bindIndexBuffer(SdlGpuBuffer buffer, int offset, SdlGpuIndexSize size) {
            requireOpen();
            requireOwn(buffer);
            if (!buffer.usages().contains(SdlGpuBufferUsage.INDEX)) {
                throw new IllegalArgumentException(buffer + " is not an index buffer");
            }
            if (offset < 0 || offset >= buffer.size() || offset % size.bytes() != 0) {
                throw new IllegalArgumentException("offset " + offset + " of " + size + " indices in " + buffer);
            }
            try (var arena = Arena.ofConfined()) {
                device.calls().buffers().bindGPUIndexBuffer().call(pass, binding(arena, buffer, offset), size.value());
            }
            indexBound = true;
        }

        private static MemorySegment binding(Arena arena, SdlGpuBuffer buffer, int offset) {
            var layout = Layouts.SDL_GPU_BUFFER_BINDING;
            var binding = arena.allocate(layout.layout());
            binding.set(ADDRESS, layout.offsetOf("buffer"), buffer.handle());
            binding.set(JAVA_INT, layout.offsetOf("offset"), offset);
            return binding;
        }

        /// Sets the vertex shader's uniform block `slot` for the draws that
        /// follow. SDL copies the values; their layout is the shader's.
        public void pushVertexUniforms(int slot, float... values) {
            requireOpen();
            push(slot, floats(values), true);
        }

        /// Sets the fragment shader's uniform block `slot` for the draws that
        /// follow. SDL copies the values; their layout is the shader's.
        public void pushFragmentUniforms(int slot, float... values) {
            requireOpen();
            push(slot, floats(values), false);
        }

        /// Sets the vertex shader's uniform block `slot` to the remaining bytes
        /// of `data`, which is not moved. SDL copies them.
        public void pushVertexUniforms(int slot, ByteBuffer data) {
            requireOpen();
            push(slot, data, true);
        }

        /// Sets the fragment shader's uniform block `slot` to the remaining bytes
        /// of `data`, which is not moved. SDL copies them.
        public void pushFragmentUniforms(int slot, ByteBuffer data) {
            requireOpen();
            push(slot, data, false);
        }

        private static ByteBuffer floats(float[] values) {
            var bytes = ByteBuffer.allocate(values.length * Float.BYTES).order(java.nio.ByteOrder.nativeOrder());
            bytes.asFloatBuffer().put(values);
            return bytes;
        }

        private void push(int slot, ByteBuffer data, boolean vertex) {
            var length = data.remaining();
            if (slot < 0 || length == 0) {
                throw new IllegalArgumentException("uniform slot " + slot + " with " + length + " bytes");
            }
            var bound = pipeline;
            if (bound != null) {
                var shader = vertex
                        ? bound.description().vertex()
                        : bound.description().fragment();
                if (slot >= shader.uniformBuffers()) {
                    throw new IllegalArgumentException(
                            shader + " reads " + shader.uniformBuffers() + " uniform blocks, not slot " + slot);
                }
            }
            try (var arena = Arena.ofConfined()) {
                var copy = arena.allocate(length);
                copy.copyFrom(MemorySegment.ofBuffer(data));
                var calls = device.calls().renderPass();
                if (vertex) {
                    calls.pushGPUVertexUniformData().call(handle, slot, copy, length);
                } else {
                    calls.pushGPUFragmentUniformData().call(handle, slot, copy, length);
                }
            }
        }

        /// Draws `vertices` vertices, made by the vertex shader from their ids
        /// or read from the bound vertex buffers.
        ///
        /// @throws IllegalStateException when no pipeline is bound, or its
        ///                               samplers or vertex buffers are not
        public void draw(int vertices) {
            draw(vertices, 1, 0, 0);
        }

        /// Draws `instances` instances of `vertices` vertices, from vertex
        /// `firstVertex` and instance `firstInstance`.
        ///
        /// @throws IllegalStateException when no pipeline is bound, or its
        ///                               samplers or vertex buffers are not
        public void draw(int vertices, int instances, int firstVertex, int firstInstance) {
            requireOpen();
            requireDrawable("draw");
            if (vertices <= 0 || instances <= 0 || firstVertex < 0 || firstInstance < 0) {
                throw new IllegalArgumentException("draw of " + vertices + " vertices, " + instances
                        + " instances from " + firstVertex + ", " + firstInstance);
            }
            device.calls().renderPass().drawGPUPrimitives().call(pass, vertices, instances, firstVertex, firstInstance);
        }

        /// Draws `instances` instances of `indices` indices from the bound index
        /// buffer, from index `firstIndex`, with `vertexOffset` added to each
        /// index, from instance `firstInstance`.
        ///
        /// @throws IllegalStateException when no pipeline or index buffer is
        ///                               bound, or the pipeline's samplers or
        ///                               vertex buffers are not
        public void drawIndexed(int indices, int instances, int firstIndex, int vertexOffset, int firstInstance) {
            requireOpen();
            requireDrawable("drawIndexed");
            if (!indexBound) {
                throw new IllegalStateException("drawIndexed with no index buffer bound");
            }
            if (indices <= 0 || instances <= 0 || firstIndex < 0 || firstInstance < 0) {
                throw new IllegalArgumentException("indexed draw of " + indices + " indices, " + instances
                        + " instances from " + firstIndex + ", " + firstInstance);
            }
            device.calls()
                    .buffers()
                    .drawGPUIndexedPrimitives()
                    .call(pass, indices, instances, firstIndex, vertexOffset, firstInstance);
        }

        private void requireDrawable(String operation) {
            var bound = requirePipeline(operation);
            if (boundSamplers != bound.samplers()) {
                throw new IllegalStateException(
                        bound + " samples " + bound.samplers() + " textures, and " + boundSamplers + " are bound");
            }
            var vertex = bound.description().vertex();
            var fragment = bound.description().fragment();
            if (boundVertexSamplers != vertex.samplers()) {
                throw new IllegalStateException(vertex + " samples " + vertex.samplers() + " textures, and "
                        + boundVertexSamplers + " are bound");
            }
            if (boundVertexStorageBuffers != vertex.storageBuffers()) {
                throw new IllegalStateException(vertex + " reads " + vertex.storageBuffers() + " storage buffers, and "
                        + boundVertexStorageBuffers + " are bound");
            }
            if (boundFragmentStorageBuffers != fragment.storageBuffers()) {
                throw new IllegalStateException(fragment + " reads " + fragment.storageBuffers()
                        + " storage buffers, and " + boundFragmentStorageBuffers + " are bound");
            }
            if (boundFragmentStorageTextures != fragment.storageTextures()) {
                throw new IllegalStateException(fragment + " reads " + fragment.storageTextures()
                        + " storage textures, and " + boundFragmentStorageTextures + " are bound");
            }
            for (var declared : bound.description().vertexBuffers()) {
                if (!boundVertexSlots.get(declared.slot())) {
                    throw new IllegalStateException(
                            bound + " reads vertex slot " + declared.slot() + ", and no buffer is bound to it");
                }
            }
        }

        /// Ends the pass. Idempotent.
        ///
        /// @throws IllegalStateException when a debug group begun in the pass is
        ///                               still open. The pass has ended anyway
        @Override
        public void close() {
            if (ended) {
                return;
            }
            ended = true;
            var leaked = closeLeakedGroups(debugDepthAtBegin);
            device.calls().commands().endGPURenderPass().call(pass);
            state = State.RECORDING;
            failIfLeaked(leaked);
        }

        private SdlGpuGraphicsPipeline requirePipeline(String operation) {
            var bound = pipeline;
            if (bound == null) {
                throw new IllegalStateException(operation + " with no pipeline bound");
            }
            return bound;
        }

        private void requireOpen() {
            if (ended) {
                throw new IllegalStateException("the render pass has ended");
            }
            device.handle();
        }
    }

    /// Begins a compute pass that writes `writtenBuffers` and
    /// `writtenTextures` (one level of one layer each), which must have been
    /// made for compute to write. The pass's pipeline declares as many of each.
    /// Nothing else is recorded until the pass is closed.
    ///
    /// @throws IllegalArgumentException when a buffer or texture is another
    ///                                  device's or was not made to be written
    ///                                  by compute
    /// @throws SdlException             when SDL cannot begin the pass
    public ComputePass beginComputePass(List<SdlGpuBuffer> writtenBuffers, List<SdlGpuTextureView> writtenTextures) {
        requireState(State.RECORDING, "beginComputePass");
        for (var buffer : writtenBuffers) {
            requireOwn(buffer);
            if (!buffer.usages().contains(SdlGpuBufferUsage.COMPUTE_STORAGE_WRITE)) {
                throw new IllegalArgumentException(buffer + " was not made to be written by compute");
            }
        }
        for (var view : writtenTextures) {
            requireOwn(view.texture());
            if (!view.texture().usages().contains(SdlGpuTextureUsage.COMPUTE_STORAGE_WRITE)) {
                throw new IllegalArgumentException(view.texture() + " was not made to be written by compute");
            }
        }
        var bufferLayout = Layouts.SDL_GPU_STORAGE_BUFFER_READ_WRITE_BINDING;
        var textureLayout = Layouts.SDL_GPU_STORAGE_TEXTURE_READ_WRITE_BINDING;
        try (var arena = Arena.ofConfined()) {
            var buffers = writtenBuffers.isEmpty()
                    ? MemorySegment.NULL
                    : arena.allocate(bufferLayout.layout(), writtenBuffers.size());
            for (var i = 0; i < writtenBuffers.size(); i++) {
                var at = i * bufferLayout.byteSize();
                buffers.set(
                        ADDRESS,
                        at + bufferLayout.offsetOf("buffer"),
                        writtenBuffers.get(i).handle());
                buffers.set(JAVA_BOOLEAN, at + bufferLayout.offsetOf("cycle"), false);
            }
            var textures = writtenTextures.isEmpty()
                    ? MemorySegment.NULL
                    : arena.allocate(textureLayout.layout(), writtenTextures.size());
            for (var i = 0; i < writtenTextures.size(); i++) {
                var view = writtenTextures.get(i);
                var at = i * textureLayout.byteSize();
                textures.set(
                        ADDRESS,
                        at + textureLayout.offsetOf("texture"),
                        view.texture().handle());
                textures.set(JAVA_INT, at + textureLayout.offsetOf("mip_level"), view.level());
                textures.set(JAVA_INT, at + textureLayout.offsetOf("layer"), view.layer());
                textures.set(JAVA_BOOLEAN, at + textureLayout.offsetOf("cycle"), false);
            }
            var pass = device.calls()
                    .compute()
                    .beginGPUComputePass()
                    .call(handle, textures, writtenTextures.size(), buffers, writtenBuffers.size());
            if (MemorySegment.NULL.equals(pass)) {
                throw new SdlException("SDL_BeginGPUComputePass", Sdl.get().lastError());
            }
            state = State.IN_COMPUTE_PASS;
            return new ComputePass(pass, writtenBuffers.size(), writtenTextures.size());
        }
    }

    /// A compute pass: the pipeline, the storage it reads, the uniforms pushed
    /// and the dispatches recorded over the storage it was begun to write.
    /// Closing it ends the pass, and the command buffer records again.
    public final class ComputePass implements AutoCloseable {

        private final MemorySegment pass;
        private final int writtenBuffers;
        private final int writtenTextures;
        private final int debugDepthAtBegin;
        private @Nullable SdlGpuComputePipeline pipeline;
        private int boundStorageBuffers;
        private int boundStorageTextures;
        private boolean ended;

        private ComputePass(MemorySegment pass, int writtenBuffers, int writtenTextures) {
            this.pass = pass;
            this.writtenBuffers = writtenBuffers;
            this.writtenTextures = writtenTextures;
            this.debugDepthAtBegin = debugDepth;
            openPassDebugDepth = debugDepth;
        }

        /// Binds the pipeline the next dispatches run. Its storage is bound
        /// after it, and again after the next.
        ///
        /// @throws IllegalArgumentException when it is another device's, or
        ///                                  writes another number of buffers or
        ///                                  textures than the pass was begun
        ///                                  with
        public void bindPipeline(SdlGpuComputePipeline bound) {
            requireOpen();
            requireOwn(bound);
            if (bound.readWriteStorageBuffers() != writtenBuffers
                    || bound.readWriteStorageTextures() != writtenTextures) {
                throw new IllegalArgumentException(bound + " writes " + bound.readWriteStorageBuffers()
                        + " buffers and " + bound.readWriteStorageTextures() + " textures, and the pass was begun with "
                        + writtenBuffers + " and " + writtenTextures);
            }
            device.calls().compute().bindGPUComputePipeline().call(pass, bound.handle());
            pipeline = bound;
            boundStorageBuffers = 0;
            boundStorageTextures = 0;
        }

        /// Binds `buffers` to the shader's read-only storage slots from 0:
        /// every slot it declares, and no more.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException when the count is not the shader's,
        ///                                  or a buffer was not made to be read
        ///                                  by compute
        public void bindStorageBuffers(SdlGpuBuffer... buffers) {
            requireOpen();
            var bound = requirePipeline("bindStorageBuffers");
            if (buffers.length != bound.readOnlyStorageBuffers()) {
                throw new IllegalArgumentException(
                        bound + " reads " + bound.readOnlyStorageBuffers() + " storage buffers, not " + buffers.length);
            }
            try (var arena = Arena.ofConfined()) {
                var handles = storageBuffers(arena, SdlGpuBufferUsage.COMPUTE_STORAGE_READ, buffers);
                device.calls().compute().bindGPUComputeStorageBuffers().call(pass, 0, handles, buffers.length);
            }
            boundStorageBuffers = buffers.length;
        }

        /// Binds `textures` to the shader's read-only storage slots from 0:
        /// every slot it declares, and no more.
        ///
        /// @throws IllegalStateException    when no pipeline is bound
        /// @throws IllegalArgumentException when the count is not the shader's,
        ///                                  or a texture was not made to be read
        ///                                  by compute
        public void bindStorageTextures(SdlGpuTexture... textures) {
            requireOpen();
            var bound = requirePipeline("bindStorageTextures");
            if (textures.length != bound.readOnlyStorageTextures()) {
                throw new IllegalArgumentException(bound + " reads " + bound.readOnlyStorageTextures()
                        + " storage textures, not " + textures.length);
            }
            try (var arena = Arena.ofConfined()) {
                var handles = storageTextures(arena, SdlGpuTextureUsage.COMPUTE_STORAGE_READ, textures);
                device.calls().compute().bindGPUComputeStorageTextures().call(pass, 0, handles, textures.length);
            }
            boundStorageTextures = textures.length;
        }

        /// Sets the shader's uniform block `slot` to `values`, for the
        /// dispatches that follow.
        ///
        /// @throws IllegalArgumentException when the bound pipeline reads no such
        ///                                  block, or there are no values
        public void pushUniforms(int slot, float... values) {
            pushUniforms(slot, floatBytes(values));
        }

        /// Sets the shader's uniform block `slot` to the remaining bytes of
        /// `data`, which is not moved. SDL copies them.
        public void pushUniforms(int slot, ByteBuffer data) {
            requireOpen();
            var length = data.remaining();
            if (slot < 0 || length == 0) {
                throw new IllegalArgumentException("uniform slot " + slot + " with " + length + " bytes");
            }
            var bound = pipeline;
            if (bound != null && slot >= bound.uniformBuffers()) {
                throw new IllegalArgumentException(
                        bound + " reads " + bound.uniformBuffers() + " uniform blocks, not slot " + slot);
            }
            try (var arena = Arena.ofConfined()) {
                var copy = arena.allocate(length);
                copy.copyFrom(MemorySegment.ofBuffer(data));
                device.calls().compute().pushGPUComputeUniformData().call(handle, slot, copy, length);
            }
        }

        /// Runs the bound pipeline over `x` by `y` by `z` workgroups.
        ///
        /// @throws IllegalStateException    when no pipeline is bound, or its
        ///                                  read-only storage is not
        /// @throws IllegalArgumentException when a count is below one
        public void dispatch(int x, int y, int z) {
            requireOpen();
            var bound = requirePipeline("dispatch");
            if (boundStorageBuffers != bound.readOnlyStorageBuffers()) {
                throw new IllegalStateException(bound + " reads " + bound.readOnlyStorageBuffers()
                        + " storage buffers, and " + boundStorageBuffers + " are bound");
            }
            if (boundStorageTextures != bound.readOnlyStorageTextures()) {
                throw new IllegalStateException(bound + " reads " + bound.readOnlyStorageTextures()
                        + " storage textures, and " + boundStorageTextures + " are bound");
            }
            if (x <= 0 || y <= 0 || z <= 0) {
                throw new IllegalArgumentException("dispatch of " + x + "x" + y + "x" + z + " workgroups");
            }
            device.calls().compute().dispatchGPUCompute().call(pass, x, y, z);
        }

        /// Ends the pass. Idempotent.
        ///
        /// @throws IllegalStateException when a debug group begun in the pass is
        ///                               still open. The pass has ended anyway
        @Override
        public void close() {
            if (ended) {
                return;
            }
            ended = true;
            var leaked = closeLeakedGroups(debugDepthAtBegin);
            device.calls().compute().endGPUComputePass().call(pass);
            state = State.RECORDING;
            failIfLeaked(leaked);
        }

        private SdlGpuComputePipeline requirePipeline(String operation) {
            var bound = pipeline;
            if (bound == null) {
                throw new IllegalStateException(operation + " with no pipeline bound");
            }
            return bound;
        }

        private void requireOpen() {
            if (ended) {
                throw new IllegalStateException("the compute pass has ended");
            }
            device.handle();
        }
    }

    /// `values` as native-order bytes.
    private static ByteBuffer floatBytes(float[] values) {
        var bytes = ByteBuffer.allocate(values.length * Float.BYTES).order(java.nio.ByteOrder.nativeOrder());
        bytes.asFloatBuffer().put(values);
        return bytes;
    }

    /// An `SDL_GPUTextureSamplerBinding` array of `textures`, each with
    /// `sampler`, checked sampleable and this device's.
    /// One slot per texture, each read with `sampler`.
    private static List<SdlGpuSamplerBinding> bindings(SdlGpuSampler sampler, SdlGpuTexture[] textures) {
        return Arrays.stream(textures)
                .map(texture -> new SdlGpuSamplerBinding(texture, sampler))
                .toList();
    }

    /// `slots` as SDL's array of `SDL_GPUTextureSamplerBinding`, each texture
    /// and sampler checked for this device's and the texture for sampling.
    private MemorySegment samplerBindings(Arena arena, List<SdlGpuSamplerBinding> slots) {
        var layout = Layouts.SDL_GPU_TEXTURE_SAMPLER_BINDING;
        var bindings = arena.allocate(layout.layout(), Math.max(1, slots.size()));
        for (var i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            var texture = slot.texture();
            requireOwn(texture);
            requireOwn(slot.sampler());
            if (!texture.usages().contains(SdlGpuTextureUsage.SAMPLER)) {
                throw new IllegalArgumentException(texture + " cannot be sampled");
            }
            var at = i * layout.byteSize();
            bindings.set(ADDRESS, at + layout.offsetOf("texture"), texture.handle());
            bindings.set(
                    ADDRESS, at + layout.offsetOf("sampler"), slot.sampler().handle());
        }
        return bindings;
    }

    /// The handles of `buffers`, each checked for `usage` and this device's.
    private MemorySegment storageBuffers(Arena arena, SdlGpuBufferUsage usage, SdlGpuBuffer[] buffers) {
        var handles = arena.allocate(ADDRESS, Math.max(1, buffers.length));
        for (var i = 0; i < buffers.length; i++) {
            requireOwn(buffers[i]);
            if (!buffers[i].usages().contains(usage)) {
                throw new IllegalArgumentException(buffers[i] + " was not made for " + usage);
            }
            handles.setAtIndex(ADDRESS, i, buffers[i].handle());
        }
        return handles;
    }

    /// The handles of `textures`, each checked for `usage` and this device's.
    private MemorySegment storageTextures(Arena arena, SdlGpuTextureUsage usage, SdlGpuTexture[] textures) {
        var handles = arena.allocate(ADDRESS, Math.max(1, textures.length));
        for (var i = 0; i < textures.length; i++) {
            requireOwn(textures[i]);
            if (!textures[i].usages().contains(usage)) {
                throw new IllegalArgumentException(textures[i] + " was not made for " + usage);
            }
            handles.setAtIndex(ADDRESS, i, textures[i].handle());
        }
        return handles;
    }

    /// Ends the debug groups begun in the pass now ending and still open, inside
    /// it, where Metal scoped them; returns how many there were.
    private int closeLeakedGroups(int depthAtBegin) {
        openPassDebugDepth = 0;
        var leaked = debugDepth - depthAtBegin;
        for (var i = 0; i < leaked; i++) {
            device.calls().debug().popGPUDebugGroup().call(handle);
        }
        debugDepth -= Math.max(leaked, 0);
        return leaked;
    }

    private static void failIfLeaked(int leaked) {
        if (leaked > 0) {
            throw new IllegalStateException(
                    leaked + " debug group" + (leaked == 1 ? "" : "s") + " begun in a pass left open at its end");
        }
    }

    /// A copy pass: uploads into textures and downloads out of them. Closing it
    /// ends the pass, and the command buffer records again.
    public final class CopyPass implements AutoCloseable {

        private final MemorySegment pass;
        private final int debugDepthAtBegin;
        private boolean ended;

        private CopyPass(MemorySegment pass) {
            this.pass = pass;
            this.debugDepthAtBegin = debugDepth;
            openPassDebugDepth = debugDepth;
        }

        /// Records a copy of `region`'s pixels, packed row after row from byte
        /// `offset` of `source`, into level 0 of layer 0 of `destination`.
        ///
        /// @param cycle take fresh texture memory if the GPU still reads the old,
        ///              rather than wait for it
        public void upload(
                SdlGpuTransferBuffer source,
                int offset,
                SdlGpuTexture destination,
                SdlGpuRegion region,
                boolean cycle) {
            upload(source, offset, destination, 0, 0, region, cycle);
        }

        /// Records a copy of `region`'s pixels, packed row after row from byte
        /// `offset` of `source`, into mip level `level` of layer `layer` of
        /// `destination`: of depth slice `layer`, for a 3D texture. The region
        /// is in the level's texels. For a block-compressed format the rows are
        /// rows of blocks, and the region starts on a block and ends on one or
        /// at the level's edge.
        ///
        /// @param cycle take fresh texture memory if the GPU still reads the old,
        ///              rather than wait for it
        /// @throws IllegalArgumentException when the level or layer does not
        ///                                  exist, or the region is outside the
        ///                                  level or not on its blocks
        public void upload(
                SdlGpuTransferBuffer source,
                int offset,
                SdlGpuTexture destination,
                int level,
                int layer,
                SdlGpuRegion region,
                boolean cycle) {
            requireOpen();
            check(source, SdlGpuTransferUsage.UPLOAD, offset, destination, level, layer, region);
            try (var arena = Arena.ofConfined()) {
                device.calls()
                        .commands()
                        .uploadToGPUTexture()
                        .call(
                                pass,
                                transferInfo(arena, source, offset, destination, region),
                                region(arena, destination, level, layer, region),
                                cycle);
            }
        }

        /// Records a copy of `region` of level 0 of layer 0 of `source` into
        /// `destination` from byte `offset`, packed row after row. The bytes
        /// are there once the command buffer's fence has signalled.
        public void download(SdlGpuTexture source, SdlGpuRegion region, SdlGpuTransferBuffer destination, int offset) {
            download(source, 0, 0, region, destination, offset);
        }

        /// Records a copy of `region` of mip level `level` of layer `layer` of
        /// `source` into `destination` from byte `offset`, packed row after
        /// row. The region is in the level's texels. The bytes are there once
        /// the command buffer's fence has signalled.
        ///
        /// @throws IllegalArgumentException when the level or layer does not
        ///                                  exist, or the region is outside the
        ///                                  level
        public void download(
                SdlGpuTexture source,
                int level,
                int layer,
                SdlGpuRegion region,
                SdlGpuTransferBuffer destination,
                int offset) {
            requireOpen();
            check(destination, SdlGpuTransferUsage.DOWNLOAD, offset, source, level, layer, region);
            try (var arena = Arena.ofConfined()) {
                device.calls()
                        .commands()
                        .downloadFromGPUTexture()
                        .call(
                                pass,
                                region(arena, source, level, layer, region),
                                transferInfo(arena, destination, offset, source, region));
            }
        }

        /// Records a copy of `size` bytes from byte `sourceOffset` of `source`
        /// into `destination` from byte `destinationOffset`.
        ///
        /// @param cycle take fresh buffer memory if the GPU still reads the old,
        ///              rather than wait for it
        public void uploadToBuffer(
                SdlGpuTransferBuffer source,
                int sourceOffset,
                SdlGpuBuffer destination,
                int destinationOffset,
                int size,
                boolean cycle) {
            requireOpen();
            checkBufferCopy(source, SdlGpuTransferUsage.UPLOAD, sourceOffset, destination, destinationOffset, size);
            try (var arena = Arena.ofConfined()) {
                device.calls()
                        .buffers()
                        .uploadToGPUBuffer()
                        .call(
                                pass,
                                location(arena, source, sourceOffset),
                                bufferRegion(arena, destination, destinationOffset, size),
                                cycle);
            }
        }

        /// Records a copy of `size` bytes from byte `sourceOffset` of `source`
        /// into `destination` from byte `destinationOffset`. The bytes are there
        /// once the command buffer's fence has signalled.
        public void downloadFromBuffer(
                SdlGpuBuffer source,
                int sourceOffset,
                int size,
                SdlGpuTransferBuffer destination,
                int destinationOffset) {
            requireOpen();
            checkBufferCopy(destination, SdlGpuTransferUsage.DOWNLOAD, destinationOffset, source, sourceOffset, size);
            try (var arena = Arena.ofConfined()) {
                device.calls()
                        .buffers()
                        .downloadFromGPUBuffer()
                        .call(
                                pass,
                                bufferRegion(arena, source, sourceOffset, size),
                                location(arena, destination, destinationOffset));
            }
        }

        private void checkBufferCopy(
                SdlGpuTransferBuffer transfer,
                SdlGpuTransferUsage usage,
                int transferOffset,
                SdlGpuBuffer buffer,
                int bufferOffset,
                int size) {
            requireOwn(transfer);
            requireOwn(buffer);
            if (transfer.usage() != usage) {
                throw new IllegalArgumentException(transfer + " is not an " + usage + " buffer");
            }
            if (transfer.isMapped()) {
                throw new IllegalStateException(transfer + " is mapped; SDL copies only unmapped buffers");
            }
            if (size <= 0) {
                throw new IllegalArgumentException("copy of " + size + " bytes");
            }
            if (!buffer.fits(bufferOffset, size)) {
                throw new IllegalArgumentException(
                        size + " bytes from offset " + bufferOffset + " do not fit " + buffer);
            }
            if (transferOffset < 0 || (long) transferOffset + size > transfer.size()) {
                throw new IllegalArgumentException(
                        size + " bytes from offset " + transferOffset + " do not fit " + transfer);
            }
        }

        private static MemorySegment location(Arena arena, SdlGpuTransferBuffer buffer, int offset) {
            var info = Layouts.SDL_GPU_TRANSFER_BUFFER_LOCATION;
            var segment = arena.allocate(info.layout());
            segment.set(ADDRESS, info.offsetOf("transfer_buffer"), buffer.handle());
            segment.set(JAVA_INT, info.offsetOf("offset"), offset);
            return segment;
        }

        private static MemorySegment bufferRegion(Arena arena, SdlGpuBuffer buffer, int offset, int size) {
            var info = Layouts.SDL_GPU_BUFFER_REGION;
            var segment = arena.allocate(info.layout());
            segment.set(ADDRESS, info.offsetOf("buffer"), buffer.handle());
            segment.set(JAVA_INT, info.offsetOf("offset"), offset);
            segment.set(JAVA_INT, info.offsetOf("size"), size);
            return segment;
        }

        /// Ends the pass. Idempotent.
        ///
        /// @throws IllegalStateException when a debug group begun in the pass is
        ///                               still open. The pass has ended anyway
        @Override
        public void close() {
            if (ended) {
                return;
            }
            ended = true;
            var leaked = closeLeakedGroups(debugDepthAtBegin);
            device.calls().commands().endGPUCopyPass().call(pass);
            state = State.RECORDING;
            failIfLeaked(leaked);
        }

        private void requireOpen() {
            if (ended) {
                throw new IllegalStateException("the copy pass has ended");
            }
            device.handle();
        }

        private void check(
                SdlGpuTransferBuffer buffer,
                SdlGpuTransferUsage usage,
                int offset,
                SdlGpuTexture texture,
                int level,
                int layer,
                SdlGpuRegion region) {
            requireOwn(buffer);
            requireOwn(texture);
            if (buffer.usage() != usage) {
                throw new IllegalArgumentException(buffer + " is not an " + usage + " buffer");
            }
            if (buffer.isMapped()) {
                throw new IllegalStateException(buffer + " is mapped; SDL copies only unmapped buffers");
            }
            texture.requireSubresource(level, layer);
            var levelWidth = texture.levelWidth(level);
            var levelHeight = texture.levelHeight(level);
            if (!region.fitsIn(levelWidth, levelHeight)) {
                throw new IllegalArgumentException(region + " is outside level " + level + " of " + texture);
            }
            var format = texture.format();
            if (!onBlocks(region.x(), region.width(), format.blockWidth(), levelWidth)
                    || !onBlocks(region.y(), region.height(), format.blockHeight(), levelHeight)) {
                throw new IllegalArgumentException(region + " of level " + level + " of " + texture
                        + " does not start and end on its " + format.blockWidth() + "x" + format.blockHeight()
                        + " blocks");
            }
            if (offset < 0 || offset + texture.byteSize(region) > buffer.size()) {
                throw new IllegalArgumentException(
                        texture.byteSize(region) + " bytes from offset " + offset + " do not fit " + buffer);
            }
        }

        /// Whether a span from `start`, `length` long, starts on a block and
        /// ends on one or at the edge `extent` away.
        private static boolean onBlocks(int start, int length, int block, int extent) {
            return start % block == 0 && (length % block == 0 || start + length == extent);
        }

        private static MemorySegment transferInfo(
                Arena arena, SdlGpuTransferBuffer buffer, int offset, SdlGpuTexture texture, SdlGpuRegion region) {
            var info = Layouts.SDL_GPU_TEXTURE_TRANSFER_INFO;
            var segment = arena.allocate(info.layout());
            var format = texture.format();
            segment.set(ADDRESS, info.offsetOf("transfer_buffer"), buffer.handle());
            segment.set(JAVA_INT, info.offsetOf("offset"), offset);
            // In texels, but whole blocks of them: a compressed row is a row of
            // blocks, and Vulkan takes a row length that is a whole number of them.
            segment.set(
                    JAVA_INT,
                    info.offsetOf("pixels_per_row"),
                    Math.ceilDiv(region.width(), format.blockWidth()) * format.blockWidth());
            segment.set(
                    JAVA_INT,
                    info.offsetOf("rows_per_layer"),
                    Math.ceilDiv(region.height(), format.blockHeight()) * format.blockHeight());
            return segment;
        }

        private static MemorySegment region(
                Arena arena, SdlGpuTexture texture, int level, int layer, SdlGpuRegion region) {
            var info = Layouts.SDL_GPU_TEXTURE_REGION;
            var segment = arena.allocate(info.layout());
            // A 3D texture has one layer, and its sub-resources are depth slices:
            // the slice is the region's z, and its layer is 0.
            var volume = texture.type() == SdlGpuTextureType.THREE_D;
            segment.set(ADDRESS, info.offsetOf("texture"), texture.handle());
            segment.set(JAVA_INT, info.offsetOf("mip_level"), level);
            segment.set(JAVA_INT, info.offsetOf("layer"), volume ? 0 : layer);
            segment.set(JAVA_INT, info.offsetOf("x"), region.x());
            segment.set(JAVA_INT, info.offsetOf("y"), region.y());
            segment.set(JAVA_INT, info.offsetOf("z"), volume ? layer : 0);
            segment.set(JAVA_INT, info.offsetOf("w"), region.width());
            segment.set(JAVA_INT, info.offsetOf("h"), region.height());
            segment.set(JAVA_INT, info.offsetOf("d"), 1);
            return segment;
        }
    }
}
