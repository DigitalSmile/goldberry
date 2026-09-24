package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Optional;

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
/// - one pass at a time: nothing is recorded while a [CopyPass] is open;
/// - a command buffer is used once: after [#submit], [#submitWithFence] or
///   [#cancel], every method throws;
/// - a transfer buffer is unmapped while a copy pass uses it;
/// - resources are the same device's, regions lie inside their texture, and
///   the bytes fit the transfer buffer.
public final class SdlGpuCommandBuffer {

    private enum State {
        RECORDING,
        IN_COPY_PASS,
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
        requireState(State.RECORDING, "clear");
        var targetHandle = colorTarget(target);
        var info = Layouts.SDL_GPU_COLOR_TARGET_INFO;
        var color = Layouts.SDL_FCOLOR;
        var clearColor = info.offsetOf("clear_color");
        try (var arena = Arena.ofConfined()) {
            var colorTarget = arena.allocate(info.layout());
            colorTarget.set(ADDRESS, info.offsetOf("texture"), targetHandle);
            colorTarget.set(JAVA_FLOAT, clearColor + color.offsetOf("r"), red);
            colorTarget.set(JAVA_FLOAT, clearColor + color.offsetOf("g"), green);
            colorTarget.set(JAVA_FLOAT, clearColor + color.offsetOf("b"), blue);
            colorTarget.set(JAVA_FLOAT, clearColor + color.offsetOf("a"), alpha);
            colorTarget.set(JAVA_INT, info.offsetOf("load_op"), SdlGpuCommandCalls.LOADOP_CLEAR);
            colorTarget.set(JAVA_INT, info.offsetOf("store_op"), SdlGpuCommandCalls.STOREOP_STORE);
            colorTarget.set(JAVA_BOOLEAN, info.offsetOf("cycle"), false);
            var commands = device.calls().commands();
            var pass = commands.beginGPURenderPass().call(handle, colorTarget, 1, MemorySegment.NULL);
            if (MemorySegment.NULL.equals(pass)) {
                throw new SdlException("SDL_BeginGPURenderPass", Sdl.get().lastError());
            }
            commands.endGPURenderPass().call(pass);
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
            return Optional.of(
                    new SdlGpuSwapchainTexture(this, acquired, width.get(JAVA_INT, 0), height.get(JAVA_INT, 0)));
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
                        case FINISHED -> "already submitted or cancelled";
                    });
        }
    }

    private void requireOwn(SdlGpuResource resource) {
        if (resource.device() != device) {
            throw new IllegalArgumentException(resource + " belongs to another device");
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
