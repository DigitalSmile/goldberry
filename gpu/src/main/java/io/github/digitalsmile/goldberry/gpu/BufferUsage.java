package io.github.digitalsmile.goldberry.gpu;

import java.util.EnumSet;
import java.util.Set;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuBufferUsage;

/// What a [GpuBuffer] may be used for.
public enum BufferUsage {
    /// Vertices, bound to a pipeline's vertex slot.
    VERTEX,
    /// Indices, for an indexed draw.
    INDEX;

    SdlGpuBufferUsage sdl() {
        return switch (this) {
            case VERTEX -> SdlGpuBufferUsage.VERTEX;
            case INDEX -> SdlGpuBufferUsage.INDEX;
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
