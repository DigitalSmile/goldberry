package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuVertexInputRate;

/// Whether a vertex buffer's elements advance per vertex or per instance.
public enum VertexInputRate {
    /// One element per vertex.
    VERTEX,
    /// One element per instance: instanced drawing.
    INSTANCE;

    SdlGpuVertexInputRate sdl() {
        return switch (this) {
            case VERTEX -> SdlGpuVertexInputRate.VERTEX;
            case INSTANCE -> SdlGpuVertexInputRate.INSTANCE;
        };
    }
}
