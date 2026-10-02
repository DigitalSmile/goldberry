package dev.goldberry.natives.sdl.gpu;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.layout.Layouts;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.calls.SdlGpuCommandCalls;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBufferUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuIndexSize;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
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
        return beginPass(target, load, depth);
    }

    private RenderPass beginPass(SdlGpuTarget target, SdlGpuLoad load, @Nullable SdlGpuDepthTarget depth) {
        requireState(State.RECORDING, "beginRenderPass");
        var targetHandle = colorTarget(target);
        var info = Layouts.SDL_GPU_COLOR_TARGET_INFO;
        var color = Layouts.SDL_FCOLOR;
        var clearColor = info.offsetOf("clear_color");
        try (var arena = Arena.ofConfined()) {
            var colorTarget = arena.allocate(info.layout());
            colorTarget.set(ADDRESS, info.offsetOf("texture"), targetHandle);
            var loadOp =
                    switch (load) {
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
            colorTarget.set(JAVA_INT, info.offsetOf("store_op"), SdlGpuCommandCalls.STOREOP_STORE);
            colorTarget.set(JAVA_BOOLEAN, info.offsetOf("cycle"), false);
            var depthTarget = depth == null ? MemorySegment.NULL : depthTarget(arena, target, depth);
            var pass = device.calls().commands().beginGPURenderPass().call(handle, colorTarget, 1, depthTarget);
            if (MemorySegment.NULL.equals(pass)) {
                throw new SdlException("SDL_BeginGPURenderPass", Sdl.get().lastError());
            }
            state = State.IN_RENDER_PASS;
            return new RenderPass(
                    pass,
                    target,
                    depth == null
                            ? Optional.empty()
                            : Optional.of(depth.texture().format()));
        }
    }

    /// The `SDL_GPUDepthStencilTargetInfo` for `depth`. Stencil is neither
    /// loaded nor kept: the depth formats here have none.
    private MemorySegment depthTarget(Arena arena, SdlGpuTarget target, SdlGpuDepthTarget depth) {
        var texture = depth.texture();
        requireOwn(texture);
        if (texture.width() != target.width() || texture.height() != target.height()) {
            throw new IllegalArgumentException(texture + " is not the size of " + target);
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
        var destinationHandle = colorTarget(destination);
        var info = Layouts.SDL_GPU_BLIT_INFO;
        try (var arena = Arena.ofConfined()) {
            var blit = arena.allocate(info.layout());
            blitRegion(blit, info.offsetOf("source"), source.handle(), sourceRegion);
            blitRegion(blit, info.offsetOf("destination"), destinationHandle, destinationRegion);
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

    /// The handle of a target this command buffer may draw into.
    private MemorySegment colorTarget(SdlGpuTarget target) {
        return switch (target) {
            case SdlGpuTexture texture -> {
                requireOwn(texture);
                if (!texture.usages().contains(SdlGpuTextureUsage.COLOR_TARGET)) {
                    throw new IllegalArgumentException(texture + " is not a colour target");
                }
                yield texture.handle();
            }
            case SdlGpuSwapchainTexture swapchain -> swapchain.handle(this);
        };
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
        private final SdlGpuTarget target;
        private final Optional<SdlGpuTextureFormat> depthFormat;
        private final int debugDepthAtBegin;
        private final BitSet boundVertexSlots = new BitSet();
        private @Nullable SdlGpuGraphicsPipeline pipeline;
        private int boundSamplers;
        private boolean indexBound;
        private boolean ended;

        private RenderPass(MemorySegment pass, SdlGpuTarget target, Optional<SdlGpuTextureFormat> depthFormat) {
            this.pass = pass;
            this.target = target;
            this.depthFormat = depthFormat;
            this.debugDepthAtBegin = debugDepth;
            openPassDebugDepth = debugDepth;
        }

        /// Binds the pipeline the next draws use. Its samplers and vertex
        /// buffers are bound after it, and again after the next.
        ///
        /// @throws IllegalArgumentException when it is another device's, for
        ///                                  another format than the target's,
        ///                                  or tests depth this pass has no
        ///                                  target for (or the reverse)
        public void bindPipeline(SdlGpuGraphicsPipeline bound) {
            requireOpen();
            requireOwn(bound);
            var targetFormat =
                    switch (target) {
                        case SdlGpuTexture texture -> Optional.of(texture.format());
                        case SdlGpuSwapchainTexture swapchain -> swapchain.format();
                    };
            if (targetFormat.isPresent() && targetFormat.get() != bound.targetFormat()) {
                throw new IllegalArgumentException(
                        bound + " draws " + bound.targetFormat() + ", and the target is " + targetFormat.get());
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
            boundVertexSlots.clear();
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
            if (!region.fitsIn(target.width(), target.height())) {
                throw new IllegalArgumentException(region + " is outside " + target);
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
            requireOpen();
            var bound = requirePipeline("bindFragmentSamplers");
            requireOwn(sampler);
            if (textures.length != bound.samplers()) {
                throw new IllegalArgumentException(
                        bound + " samples " + bound.samplers() + " textures, not " + textures.length);
            }
            var layout = Layouts.SDL_GPU_TEXTURE_SAMPLER_BINDING;
            try (var arena = Arena.ofConfined()) {
                var bindings = arena.allocate(layout.layout(), textures.length);
                for (var i = 0; i < textures.length; i++) {
                    var texture = textures[i];
                    requireOwn(texture);
                    if (!texture.usages().contains(SdlGpuTextureUsage.SAMPLER)) {
                        throw new IllegalArgumentException(texture + " cannot be sampled");
                    }
                    var at = i * layout.byteSize();
                    bindings.set(ADDRESS, at + layout.offsetOf("texture"), texture.handle());
                    bindings.set(ADDRESS, at + layout.offsetOf("sampler"), sampler.handle());
                }
                device.calls().renderPass().bindGPUFragmentSamplers().call(pass, 0, bindings, textures.length);
            }
            boundSamplers = textures.length;
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
        /// `offset` of `source`, into `destination`.
        ///
        /// @param cycle take fresh texture memory if the GPU still reads the old,
        ///              rather than wait for it
        public void upload(
                SdlGpuTransferBuffer source,
                int offset,
                SdlGpuTexture destination,
                SdlGpuRegion region,
                boolean cycle) {
            requireOpen();
            check(source, SdlGpuTransferUsage.UPLOAD, offset, destination, region);
            try (var arena = Arena.ofConfined()) {
                device.calls()
                        .commands()
                        .uploadToGPUTexture()
                        .call(
                                pass,
                                transferInfo(arena, source, offset, region),
                                region(arena, destination, region),
                                cycle);
            }
        }

        /// Records a copy of `region` of `source` into `destination` from byte
        /// `offset`, packed row after row. The bytes are there once the command
        /// buffer's fence has signalled.
        public void download(SdlGpuTexture source, SdlGpuRegion region, SdlGpuTransferBuffer destination, int offset) {
            requireOpen();
            check(destination, SdlGpuTransferUsage.DOWNLOAD, offset, source, region);
            try (var arena = Arena.ofConfined()) {
                device.calls()
                        .commands()
                        .downloadFromGPUTexture()
                        .call(pass, region(arena, source, region), transferInfo(arena, destination, offset, region));
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
                SdlGpuRegion region) {
            requireOwn(buffer);
            requireOwn(texture);
            if (buffer.usage() != usage) {
                throw new IllegalArgumentException(buffer + " is not an " + usage + " buffer");
            }
            if (buffer.isMapped()) {
                throw new IllegalStateException(buffer + " is mapped; SDL copies only unmapped buffers");
            }
            if (!region.fitsIn(texture.width(), texture.height())) {
                throw new IllegalArgumentException(region + " is outside " + texture);
            }
            if (offset < 0 || offset + texture.byteSize(region) > buffer.size()) {
                throw new IllegalArgumentException(
                        texture.byteSize(region) + " bytes from offset " + offset + " do not fit " + buffer);
            }
        }

        private static MemorySegment transferInfo(
                Arena arena, SdlGpuTransferBuffer buffer, int offset, SdlGpuRegion region) {
            var info = Layouts.SDL_GPU_TEXTURE_TRANSFER_INFO;
            var segment = arena.allocate(info.layout());
            segment.set(ADDRESS, info.offsetOf("transfer_buffer"), buffer.handle());
            segment.set(JAVA_INT, info.offsetOf("offset"), offset);
            segment.set(JAVA_INT, info.offsetOf("pixels_per_row"), region.width());
            segment.set(JAVA_INT, info.offsetOf("rows_per_layer"), region.height());
            return segment;
        }

        private static MemorySegment region(Arena arena, SdlGpuTexture texture, SdlGpuRegion region) {
            var info = Layouts.SDL_GPU_TEXTURE_REGION;
            var segment = arena.allocate(info.layout());
            segment.set(ADDRESS, info.offsetOf("texture"), texture.handle());
            segment.set(JAVA_INT, info.offsetOf("x"), region.x());
            segment.set(JAVA_INT, info.offsetOf("y"), region.y());
            segment.set(JAVA_INT, info.offsetOf("w"), region.width());
            segment.set(JAVA_INT, info.offsetOf("h"), region.height());
            segment.set(JAVA_INT, info.offsetOf("d"), 1);
            return segment;
        }
    }
}
