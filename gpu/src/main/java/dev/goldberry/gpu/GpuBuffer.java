package dev.goldberry.gpu;

import java.util.Objects;
import java.util.Set;

import dev.goldberry.natives.sdl.gpu.SdlGpuBuffer;

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

    /// Names it for a frame capture in RenderDoc, PIX or Xcode, which shows
    /// buffers by name. A driver with no debugger attached ignores it.
    ///
    /// @throws IllegalStateException when it is closed
    public void setDebugName(String name) {
        Objects.requireNonNull(name, "name");
        var gpu = device();
        gpu.sdl().setName(sdl(gpu), name);
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
