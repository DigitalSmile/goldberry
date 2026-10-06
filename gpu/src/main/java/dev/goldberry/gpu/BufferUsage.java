package dev.goldberry.gpu;

import java.util.EnumSet;
import java.util.Set;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBufferUsage;

/// What a [GpuBuffer] may be used for.
public enum BufferUsage {
    /// Vertices, bound to a pipeline's vertex slot.
    VERTEX,
    /// Indices, for an indexed draw.
    INDEX,
    /// Read as a storage buffer by a vertex or fragment shader: an instance
    /// table, positions a compute pass wrote.
    GRAPHICS_STORAGE_READ,
    /// Read as a storage buffer by a compute shader.
    COMPUTE_STORAGE_READ,
    /// Written as a storage buffer by a compute shader.
    COMPUTE_STORAGE_WRITE;

    SdlGpuBufferUsage sdl() {
        return switch (this) {
            case VERTEX -> SdlGpuBufferUsage.VERTEX;
            case INDEX -> SdlGpuBufferUsage.INDEX;
            case GRAPHICS_STORAGE_READ -> SdlGpuBufferUsage.GRAPHICS_STORAGE_READ;
            case COMPUTE_STORAGE_READ -> SdlGpuBufferUsage.COMPUTE_STORAGE_READ;
            case COMPUTE_STORAGE_WRITE -> SdlGpuBufferUsage.COMPUTE_STORAGE_WRITE;
        };
    }

    static Set<SdlGpuBufferUsage> sdl(Set<BufferUsage> usages) {
        var mapped = EnumSet.noneOf(SdlGpuBufferUsage.class);
        for (var usage : usages) {
            mapped.add(usage.sdl());
        }
        return mapped;
    }
}
