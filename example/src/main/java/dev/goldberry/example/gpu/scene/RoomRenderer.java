package dev.goldberry.example.gpu.scene;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.AddressMode;
import dev.goldberry.gpu.BufferUsage;
import dev.goldberry.gpu.DepthTarget;
import dev.goldberry.gpu.DepthTest;
import dev.goldberry.gpu.GpuBuffer;
import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuException;
import dev.goldberry.gpu.GpuFeatures;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.GpuResource;
import dev.goldberry.gpu.GpuSampler;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.GraphicsPipeline;
import dev.goldberry.gpu.Load;
import dev.goldberry.gpu.PipelineSpec;
import dev.goldberry.gpu.RenderPass;
import dev.goldberry.gpu.SamplerBinding;
import dev.goldberry.gpu.SamplerSpec;
import dev.goldberry.gpu.Shader;
import dev.goldberry.gpu.ShaderCode;
import dev.goldberry.gpu.ShaderStage;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.TextureUsage;
import dev.goldberry.gpu.VertexAttribute;
import dev.goldberry.gpu.VertexBufferLayout;
import dev.goldberry.gpu.VertexFormat;
import dev.goldberry.gpu.raytrace.Lighting;
import dev.goldberry.gpu.raytrace.LightingRequest;
import dev.goldberry.gpu.raytrace.RayScene;
import dev.goldberry.gpu.raytrace.RayTracer;
import dev.goldberry.gpu.raytrace.RayTracingMode;
import dev.goldberry.gpu.raytrace.TraceRequest;
import dev.goldberry.gpu.view.Canvas3dRenderer;
import dev.goldberry.gpu.view.Canvas3dTarget;
import dev.goldberry.render.model.PhysicalSize;

/// The ray-traced room, drawn by a `canvas3d`: a renderer an application
/// writes, with the GPU API for the picture and the ray tracer for its light.
///
/// Every frame it places the room's solids for the frame's time and draws
/// them. With ray tracing on it first traces the room's lighting at the size
/// the canvas draws at — a shadow ray, occlusion rays and a reflection ray from
/// every pixel's hit — and uploads it into two textures the fragment shader
/// reads a texel a pixel. The tracer runs on a device of its own, so the
/// lighting crosses through this process's memory: eight bytes a pixel.
///
/// [SceneSettings] are read as the frame starts. Where the device lacks the
/// anti-aliasing asked for, the most it has below it is used. Where there is no
/// ray tracing the renderer says why, once, and draws the room lit by the sun's
/// angle alone.
///
/// Read more: [The renderer](https://goldberry.dev/docs/components/gpu.html#the-renderer).
public final class RoomRenderer implements Canvas3dRenderer {

    /// The colour and depth formats it draws into.
    private static final TextureFormat COLOUR = TextureFormat.B8G8R8A8_UNORM;

    private static final TextureFormat DEPTH = TextureFormat.D32_FLOAT;

    /// The background, Nord's polar night.
    private static final Load CLEAR = Load.clear(0.180f, 0.204f, 0.251f, 1);

    /// How many occlusion rays a hit, and how far they look.
    static final int OCCLUSION_RAYS = 12;

    static final float OCCLUSION_RADIUS = 1.4f;

    /// The floor texture's side, in texels, and the side of a check.
    private static final int CHECKS = 256;

    private static final int CHECK = 32;

    private final Supplier<SceneSettings> settings;
    private final Consumer<String> onUnavailable;
    private final RoomScene room = new RoomScene();
    private final List<GpuResource> made = new ArrayList<>();
    /// By identity: a mesh does not define equality.
    private final Map<Mesh, GpuBuffer> buffers = new HashMap<>();
    private final Map<Integer, GraphicsPipeline> pipelines = new HashMap<>();
    private final Map<Integer, GpuSampler> anisotropic = new HashMap<>();

    private @Nullable GpuDevice device;
    private @Nullable GpuFeatures features;
    private @Nullable Shader vertex;
    private @Nullable Shader fragment;
    private @Nullable GpuSampler nearest;
    private @Nullable GpuTexture checks;
    private @Nullable GpuTexture untracedShading;
    private @Nullable GpuTexture untracedReflections;
    private @Nullable GpuTexture shading;
    private @Nullable GpuTexture reflections;
    private @Nullable GpuTexture multisampled;
    private @Nullable GpuTexture multisampledDepth;
    private boolean uploaded;

    private @Nullable RayTracer tracer;
    private @Nullable RayScene traced;
    private @Nullable String refused;

    private int samplesInUse = 1;
    private boolean tracedLastFrame;

    /// A renderer that reads `settings` as each frame starts, and tells
    /// `onUnavailable` why, once, when ray tracing is asked for and there is
    /// none. Both are called on the UI thread, from inside a paint.
    public RoomRenderer(Supplier<SceneSettings> settings, Consumer<String> onUnavailable) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.onUnavailable = Objects.requireNonNull(onUnavailable, "onUnavailable");
    }

    @Override
    public void init(GpuDevice on) {
        device = on;
        features = on.features();
        vertex = keep(on.createShader(ShaderCode.load(ShaderStage.VERTEX, "room.vert", 0, 1, RoomRenderer::resource)));
        fragment =
                keep(on.createShader(ShaderCode.load(ShaderStage.FRAGMENT, "room.frag", 3, 2, RoomRenderer::resource)));
        nearest = keep(on.createSampler(SamplerSpec.nearest()));
        checks = keep(on.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, CHECKS, CHECKS)
                .withMipChain()));
        untracedShading = keep(on.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 1, 1)));
        untracedReflections = keep(on.createTexture(TextureSpec.sampled(TextureFormat.R32_FLOAT, 1, 1)));
        for (var mesh : room.meshes()) {
            buffers.put(mesh, keep(on.createBuffer(BufferUsage.VERTEX, mesh.vertexCount() * Mesh.STRIDE)));
        }
        uploaded = false;
    }

    @Override
    public void resize(PhysicalSize size) {
        // The traced textures and the multisampled targets follow the size
        // drawn at, and are made again at the next frame that needs them.
        for (var texture : new @Nullable GpuTexture[] {shading, reflections, multisampled, multisampledDepth}) {
            release(texture);
        }
        shading = null;
        reflections = null;
        multisampled = null;
        multisampledDepth = null;
    }

    @Override
    public void render(GpuFrame frame, Canvas3dTarget target) {
        var on = required(device);
        var asked = settings.get();
        if (!uploaded) {
            upload(frame);
            uploaded = true;
        }
        var placed = room.at(target.seconds());
        var size = target.size();
        var lighting = asked.rayTracing() ? trace(placed, size, asked) : null;
        tracedLastFrame = lighting != null;
        if (lighting != null) {
            var shadingTexture = sized(on, shading, TextureFormat.R8G8B8A8_UNORM, size);
            var reflectionTexture = sized(on, reflections, TextureFormat.R32_FLOAT, size);
            shading = shadingTexture;
            reflections = reflectionTexture;
            frame.copyPass(copy -> {
                copy.upload(shadingTexture, lighting.shading());
                copy.upload(reflectionTexture, lighting.reflectedIds());
            });
        }
        var samples = samplesFor(asked.samples());
        samplesInUse = samples;
        var pipeline = pipeline(on, samples);
        var floorSampler = anisotropic(on, asked.anisotropy());
        var scene = sceneUniforms(asked, lighting != null, size);
        var viewProjection = RoomScene.viewProjection(target.aspect());
        var shadingInput = lighting != null ? required(shading) : required(untracedShading);
        var reflectionInput = lighting != null ? required(reflections) : required(untracedReflections);
        var samplers = List.of(
                new SamplerBinding(shadingInput, required(nearest)),
                new SamplerBinding(reflectionInput, required(nearest)),
                new SamplerBinding(required(checks), floorSampler));
        Consumer<RenderPass> draw = pass -> {
            pass.bindPipeline(pipeline);
            pass.bindFragmentSamplers(samplers);
            pass.pushFragmentUniforms(0, scene);
            for (var solid : placed) {
                var transform = new float[32];
                System.arraycopy(viewProjection, 0, transform, 0, 16);
                System.arraycopy(solid.model(), 0, transform, 16, 16);
                pass.pushVertexUniforms(0, transform);
                pass.pushFragmentUniforms(1, solid.id(), solid.id() == RoomScene.FLOOR ? 1 : 0, 0, 0);
                pass.bindVertexBuffer(0, required(buffers.get(solid.mesh())));
                pass.draw(solid.mesh().vertexCount());
            }
        };
        if (samples == 1) {
            frame.renderPass(target.colour(), CLEAR, target.clearDepth(), draw);
            return;
        }
        var colour = multisampled(on, multisampled, COLOUR, size, samples);
        var depth = multisampled(on, multisampledDepth, DEPTH, size, samples);
        multisampled = colour;
        multisampledDepth = depth;
        frame.renderPass(colour, CLEAR, target.colour().view(0, 0), DepthTarget.clear(depth), draw);
    }

    /// The room's lighting for `placed` at `size`, or null where there is no
    /// ray tracing, said once to the panel.
    private @Nullable Lighting trace(List<RoomScene.Placed> placed, PhysicalSize size, SceneSettings asked) {
        if (refused != null) {
            return null;
        }
        try {
            var opened = tracer;
            if (opened == null) {
                opened = RayTracer.open(RayTracingMode.HARDWARE).orElse(null);
                if (opened == null) {
                    refuse(RayTracingMode.PROPERTY + " is off");
                    return null;
                }
                tracer = opened;
            }
            var geometry = RoomScene.geometry(placed);
            var scene = traced;
            if (scene == null) {
                scene = opened.build(geometry);
                traced = scene;
            } else {
                scene.update(geometry);
            }
            var rays = TraceRequest.of(RoomScene.camera(), size.width(), size.height())
                    .withShadowsToward(RoomScene.SUN);
            return opened.traceLighting(
                    scene,
                    LightingRequest.of(rays)
                            .withOcclusion(asked.occlusion() ? OCCLUSION_RAYS : 0, OCCLUSION_RADIUS)
                            .withReflections(asked.reflections()));
        } catch (UnsupportedOperationException | GpuException e) {
            refuse(e.getMessage() == null ? e.toString() : e.getMessage());
            return null;
        }
    }

    private void refuse(String reason) {
        refused = reason;
        closeTracer();
        onUnavailable.accept(reason);
    }

    /// The scene's uniform block: the sun, the effects, the traced picture's
    /// last pixel, the sky and the materials.
    private static float[] sceneUniforms(SceneSettings asked, boolean fromTrace, PhysicalSize size) {
        var uniforms = new float[16 + RoomScene.MATERIALS.length * 4];
        uniforms[0] = RoomScene.SUN.x();
        uniforms[1] = RoomScene.SUN.y();
        uniforms[2] = RoomScene.SUN.z();
        uniforms[3] = fromTrace ? 1 : 0;
        uniforms[4] = asked.shadows() ? 1 : 0;
        uniforms[5] = asked.occlusion() ? 1 : 0;
        uniforms[6] = asked.reflections() ? 1 : 0;
        uniforms[8] = fromTrace ? size.width() - 1 : 0;
        uniforms[9] = fromTrace ? size.height() - 1 : 0;
        System.arraycopy(RoomScene.SKY, 0, uniforms, 12, 3);
        for (var i = 0; i < RoomScene.MATERIALS.length; i++) {
            System.arraycopy(RoomScene.MATERIALS[i], 0, uniforms, 16 + i * 4, 4);
        }
        return uniforms;
    }

    /// The meshes, the floor's checks and their mip levels, and the textures
    /// read when nothing was traced: lit, open, seeing nothing.
    private void upload(GpuFrame frame) {
        frame.copyPass(copy -> {
            for (var mesh : room.meshes()) {
                copy.upload(required(buffers.get(mesh)), 0, mesh.bytes());
            }
            copy.upload(required(checks).view(0, 0), checks());
            copy.upload(required(untracedShading), ByteBuffer.wrap(new byte[] {(byte) 255, (byte) 255, 0, 0}));
            copy.upload(
                    required(untracedReflections),
                    ByteBuffer.allocate(Float.BYTES)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putFloat(0, Lighting.NO_REFLECTION));
        });
        frame.generateMipmaps(required(checks));
    }

    /// Light and lighter grey checks, in RGBA.
    private static ByteBuffer checks() {
        var bytes = ByteBuffer.allocate(CHECKS * CHECKS * 4);
        for (var y = 0; y < CHECKS; y++) {
            for (var x = 0; x < CHECKS; x++) {
                var light = ((x / CHECK) + (y / CHECK)) % 2 == 0;
                var grey = (byte) (light ? 255 : 170);
                bytes.put(grey).put(grey).put(grey).put((byte) 255);
            }
        }
        return bytes.flip();
    }

    /// The most samples up to `asked` the device draws both colour and depth
    /// with.
    private int samplesFor(int asked) {
        var known = features;
        if (known == null) {
            return 1;
        }
        var best = 1;
        for (var count : known.colorSampleCounts()) {
            if (count <= asked && count > best && known.depthSampleCounts().contains(count)) {
                best = count;
            }
        }
        return best;
    }

    private GraphicsPipeline pipeline(GpuDevice on, int samples) {
        var pipeline = pipelines.get(samples);
        if (pipeline == null) {
            pipeline = keep(on.createPipeline(PipelineSpec.builder(required(vertex), required(fragment), COLOUR)
                    .vertexBuffer(VertexBufferLayout.perVertex(
                            0,
                            Mesh.STRIDE,
                            VertexAttribute.of(0, VertexFormat.FLOAT3, 0),
                            VertexAttribute.of(1, VertexFormat.FLOAT3, 12)))
                    .depthTest(DepthTest.less(DEPTH))
                    .samples(samples)
                    .build()));
            pipelines.put(samples, pipeline);
        }
        return pipeline;
    }

    private GpuSampler anisotropic(GpuDevice on, int anisotropy) {
        var sampler = anisotropic.get(anisotropy);
        if (sampler == null) {
            sampler = keep(on.createSampler(
                    SamplerSpec.trilinear().withAddressMode(AddressMode.REPEAT).withAnisotropy(anisotropy)));
            anisotropic.put(anisotropy, sampler);
        }
        return sampler;
    }

    /// `current` when it is `size`, else a new sampled texture of `format` at
    /// it, `current` released.
    private GpuTexture sized(GpuDevice on, @Nullable GpuTexture current, TextureFormat format, PhysicalSize size) {
        if (current != null && current.width() == size.width() && current.height() == size.height()) {
            return current;
        }
        release(current);
        return keep(on.createTexture(TextureSpec.sampled(format, size.width(), size.height())));
    }

    /// `current` when it is `size` and `samples`, else a new multisampled
    /// target of `format`, `current` released.
    private GpuTexture multisampled(
            GpuDevice on, @Nullable GpuTexture current, TextureFormat format, PhysicalSize size, int samples) {
        if (current != null
                && current.width() == size.width()
                && current.height() == size.height()
                && current.samples() == samples) {
            return current;
        }
        release(current);
        var usage = format.isDepth() ? TextureUsage.DEPTH_TARGET : TextureUsage.COLOR_TARGET;
        return keep(on.createTexture(
                new TextureSpec(format, size.width(), size.height(), EnumSet.of(usage)).withSamples(samples)));
    }

    /// How many samples a pixel the last frame was drawn with.
    public int samplesInUse() {
        return samplesInUse;
    }

    /// Whether the last frame was lit by the ray tracer.
    public boolean tracedLastFrame() {
        return tracedLastFrame;
    }

    @Override
    public void dispose() {
        closeTracer();
        var open = device;
        if (open != null && !open.isClosed()) {
            made.forEach(GpuResource::close);
        }
        made.clear();
        buffers.clear();
        pipelines.clear();
        anisotropic.clear();
        device = null;
        features = null;
        vertex = null;
        fragment = null;
        nearest = null;
        checks = null;
        untracedShading = null;
        untracedReflections = null;
        shading = null;
        reflections = null;
        multisampled = null;
        multisampledDepth = null;
        refused = null;
    }

    private void closeTracer() {
        var scene = traced;
        traced = null;
        if (scene != null) {
            scene.close();
        }
        var opened = tracer;
        tracer = null;
        if (opened != null) {
            opened.close();
        }
    }

    private <T extends GpuResource> T keep(T resource) {
        made.add(resource);
        return resource;
    }

    private void release(@Nullable GpuResource resource) {
        if (resource != null && made.remove(resource) && !resource.isClosed()) {
            resource.close();
        }
    }

    private static @Nullable InputStream resource(String name) {
        return RoomRenderer.class.getResourceAsStream("/dev/goldberry/example/gpu/" + name);
    }

    private static <T> T required(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("drawn before init");
        }
        return value;
    }
}
