package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBlend;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuSampleCount;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;

/// A graphics pipeline: two shaders, how vertices are assembled and read, at
/// most one colour target of one format with one blend, and an optional depth
/// test, all as its [SdlGpuPipelineDescription] says.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
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

    /// The format of the colour target it draws into, or empty when it writes
    /// depth alone.
    public Optional<SdlGpuTextureFormat> targetFormat() {
        return description.targetFormat();
    }

    /// Whether it writes depth alone, with no colour target.
    public boolean isDepthOnly() {
        return description.isDepthOnly();
    }

    /// The format of the depth target it tests against, or empty for none.
    public Optional<SdlGpuTextureFormat> depthFormat() {
        return description.depthFormat();
    }

    /// How it blends.
    public SdlGpuBlend blend() {
        return description.blend();
    }

    /// How many samples the targets it draws into have.
    public SdlGpuSampleCount sampleCount() {
        return description.sampleCount();
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
        return "SdlGpuGraphicsPipeline["
                + description.targetFormat().map(Object::toString).orElse("depth only")
                + ", " + description.blend()
                + description.depthFormat().map(format -> ", depth " + format).orElse("") + "]";
    }
}
