package io.github.digitalsmile.goldberry.gpu;

import java.util.Set;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuBuffer;

/// A buffer on a [GpuDevice]: vertices or indices, filled in a
/// [CopyPass] and read by draws.
public final class GpuBuffer extends GpuResource {

    private final SdlGpuBuffer sdl;
    private final Set<BufferUsage> usages;

    GpuBuffer(GpuDevice device, SdlGpuBuffer sdl, Set<BufferUsage> usages) {
        super(device, sdl);
        this.sdl = sdl;
        this.usages = usages;
    }

    /// What it may be used for.
    public Set<BufferUsage> usages() {
        return usages;
    }

    /// Its size in bytes.
    public int size() {
        return sdl.size();
    }

    /// The SDL buffer, for `user`'s commands.
    SdlGpuBuffer sdl(GpuDevice user) {
        requireUsableBy(user);
        return sdl;
    }

    @Override
    public String toString() {
        return "GpuBuffer[" + usages + ", " + sdl.size() + " bytes" + (isClosed() ? ", closed]" : "]");
    }
}
