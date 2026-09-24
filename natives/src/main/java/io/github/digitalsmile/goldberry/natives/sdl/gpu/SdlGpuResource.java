package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;

/// Something a [SdlGpuDevice] made or holds and has to give back: a texture, a
/// buffer, a transfer buffer, a fence, a claimed window, a shader, a sampler or a
/// pipeline.
///
/// Closing one releases it to SDL, which frees it once no submitted command
/// still uses it. Using one after that fails here, in Java, with the name of
/// what was used, rather than in the driver. A resource still open when its
/// device closes is released by the device, so closing the device is always
/// safe; SDL requires it and would otherwise leak or crash.
public abstract sealed class SdlGpuResource implements AutoCloseable
        permits SdlGpuTexture,
                SdlGpuBuffer,
                SdlGpuTransferBuffer,
                SdlGpuFence,
                SdlGpuWindow,
                SdlGpuShader,
                SdlGpuSampler,
                SdlGpuGraphicsPipeline {

    private final SdlGpuDevice device;
    private final MemorySegment handle;
    private boolean closed;

    SdlGpuResource(SdlGpuDevice device, MemorySegment handle) {
        this.device = device;
        this.handle = handle;
        device.adopt(this);
    }

    /// The device this was made on.
    public final SdlGpuDevice device() {
        return device;
    }

    /// Whether [#close] has run, directly or through the device's.
    public final boolean isClosed() {
        return closed;
    }

    /// The SDL handle, for this package's calls.
    ///
    /// @throws IllegalStateException once closed
    final MemorySegment handle() {
        if (closed) {
            throw new IllegalStateException(this + " is closed");
        }
        return handle;
    }

    /// Releases the resource. Idempotent.
    @Override
    public final void close() {
        if (closed) {
            return;
        }
        device.disown(this);
        releaseOnce();
    }

    /// Called by the device as it closes, for what is still open.
    final void releaseOnce() {
        if (!closed) {
            closed = true;
            release(device.handle(), handle);
        }
    }

    /// Gives the handle back to SDL. Runs at most once.
    abstract void release(MemorySegment device, MemorySegment handle);
}
