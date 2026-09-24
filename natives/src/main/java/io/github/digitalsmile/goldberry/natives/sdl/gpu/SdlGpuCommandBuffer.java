package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuCommandCalls;

/// Commands recorded for a [SdlGpuDevice], then submitted or cancelled, once.
///
/// SDL's rules are checked here, before SDL is called, because SDL's answer to
/// breaking one is an assertion in a debug build and undefined behaviour in a
/// release one:
///
/// - one pass at a time: nothing is recorded while a [CopyPass] or a
///   [RenderPass] is open;
/// - a draw has a pipeline bound, for the target's format, and a texture bound
///   to every sampler slot the pipeline's fragment shader declares;
/// - a command buffer is used once: after [#submit], [#submitWithFence] or
///   [#cancel], every method throws;
/// - a transfer buffer is unmapped while a copy pass uses it;
/// - resources are the same device's, regions lie inside their texture, and
///   the bytes fit the transfer buffer.
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
            var pass = device.calls().commands().beginGPURenderPass().call(handle, colorTarget, 1, MemorySegment.NULL);
            if (MemorySegment.NULL.equals(pass)) {
                throw new SdlException("SDL_BeginGPURenderPass", Sdl.get().lastError());
            }
            state = State.IN_RENDER_PASS;
            return new RenderPass(pass, target);
        }
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

    /// Submits the commands.
    ///
    /// @throws SdlException when SDL refuses
    public void submit() {
        requireState(State.RECORDING, "submit");
        state = State.FINISHED;
        if (!device.calls().commands().submitGPUCommandBuffer().call(handle)) {
            throw new SdlException("SDL_SubmitGPUCommandBuffer", Sdl.get().lastError());
        }
    }

    /// Submits the commands and returns a fence that signals when the GPU has
    /// finished them. The caller closes the fence.
    ///
    /// @throws SdlException when SDL refuses
    public SdlGpuFence submitWithFence() {
        requireState(State.RECORDING, "submitWithFence");
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

    private void requireOwn(SdlGpuResource resource) {
        if (resource.device() != device) {
            throw new IllegalArgumentException(resource + " belongs to another device");
        }
    }

    /// A render pass: the pipeline, viewport, scissor, sampled textures,
    /// uniforms and draws recorded into one target. Closing it ends the pass,
    /// and the command buffer records again.
    public final class RenderPass implements AutoCloseable {

        private final MemorySegment pass;
        private final SdlGpuTarget target;
        private @Nullable SdlGpuGraphicsPipeline pipeline;
        private int boundSamplers;
        private boolean ended;

        private RenderPass(MemorySegment pass, SdlGpuTarget target) {
            this.pass = pass;
            this.target = target;
        }

        /// Binds the pipeline the next draws use.
        ///
        /// @throws IllegalArgumentException when it is another device's, or for
        ///                                  another format than the target's
        public void bindPipeline(SdlGpuGraphicsPipeline bound) {
            requireOpen();
            requireOwn(bound);
            var targetFormat =
                    switch (target) {
                        case SdlGpuTexture texture -> java.util.Optional.of(texture.format());
                        case SdlGpuSwapchainTexture swapchain -> swapchain.format();
                    };
            if (targetFormat.isPresent() && targetFormat.get() != bound.targetFormat()) {
                throw new IllegalArgumentException(
                        bound + " draws " + bound.targetFormat() + ", and the target is " + targetFormat.get());
            }
            device.calls().renderPass().bindGPUGraphicsPipeline().call(pass, bound.handle());
            pipeline = bound;
            boundSamplers = 0;
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

        /// Draws nothing outside `region` of the target: a layer's clip
        /// (`docs/gpu-plan.md`, D4).
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

        /// Sets the vertex shader's uniform block `slot` for the draws that
        /// follow. SDL copies the values; their layout is the shader's.
        public void pushVertexUniforms(int slot, float... values) {
            requireOpen();
            push(slot, values, true);
        }

        /// Sets the fragment shader's uniform block `slot` for the draws that
        /// follow. SDL copies the values; their layout is the shader's.
        public void pushFragmentUniforms(int slot, float... values) {
            requireOpen();
            push(slot, values, false);
        }

        private void push(int slot, float[] values, boolean vertex) {
            if (slot < 0 || values.length == 0) {
                throw new IllegalArgumentException("uniform slot " + slot + " with " + values.length + " values");
            }
            try (var arena = Arena.ofConfined()) {
                var data = arena.allocateFrom(JAVA_FLOAT, values);
                var calls = device.calls().renderPass();
                if (vertex) {
                    calls.pushGPUVertexUniformData().call(handle, slot, data, (int) data.byteSize());
                } else {
                    calls.pushGPUFragmentUniformData().call(handle, slot, data, (int) data.byteSize());
                }
            }
        }

        /// Draws `vertices` vertices, made by the vertex shader from their ids.
        ///
        /// @throws IllegalStateException when no pipeline is bound, or its
        ///                               samplers are not
        public void draw(int vertices) {
            requireOpen();
            var bound = requirePipeline("draw");
            if (boundSamplers != bound.samplers()) {
                throw new IllegalStateException(
                        bound + " samples " + bound.samplers() + " textures, and " + boundSamplers + " are bound");
            }
            if (vertices <= 0) {
                throw new IllegalArgumentException("draw of " + vertices + " vertices");
            }
            device.calls().renderPass().drawGPUPrimitives().call(pass, vertices, 1, 0, 0);
        }

        /// Ends the pass. Idempotent.
        @Override
        public void close() {
            if (ended) {
                return;
            }
            ended = true;
            device.calls().commands().endGPURenderPass().call(pass);
            state = State.RECORDING;
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

    /// A copy pass: uploads into textures and downloads out of them. Closing it
    /// ends the pass, and the command buffer records again.
    public final class CopyPass implements AutoCloseable {

        private final MemorySegment pass;
        private boolean ended;

        private CopyPass(MemorySegment pass) {
            this.pass = pass;
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

        /// Ends the pass. Idempotent.
        @Override
        public void close() {
            if (ended) {
                return;
            }
            ended = true;
            device.calls().commands().endGPUCopyPass().call(pass);
            state = State.RECORDING;
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
