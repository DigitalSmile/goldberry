package io.github.digitalsmile.goldberry.gpu.render;

import java.util.EnumMap;
import java.util.Map;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuBlend;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuFilter;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuGraphicsPipeline;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuLoad;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuRegion;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuSampler;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuShader;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTarget;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTexture;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTextureFormat;

/// The composite pass of a window (`docs/gpu-plan.md`, D4): clear, then the UI
/// texture drawn 1:1 over it with premultiplied "over", from the top left.
///
/// GPU layers go between the two, in paint order, when phase 4 brings them;
/// until then the pass clears to opaque black and draws the UI, which is what
/// the window surface showed. A UI pixel is premultiplied, so drawn over black
/// it keeps its colour bytes, as the window surface, which ignores alpha, did:
/// the two paths show the same pixels.
///
/// The quad is `quad.vert` with `texture.frag` sampled nearest, the pair phase
/// 2 proved byte for byte; no shader of its own is needed. One pipeline per
/// target format, made the first time a target of it is drawn into, and shared
/// by every window on the device.
public final class UiComposite implements AutoCloseable {

    private final SdlGpuDevice device;
    private final SdlGpuShader vertex;
    private final SdlGpuShader fragment;
    private final SdlGpuSampler nearest;
    private final Map<SdlGpuTextureFormat, SdlGpuGraphicsPipeline> pipelines = new EnumMap<>(SdlGpuTextureFormat.class);

    /// The shaders and the sampler, on `device`.
    ///
    /// @throws io.github.digitalsmile.goldberry.natives.sdl.SdlException when the
    ///         driver refuses them
    public UiComposite(SdlGpuDevice device) {
        this.device = device;
        this.vertex = ShaderLibrary.create(device, BuiltInShader.QUAD_VERTEX);
        this.fragment = ShaderLibrary.create(device, BuiltInShader.TEXTURE_FRAGMENT);
        this.nearest = device.createSampler(SdlGpuFilter.NEAREST);
    }

    /// Records the composite of `ui` into `target`, whose format is
    /// `targetFormat`: a render pass that clears to opaque black and draws `ui`
    /// 1:1 from the top left. Where the two sizes differ -- a resize between the
    /// paint and the present -- the uncovered part stays black and the rest of
    /// `ui` is cut off, until the next frame is painted at the new size.
    public void draw(
            SdlGpuCommandBuffer commands, SdlGpuTarget target, SdlGpuTextureFormat targetFormat, SdlGpuTexture ui) {
        var pipeline = pipeline(targetFormat);
        try (var pass = commands.beginRenderPass(target, SdlGpuLoad.clear(0, 0, 0, 1))) {
            pass.bindPipeline(pipeline);
            pass.bindFragmentSamplers(nearest, ui);
            pass.pushVertexUniforms(
                    0,
                    Quad.of(0, 0, ui.width(), ui.height(), target.width(), target.height())
                            .uniforms());
            pass.draw(Quad.VERTICES);
        }
    }

    /// Records a copy of `ui` into `target` instead of a draw, for a target of a
    /// format this package does not model and so cannot make a pipeline for: an
    /// exact copy with nothing composited under it.
    public static void blit(SdlGpuCommandBuffer commands, SdlGpuTarget target, SdlGpuTexture ui) {
        var width = Math.min(ui.width(), target.width());
        var height = Math.min(ui.height(), target.height());
        var region = new SdlGpuRegion(0, 0, width, height);
        commands.blit(ui, region, target, region, SdlGpuFilter.NEAREST);
    }

    private SdlGpuGraphicsPipeline pipeline(SdlGpuTextureFormat format) {
        var pipeline = pipelines.get(format);
        if (pipeline == null) {
            pipeline = device.createGraphicsPipeline(vertex, fragment, format, SdlGpuBlend.PREMULTIPLIED_OVER);
            pipelines.put(format, pipeline);
        }
        return pipeline;
    }

    /// Releases the pipelines, the shaders and the sampler.
    @Override
    public void close() {
        pipelines.values().forEach(SdlGpuGraphicsPipeline::close);
        pipelines.clear();
        vertex.close();
        fragment.close();
        nearest.close();
    }
}
