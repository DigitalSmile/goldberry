package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuShaderStage;

/// The stage a [Shader] runs in.
public enum ShaderStage {
    /// Makes the vertices, from vertex buffers or from the vertex id alone.
    VERTEX,
    /// Colours the pixels.
    FRAGMENT;

    SdlGpuShaderStage sdl() {
        return switch (this) {
            case VERTEX -> SdlGpuShaderStage.VERTEX;
            case FRAGMENT -> SdlGpuShaderStage.FRAGMENT;
        };
    }
}
