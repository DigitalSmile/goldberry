package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuBlend;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;

/// A graphics pipeline: two shaders, how vertices are assembled and read, one
/// colour target of one format with one blend, and an optional depth test, all
/// as its [SdlGpuPipelineDescription] says.
public final class SdlGpuGraphicsPipeline extends SdlGpuResource {

    private final SdlGpuPipelineDescription description;
    private final int samplers;

    SdlGpuGraphicsPipeline(SdlGpuDevice device, MemorySegment handle, SdlGpuPipelineDescription description) {
        super(device, handle);
        this.description = description;
        this.samplers = description.fragment().samplers();
    }

    /// What it was made from.
    public SdlGpuPipelineDescription description() {
        return description;
    }

    /// The format of the colour target it draws into.
    public SdlGpuTextureFormat targetFormat() {
        return description.targetFormat();
    }

    /// The format of the depth target it tests against, or empty for none.
    public Optional<SdlGpuTextureFormat> depthFormat() {
        return description.depthFormat();
    }

    /// How it blends.
    public SdlGpuBlend blend() {
        return description.blend();
    }

    /// How many textures its fragment shader samples.
    public int samplers() {
        return samplers;
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        device().calls().pipelines().releaseGPUGraphicsPipeline().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuGraphicsPipeline[" + description.targetFormat() + ", " + description.blend()
                + description.depthFormat().map(format -> ", depth " + format).orElse("") + "]";
    }
}
