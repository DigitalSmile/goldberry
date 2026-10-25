package dev.goldberry.gpu.upscale;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.GpuResource;
import dev.goldberry.gpu.GpuSampler;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.GraphicsPipeline;
import dev.goldberry.gpu.Load;
import dev.goldberry.gpu.PipelineSpec;
import dev.goldberry.gpu.SamplerSpec;
import dev.goldberry.gpu.Shader;
import dev.goldberry.gpu.ShaderCode;
import dev.goldberry.gpu.ShaderStage;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.TextureUsage;
import dev.goldberry.gpu.render.BuiltInShader;
import dev.goldberry.gpu.render.Quad;
import dev.goldberry.gpu.upscale.fsr.Fsr1;
import dev.goldberry.render.model.PhysicalSize;

/// Brings a picture drawn at one size to another, on the GPU: what a `canvas3d`
/// with a [RenderScale] does after its renderer has drawn, and what a renderer
/// of its own can do with any texture it drew.
///
/// ```java
/// upscaler = new Upscaler(device);
/// // each frame, after drawing the scene into `small`:
/// upscaler.upscale(frame, small, target, UpscaleFilter.FSR1, RenderScale.DEFAULT_SHARPNESS);
/// ```
///
/// Each pass is a render pass over the whole target with one of the toolkit's
/// shaders: bilinear, or FSR 1's EASU and RCAS. Between EASU and RCAS the
/// picture is held in a texture of the upscaler's own at the target's size and
/// format, made on first use and kept while the size does not change.
///
/// FSR 1 wants a picture whose values are perceptual, as a `B8G8R8A8_UNORM`
/// target drawn by a renderer that writes display colours holds them, and
/// leaves alpha opaque.
///
/// Made for one device, on its thread, like everything it makes.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
public final class Upscaler implements AutoCloseable {

    /// Where the toolkit's shaders are, in this module.
    private static final String SHADERS = "/dev/goldberry/gpu/shaders/";

    /// One render pass of an upscale, and the fragment shader it draws with.
    enum Pass {
        /// Bilinear, through the toolkit's sampling shader and a linear sampler.
        LINEAR(BuiltInShader.TEXTURE_FRAGMENT),
        /// FSR 1's upscale.
        EASU(BuiltInShader.EASU_FRAGMENT),
        /// FSR 1's sharpening, at the target's size.
        RCAS(BuiltInShader.RCAS_FRAGMENT);

        private final BuiltInShader shader;

        Pass(BuiltInShader shader) {
            this.shader = shader;
        }
    }

    private record PipelineKey(Pass pass, TextureFormat format) {}

    private final GpuDevice device;
    private final Map<Pass, Shader> fragments = new EnumMap<>(Pass.class);
    private final Map<PipelineKey, GraphicsPipeline> pipelines = new HashMap<>();
    private @Nullable Shader vertex;
    private @Nullable GpuSampler linear;
    private @Nullable GpuSampler nearest;
    private @Nullable GpuTexture between;
    private boolean closed;

    /// An upscaler for `device`. Nothing is made on it until the first
    /// [#upscale].
    public Upscaler(GpuDevice device) {
        this.device = Objects.requireNonNull(device, "device");
    }

    /// The device it draws on.
    public GpuDevice device() {
        return device;
    }

    /// The passes that bring a picture of `from` to `to` with `filter`, in
    /// order.
    ///
    /// FSR 1 upscales with EASU where the picture is smaller on an axis and on
    /// neither larger, then sharpens with RCAS unless `sharpness` is 0; at the
    /// same size it sharpens alone; a picture larger on either axis is brought
    /// down linearly, which EASU is not made for. A copy at the same size with
    /// nothing to sharpen is a linear pass, which samples texel centres and so
    /// changes nothing.
    static List<Pass> passes(UpscaleFilter filter, double sharpness, PhysicalSize from, PhysicalSize to) {
        if (filter == UpscaleFilter.LINEAR) {
            return List.of(Pass.LINEAR);
        }
        var larger = from.width() > to.width() || from.height() > to.height();
        if (larger) {
            return List.of(Pass.LINEAR);
        }
        var sharpen = sharpness > 0;
        if (from.equals(to)) {
            return List.of(sharpen ? Pass.RCAS : Pass.LINEAR);
        }
        return sharpen ? List.of(Pass.EASU, Pass.RCAS) : List.of(Pass.EASU);
    }

    /// Records the passes that bring the whole of `source` to the whole of
    /// `target` with `filter` into `frame`, outside any pass.
    ///
    /// @param sharpness how much [UpscaleFilter#FSR1] sharpens, 0 to 1, as
    ///                  [RenderScale#sharpness]
    /// @throws IllegalArgumentException when the frame is another device's,
    ///                                  `source` is `target`, was not made to
    ///                                  be sampled or is a depth texture,
    ///                                  `target` was not made to be rendered
    ///                                  into, or the sharpness is outside 0 to
    ///                                  1
    /// @throws IllegalStateException    when the upscaler is closed, or the
    ///                                  frame is finished or inside a pass
    public void upscale(GpuFrame frame, GpuTexture source, GpuTexture target, UpscaleFilter filter, double sharpness) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(filter, "filter");
        if (closed) {
            throw new IllegalStateException("this upscaler is closed");
        }
        if (frame.device() != device) {
            throw new IllegalArgumentException("a frame of " + frame.device() + " for an upscaler of " + device);
        }
        if (source == target) {
            throw new IllegalArgumentException(source + " cannot be upscaled into itself");
        }
        if (source.format().isDepth() || !source.usages().contains(TextureUsage.SAMPLER)) {
            throw new IllegalArgumentException(source + " was not made to be sampled");
        }
        if (!target.usages().contains(TextureUsage.COLOR_TARGET)) {
            throw new IllegalArgumentException(target + " was not made to be rendered into");
        }
        if (!(sharpness >= 0 && sharpness <= 1)) {
            throw new IllegalArgumentException("sharpness " + sharpness + "; it is 0 to 1");
        }
        var passes = passes(filter, sharpness, sizeOf(source), sizeOf(target));
        var input = source;
        for (var i = 0; i < passes.size(); i++) {
            var output = i == passes.size() - 1 ? target : between(target);
            draw(frame, passes.get(i), input, output, sharpness);
            input = output;
        }
    }

    private void draw(GpuFrame frame, Pass pass, GpuTexture input, GpuTexture output, double sharpness) {
        var pipeline = pipeline(pass, output.format());
        var sampler = pass == Pass.LINEAR ? linear() : nearest();
        var width = output.width();
        var height = output.height();
        var constants = switch (pass) {
            case LINEAR -> null;
            case EASU -> Fsr1.easu(input.width(), input.height(), width, height);
            case RCAS -> Fsr1.rcas(sharpness, width, height);
        };
        // Every pixel is drawn, so what was there is not loaded.
        frame.renderPass(output, Load.dontCare(), render -> {
            render.bindPipeline(pipeline);
            render.pushVertexUniforms(
                    0, Quad.of(0, 0, width, height, width, height).uniforms());
            if (constants != null) {
                render.pushFragmentUniforms(0, constants);
            }
            render.bindFragmentSamplers(sampler, input);
            render.draw(Quad.VERTICES);
        });
    }

    /// EASU's output and RCAS's input, at the target's size and format.
    private GpuTexture between(GpuTexture target) {
        var current = between;
        if (current != null
                && current.width() == target.width()
                && current.height() == target.height()
                && current.format() == target.format()) {
            return current;
        }
        if (current != null) {
            current.close();
        }
        current = device.createTexture(TextureSpec.renderTarget(target.format(), target.width(), target.height()));
        current.setDebugName("upscaler: EASU to RCAS");
        between = current;
        return current;
    }

    private GraphicsPipeline pipeline(Pass pass, TextureFormat format) {
        var key = new PipelineKey(pass, format);
        var pipeline = pipelines.get(key);
        if (pipeline == null) {
            var vertexShader = vertex;
            if (vertexShader == null) {
                vertexShader = device.createShader(code(BuiltInShader.QUAD_VERTEX, ShaderStage.VERTEX));
                vertex = vertexShader;
            }
            var fragmentShader = fragments.get(pass);
            if (fragmentShader == null) {
                fragmentShader = device.createShader(code(pass.shader, ShaderStage.FRAGMENT));
                fragments.put(pass, fragmentShader);
            }
            pipeline = device.createPipeline(
                    PipelineSpec.builder(vertexShader, fragmentShader, format).build());
            pipelines.put(key, pipeline);
        }
        return pipeline;
    }

    private GpuSampler linear() {
        var current = linear;
        if (current == null) {
            current = device.createSampler(SamplerSpec.linear());
            linear = current;
        }
        return current;
    }

    /// Clamped to the edge, which is what EASU's gathers and RCAS's ring read
    /// outside the picture; neither filters.
    private GpuSampler nearest() {
        var current = nearest;
        if (current == null) {
            current = device.createSampler(SamplerSpec.nearest());
            nearest = current;
        }
        return current;
    }

    private static PhysicalSize sizeOf(GpuTexture texture) {
        return new PhysicalSize(texture.width(), texture.height());
    }

    /// One of the toolkit's shaders, loaded as the GPU API loads an
    /// application's.
    private static ShaderCode code(BuiltInShader shader, ShaderStage stage) {
        return ShaderCode.load(
                stage,
                SHADERS + shader.fileName(),
                shader.samplers(),
                shader.uniformBuffers(),
                Upscaler.class::getResourceAsStream);
    }

    /// Releases what it made on the device, if the device is still open: a
    /// closed one released it all itself. Upscaling afterwards throws.
    /// Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!device.isClosed()) {
            var made = new ArrayList<GpuResource>(pipelines.values());
            made.addAll(fragments.values());
            for (var resource : new GpuResource[] {vertex, linear, nearest, between}) {
                if (resource != null) {
                    made.add(resource);
                }
            }
            made.forEach(GpuResource::close);
        }
        pipelines.clear();
        fragments.clear();
        vertex = null;
        linear = null;
        nearest = null;
        between = null;
    }

    @Override
    public String toString() {
        return "Upscaler[" + device + (closed ? ", closed]" : "]");
    }
}
