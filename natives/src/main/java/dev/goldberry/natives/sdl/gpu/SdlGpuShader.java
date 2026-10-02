package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderStage;

/// A compiled shader on a [SdlGpuDevice]. A pipeline made from it keeps working
/// after it is closed.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlGpuShader extends SdlGpuResource {

    private final SdlGpuShaderStage stage;
    private final int samplers;
    private final int uniformBuffers;

    SdlGpuShader(SdlGpuDevice device, MemorySegment handle, SdlGpuShaderStage stage, int samplers, int uniformBuffers) {
        super(device, handle);
        this.stage = stage;
        this.samplers = samplers;
        this.uniformBuffers = uniformBuffers;
    }

    /// The stage it runs in.
    public SdlGpuShaderStage stage() {
        return stage;
    }

    /// How many textures it samples.
    public int samplers() {
        return samplers;
    }

    /// How many uniform blocks it reads.
    public int uniformBuffers() {
        return uniformBuffers;
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        device().calls().pipelines().releaseGPUShader().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuShader[" + stage + "]";
    }
}
