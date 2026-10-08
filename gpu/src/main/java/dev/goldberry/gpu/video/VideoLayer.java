package dev.goldberry.gpu.video;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.GpuLayer;
import dev.goldberry.gpu.GpuSampler;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.GraphicsPipeline;
import dev.goldberry.gpu.Load;
import dev.goldberry.gpu.PipelineSpec;
import dev.goldberry.gpu.RenderTarget;
import dev.goldberry.gpu.SamplerSpec;
import dev.goldberry.gpu.Shader;
import dev.goldberry.gpu.ShaderCode;
import dev.goldberry.gpu.ShaderStage;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.render.BuiltInShader;
import dev.goldberry.gpu.render.Quad;
import dev.goldberry.gpu.render.YuvConversion;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;

/// A video's picture on the GPU: a [GpuLayer] that shows one [VideoImage] at a
/// time, stretched over the whole of its box.
///
/// The caller [#show]s the picture and the part of it to show, then places the
/// layer over the rectangle it goes in. Letterboxing is the caller's, by where
/// it places the layer: the layer covers its box and nothing else. Or the
/// caller renders it itself, into a [RenderTarget] of its own: a texture, or
/// one level of one layer of one, which a shader then samples.
///
/// **Planes** are uploaded into one texture each (`R8`, `R8G8`, `R16`,
/// `R16G16`) and converted by `yuv2.frag` or `yuv3.frag` with the picture's
/// matrix and range ([YuvConversion]), sampled linearly, which scales the
/// picture to the box. **BGRA** is uploaded into one texture and drawn by
/// `texture.frag`. Either way, the textures are kept between frames and
/// uploaded to only when the image changes, so a paused video, or a frame
/// repainted for something beside it, costs a draw at most.
///
/// Confined to the UI thread, as every layer is. Made on first render on the
/// device it renders with, and made again on a new device.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html#video-view).
public final class VideoLayer implements GpuLayer, AutoCloseable {

    /// Where the toolkit's shader bytecode is, in this module.
    private static final String SHADERS = "/dev/goldberry/gpu/shaders/";

    private @Nullable VideoImage image;
    private @Nullable PhysicalRect source;

    /// What was drawn last, so an unchanged picture is not drawn again.
    private @Nullable VideoImage rendered;
    private @Nullable PhysicalRect renderedSource;

    /// What the device holds: the image last uploaded, and the textures it went
    /// into, made for [#texturesFor]'s key.
    private @Nullable VideoImage uploaded;
    private @Nullable Object texturesKey;
    private final List<GpuTexture> textures = new ArrayList<>();

    private @Nullable GpuDevice device;
    private @Nullable Shader vertex;
    private @Nullable GpuSampler sampler;
    private final Map<BuiltInShader, Shader> fragments = new EnumMap<>(BuiltInShader.class);
    private final Map<BuiltInShader, GraphicsPipeline> pipelines = new EnumMap<>(BuiltInShader.class);
    private boolean closed;

    /// Shows the whole of `image` from the next render.
    public void show(VideoImage image) {
        show(image, new PhysicalRect(0, 0, image.width(), image.height()));
    }

    /// Shows `source`, a part of `image` in its pixels, stretched over the
    /// layer's box from the next render: what `object-fit: cover` crops to.
    ///
    /// @throws IllegalArgumentException when `source` is empty or reaches
    ///                                  outside the image
    public void show(VideoImage image, PhysicalRect source) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(source, "source");
        if (source.isEmpty() || !source.fitsWithin(new PhysicalSize(image.width(), image.height()))) {
            throw new IllegalArgumentException(
                    source + " is not a part of a " + image.width() + "×" + image.height() + " picture");
        }
        this.image = image;
        this.source = source;
    }

    /// The image shown, or null before the first.
    public @Nullable VideoImage image() {
        return image;
    }

    /// True when the image or the part shown has changed since the last render.
    @Override
    public boolean needsRender() {
        return !closed && (image != rendered || !Objects.equals(source, renderedSource));
    }

    /// Uploads the image if it is new to this device, and draws it over the
    /// whole of `target`. Before an image is shown, clears to black.
    @Override
    public void render(GpuFrame frame, GpuTexture target) {
        render(frame, (RenderTarget) target);
    }

    /// Uploads the image if it is new to this device, and draws it over the
    /// whole of `target`: a texture, or one level of one layer of one, at that
    /// level's size. Before an image is shown, clears to black. What the rest
    /// of a texture holds, its other layers and levels, is left as it was.
    ///
    /// @throws IllegalArgumentException when `target` is not
    ///                                  [TextureFormat#B8G8R8A8_UNORM], or not
    ///                                  a colour target of `frame`'s device
    /// @throws IllegalStateException    when this layer is closed
    public void render(GpuFrame frame, RenderTarget target) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(target, "target");
        if (closed) {
            throw new IllegalStateException("this video layer is closed");
        }
        if (target.format() != TextureFormat.B8G8R8A8_UNORM) {
            throw new IllegalArgumentException(
                    target + " is " + target.format() + ", and a video layer draws " + TextureFormat.B8G8R8A8_UNORM);
        }
        var current = frame.device();
        if (device != current) {
            // A first frame, or a new device: nothing made on the old one is left.
            release();
            device = current;
        }
        var shown = image;
        var part = source;
        if (shown == null || part == null) {
            frame.renderPass(target, Load.clear(0, 0, 0, 1), _ -> {});
            rendered = null;
            renderedSource = null;
            return;
        }
        if (shown != uploaded) {
            upload(frame, current, shown);
            uploaded = shown;
        }
        var fragment =
                switch (shown) {
                    case VideoImage.Planes planes -> planes.layout().yuv().shader();
                    case VideoImage.Bgra _ -> BuiltInShader.TEXTURE_FRAGMENT;
                };
        var pipeline = pipeline(current, fragment);
        var linear = sampler(current);
        var quad = Quad.of(
                0,
                0,
                target.width(),
                target.height(),
                target.width(),
                target.height(),
                new Quad.Edges(
                        (float) part.x() / shown.width(),
                        (float) part.y() / shown.height(),
                        (float) part.right() / shown.width(),
                        (float) part.bottom() / shown.height()));
        var planeTextures = textures.toArray(GpuTexture[]::new);
        frame.renderPass(target, Load.dontCare(), pass -> {
            pass.bindPipeline(pipeline);
            pass.pushVertexUniforms(0, quad.uniforms());
            if (shown instanceof VideoImage.Planes planes) {
                var conversion =
                        new YuvConversion(planes.layout().yuv(), planes.matrix().yuv(), planes.fullRange());
                pass.pushFragmentUniforms(0, conversion.uniforms(planeTextures[1].width()));
            }
            pass.bindFragmentSamplers(linear, planeTextures);
            pass.draw(Quad.VERTICES);
        });
        rendered = shown;
        renderedSource = part;
    }

    /// Uploads `shown` into the textures, made anew when its layout or size
    /// changed. Whole textures, so the driver may give them fresh memory rather
    /// than wait for a frame still sampling the last picture.
    private void upload(GpuFrame frame, GpuDevice on, VideoImage shown) {
        var made = texturesFor(on, shown);
        frame.copyPass(copy -> {
            switch (shown) {
                case VideoImage.Planes planes -> {
                    for (var plane = 0; plane < made.size(); plane++) {
                        var texture = made.get(plane);
                        copy.upload(
                                texture,
                                planes.planes().get(plane),
                                planes.strides().get(plane),
                                List.of(new PhysicalRect(0, 0, texture.width(), texture.height())));
                    }
                }
                case VideoImage.Bgra bgra ->
                    copy.upload(
                            made.getFirst(),
                            bgra.pixels(),
                            bgra.stride(),
                            List.of(new PhysicalRect(0, 0, bgra.width(), bgra.height())));
            }
        });
    }

    /// The textures an image like `shown` goes into: kept while the layout and
    /// size stay the same.
    private List<GpuTexture> texturesFor(GpuDevice on, VideoImage shown) {
        var key =
                switch (shown) {
                    case VideoImage.Planes planes -> List.of(planes.layout(), planes.width(), planes.height());
                    case VideoImage.Bgra bgra -> List.of("BGRA", bgra.width(), bgra.height());
                };
        if (key.equals(texturesKey)) {
            return textures;
        }
        closeTextures();
        switch (shown) {
            case VideoImage.Planes planes -> {
                var layout = planes.layout();
                for (var plane = 0; plane < layout.planes(); plane++) {
                    textures.add(on.createTexture(TextureSpec.sampled(
                            layout.textureFormats().get(plane),
                            layout.planeWidth(plane, planes.width()),
                            layout.planeHeight(plane, planes.height()))));
                }
            }
            case VideoImage.Bgra bgra ->
                textures.add(on.createTexture(
                        TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, bgra.width(), bgra.height())));
        }
        texturesKey = key;
        return textures;
    }

    private GraphicsPipeline pipeline(GpuDevice on, BuiltInShader fragment) {
        var pipeline = pipelines.get(fragment);
        if (pipeline == null) {
            var vertexShader = vertex;
            if (vertexShader == null) {
                vertexShader = on.createShader(code(BuiltInShader.QUAD_VERTEX, ShaderStage.VERTEX));
                vertex = vertexShader;
            }
            var fragmentShader = on.createShader(code(fragment, ShaderStage.FRAGMENT));
            fragments.put(fragment, fragmentShader);
            pipeline =
                    on.createPipeline(PipelineSpec.builder(vertexShader, fragmentShader, TextureFormat.B8G8R8A8_UNORM)
                            .build());
            pipelines.put(fragment, pipeline);
        }
        return pipeline;
    }

    private GpuSampler sampler(GpuDevice on) {
        var current = sampler;
        if (current == null) {
            current = on.createSampler(SamplerSpec.linear());
            sampler = current;
        }
        return current;
    }

    /// One of the toolkit's shaders as the GPU API loads an application's.
    private static ShaderCode code(BuiltInShader shader, ShaderStage stage) {
        return ShaderCode.load(
                stage,
                SHADERS + shader.fileName(),
                shader.samplers(),
                shader.uniformBuffers(),
                VideoLayer.class::getResourceAsStream);
    }

    /// Releases everything made on the device. Rendering afterwards throws.
    /// Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        release();
        image = null;
        source = null;
    }

    /// Back to before the first render: what was made on the device released,
    /// if the device is still open (a closed one released it all itself).
    private void release() {
        var made = device;
        device = null;
        uploaded = null;
        rendered = null;
        renderedSource = null;
        if (made != null && !made.isClosed()) {
            closeTextures();
            pipelines.values().forEach(GraphicsPipeline::close);
            fragments.values().forEach(Shader::close);
            if (vertex != null) {
                vertex.close();
            }
            if (sampler != null) {
                sampler.close();
            }
        }
        textures.clear();
        texturesKey = null;
        pipelines.clear();
        fragments.clear();
        vertex = null;
        sampler = null;
    }

    private void closeTextures() {
        textures.forEach(GpuTexture::close);
        textures.clear();
        texturesKey = null;
    }

    @Override
    public String toString() {
        return "video[" + image + "]";
    }
}
