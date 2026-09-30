package io.github.digitalsmile.goldberry.gpu.composite;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.digitalsmile.goldberry.gpu.render.BuiltInShader;
import io.github.digitalsmile.goldberry.gpu.render.Quad;
import io.github.digitalsmile.goldberry.gpu.render.ShaderLibrary;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuGraphicsPipeline;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuLoad;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuRegion;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuSampler;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuShader;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTarget;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTexture;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuBlend;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;

/// The composite pass of a window (`docs/gpu-plan.md`, D4; ADR-0479, ADR-0481):
/// clear to opaque black, draw each GPU layer where it was placed, in paint
/// order, and draw the UI texture 1:1 over them all with premultiplied "over",
/// from the top left.
///
/// A UI pixel is premultiplied, so drawn over black it keeps its colour bytes,
/// as the window surface, which ignores alpha, did: the two paths show the same
/// pixels. Where a layer was placed the UI has a transparent hole, so the layer
/// shows through it, under whatever was painted after it.
///
/// A layer's quad replaces what is under it -- layers are opaque -- and is
/// scissored to the part of it its clips let through. Every quad is
/// `quad.vert` with `texture.frag` sampled nearest, the pair phase 2 proved
/// byte for byte, so a layer drawn 1:1 is its texture exactly; no shader of its
/// own is needed. Two pipelines per target format, one replacing and one
/// blending, made the first time a target of it is drawn into, and shared by
/// every window on the device.
public final class UiComposite implements AutoCloseable {

    private final SdlGpuDevice device;
    private final SdlGpuShader vertex;
    private final SdlGpuShader fragment;
    private final SdlGpuSampler nearest;
    private final Map<SdlGpuTextureFormat, SdlGpuGraphicsPipeline> pipelines = new EnumMap<>(SdlGpuTextureFormat.class);
    private final Map<SdlGpuTextureFormat, SdlGpuGraphicsPipeline> opaquePipelines =
            new EnumMap<>(SdlGpuTextureFormat.class);

    /// A GPU layer's rendered texture, and where it goes in the target.
    ///
    /// @param texture what the layer rendered, at `target`'s size
    /// @param target  where it is drawn, 1:1, in the target's pixels; it may
    ///                reach outside the target
    /// @param scissor the part of `target` that is drawn
    public record Layer(SdlGpuTexture texture, PhysicalRect target, PhysicalRect scissor) {

        /// Checks nothing is missing.
        public Layer {
            Objects.requireNonNull(texture, "texture");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(scissor, "scissor");
        }
    }

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
        draw(commands, target, targetFormat, ui, List.of());
    }

    /// Records the composite of `layers` and `ui` into `target`: a render pass
    /// that clears to opaque black, draws each layer, in order, 1:1 at its
    /// place and cut to its scissor, and draws `ui` 1:1 over them from the top
    /// left. A layer that falls outside the target is not drawn.
    public void draw(
            SdlGpuCommandBuffer commands,
            SdlGpuTarget target,
            SdlGpuTextureFormat targetFormat,
            SdlGpuTexture ui,
            List<Layer> layers) {
        var pipeline = pipeline(targetFormat);
        var whole = new SdlGpuRegion(0, 0, target.width(), target.height());
        try (var pass = commands.beginRenderPass(target, SdlGpuLoad.clear(0, 0, 0, 1))) {
            if (!layers.isEmpty()) {
                pass.bindPipeline(opaque(targetFormat));
                for (var layer : layers) {
                    var place = layer.target();
                    var scissor = layer.scissor();
                    var left = Math.max(0, scissor.x());
                    var top = Math.max(0, scissor.y());
                    var right = Math.min(target.width(), scissor.right());
                    var bottom = Math.min(target.height(), scissor.bottom());
                    if (right <= left || bottom <= top || place.isEmpty()) {
                        continue;
                    }
                    pass.setScissor(new SdlGpuRegion(left, top, right - left, bottom - top));
                    pass.bindFragmentSamplers(nearest, layer.texture());
                    pass.pushVertexUniforms(
                            0,
                            Quad.of(
                                            place.x(),
                                            place.y(),
                                            place.width(),
                                            place.height(),
                                            target.width(),
                                            target.height())
                                    .uniforms());
                    pass.draw(Quad.VERTICES);
                }
                pass.setScissor(whole);
            }
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

    /// The pipeline a layer is drawn with: replacing, since layers are opaque
    /// and a read-back layer replaces what is under it too (ADR-0481).
    private SdlGpuGraphicsPipeline opaque(SdlGpuTextureFormat format) {
        var pipeline = opaquePipelines.get(format);
        if (pipeline == null) {
            pipeline = device.createGraphicsPipeline(vertex, fragment, format, SdlGpuBlend.REPLACE);
            opaquePipelines.put(format, pipeline);
        }
        return pipeline;
    }

    /// Releases the pipelines, the shaders and the sampler.
    @Override
    public void close() {
        pipelines.values().forEach(SdlGpuGraphicsPipeline::close);
        pipelines.clear();
        opaquePipelines.values().forEach(SdlGpuGraphicsPipeline::close);
        opaquePipelines.clear();
        vertex.close();
        fragment.close();
        nearest.close();
    }
}
