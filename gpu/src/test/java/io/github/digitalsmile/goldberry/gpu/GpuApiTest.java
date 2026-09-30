package io.github.digitalsmile.goldberry.gpu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuTestLauncher;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;

/// The public GPU API on a real device: Metal here, Vulkan under lavapipe on the
/// GPU lane (`docs/gpu-plan.md`, phase 2).
///
/// Phase 2's exit is [Drawing#triangle]: a triangle from a vertex buffer,
/// rendered to an offscreen texture and read back, against a reference
/// rasterized in Java. Pixels whose centre lies exactly on an edge belong to
/// whichever side the driver's fill rule gives them, so the reference leaves
/// them out; every other pixel must be exact. The rest holds the API to what
/// its documentation says: uploads, readbacks, scoped passes, and every misuse
/// refused in Java before the driver sees it.
@Tag(GpuTestLauncher.TAG)
@DisplayName("the GPU API, on a real device")
class GpuApiTest {

    private static final int SIZE = 32;
    private static final int BLACK = 0xFF000000;
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    private static SdlGpuDevice sdl;
    private static GpuDevice device;
    private static Shader meshVertex;
    private static Shader meshFragment;
    private static GraphicsPipeline mesh;
    private static GraphicsPipeline meshWithDepth;
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
        meshWithDepth =
                device.createPipeline(PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                        .vertexBuffer(TestShaders.meshLayout())
                        .depthTest(DepthTest.less(TextureFormat.D16_UNORM))
                        .build());
        baseline = sdl.openResources();
    }

    @AfterAll
    static void destroyDevice() {
        // Skipped before SDL was reached: nothing to give back, and no library to
        // call. Calling it anyway failed the class, and a build without it (ADR-0495).
        if (sdl == null) {
            return;
        }
        if (sdl != null) {
            sdl.close();
        }
        Sdl.get().quit();
    }

    @AfterEach
    void nothingLeaks() {
        var staging = device.upload().capacity() > 0 ? 1 : 0;
        assertEquals(baseline + staging, sdl.openResources(), "a test left a resource open");
    }

    @Nested
    @DisplayName("drawing")
    class Drawing {

        @Test
        @DisplayName("a triangle from a vertex buffer reads back as the reference rasterizes it")
        void triangle() {
            try (var vertices = vertexBuffer(triangleVertices(4, 4, 28, 4, 4, 28, 0.5f, 1, 0, 0))) {
                var pixels = draw(mesh, pass -> {
                    pass.bindPipeline(mesh);
                    pass.bindVertexBuffer(0, vertices);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.draw(3);
                });
                // Inside: right of x = 4, below y = 4, and above x + y = 32.
                assertMatches(pixels, (x, y) -> x > 4 && y > 4 && x + y < 32, (x, y) -> x + y == 32);
            }
        }

        @Test
        @DisplayName("a uniform matrix, pushed as bytes, moves the triangle")
        void uniformsMove() {
            var translate = ByteBuffer.allocate(16 * Float.BYTES).order(ByteOrder.nativeOrder());
            // Row-major: the first row carries x's translation, 8 pixels.
            translate.asFloatBuffer().put(new float[] {1, 0, 0, 2f * 8 / SIZE, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
            try (var vertices = vertexBuffer(triangleVertices(4, 4, 20, 4, 4, 20, 0.5f, 1, 0, 0))) {
                var pixels = draw(mesh, pass -> {
                    pass.bindPipeline(mesh);
                    pass.bindVertexBuffer(0, vertices);
                    pass.pushVertexUniforms(0, translate);
                    pass.draw(3);
                });
                assertMatches(pixels, (x, y) -> x > 12 && y > 4 && (x - 8) + y < 24, (x, y) -> (x - 8) + y == 24);
                assertEquals(0, translate.position(), "the uniforms' buffer is not moved");
            }
        }

        @Test
        @DisplayName("an indexed quad covers exactly its pixels, with 16- and 32-bit indices")
        void indexedQuad() {
            var corners = vertices(
                    new float[] {8, 8, 0.5f, 0, 1, 0, 1},
                    new float[] {24, 8, 0.5f, 0, 1, 0, 1},
                    new float[] {8, 24, 0.5f, 0, 1, 0, 1},
                    new float[] {24, 24, 0.5f, 0, 1, 0, 1});
            var shorts = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder());
            for (var index : new short[] {0, 1, 2, 2, 1, 3}) {
                shorts.putShort(index);
            }
            shorts.flip();
            // Two ints of padding first, bound past with an offset.
            var ints = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder());
            for (var index : new int[] {-1, -1, 0, 1, 2, 2, 1, 3}) {
                ints.putInt(index);
            }
            ints.flip();
            try (var vertices = vertexBuffer(corners);
                    var sixteen = buffer(BufferUsage.INDEX, shorts);
                    var thirtyTwo = buffer(BufferUsage.INDEX, ints)) {
                for (var bound : List.of(
                        (Consumer<RenderPass>) pass -> pass.bindIndexBuffer(sixteen, IndexFormat.UINT16),
                        pass -> pass.bindIndexBuffer(thirtyTwo, IndexFormat.UINT32, 8))) {
                    var pixels = draw(mesh, pass -> {
                        pass.bindPipeline(mesh);
                        pass.bindVertexBuffer(0, vertices);
                        bound.accept(pass);
                        pass.pushVertexUniforms(0, IDENTITY);
                        pass.drawIndexed(6);
                    });
                    for (var y = 0; y < SIZE; y++) {
                        for (var x = 0; x < SIZE; x++) {
                            var inside = x >= 8 && x < 24 && y >= 8 && y < 24;
                            assertEquals(inside ? GREEN : BLACK, argb(pixels, x, y), "at " + x + "," + y);
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("a depth test keeps the nearer quad where a later, farther one overlaps it")
        void depth() {
            var near = quad(4, 4, 20, 20, 0.25f, 0, 1, 0);
            var far = quad(12, 12, 28, 28, 0.75f, 0, 0, 1);
            try (var nearBuffer = vertexBuffer(near);
                    var farBuffer = vertexBuffer(far);
                    var depthTexture = device.createTexture(TextureSpec.depth(TextureFormat.D16_UNORM, SIZE, SIZE))) {
                var tested = drawInto(DepthTarget.clear(depthTexture), pass -> {
                    pass.bindPipeline(meshWithDepth);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.bindVertexBuffer(0, nearBuffer);
                    pass.draw(6);
                    pass.bindVertexBuffer(0, farBuffer);
                    pass.draw(6);
                });
                var untested = draw(mesh, pass -> {
                    pass.bindPipeline(mesh);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.bindVertexBuffer(0, nearBuffer);
                    pass.draw(6);
                    pass.bindVertexBuffer(0, farBuffer);
                    pass.draw(6);
                });
                assertEquals(GREEN, argb(tested, 16, 16), "the nearer quad stays in front");
                assertEquals(BLUE, argb(untested, 16, 16), "with no depth test the later quad wins");
                assertEquals(BLUE, argb(tested, 24, 24), "the farther quad shows where nothing is nearer");
                assertEquals(GREEN, argb(tested, 6, 6));
            }
        }

        @Test
        @DisplayName("culling discards a clockwise triangle as back-facing, unless clockwise is front")
        void culling() {
            // Top left, top right, bottom left: clockwise with y up.
            try (var vertices = vertexBuffer(triangleVertices(4, 4, 28, 4, 4, 28, 0.5f, 1, 0, 0));
                    var cullBack = device.createPipeline(
                            PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .vertexBuffer(TestShaders.meshLayout())
                                    .cull(CullMode.BACK, FrontFace.COUNTER_CLOCKWISE)
                                    .build());
                    var clockwiseFront = device.createPipeline(
                            PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .vertexBuffer(TestShaders.meshLayout())
                                    .cull(CullMode.BACK, FrontFace.CLOCKWISE)
                                    .build())) {
                for (var pipeline : List.of(cullBack, clockwiseFront)) {
                    var pixels = draw(pipeline, pass -> {
                        pass.bindPipeline(pipeline);
                        pass.bindVertexBuffer(0, vertices);
                        pass.pushVertexUniforms(0, IDENTITY);
                        pass.draw(3);
                    });
                    assertEquals(pipeline == cullBack ? BLACK : RED, argb(pixels, 8, 8), pipeline.toString());
                }
            }
        }
    }

    @Nested
    @DisplayName("uploads")
    class Uploads {

        @Test
        @DisplayName("of regions write those regions from a padded image, and keep every other pixel")
        void partialUploadsKeepTheRest() {
            var width = 16;
            var height = 12;
            var first = random(width * height * 4, 1);
            var rowBytes = width * 4 + 12;
            var second = ByteBuffer.wrap(random(rowBytes * height, 2));
            var regions =
                    List.of(new PhysicalRect(2, 3, 5, 4), new PhysicalRect(10, 6, 6, 6), PhysicalRect.of(0, 0, 0, 0));
            try (var texture = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, width, height))) {
                try (var frame = device.beginFrame()) {
                    frame.copyPass(copy -> copy.upload(texture, ByteBuffer.wrap(first)));
                    frame.copyPass(copy -> copy.upload(texture, second, rowBytes, regions));
                    var readback = frame.readback(texture);
                    frame.submit();
                    var back = readback.await();
                    for (var y = 0; y < height; y++) {
                        for (var x = 0; x < width; x++) {
                            var damaged = regions.stream().anyMatch(contains(x, y));
                            for (var channel = 0; channel < 4; channel++) {
                                var expected = damaged
                                        ? second.get(y * rowBytes + x * 4 + channel)
                                        : first[(y * width + x) * 4 + channel];
                                assertEquals(expected, back.get((y * width + x) * 4 + channel), "at " + x + "," + y);
                            }
                        }
                    }
                    assertEquals(0, second.position(), "the source is not moved");
                }
            }
        }

        @Test
        @DisplayName("of a painted frame's damage land in a BGRA texture of its size, and nowhere else")
        void pixelBufferDamage() {
            var frameSize = new PhysicalSize(8, 8);
            var painted = PixelBuffer.allocate(frameSize, PixelFormat.BGRA32_PREMULTIPLIED);
            for (var i = 0; i < 64; i++) {
                painted.pixels().putInt(i * 4, 0xFF336699);
            }
            try (var texture = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                    var wrongSize = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 4, 4));
                    var wrongFormat = device.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 8, 8));
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> {
                    copy.upload(texture, ByteBuffer.allocate(8 * 8 * 4));
                    copy.upload(texture, painted, List.of(new PhysicalRect(2, 2, 3, 3)));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.upload(wrongSize, painted, List.of(new PhysicalRect(0, 0, 1, 1))));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.upload(wrongFormat, painted, List.of(new PhysicalRect(0, 0, 1, 1))));
                });
                var readback = frame.readback(texture);
                frame.submit();
                var pixels = readback.awaitPixels();
                assertEquals(0xFF336699, argb(pixels, 3, 3));
                assertEquals(0, argb(pixels, 1, 1));
                assertEquals(0, argb(pixels, 5, 5));
            }
        }

        @Test
        @DisplayName("grow the staging memory past its first size, and several in one pass each land")
        void growAndCycle() {
            // 128 KiB, past the 64 KiB the first, small upload makes.
            var big = random(256 * 128 * 4, 3);
            var small = random(4 * 4 * 4, 4);
            try (var large = device.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 256, 128));
                    var little = device.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 4, 4));
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> {
                    copy.upload(little, ByteBuffer.wrap(small));
                    copy.upload(large, ByteBuffer.wrap(big));
                    copy.upload(little, ByteBuffer.wrap(small));
                });
                assertTrue(device.upload().capacity() >= big.length, "the staging memory grew to the upload");
                var largeBack = frame.readback(large);
                var littleBack = frame.readback(little);
                frame.submit();
                assertArrayEquals(big, bytes(largeBack.await()));
                assertArrayEquals(small, bytes(littleBack.await()));
            }
        }

        @Test
        @DisplayName("refuse a region outside the texture, short rows, a short source and a depth texture")
        void refusals() {
            try (var texture = device.createTexture(TextureSpec.sampled(TextureFormat.R8_UNORM, 8, 8));
                    var depth = device.createTexture(TextureSpec.depth(TextureFormat.D16_UNORM, 8, 8));
                    var vertices = device.createBuffer(BufferUsage.VERTEX, 16);
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> {
                    var source = ByteBuffer.allocate(64);
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.upload(texture, source, 8, List.of(new PhysicalRect(4, 4, 8, 1))));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.upload(texture, source, 7, List.of(new PhysicalRect(0, 0, 1, 1))));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.upload(
                                    texture, ByteBuffer.allocate(63), 8, List.of(new PhysicalRect(0, 0, 8, 8))));
                    assertThrows(IllegalArgumentException.class, () -> copy.upload(depth, ByteBuffer.allocate(128)));
                    assertThrows(
                            IllegalArgumentException.class, () -> copy.upload(vertices, 8, ByteBuffer.allocate(9)));
                    assertThrows(
                            IllegalArgumentException.class, () -> copy.upload(vertices, 0, ByteBuffer.allocate(0)));
                });
            }
        }
    }

    @Nested
    @DisplayName("readbacks")
    class Readbacks {

        @Test
        @DisplayName("of an RGBA texture come back as BGRA pixels, and of a region, just that region")
        void formsAndRegions() {
            var rgba = new byte[4 * 4 * 4];
            for (var i = 0; i < 16; i++) {
                rgba[i * 4] = (byte) 0x11;
                rgba[i * 4 + 1] = (byte) 0x22;
                rgba[i * 4 + 2] = (byte) (i * 8);
                rgba[i * 4 + 3] = (byte) 0xFF;
            }
            try (var texture = device.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 4, 4));
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> copy.upload(texture, ByteBuffer.wrap(rgba)));
                var whole = frame.readback(texture);
                var corner = frame.readback(texture, new PhysicalRect(2, 1, 2, 3));
                frame.submit();
                var pixels = whole.awaitPixels();
                assertEquals(PixelFormat.BGRA32_PREMULTIPLIED, pixels.format());
                assertEquals(0xFF112200 | (5 * 8), argb(pixels, 1, 1), "red and blue swapped into BGRA");
                assertEquals(2, corner.width());
                assertEquals(3, corner.height());
                var bytes = corner.await();
                assertEquals(2 * 3 * 4, bytes.remaining());
                assertEquals((byte) ((1 * 4 + 2) * 8), bytes.get(2), "the region's first pixel is (2, 1)");
                assertTrue(bytes.isReadOnly());
                assertTrue(corner.isDone());
                assertEquals(bytes, corner.await(), "awaiting again gives the same bytes");
            }
        }

        @Test
        @DisplayName("wait for their frame's submission, fail when it is discarded, and close idempotently")
        void lifecycle() {
            try (var texture = device.createTexture(TextureSpec.sampled(TextureFormat.R8_UNORM, 2, 2))) {
                Readback discarded;
                try (var frame = device.beginFrame()) {
                    discarded = frame.readback(texture);
                    assertThrows(IllegalStateException.class, discarded::await, "not submitted yet");
                }
                assertThrows(IllegalStateException.class, discarded::await, "discarded with its frame");

                try (var frame = device.beginFrame()) {
                    var unread = frame.readback(texture);
                    var read = frame.readback(texture);
                    frame.submit();
                    assertThrows(IllegalStateException.class, read::awaitPixels, "R8 has no PixelBuffer form");
                    assertEquals(4, read.await().remaining());
                    unread.close();
                    unread.close();
                    assertThrows(IllegalStateException.class, unread::await);
                }
            }
        }

        @Test
        @DisplayName("refuse a depth texture and a region outside the texture")
        void refusals() {
            try (var texture = device.createTexture(TextureSpec.sampled(TextureFormat.R8_UNORM, 2, 2));
                    var depth = device.createTexture(TextureSpec.depth(TextureFormat.D16_UNORM, 2, 2));
                    var frame = device.beginFrame()) {
                assertThrows(IllegalArgumentException.class, () -> frame.readback(depth));
                assertThrows(
                        IllegalArgumentException.class, () -> frame.readback(texture, new PhysicalRect(1, 1, 2, 2)));
                assertThrows(
                        IllegalArgumentException.class, () -> frame.readback(texture, new PhysicalRect(0, 0, 0, 1)));
            }
        }
    }

    @Nested
    @DisplayName("frames")
    class Frames {

        @Test
        @DisplayName("scope their passes: no nesting, and a pass kept past its body throws")
        void scopedPasses() {
            var escaped = new AtomicReference<RenderPass>();
            try (var target = renderTarget();
                    var frame = device.beginFrame()) {
                frame.renderPass(target, Load.clearTransparent(), pass -> {
                    escaped.set(pass);
                    assertSame(target, pass.target());
                    assertThrows(IllegalStateException.class, () -> frame.copyPass(copy -> {}));
                    assertThrows(IllegalStateException.class, () -> frame.readback(target));
                    assertThrows(IllegalStateException.class, frame::submit);
                    assertThrows(IllegalStateException.class, frame::close);
                });
                assertThrows(IllegalStateException.class, () -> escaped.get().draw(3));
                frame.submit();
                assertTrue(frame.isSubmitted());
                assertThrows(IllegalStateException.class, frame::submit);
                assertThrows(IllegalStateException.class, () -> frame.copyPass(copy -> {}));
            }
        }

        @Test
        @DisplayName("end a pass whose body threw, pass the exception on, and discard on close")
        void bodiesThatThrow() {
            try (var target = renderTarget()) {
                var frame = device.beginFrame();
                var thrown = assertThrows(
                        IllegalStateException.class,
                        () -> frame.renderPass(target, Load.keep(), pass -> {
                            throw new IllegalStateException("the body failed");
                        }));
                assertEquals("the body failed", thrown.getMessage());
                assertTrue(frame.isRecording(), "the pass ended, and the frame records again");
                frame.close();
                assertFalse(frame.isRecording());
                frame.close();
            }
        }

        @Test
        @DisplayName("nest debug groups and labels around and inside passes, and refuse a submit inside one")
        void debugGroups() {
            try (var target = renderTarget();
                    var frame = device.beginFrame()) {
                frame.debugGroup("frame", () -> {
                    frame.debugLabel("start");
                    frame.renderPass(target, Load.clearTransparent(), pass -> frame.debugGroup("layer", () -> {}));
                    assertThrows(IllegalStateException.class, frame::submit);
                });
                frame.submit();
                assertThrows(IllegalStateException.class, () -> frame.debugLabel("late"));
            }
        }
    }

    @Nested
    @DisplayName("misuse, refused in Java")
    class Misuse {

        @Test
        @DisplayName("a draw with its vertex buffer or index buffer unbound, and a slot the pipeline does not read")
        void incompleteDraws() {
            try (var target = renderTarget();
                    var vertices = device.createBuffer(BufferUsage.VERTEX, TestShaders.MESH_STRIDE * 3);
                    var frame = device.beginFrame()) {
                frame.renderPass(target, Load.keep(), pass -> {
                    assertThrows(IllegalStateException.class, () -> pass.draw(3), "no pipeline");
                    pass.bindPipeline(mesh);
                    assertThrows(IllegalStateException.class, () -> pass.draw(3), "no vertex buffer");
                    assertThrows(IllegalArgumentException.class, () -> pass.bindVertexBuffer(1, vertices));
                    pass.bindVertexBuffer(0, vertices);
                    assertThrows(IllegalStateException.class, () -> pass.drawIndexed(3), "no index buffer");
                    assertThrows(
                            IllegalArgumentException.class, () -> pass.bindIndexBuffer(vertices, IndexFormat.UINT16));
                    assertThrows(IllegalArgumentException.class, () -> pass.pushVertexUniforms(1, IDENTITY));
                    assertThrows(IllegalArgumentException.class, () -> pass.pushFragmentUniforms(0, 1f));
                    assertThrows(IllegalArgumentException.class, () -> pass.bindPipeline(meshWithDepth));
                    assertThrows(IllegalArgumentException.class, () -> pass.setScissor(new PhysicalRect(0, 0, 0, 4)));
                });
            }
        }

        @Test
        @DisplayName("a pipeline whose shaders are swapped, or whose target is a depth format")
        void badPipelines() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PipelineSpec.builder(meshFragment, meshVertex, TextureFormat.B8G8R8A8_UNORM)
                            .build());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.D16_UNORM)
                            .build());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                            .vertexBuffer(TestShaders.meshLayout())
                            .vertexBuffer(TestShaders.meshLayout())
                            .build(),
                    "slot 0 twice");
        }

        @Test
        @DisplayName("a shader in no format the device takes, naming what it has")
        void shaderFormats() {
            var other = device.shaderFormats().contains(ShaderFormat.DXIL) ? ShaderFormat.SPIRV : ShaderFormat.DXIL;
            var code = ShaderCode.builder(ShaderStage.VERTEX)
                    .code(other, new byte[] {1})
                    .build();
            var refused = assertThrows(IllegalArgumentException.class, () -> device.createShader(code));
            assertTrue(refused.getMessage().contains(other.toString()), refused.getMessage());
        }

        @Test
        @DisplayName("a closed resource, and a texture that cannot be rendered into")
        void closedAndUnusable() {
            var texture = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 4, 4));
            texture.close();
            texture.close();
            assertTrue(texture.isClosed());
            try (var sampled = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 4, 4));
                    var frame = device.beginFrame()) {
                var closed = assertThrows(
                        IllegalStateException.class, () -> frame.renderPass(texture, Load.keep(), pass -> {}));
                assertTrue(closed.getMessage().contains("closed"), closed.getMessage());
                assertThrows(IllegalArgumentException.class, () -> frame.renderPass(sampled, Load.keep(), pass -> {}));
            }
        }

        @Test
        @DisplayName("any call from another thread than the device's")
        void otherThreads() throws InterruptedException {
            var failure = new AtomicReference<Throwable>();
            var texture = device.createTexture(TextureSpec.sampled(TextureFormat.R8_UNORM, 1, 1));
            var closer = Thread.ofPlatform().start(() -> {
                try {
                    texture.close();
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            closer.join();
            assertInstanceOf(WrongThreadException.class, failure.get());
            assertFalse(texture.isClosed());
            texture.close();
            var thread = Thread.ofPlatform().start(() -> {
                try {
                    device.beginFrame();
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            thread.join();
            assertInstanceOf(WrongThreadException.class, failure.get());
        }
    }

    @Nested
    @DisplayName("a second device")
    class SecondDevice {

        @Test
        @DisplayName("refuses the first device's resources, and closes everything made on it when it closes")
        void secondDevice() {
            var otherSdl = SdlGpuDevice.create(SdlGpuDevice.Options.defaults().withDebugMode(true));
            var other = GpuDevice.wrap(otherSdl);
            try (var target = renderTarget()) {
                var foreign = other.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 4, 4));
                try (var frame = device.beginFrame()) {
                    assertThrows(IllegalArgumentException.class, () -> frame.readback(foreign));
                    assertThrows(
                            IllegalArgumentException.class, () -> frame.renderPass(foreign, Load.keep(), pass -> {}));
                }
                try (var frame = other.beginFrame()) {
                    assertThrows(IllegalArgumentException.class, () -> frame.readback(target));
                }
                otherSdl.close();
                assertTrue(other.isClosed());
                assertTrue(foreign.isClosed(), "closed with its device");
                foreign.close();
                assertThrows(IllegalStateException.class, other::beginFrame);
                assertTrue(other.toString().contains("closed"));
            } finally {
                otherSdl.close();
            }
        }
    }

    // --- helpers ---------------------------------------------------------------

    /// A test of a pixel's centre, `(x + 0.5, y + 0.5)`.
    @FunctionalInterface
    private interface CentreTest {
        boolean test(double x, double y);
    }

    /// Checks `pixels` against a reference: red where `inside` holds for a
    /// pixel's centre and black where it does not, except where `onEdge` holds,
    /// which the driver's fill rule decides and the reference does not.
    private static void assertMatches(PixelBuffer pixels, CentreTest inside, CentreTest onEdge) {
        var checked = 0;
        for (var y = 0; y < SIZE; y++) {
            for (var x = 0; x < SIZE; x++) {
                var cx = x + 0.5;
                var cy = y + 0.5;
                if (onEdge.test(cx, cy)) {
                    continue;
                }
                assertEquals(inside.test(cx, cy) ? RED : BLACK, argb(pixels, x, y), "at " + x + "," + y);
                checked++;
            }
        }
        assertTrue(checked > SIZE * SIZE * 3 / 4, "the reference decides most pixels");
    }

    private static java.util.function.Predicate<PhysicalRect> contains(int x, int y) {
        return rect -> x >= rect.x() && x < rect.right() && y >= rect.y() && y < rect.bottom();
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

    private static byte[] random(int length, long seed) {
        var bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private static GpuTexture renderTarget() {
        return device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, SIZE, SIZE));
    }

    /// Clears a render target to opaque black, runs `body` in a render pass
    /// into it, and reads it back.
    private static PixelBuffer draw(GraphicsPipeline pipeline, Consumer<RenderPass> body) {
        try (var target = renderTarget();
                var frame = device.beginFrame()) {
            frame.renderPass(target, Load.clear(0, 0, 0, 1), body);
            var readback = frame.readback(target);
            frame.submit();
            return readback.awaitPixels();
        }
    }

    /// [#draw], with a depth target.
    private static PixelBuffer drawInto(DepthTarget depth, Consumer<RenderPass> body) {
        try (var target = renderTarget();
                var frame = device.beginFrame()) {
            frame.renderPass(target, Load.clear(0, 0, 0, 1), depth, body);
            var readback = frame.readback(target);
            frame.submit();
            return readback.awaitPixels();
        }
    }

    /// Mesh vertices, each `{x, y, z, r, g, b, a}` with `x` and `y` in pixels
    /// from the top left of a [#SIZE] square target, turned into normalised
    /// device coordinates.
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

    private static ByteBuffer triangleVertices(
            float x0, float y0, float x1, float y1, float x2, float y2, float z, float r, float g, float b) {
        return vertices(new float[] {x0, y0, z, r, g, b, 1}, new float[] {x1, y1, z, r, g, b, 1}, new float[] {
            x2, y2, z, r, g, b, 1
        });
    }

    /// Two triangles covering pixels `left` to `right` by `top` to `bottom`.
    private static ByteBuffer quad(
            float left, float top, float right, float bottom, float z, float r, float g, float b) {
        return vertices(
                new float[] {left, top, z, r, g, b, 1},
                new float[] {right, top, z, r, g, b, 1},
                new float[] {left, bottom, z, r, g, b, 1},
                new float[] {left, bottom, z, r, g, b, 1},
                new float[] {right, top, z, r, g, b, 1},
                new float[] {right, bottom, z, r, g, b, 1});
    }

    private static GpuBuffer vertexBuffer(ByteBuffer data) {
        return buffer(BufferUsage.VERTEX, data);
    }

    /// A buffer holding `data`, uploaded in a frame of its own.
    private static GpuBuffer buffer(BufferUsage usage, ByteBuffer data) {
        var buffer = device.createBuffer(usage, data.remaining());
        try (var frame = device.beginFrame()) {
            frame.copyPass(copy -> copy.upload(buffer, 0, data));
            frame.submit();
        }
        return buffer;
    }
}
