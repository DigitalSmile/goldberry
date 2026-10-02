package dev.goldberry.gpu.view;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.BufferUsage;
import dev.goldberry.gpu.DepthTest;
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
import dev.goldberry.render.model.PhysicalSize;

/// A lit cube, drawn with depth: the `canvas3d` golden's scene, written as an
/// application's renderer would be, on the public API alone. It uses the
/// tests' mesh shaders (a position and a colour, placed by one matrix), so the
/// light is worked out here, per face, as the cube turns.
///
/// It turns by [#SPEED] radians a second from [#START], so the picture at a
/// frame's time is exact, and it records what its canvas asked of it.
final class TestCube implements Canvas3dRenderer {

    static final double START = 0.6;
    static final double SPEED = 1.0;

    private static final String SHADERS = "/dev/goldberry/gpu/testshaders/";
    private static final int STRIDE = 28;
    private static final int VERTICES = 36;

    /// Red, green, blue, yellow, magenta, cyan: +x, -x, +y, -y, +z, -z.
    private static final float[][] FACE_COLOURS = {
        {0.90f, 0.30f, 0.30f}, {0.30f, 0.80f, 0.35f}, {0.30f, 0.45f, 0.90f},
        {0.90f, 0.80f, 0.30f}, {0.80f, 0.35f, 0.80f}, {0.30f, 0.80f, 0.85f}
    };

    /// The calls it was given, in order: `init`, `resize WxH`, `render WxH`,
    /// `dispose`.
    final List<String> calls = new ArrayList<>();

    private @Nullable GpuDevice device;
    private @Nullable Shader vertex;
    private @Nullable Shader fragment;
    private @Nullable GraphicsPipeline pipeline;
    private @Nullable GpuBuffer vertices;

    @Override
    public void init(GpuDevice device) {
        calls.add("init");
        this.device = device;
        vertex = device.createShader(
                ShaderCode.load(ShaderStage.VERTEX, SHADERS + "mesh.vert", 0, 1, TestCube.class::getResourceAsStream));
        fragment = device.createShader(ShaderCode.load(
                ShaderStage.FRAGMENT, SHADERS + "mesh.frag", 0, 0, TestCube.class::getResourceAsStream));
        pipeline = device.createPipeline(PipelineSpec.builder(vertex, fragment, TextureFormat.B8G8R8A8_UNORM)
                .vertexBuffer(VertexBufferLayout.perVertex(
                        0,
                        STRIDE,
                        VertexAttribute.of(0, VertexFormat.FLOAT3, 0),
                        VertexAttribute.of(1, VertexFormat.FLOAT4, 12)))
                .depthTest(DepthTest.less(TextureFormat.D16_UNORM))
                .build());
        vertices = device.createBuffer(BufferUsage.VERTEX, STRIDE * VERTICES);
    }

    @Override
    public void resize(PhysicalSize size) {
        calls.add("resize " + size.width() + "x" + size.height());
    }

    @Override
    public void render(GpuFrame frame, Canvas3dTarget target) {
        calls.add("render " + target.colour().width() + "x" + target.colour().height());
        var buffer = required(vertices);
        var drawing = required(pipeline);
        var angle = START + SPEED * target.seconds();
        var model = multiply(rotateY(angle), rotateX(angle * 0.7));
        var clip = multiply(perspective(0.9, target.aspect(), 0.1, 10), multiply(translate(0, 0, -3.2f), model));
        var data = cube(model);
        frame.copyPass(copy -> copy.upload(buffer, 0, data));
        frame.renderPass(target.colour(), Load.clear(0.10f, 0.12f, 0.16f, 1), target.clearDepth(), pass -> {
            pass.bindPipeline(drawing);
            pass.bindVertexBuffer(0, buffer);
            pass.pushVertexUniforms(0, clip);
            pass.draw(VERTICES);
        });
    }

    @Override
    public void dispose() {
        calls.add("dispose");
        for (var resource : new AutoCloseable[] {vertices, pipeline, vertex, fragment}) {
            if (resource != null && device != null && !device.isClosed()) {
                try {
                    resource.close();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        vertices = null;
        pipeline = null;
        vertex = null;
        fragment = null;
        device = null;
    }

    private static <T> T required(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("rendered before init");
        }
        return value;
    }

    /// Six faces of two triangles, each coloured by its face's colour times how
    /// squarely `model` turns it to a light up, left and towards the viewer.
    private static ByteBuffer cube(float[] model) {
        var light = normalise(new double[] {-0.4, 0.6, 0.7});
        var bytes = ByteBuffer.allocate(STRIDE * VERTICES).order(ByteOrder.nativeOrder());
        for (var face = 0; face < 6; face++) {
            var axis = face / 2;
            var sign = face % 2 == 0 ? 1f : -1f;
            var normal = new double[3];
            normal[axis] = sign;
            var turned = new double[] {
                model[0] * normal[0] + model[1] * normal[1] + model[2] * normal[2],
                model[4] * normal[0] + model[5] * normal[1] + model[6] * normal[2],
                model[8] * normal[0] + model[9] * normal[1] + model[10] * normal[2]
            };
            var lit = (float) (0.25 + 0.75 * Math.max(0, dot(turned, light)));
            var colour = FACE_COLOURS[face];
            // The face's four corners: the two axes other than its own.
            var u = (axis + 1) % 3;
            var v = (axis + 2) % 3;
            float[][] corners = new float[4][3];
            float[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (var i = 0; i < 4; i++) {
                corners[i][axis] = sign * 0.5f;
                corners[i][u] = signs[i][0] * 0.5f;
                corners[i][v] = signs[i][1] * 0.5f;
            }
            for (var index : new int[] {0, 1, 2, 0, 2, 3}) {
                bytes.putFloat(corners[index][0]).putFloat(corners[index][1]).putFloat(corners[index][2]);
                bytes.putFloat(colour[0] * lit)
                        .putFloat(colour[1] * lit)
                        .putFloat(colour[2] * lit)
                        .putFloat(1);
            }
        }
        return bytes.flip();
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] normalise(double[] v) {
        var length = Math.sqrt(dot(v, v));
        return new double[] {v[0] / length, v[1] / length, v[2] / length};
    }

    // Row-major 4x4 matrices, which is how the mesh shader reads its sixteen
    // floats: a point is a column, multiplied on the right.

    static float[] multiply(float[] a, float[] b) {
        var out = new float[16];
        for (var row = 0; row < 4; row++) {
            for (var column = 0; column < 4; column++) {
                var sum = 0f;
                for (var k = 0; k < 4; k++) {
                    sum += a[row * 4 + k] * b[k * 4 + column];
                }
                out[row * 4 + column] = sum;
            }
        }
        return out;
    }

    static float[] rotateY(double angle) {
        var c = (float) Math.cos(angle);
        var s = (float) Math.sin(angle);
        return new float[] {c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
    }

    static float[] rotateX(double angle) {
        var c = (float) Math.cos(angle);
        var s = (float) Math.sin(angle);
        return new float[] {1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
    }

    static float[] translate(float x, float y, float z) {
        return new float[] {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
    }

    /// A right-handed perspective onto SDL's clip space: y up, depth from 0 at
    /// `near` to 1 at `far`.
    static float[] perspective(double fovY, double aspect, double near, double far) {
        var f = (float) (1 / Math.tan(fovY / 2));
        var depth = (float) (far / (near - far));
        var offset = (float) (near * far / (near - far));
        return new float[] {f / (float) aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, depth, offset, 0, 0, -1, 0};
    }
}
