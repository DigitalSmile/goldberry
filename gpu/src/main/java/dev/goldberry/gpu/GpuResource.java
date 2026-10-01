package dev.goldberry.gpu;

import dev.goldberry.natives.sdl.gpu.SdlGpuResource;

/// Something a [GpuDevice] made that has to be given back: a texture, a buffer,
/// a sampler, a shader or a pipeline.
///
/// Closing one releases it. The GPU keeps what it needs until the commands
/// already submitted that use it have finished, so closing is safe at any point
/// after the last frame that uses it was submitted. Using one after it is closed
/// fails at once, in Java, naming what was used, rather than in the driver. So
/// does using one with another device, or from another thread than the
/// device's.
///
/// A resource still open when its device closes is released with it.
public abstract sealed class GpuResource implements AutoCloseable
        permits GpuTexture, GpuBuffer, GpuSampler, Shader, GraphicsPipeline {

    private final GpuDevice device;
    private final SdlGpuResource sdl;

    GpuResource(GpuDevice device, SdlGpuResource sdl) {
        this.device = device;
        this.sdl = sdl;
    }

    /// The device that made it.
    public final GpuDevice device() {
        return device;
    }

    /// Whether it has been closed, directly or with its device.
    public final boolean isClosed() {
        return sdl.isClosed();
    }

    /// Releases it. Idempotent.
    ///
    /// @throws WrongThreadException when called from another thread than the
    ///                              device's
    @Override
    public final void close() {
        device.requireThread();
        sdl.close();
    }

    /// Checks it may be used now, on `user`'s behalf: open, on the calling
    /// thread, and of `user`.
    ///
    /// @throws IllegalStateException    when it is closed
    /// @throws IllegalArgumentException when it is another device's
    final void requireUsableBy(GpuDevice user) {
        user.requireThread();
        if (device != user) {
            throw new IllegalArgumentException(this + " belongs to " + device + ", not " + user);
        }
        if (sdl.isClosed()) {
            throw new IllegalStateException(this + " is closed");
        }
    }
}
