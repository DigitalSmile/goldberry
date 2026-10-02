package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBufferUsage;

/// A buffer on a [SdlGpuDevice]: vertices or indices, filled by a copy pass from
/// a transfer buffer, and read by draws.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlGpuBuffer extends SdlGpuResource {

    private final Set<SdlGpuBufferUsage> usages;
    private final int size;

    SdlGpuBuffer(SdlGpuDevice device, MemorySegment handle, Set<SdlGpuBufferUsage> usages, int size) {
        super(device, handle);
        this.usages = Collections.unmodifiableSet(EnumSet.copyOf(usages));
        this.size = size;
    }

    /// What the buffer may be used for.
    public Set<SdlGpuBufferUsage> usages() {
        return usages;
    }

    /// Its size in bytes.
    public int size() {
        return size;
    }

    /// Whether `length` bytes from `offset` lie inside the buffer.
    boolean fits(int offset, long length) {
        return offset >= 0 && length >= 0 && offset + length <= size;
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        device().calls().buffers().releaseGPUBuffer().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuBuffer[" + usages + ", " + size + " bytes]";
    }
}
