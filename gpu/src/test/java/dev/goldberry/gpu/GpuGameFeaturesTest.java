package dev.goldberry.gpu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.indirect.DispatchCommand;
import dev.goldberry.gpu.indirect.DrawCommand;
import dev.goldberry.gpu.indirect.IndexedDrawCommand;
import dev.goldberry.gpu.indirect.IndirectCommand;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalRect;

/// What a game asks of the GPU API after a lit cube, on a real device: draws
/// and dispatches whose counts a buffer holds (and a compute pass wrote), the
/// stencil test and its reference, blending by constants, copies from texture
/// to texture and buffer to buffer, textures sampled by compute and read as
/// storage by a vertex shader, and what the device says it is.
///
/// Each picture is checked against what the draw means, pixel by pixel away
/// from the edges, as [GpuApiTest] does; each misuse is refused in Java.
@Tag(GpuTestLauncher.TAG)
@DisplayName("what a game asks of the GPU API, on a real device")
class GpuGameFeaturesTest {

    private static final int SIZE = 32;
    private static final int BLACK = 0xFF000000;
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    private static SdlGpuDevice sdl;
    private static GpuDevice device;
    private static Shader meshVertex;
    private static Shader meshFragment;
    private static GraphicsPipeline mesh;
    private static int baseline;

    @BeforeAll
    static void createDevice() {
        sdl = GpuDeviceRequirement.enforce();
        device = GpuDevice.wrap(sdl);
        meshVertex = device.createShader(TestShaders.meshVertex());
        meshFragment = device.createShader(TestShaders.meshFragment());
        mesh = device.createPipeline(PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                .vertexBuffer(TestShaders.meshLayout())
                .build());
        baseline = sdl.openResources();
    }

    @AfterAll
    static void destroyDevice() {
        // Skipped before SDL was reached: nothing to give back.
        if (sdl == null) {
            return;
        }
        sdl.close();
        Sdl.get().quit();
    }

    @AfterEach
    void nothingLeaks() {
        var staging = device.upload().capacity() > 0 ? 1 : 0;
        assertEquals(baseline + staging, sdl.openResources(), "a test left a resource open");
    }

    @Nested
    @DisplayName("indirect draws and dispatches")
    class Indirect {

        @Test
        @DisplayName("draw what an uploaded buffer of commands says, a zero count drawing nothing")
        void uploadedCommands() {
            var commands = IndirectCommand.pack(List.of(new DrawCommand(6, 1, 0, 0), new DrawCommand(0, 1, 0, 0)));
            try (var quad = buffer(BufferUsage.VERTEX, quad(8, 8, 24, 24, 1, 0, 0));
                    var indirect = buffer(BufferUsage.INDIRECT, commands)) {
                var pixels = draw(pass -> {
                    pass.bindPipeline(mesh);
                    pass.bindVertexBuffer(0, quad);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.drawIndirect(indirect, 0, 2);
                });
                assertSquare(pixels, 8, 24, RED);
            }
        }

        @Test
        @DisplayName("draw the count a compute pass wrote, with nothing read back")
        void commandsTheGpuWrote() {
            for (var vertices : new int[] {6, 0}) {
                try (var quad = buffer(BufferUsage.VERTEX, quad(8, 8, 24, 24, 1, 0, 0));
                        var indirect = device.createBuffer(
                                EnumSet.of(BufferUsage.COMPUTE_STORAGE_WRITE, BufferUsage.INDIRECT),
                                DrawCommand.BYTES);
                        var writer = device.createComputePipeline(TestShaders.commandCompute());
                        var target = renderTarget();
                        var frame = device.beginFrame()) {
                    frame.computePass(indirect, pass -> {
                        pass.bindPipeline(writer);
                        pass.pushUniforms(0, uints(vertices, 1, 0, 0));
                        pass.dispatch(1);
                    });
                    frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                        pass.bindPipeline(mesh);
                        pass.bindVertexBuffer(0, quad);
                        pass.pushVertexUniforms(0, IDENTITY);
                        pass.drawIndirect(indirect, 0, 1);
                    });
                    var readback = frame.readback(target);
                    frame.submit();
                    var pixels = readback.awaitPixels();
                    if (vertices == 0) {
                        assertSquare(pixels, 0, 0, RED);
                    } else {
                        assertSquare(pixels, 8, 24, RED);
                    }
                }
            }
        }

        @Test
        @DisplayName("draw indexed from a command, its first index and vertex offset applied")
        void indexed() {
            // Two quads' corners; the command's vertex offset of 4 picks the second.
            var corners = vertices(
                    new float[] {0, 0, 0.5f, 0, 1, 0, 1},
                    new float[] {4, 0, 0.5f, 0, 1, 0, 1},
                    new float[] {0, 4, 0.5f, 0, 1, 0, 1},
                    new float[] {4, 4, 0.5f, 0, 1, 0, 1},
                    new float[] {8, 8, 0.5f, 1, 0, 0, 1},
                    new float[] {24, 8, 0.5f, 1, 0, 0, 1},
                    new float[] {8, 24, 0.5f, 1, 0, 0, 1},
                    new float[] {24, 24, 0.5f, 1, 0, 0, 1});
            var indices = ByteBuffer.allocate(8 * Short.BYTES).order(ByteOrder.nativeOrder());
            // Two unused indices first, so the command's first index matters.
            for (var index : new short[] {3, 3, 0, 1, 2, 2, 1, 3}) {
                indices.putShort(index);
            }
            var command = IndirectCommand.pack(List.of(new IndexedDrawCommand(6, 1, 2, 4, 0)));
            try (var vertexBuffer = buffer(BufferUsage.VERTEX, corners);
                    var indexBuffer = buffer(BufferUsage.INDEX, indices.flip());
                    var indirect = buffer(BufferUsage.INDIRECT, command)) {
                var pixels = draw(pass -> {
                    pass.bindPipeline(mesh);
                    pass.bindVertexBuffer(0, vertexBuffer);
                    pass.bindIndexBuffer(indexBuffer, IndexFormat.UINT16);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.drawIndexedIndirect(indirect, 0, 1);
                });
                assertSquare(pixels, 8, 24, RED);
            }
        }

        @Test
        @DisplayName("dispatch the workgroups a buffer names: a quad's halved corners doubled, and drawn")
        void dispatch() {
            var command = IndirectCommand.pack(List.of(new DispatchCommand(1, 1, 1)));
            try (var halved = buffer(BufferUsage.COMPUTE_STORAGE_READ, positions(quad(8, 8, 24, 24, 1, 0, 0)));
                    var positions = device.createBuffer(
                            EnumSet.of(BufferUsage.COMPUTE_STORAGE_WRITE, BufferUsage.GRAPHICS_STORAGE_READ), 6 * 16);
                    var indirect = buffer(BufferUsage.INDIRECT, command);
                    var scale = device.createComputePipeline(TestShaders.scaleCompute());
                    var storageVertex = device.createShader(TestShaders.storageVertex());
                    var pipeline = device.createPipeline(
                            PipelineSpec.builder(storageVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var target = renderTarget();
                    var frame = device.beginFrame()) {
                frame.computePass(positions, pass -> {
                    pass.bindPipeline(scale);
                    pass.bindStorageBuffers(halved);
                    pass.pushUniforms(0, 2f, 0f, 0f, 0f);
                    pass.dispatchIndirect(indirect, 0);
                });
                frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(pipeline);
                    pass.bindVertexStorageBuffers(positions);
                    pass.draw(6);
                });
                var readback = frame.readback(target);
                frame.submit();
                assertSquare(readback.awaitPixels(), 8, 24, RED);
            }
        }

        @Test
        @DisplayName("refuse buffers not made for it, parts of words, no commands, and the end of the buffer")
        void refusals() {
            try (var vertexOnly = device.createBuffer(BufferUsage.VERTEX, 64);
                    var indirect = device.createBuffer(BufferUsage.INDIRECT, 2 * DrawCommand.BYTES);
                    var quad = buffer(BufferUsage.VERTEX, quad(8, 8, 24, 24, 1, 0, 0));
                    var writer = device.createComputePipeline(TestShaders.commandCompute());
                    var written = device.createBuffer(BufferUsage.COMPUTE_STORAGE_WRITE, 16);
                    var target = renderTarget();
                    var frame = device.beginFrame()) {
                frame.renderPass(target, Load.dontCare(), pass -> {
                    assertThrows(IllegalStateException.class, () -> pass.drawIndirect(indirect, 0, 1), "no pipeline");
                    pass.bindPipeline(mesh);
                    pass.bindVertexBuffer(0, quad);
                    assertThrows(IllegalArgumentException.class, () -> pass.drawIndirect(vertexOnly, 0, 1));
                    assertThrows(IllegalArgumentException.class, () -> pass.drawIndirect(indirect, 2, 1));
                    assertThrows(IllegalArgumentException.class, () -> pass.drawIndirect(indirect, 0, 0));
                    assertThrows(IllegalArgumentException.class, () -> pass.drawIndirect(indirect, 0, 3));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> pass.drawIndirect(indirect, DrawCommand.BYTES + 4, 1),
                            "the second command runs past the end");
                    assertThrows(
                            IllegalStateException.class,
                            () -> pass.drawIndexedIndirect(indirect, 0, 1),
                            "no index buffer bound");
                });
                frame.computePass(written, pass -> {
                    pass.bindPipeline(writer);
                    assertThrows(IllegalArgumentException.class, () -> pass.dispatchIndirect(vertexOnly, 0));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> pass.dispatchIndirect(indirect, 2 * DrawCommand.BYTES - 8),
                            "twelve bytes from 24 run past 32");
                    assertThrows(IllegalArgumentException.class, () -> pass.dispatchIndirect(indirect, 1));
                });
            }
        }
    }

    @Nested
    @DisplayName("the stencil test")
    class Stencil {

        @Test
        @DisplayName("draws only where a first pipeline marked, and around it where the mark is not")
        void marksAndMasks() {
            var format = stencilFormat();
            try (var depth = device.createTexture(TextureSpec.depth(format, SIZE, SIZE));
                    var marker = buffer(BufferUsage.VERTEX, quad(8, 8, 24, 24, 0, 0, 0));
                    var everything = buffer(BufferUsage.VERTEX, quad(0, 0, SIZE, SIZE, 1, 0, 0));
                    var around = buffer(BufferUsage.VERTEX, quad(0, 0, SIZE, SIZE, 0, 1, 0));
                    var mark = stencilled(format, StencilTest.replace());
                    var inside = stencilled(format, StencilTest.equal());
                    var outside = stencilled(format, StencilTest.notEqual());
                    var target = renderTarget();
                    var frame = device.beginFrame()) {
                frame.renderPass(target, Load.clear(0, 0, 0, 1), DepthTarget.clear(depth, 1, 0), pass -> {
                    pass.setStencilReference(1);
                    pass.bindPipeline(mark);
                    pass.bindVertexBuffer(0, marker);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.draw(6);
                    pass.bindPipeline(inside);
                    pass.bindVertexBuffer(0, everything);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.draw(6);
                    pass.bindPipeline(outside);
                    pass.bindVertexBuffer(0, around);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.draw(6);
                });
                var readback = frame.readback(target);
                frame.submit();
                var pixels = readback.awaitPixels();
                for (var y = 0; y < SIZE; y++) {
                    for (var x = 0; x < SIZE; x++) {
                        var marked = x >= 8 && x < 24 && y >= 8 && y < 24;
                        assertEquals(marked ? RED : GREEN, argb(pixels, x, y), "at " + x + "," + y);
                    }
                }
            }
        }

        @Test
        @DisplayName("is refused a depth format with no stencil, and its reference a pass with none")
        void refusals() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                            .depthTest(DepthTest.less(TextureFormat.D16_UNORM))
                            .stencilTest(StencilTest.replace())
                            .build());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                            .stencilTest(StencilTest.replace())
                            .build(),
                    "no depth test at all");
            try (var plain = device.createTexture(TextureSpec.depth(TextureFormat.D16_UNORM, SIZE, SIZE));
                    var stencilled = device.createTexture(TextureSpec.depth(stencilFormat(), SIZE, SIZE));
                    var target = renderTarget();
                    var frame = device.beginFrame()) {
                assertThrows(IllegalArgumentException.class, () -> DepthTarget.clear(plain, 1, 0));
                assertThrows(IllegalArgumentException.class, () -> new DepthTarget(stencilled, true, 1, 256));
                frame.renderPass(
                        target,
                        Load.dontCare(),
                        DepthTarget.clear(plain),
                        pass -> assertThrows(
                                IllegalStateException.class, () -> pass.setStencilReference(1), "D16 has no stencil"));
                frame.renderPass(
                        target,
                        Load.dontCare(),
                        pass -> assertThrows(
                                IllegalStateException.class, () -> pass.setStencilReference(1), "no depth target"));
                frame.renderPass(target, Load.dontCare(), DepthTarget.clear(stencilled, 1, 7), pass -> {
                    assertThrows(IllegalArgumentException.class, () -> pass.setStencilReference(256));
                    assertThrows(IllegalArgumentException.class, () -> pass.setStencilReference(-1));
                    pass.setStencilReference(255);
                });
            }
        }

        private GraphicsPipeline stencilled(TextureFormat format, StencilTest test) {
            return device.createPipeline(PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                    .vertexBuffer(TestShaders.meshLayout())
                    .depthTest(DepthTest.off(format))
                    .stencilTest(test)
                    .build());
        }
    }

    @Nested
    @DisplayName("blend constants")
    class BlendConstants {

        @Test
        @DisplayName("mix the output into the target by their weight, channel by channel")
        void mix() {
            try (var quad = buffer(BufferUsage.VERTEX, quad(0, 0, SIZE, SIZE, 1, 0, 0));
                    var constant = device.createPipeline(
                            PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .vertexBuffer(TestShaders.meshLayout())
                                    .blend(BlendMode.CONSTANT)
                                    .build());
                    var target = renderTarget();
                    var frame = device.beginFrame()) {
                frame.renderPass(target, Load.clear(0, 0, 1, 1), pass -> {
                    pass.bindPipeline(constant);
                    pass.bindVertexBuffer(0, quad);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.setBlendConstants(0.25f, 0.25f, 0.25f, 1f);
                    pass.draw(6);
                    assertThrows(IllegalArgumentException.class, () -> pass.setBlendConstants(1.5f, 0, 0, 0));
                    assertThrows(IllegalArgumentException.class, () -> pass.setBlendConstants(Float.NaN, 0, 0, 0));
                });
                var readback = frame.readback(target);
                frame.submit();
                var pixel = argb(readback.awaitPixels(), 16, 16);
                assertEquals(0.25 * 255, (pixel >> 16) & 0xFF, 1, "a quarter of the red drawn");
                assertEquals(0, (pixel >> 8) & 0xFF);
                assertEquals(0.75 * 255, pixel & 0xFF, 1, "three quarters of the blue cleared");
                assertEquals(255, pixel >>> 24);
            }
        }
    }

    @Nested
    @DisplayName("copies on the GPU")
    class Copies {

        @Test
        @DisplayName("move a box of texels into another texture, and a whole texture into one of its size")
        void textures() {
            var bytes = new byte[8 * 8 * 4];
            new Random(26).nextBytes(bytes);
            try (var source = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                    var twin = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                    var destination = renderTarget();
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> {
                    copy.upload(source, ByteBuffer.wrap(bytes));
                    copy.copy(source.view(0, 0), 0, 0, destination.view(0, 0), 4, 6, 8, 8);
                    copy.copy(source, twin);
                });
                var placed = frame.readback(destination, new PhysicalRect(4, 6, 8, 8));
                var whole = frame.readback(twin);
                frame.submit();
                assertArrayEquals(bytes, bytes(placed.await()), "the box, where it was placed");
                assertArrayEquals(bytes, bytes(whole.await()), "the whole of the twin");
            }
        }

        @Test
        @DisplayName("move bytes into another buffer, which draws as the first would")
        void buffers() {
            var vertices = quad(8, 8, 24, 24, 1, 0, 0);
            var size = vertices.remaining();
            try (var source = buffer(BufferUsage.VERTEX, vertices);
                    var destination = device.createBuffer(BufferUsage.VERTEX, size + 64)) {
                try (var frame = device.beginFrame()) {
                    frame.copyPass(copy -> copy.copy(source, 0, destination, 64, size));
                    frame.submit();
                }
                var pixels = draw(pass -> {
                    pass.bindPipeline(mesh);
                    pass.bindVertexBuffer(0, destination, 64);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.draw(6);
                });
                assertSquare(pixels, 8, 24, RED);
            }
        }

        @Test
        @DisplayName("refuse other sizes and formats, parts of words, overlaps, and multisampled textures")
        void refusals() {
            try (var bgra = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                    var rgba = device.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 8, 8));
                    var bigger = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 16, 16));
                    var multisampled = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 8, 8)
                            .withUsages(EnumSet.of(TextureUsage.COLOR_TARGET))
                            .withSamples(4));
                    var single = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                    var buffer = device.createBuffer(BufferUsage.VERTEX, 64);
                    var other = device.createBuffer(BufferUsage.VERTEX, 32);
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> {
                    assertThrows(IllegalArgumentException.class, () -> copy.copy(bgra, rgba), "formats differ");
                    assertThrows(IllegalArgumentException.class, () -> copy.copy(bgra, bigger), "sizes differ");
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.copy(bgra.view(0, 0), 0, 0, bigger.view(0, 0), 12, 0, 8, 8),
                            "past the destination's edge");
                    assertThrows(IllegalArgumentException.class, () -> copy.copy(multisampled, single));
                    assertThrows(IllegalArgumentException.class, () -> copy.copy(buffer, other), "sizes differ");
                    assertThrows(IllegalArgumentException.class, () -> copy.copy(buffer, 0, other, 0, 6));
                    assertThrows(IllegalArgumentException.class, () -> copy.copy(buffer, 0, buffer, 16, 32));
                    copy.copy(buffer, 0, buffer, 32, 32);
                });
                frame.submit();
            }
        }
    }

    @Nested
    @DisplayName("textures a compute or vertex shader reads")
    class ShaderTextures {

        @Test
        @DisplayName("are sampled by a compute pass, through the sampler bound beside them")
        void computeSamplers() {
            var colours = new byte[] {
                (byte) 255,
                0,
                0,
                (byte) 255,
                0,
                (byte) 255,
                0,
                (byte) 255,
                0,
                0,
                (byte) 255,
                (byte) 255,
                (byte) 255,
                (byte) 255,
                (byte) 255,
                (byte) 255
            };
            try (var image = device.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 4, 1));
                    var output = device.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 4, 1)
                            .withUsages(EnumSet.of(TextureUsage.SAMPLER, TextureUsage.COMPUTE_STORAGE_WRITE)));
                    var nearest = device.createSampler(SamplerSpec.nearest());
                    var sampled = device.createComputePipeline(TestShaders.sampledCompute());
                    var frame = device.beginFrame()) {
                assertEquals(1, sampled.code().samplers());
                frame.copyPass(copy -> copy.upload(image, ByteBuffer.wrap(colours)));
                frame.computePass(List.of(), List.of(output.view(0, 0)), pass -> {
                    pass.bindPipeline(sampled);
                    assertThrows(IllegalStateException.class, () -> pass.dispatch(1), "the sampler is not bound");
                    assertThrows(IllegalArgumentException.class, () -> pass.bindSamplers(nearest), "one is declared");
                    pass.bindSamplers(nearest, image);
                    pass.dispatch(1);
                });
                var readback = frame.readback(output);
                frame.submit();
                assertArrayEquals(colours, bytes(readback.await()));
            }
        }

        @Test
        @DisplayName("are read as storage by a vertex shader, which moves the mesh by a texel")
        void vertexStorage() {
            var usages = Set.of(TextureUsage.GRAPHICS_STORAGE_READ);
            assertTrue(
                    device.supports(TextureFormat.R16G16B16A16_FLOAT, usages),
                    "half floats are read as storage everywhere the lane runs");
            // Half a clip space to the right: 8 pixels of 32.
            var texel = ByteBuffer.allocate(4 * Short.BYTES).order(ByteOrder.nativeOrder());
            texel.putShort(Float.floatToFloat16(0.5f))
                    .putShort(Float.floatToFloat16(0f))
                    .putShort(Float.floatToFloat16(0f))
                    .putShort(Float.floatToFloat16(1f));
            try (var displacement = device.createTexture(TextureSpec.sampled(TextureFormat.R16G16B16A16_FLOAT, 1, 1)
                            .withUsages(EnumSet.copyOf(usages)));
                    var displaced = device.createShader(TestShaders.displacedVertex());
                    var pipeline = device.createPipeline(
                            PipelineSpec.builder(displaced, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .vertexBuffer(TestShaders.meshLayout())
                                    .build());
                    var quad = buffer(BufferUsage.VERTEX, quad(4, 8, 12, 24, 1, 0, 0));
                    var target = renderTarget();
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> copy.upload(displacement, texel.flip()));
                frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(pipeline);
                    pass.bindVertexBuffer(0, quad);
                    assertThrows(IllegalStateException.class, () -> pass.draw(6), "the storage texture is not bound");
                    pass.bindVertexStorageTextures(displacement);
                    pass.draw(6);
                });
                var readback = frame.readback(target);
                frame.submit();
                var pixels = readback.awaitPixels();
                assertEquals(BLACK, argb(pixels, 6, 16), "where the quad was before it moved");
                assertEquals(RED, argb(pixels, 16, 16), "where it moved to");
                assertEquals(BLACK, argb(pixels, 22, 16));
            }
        }

        @Test
        @DisplayName("are refused a vertex shader that declares none")
        void refusals() {
            try (var storage = device.createTexture(TextureSpec.sampled(TextureFormat.R16G16B16A16_FLOAT, 1, 1)
                            .withUsages(EnumSet.of(TextureUsage.GRAPHICS_STORAGE_READ)));
                    var sampledOnly =
                            device.createTexture(TextureSpec.sampled(TextureFormat.R16G16B16A16_FLOAT, 1, 1));
                    var displaced = device.createShader(TestShaders.displacedVertex());
                    var pipeline = device.createPipeline(
                            PipelineSpec.builder(displaced, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .vertexBuffer(TestShaders.meshLayout())
                                    .build());
                    var target = renderTarget();
                    var frame = device.beginFrame()) {
                frame.renderPass(target, Load.dontCare(), pass -> {
                    pass.bindPipeline(mesh);
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> pass.bindVertexStorageTextures(storage),
                            "the mesh shader reads none");
                    pass.bindPipeline(pipeline);
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> pass.bindVertexStorageTextures(sampledOnly),
                            "not made to be read as storage");
                });
            }
        }
    }

    @Nested
    @DisplayName("the device")
    class Device {

        @Test
        @DisplayName("names its adapter, waits for idle, and names its textures and buffers")
        void describesAndNames() {
            var adapter = device.adapter();
            assertFalse(adapter.name().isBlank(), "every driver the lane runs names its adapter: " + adapter);
            device.waitForIdle();
            var texture = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 4, 4));
            var buffer = device.createBuffer(BufferUsage.VERTEX, 16);
            texture.setDebugName("scene colour");
            buffer.setDebugName("cube vertices");
            texture.close();
            buffer.close();
            assertThrows(IllegalStateException.class, () -> texture.setDebugName("after"));
            assertThrows(IllegalStateException.class, () -> buffer.setDebugName("after"));
        }
    }

    /// The first depth format with a stencil the device can make a target of.
    private static TextureFormat stencilFormat() {
        return List.of(TextureFormat.D24_UNORM_S8_UINT, TextureFormat.D32_FLOAT_S8_UINT).stream()
                .filter(format -> device.supports(format, EnumSet.of(TextureUsage.DEPTH_TARGET)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(device + " has no depth format with a stencil"));
    }

    /// Checks `pixels` are `inside` in the square from `from` to `to` on both
    /// axes and black outside it, leaving out the edges, which the fill rule
    /// decides.
    private static void assertSquare(PixelBuffer pixels, int from, int to, int inside) {
        for (var y = 0; y < SIZE; y++) {
            for (var x = 0; x < SIZE; x++) {
                if (x == from || x == to - 1 || y == from || y == to - 1) {
                    continue;
                }
                var within = x > from && x < to - 1 && y > from && y < to - 1;
                assertEquals(within ? inside : BLACK, argb(pixels, x, y), "at " + x + "," + y);
            }
        }
    }

    /// The colour of pixel `(x, y)` as `0xAARRGGBB`.
    private static int argb(PixelBuffer pixels, int x, int y) {
        return pixels.pixels().getInt(y * pixels.stride() + x * 4);
    }

    private static byte[] bytes(ByteBuffer buffer) {
        var bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    /// Four unsigned words, as a `uint4` uniform.
    private static ByteBuffer uints(int x, int y, int z, int w) {
        return ByteBuffer.allocate(4 * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .putInt(x)
                .putInt(y)
                .putInt(z)
                .putInt(w)
                .flip();
    }

    private static GpuTexture renderTarget() {
        return device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, SIZE, SIZE));
    }

    /// Clears a render target to opaque black, runs `body` in a render pass
    /// into it, and reads it back.
    private static PixelBuffer draw(Consumer<RenderPass> body) {
        try (var target = renderTarget();
                var frame = device.beginFrame()) {
            frame.renderPass(target, Load.clear(0, 0, 0, 1), body);
            var readback = frame.readback(target);
            frame.submit();
            return readback.awaitPixels();
        }
    }

    /// Mesh vertices, each `{x, y, z, r, g, b, a}` with `x` and `y` in pixels
    /// from the top left of a [#SIZE] square target, in clip space.
    private static ByteBuffer vertices(float[]... vertices) {
        var bytes =
                ByteBuffer.allocate(vertices.length * TestShaders.MESH_STRIDE).order(ByteOrder.nativeOrder());
        for (var vertex : vertices) {
            bytes.putFloat(2f * vertex[0] / SIZE - 1f);
            bytes.putFloat(1f - 2f * vertex[1] / SIZE);
            for (var i = 2; i < 7; i++) {
                bytes.putFloat(vertex[i]);
            }
        }
        return bytes.flip();
    }

    /// Two triangles covering pixels `left` to `right` by `top` to `bottom`.
    private static ByteBuffer quad(float left, float top, float right, float bottom, float r, float g, float b) {
        return vertices(
                new float[] {left, top, 0.5f, r, g, b, 1},
                new float[] {right, top, 0.5f, r, g, b, 1},
                new float[] {left, bottom, 0.5f, r, g, b, 1},
                new float[] {left, bottom, 0.5f, r, g, b, 1},
                new float[] {right, top, 0.5f, r, g, b, 1},
                new float[] {right, bottom, 0.5f, r, g, b, 1});
    }

    /// The `xy` of each mesh vertex in `vertices`, as the `float4`s
    /// storage.vert reads, halved so that scale.comp's doubling restores them.
    private static ByteBuffer positions(ByteBuffer vertices) {
        var count = vertices.remaining() / TestShaders.MESH_STRIDE;
        var positions = ByteBuffer.allocate(count * 16).order(ByteOrder.nativeOrder());
        for (var i = 0; i < count; i++) {
            var at = i * TestShaders.MESH_STRIDE;
            positions.putFloat(vertices.getFloat(at) / 2).putFloat(vertices.getFloat(at + 4) / 2);
            positions.putFloat(0f).putFloat(0.5f);
        }
        return positions.flip();
    }

    /// A buffer for `usage` holding `data`, uploaded in a frame of its own.
    private static GpuBuffer buffer(BufferUsage usage, ByteBuffer data) {
        var buffer = device.createBuffer(usage, data.remaining());
        try (var frame = device.beginFrame()) {
            frame.copyPass(copy -> copy.upload(buffer, 0, data));
            frame.submit();
        }
        return buffer;
    }
}
