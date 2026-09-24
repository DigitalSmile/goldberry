package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuPipelineCalls;

/// Which stage a shader runs in, as SDL's `SDL_GPUShaderStage`.
public enum SdlGpuShaderStage {
    /// Makes the vertices: for the toolkit's shaders, from the vertex id alone.
    VERTEX(SdlGpuPipelineCalls.SHADERSTAGE_VERTEX),
    /// Colours the pixels.
    FRAGMENT(SdlGpuPipelineCalls.SHADERSTAGE_FRAGMENT);

    private final int value;

    SdlGpuShaderStage(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }
}
