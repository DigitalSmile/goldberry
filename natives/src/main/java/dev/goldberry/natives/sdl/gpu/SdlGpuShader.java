package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderStage;

/// A compiled shader on a [SdlGpuDevice], for one stage, and what it declared:
/// how many textures it samples, how many storage textures and buffers it
/// reads, and how many uniform blocks. A pipeline made from it keeps working
/// after it is closed.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlGpuShader extends SdlGpuResource {

    private final SdlGpuShaderStage stage;
    private final int samplers;
    private final int uniformBuffers;
    private final int storageTextures;
    private final int storageBuffers;

    SdlGpuShader(SdlGpuDevice device, MemorySegment handle, SdlGpuShaderCode code) {
        super(device, handle);
        this.stage = code.stage();
        this.samplers = code.samplers();
        this.uniformBuffers = code.uniformBuffers();
        this.storageTextures = code.storageTextures();
        this.storageBuffers = code.storageBuffers();
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

    /// How many storage textures it reads.
    public int storageTextures() {
        return storageTextures;
    }

    /// How many storage buffers it reads.
    public int storageBuffers() {
        return storageBuffers;
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
