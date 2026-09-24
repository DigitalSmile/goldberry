package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;

/// A graphics pipeline: two shaders, triangles made from the vertex id, no
/// culling and no depth, drawing into one colour target of one format with one
/// blend.
///
/// Vertex buffers and depth join when `canvas3d` needs them
/// (`docs/gpu-plan.md`, phase 5).
public final class SdlGpuGraphicsPipeline extends SdlGpuResource {

    private final SdlGpuTextureFormat targetFormat;
    private final SdlGpuBlend blend;
    private final int samplers;

    SdlGpuGraphicsPipeline(
            SdlGpuDevice device,
            MemorySegment handle,
            SdlGpuTextureFormat targetFormat,
            SdlGpuBlend blend,
            int samplers) {
        super(device, handle);
        this.targetFormat = targetFormat;
        this.blend = blend;
        this.samplers = samplers;
    }

    /// The format of the target it draws into.
    public SdlGpuTextureFormat targetFormat() {
        return targetFormat;
    }

    /// How it blends.
    public SdlGpuBlend blend() {
        return blend;
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
        return "SdlGpuGraphicsPipeline[" + targetFormat + ", " + blend + "]";
    }
}
