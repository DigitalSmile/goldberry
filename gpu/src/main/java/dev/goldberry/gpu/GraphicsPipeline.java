package dev.goldberry.gpu;

import dev.goldberry.natives.sdl.gpu.SdlGpuGraphicsPipeline;

/// A graphics pipeline on a [GpuDevice], made to a [PipelineSpec]: bound in a
/// [RenderPass] before its draws.
public final class GraphicsPipeline extends GpuResource {

    private final SdlGpuGraphicsPipeline sdl;
    private final PipelineSpec spec;

    GraphicsPipeline(GpuDevice device, SdlGpuGraphicsPipeline sdl, PipelineSpec spec) {
        super(device, sdl);
        this.sdl = sdl;
        this.spec = spec;
    }

    /// What it was made from.
    public PipelineSpec spec() {
        return spec;
    }

    /// The SDL pipeline, for `user`'s commands.
    SdlGpuGraphicsPipeline sdl(GpuDevice user) {
        requireUsableBy(user);
        return sdl;
    }

    @Override
    public String toString() {
        return "GraphicsPipeline[" + spec.targetFormat().map(Object::toString).orElse("depth only") + ", "
                + spec.blend()
                + spec.depthTest().map(test -> ", depth " + test.format()).orElse("")
                + (spec.samples() > 1 ? ", " + spec.samples() + " samples" : "")
                + (isClosed() ? ", closed]" : "]");
    }
}
