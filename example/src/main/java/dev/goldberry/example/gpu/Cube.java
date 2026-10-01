package dev.goldberry.example.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.DoubleUnaryOperator;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.BufferUsage;
import dev.goldberry.gpu.CullMode;
import dev.goldberry.gpu.DepthTest;
import dev.goldberry.gpu.FrontFace;
import dev.goldberry.gpu.GpuBuffer;
import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.GraphicsPipeline;
import dev.goldberry.gpu.Load;
import dev.goldberry.gpu.PipelineSpec;
import dev.goldberry.gpu.Shader;
import dev.goldberry.gpu.ShaderCode;
import dev.goldberry.gpu.ShaderStage;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.VertexAttribute;
import dev.goldberry.gpu.VertexBufferLayout;
import dev.goldberry.gpu.VertexFormat;
import dev.goldberry.gpu.view.Canvas3dRenderer;
import dev.goldberry.gpu.view.Canvas3dTarget;

/// The GPU screen's lit cube: a `canvas3d` renderer written as an application
/// writes one (`docs/gpu-plan.md`, phase 5).
///
/// Its shaders are the showcase's own HLSL, `src/main/shaders/cube.*.hlsl`,
/// compiled by `:gpu:compileShaders` into this package's resources and loaded
/// with [ShaderCode#load]. The mesh -- a position, a normal and a colour per
/// vertex -- goes up once, on the first frame, and the light is worked out in
/// the vertex shader from one matrix for the place and one for the turn.
///
/// How far it is turned is a function of the frame's time: [#spinning] turns
/// with it, and [#turnedBy] turns to whatever its supplier says, for a canvas
/// that is drawn only when that changes.
public final class Cube implements Canvas3dRenderer {

    private static final int STRIDE = 40;
    private static final int VERTICES = 36;

    /// One per face, Nord's aurora and frost: +x, -x, +y, -y, +z, -z.
    private static final float[][] FACE_COLOURS = {
        {0.749f, 0.380f, 0.416f}, {0.639f, 0.745f, 0.549f}, {0.506f, 0.631f, 0.757f},
        {0.922f, 0.796f, 0.545f}, {0.706f, 0.557f, 0.678f}, {0.533f, 0.753f, 0.816f}
    };

    private final DoubleUnaryOperator angle;

    private @Nullable Shader vertex;
    private @Nullable Shader fragment;
    private @Nullable GraphicsPipeline pipeline;
    private @Nullable GpuBuffer mesh;
    private boolean uploaded;

    private Cube(DoubleUnaryOperator angle) {
        this.angle = angle;
    }

    /// A cube that turns half a radian a second, from the canvas's first frame.
    public static Cube spinning() {
        return new Cube(seconds -> 0.6 + 0.5 * seconds);
    }

    /// A cube turned to `turn.get()` radians whenever it is drawn.
    public static Cube turnedBy(java.util.function.DoubleSupplier turn) {
        return new Cube(seconds -> turn.getAsDouble());
    }

    @Override
    public void init(GpuDevice device) {
        vertex = device.createShader(ShaderCode.load(ShaderStage.VERTEX, "cube.vert", 0, 1, Cube::resource));
        fragment = device.createShader(ShaderCode.load(ShaderStage.FRAGMENT, "cube.frag", 0, 0, Cube::resource));
        pipeline = device.createPipeline(PipelineSpec.builder(vertex, fragment, TextureFormat.B8G8R8A8_UNORM)
                .vertexBuffer(VertexBufferLayout.perVertex(
                        0,
                        STRIDE,
                        VertexAttribute.of(0, VertexFormat.FLOAT3, 0),
                        VertexAttribute.of(1, VertexFormat.FLOAT3, 12),
                        VertexAttribute.of(2, VertexFormat.FLOAT4, 24)))
                .cull(CullMode.BACK, FrontFace.COUNTER_CLOCKWISE)
                .depthTest(DepthTest.less(TextureFormat.D16_UNORM))
                .build());
        mesh = device.createBuffer(BufferUsage.VERTEX, STRIDE * VERTICES);
        uploaded = false;
    }

    @Override
    public void render(GpuFrame frame, Canvas3dTarget target) {
        var buffer = required(mesh);
        var drawing = required(pipeline);
        if (!uploaded) {
            var data = mesh();
            frame.copyPass(copy -> copy.upload(buffer, 0, data));
            uploaded = true;
        }
        var turn = angle.applyAsDouble(target.seconds());
        var model = Matrices.multiply(Matrices.rotateY(turn), Matrices.rotateX(turn * 0.7));
        var clip = Matrices.multiply(
                Matrices.perspective(0.9, target.aspect(), 0.1, 10),
                Matrices.multiply(Matrices.translate(0, 0, -3.2f), model));
        var uniforms = new float[36];
        System.arraycopy(clip, 0, uniforms, 0, 16);
        System.arraycopy(model, 0, uniforms, 16, 16);
        // Towards the light: above, a little right, and towards the viewer, so
        // every face the cube turns to the front is lit.
        uniforms[32] = 0.35f;
        uniforms[33] = 0.7f;
        uniforms[34] = 0.75f;
        frame.renderPass(target.colour(), Load.clear(0.180f, 0.204f, 0.251f, 1), target.clearDepth(), pass -> {
            pass.bindPipeline(drawing);
            pass.bindVertexBuffer(0, buffer);
            pass.pushVertexUniforms(0, uniforms);
            pass.draw(VERTICES);
        });
    }

    @Override
    public void dispose() {
        for (var resource : new @Nullable AutoCloseable[] {mesh, pipeline, vertex, fragment}) {
            if (resource instanceof dev.goldberry.gpu.GpuResource gpu && !gpu.isClosed()) {
                gpu.close();
            }
        }
        mesh = null;
        pipeline = null;
        vertex = null;
        fragment = null;
    }

    private static java.io.@Nullable InputStream resource(String name) {
        return Cube.class.getResourceAsStream(name);
    }

    private static <T> T required(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("drawn before init");
        }
        return value;
    }

    /// Six faces of two counter-clockwise triangles, each with its face's
    /// outward normal and colour.
    private static ByteBuffer mesh() {
        var bytes = ByteBuffer.allocate(STRIDE * VERTICES).order(ByteOrder.nativeOrder());
        for (var face = 0; face < 6; face++) {
            var axis = face / 2;
            var sign = face % 2 == 0 ? 1f : -1f;
            var u = (axis + 1) % 3;
            var v = (axis + 2) % 3;
            var corners = new float[4][3];
            // Counter-clockwise seen from outside: u then v for a positive face,
            // v then u for a negative one.
            float[][] around = sign > 0
                    ? new float[][] {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}
                    : new float[][] {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            for (var i = 0; i < 4; i++) {
                corners[i][axis] = sign * 0.5f;
                corners[i][u] = around[i][0] * 0.5f;
                corners[i][v] = around[i][1] * 0.5f;
            }
            var normal = new float[3];
            normal[axis] = sign;
            var colour = FACE_COLOURS[face];
            for (var index : new int[] {0, 1, 2, 0, 2, 3}) {
                bytes.putFloat(corners[index][0]).putFloat(corners[index][1]).putFloat(corners[index][2]);
                bytes.putFloat(normal[0]).putFloat(normal[1]).putFloat(normal[2]);
                bytes.putFloat(colour[0])
                        .putFloat(colour[1])
                        .putFloat(colour[2])
                        .putFloat(1);
            }
        }
        return bytes.flip();
    }
}
