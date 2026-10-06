package dev.goldberry.gpu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
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

import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// The public GPU API on a real device: Metal here, Vulkan under lavapipe on the
/// GPU lane.
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
        // call. Calling it anyway failed the class, and a build without it.
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
    @DisplayName("drawing")
    class Drawing {

        @Test
        @DisplayName("a triangle from a vertex buffer reads back as the reference rasterizes it")
        void triangle() {
            try (var vertices = vertexBuffer(triangleVertices(4, 4, 28, 4, 4, 28, 0.5f, 1, 0, 0))) {
                var pixels = draw(pass -> {
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
                var pixels = draw(pass -> {
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
                    var pixels = draw(pass -> {
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
                var untested = draw(pass -> {
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
                    var pixels = draw(pass -> {
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
    @DisplayName("the texture model")
    class TextureModel {

        @Test
        @DisplayName("float colour targets clear past 1 and below 0, and read back as halves and floats")
        void floatTargets() {
            assertTrue(device.supports(
                    TextureFormat.R16G16B16A16_FLOAT, EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER)));
            try (var halves = device.createTexture(TextureSpec.renderTarget(TextureFormat.R16G16B16A16_FLOAT, 2, 2));
                    var floats = device.createTexture(TextureSpec.renderTarget(TextureFormat.R32_FLOAT, 2, 2));
                    var frame = device.beginFrame()) {
                assertTrue(halves.format().isFloat());
                frame.renderPass(halves, Load.clear(2f, 0.5f, -1f, 1f), pass -> {});
                frame.renderPass(floats, Load.clear(3.5f, 0, 0, 0), pass -> {});
                var halfBytes = frame.readback(halves);
                var floatBytes = frame.readback(floats);
                frame.submit();
                var read = halfBytes.await();
                assertEquals(2 * 2 * 8, read.remaining());
                assertEquals(2f, Float.float16ToFloat(read.getShort(0)));
                assertEquals(0.5f, Float.float16ToFloat(read.getShort(2)));
                assertEquals(-1f, Float.float16ToFloat(read.getShort(4)));
                assertEquals(1f, Float.float16ToFloat(read.getShort(6)));
                assertEquals(3.5f, floatBytes.await().getFloat(3 * 4), "the last pixel");
            }
            if (device.supports(TextureFormat.R11G11B10_UFLOAT, EnumSet.of(TextureUsage.COLOR_TARGET))) {
                try (var packed = device.createTexture(TextureSpec.renderTarget(TextureFormat.R11G11B10_UFLOAT, 2, 2)
                                .withUsages(EnumSet.of(TextureUsage.COLOR_TARGET)));
                        var frame = device.beginFrame()) {
                    frame.renderPass(packed, Load.clear(1f, 0.5f, 0.25f, 0f), pass -> {});
                    frame.submit();
                }
            }
        }

        @Test
        @DisplayName("a depth-only pass writes a depth map a later pass samples, and the pipelines do not cross")
        void depthOnlyAndSampledDepth() {
            try (var depth = device.createTexture(TextureSpec.sampledDepth(TextureFormat.D32_FLOAT, SIZE, SIZE));
                    var depthOnlyFragment = device.createShader(TestShaders.depthOnlyFragment());
                    var depthOnly = device.createPipeline(PipelineSpec.depthOnly(
                                    meshVertex, depthOnlyFragment, DepthTest.less(TextureFormat.D32_FLOAT))
                            .vertexBuffer(TestShaders.meshLayout())
                            .build());
                    var fullscreen = device.createShader(TestShaders.fullscreenVertex());
                    var sampleFragment = device.createShader(TestShaders.sampleFragment());
                    var sampled = device.createPipeline(
                            PipelineSpec.builder(fullscreen, sampleFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var nearest = device.createSampler(SamplerSpec.nearest());
                    var comparison = device.createSampler(SamplerSpec.linear().withCompare(CompareOp.LESS_OR_EQUAL));
                    var quadBuffer = vertexBuffer(quad(4, 4, 20, 20, 0.25f, 1, 0, 0));
                    var target = renderTarget();
                    var frame = device.beginFrame()) {
                assertTrue(depthOnly.spec().isDepthOnly());
                assertTrue(comparison.spec().compare().isPresent());
                frame.renderPass(DepthTarget.clear(depth), pass -> {
                    assertTrue(pass.isDepthOnly());
                    assertTrue(pass.target().isEmpty());
                    assertThrows(IllegalArgumentException.class, () -> pass.bindPipeline(mesh), "draws colour");
                    pass.bindPipeline(depthOnly);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.bindVertexBuffer(0, quadBuffer);
                    pass.draw(6);
                });
                var depths = frame.readback(depth);
                frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                    assertThrows(IllegalArgumentException.class, () -> pass.bindPipeline(depthOnly), "no colour");
                    pass.bindPipeline(sampled);
                    pass.bindFragmentSamplers(nearest, depth);
                    pass.draw(3);
                });
                var grey = frame.readback(target);
                frame.submit();
                var floats = depths.await();
                assertEquals(SIZE * SIZE * 4, floats.remaining());
                assertEquals(0.25f, floats.getFloat((8 * SIZE + 8) * 4), "inside the quad");
                assertEquals(1f, floats.getFloat((28 * SIZE + 28) * 4), "cleared to the far plane");
                var pixels = grey.awaitPixels();
                assertEquals(64, (argb(pixels, 8, 8) >> 16) & 0xFF, 1, "0.25 sampled from the depth map");
                assertEquals(255, (argb(pixels, 28, 28) >> 16) & 0xFF, "1.0 sampled from the depth map");
            }
        }

        @Test
        @DisplayName("mip levels are uploaded one at a time, generated from level 0, and read by a mipmapped sampler")
        void mipLevels() {
            var checker = new byte[4 * 4 * 4];
            for (var y = 0; y < 4; y++) {
                for (var x = 0; x < 4; x++) {
                    var white = (x + y) % 2 == 0;
                    var at = (y * 4 + x) * 4;
                    checker[at] = checker[at + 1] = checker[at + 2] = (byte) (white ? 0xFF : 0);
                    checker[at + 3] = (byte) 0xFF;
                }
            }
            var red = new byte[] {(byte) 0xFF, 0, 0, (byte) 0xFF};
            try (var texture = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, 4, 4)
                            .withMipChain());
                    var fullscreen = device.createShader(TestShaders.fullscreenVertex());
                    var sampleFragment = device.createShader(TestShaders.sampleFragment());
                    var sampled = device.createPipeline(
                            PipelineSpec.builder(fullscreen, sampleFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var trilinear = device.createSampler(SamplerSpec.trilinear());
                    var level0 = device.createSampler(SamplerSpec.nearest());
                    var small = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 1, 1));
                    var frame = device.beginFrame()) {
                assertEquals(3, texture.mipLevels());
                assertEquals(new PhysicalSize(2, 2), texture.level(1).size());
                frame.copyPass(copy -> {
                    copy.upload(texture, ByteBuffer.wrap(checker));
                    copy.upload(texture.level(2), ByteBuffer.wrap(red));
                });
                var uploaded = frame.readback(texture.level(2));
                frame.generateMipmaps(texture);
                var generated1 = frame.readback(texture.level(1));
                var generated2 = frame.readback(texture.level(2));
                frame.renderPass(small, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(sampled);
                    pass.bindFragmentSamplers(trilinear, texture);
                    pass.draw(3);
                });
                var throughMips = frame.readback(small);
                frame.renderPass(small, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(sampled);
                    pass.bindFragmentSamplers(level0, texture);
                    pass.draw(3);
                });
                var throughLevel0 = frame.readback(small);
                frame.submit();
                assertEquals(0xFFFF0000, uploaded.awaitPixels().pixels().getInt(0), "level 2 as uploaded");
                var one = generated1.await();
                assertEquals(2 * 2 * 4, one.remaining());
                for (var i = 0; i < 4; i++) {
                    assertEquals(127.5, Byte.toUnsignedInt(one.get(i * 4)), 1, "level 1 texel " + i);
                }
                var two = generated2.await();
                assertEquals(127.5, Byte.toUnsignedInt(two.get(0)), 1, "level 2, over the generated level 1");
                assertEquals(255, Byte.toUnsignedInt(two.get(3)));
                assertEquals(127.5, (argb(throughMips.awaitPixels(), 0, 0) >> 16) & 0xFF, 2, "the last level");
                var texel = (argb(throughLevel0.awaitPixels(), 0, 0) >> 16) & 0xFF;
                assertTrue(texel == 0 || texel == 255, "one texel of level 0, not a mean: " + texel);
            }
        }

        @Test
        @DisplayName("an array's layers are rendered into, uploaded and read back one at a time, and sampled by index")
        void arrayLayers() {
            var yellow = new byte[8 * 8 * 4];
            for (var i = 0; i < 64; i++) {
                yellow[i * 4] = 0;
                yellow[i * 4 + 1] = (byte) 0xFF;
                yellow[i * 4 + 2] = (byte) 0xFF;
                yellow[i * 4 + 3] = (byte) 0xFF;
            }
            try (var array = device.createTexture(TextureSpec.array(
                            TextureFormat.B8G8R8A8_UNORM,
                            8,
                            8,
                            4,
                            EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER)));
                    var fullscreen = device.createShader(TestShaders.fullscreenVertex());
                    var arrayFragment = device.createShader(TestShaders.arrayFragment());
                    var sampled = device.createPipeline(
                            PipelineSpec.builder(fullscreen, arrayFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var nearest = device.createSampler(SamplerSpec.nearest());
                    var target = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                    var frame = device.beginFrame()) {
                assertEquals(4, array.layers());
                assertTrue(array.spec().isArray());
                frame.renderPass(
                        array.layer(0),
                        Load.clear(1, 0, 0, 1),
                        pass -> assertEquals(array.layer(0), pass.target().orElseThrow()));
                frame.renderPass(array.layer(3), Load.clear(0, 0, 1, 1), pass -> {});
                frame.renderPass(array.layer(1), Load.clear(0, 1, 0, 1), pass -> {});
                frame.copyPass(copy -> copy.upload(array.layer(2), ByteBuffer.wrap(yellow)));
                var layer0 = frame.readback(array.layer(0));
                var layer1 = frame.readback(array.layer(1));
                var layer2 = frame.readback(array.layer(2), new PhysicalRect(3, 3, 2, 2));
                var layer3 = frame.readback(array.layer(3));
                frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(sampled);
                    pass.bindFragmentSamplers(nearest, array);
                    pass.pushFragmentUniforms(0, 3f, 0f, 0f, 0f);
                    pass.draw(3);
                });
                var sampledLayer3 = frame.readback(target);
                frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(sampled);
                    pass.bindFragmentSamplers(nearest, array);
                    pass.pushFragmentUniforms(0, 2f, 0f, 0f, 0f);
                    pass.draw(3);
                });
                var sampledLayer2 = frame.readback(target);
                frame.submit();
                assertEquals(RED, argb(layer0.awaitPixels(), 4, 4));
                assertEquals(GREEN, argb(layer1.awaitPixels(), 4, 4));
                assertEquals(0xFFFFFF00, argb(layer2.awaitPixels(), 1, 1));
                assertEquals(2, layer2.width());
                assertEquals(BLUE, argb(layer3.awaitPixels(), 7, 7));
                assertEquals(BLUE, argb(sampledLayer3.awaitPixels(), 4, 4), "layer 3 by index");
                assertEquals(0xFFFFFF00, argb(sampledLayer2.awaitPixels(), 4, 4), "layer 2 by index");
            }
        }

        @Test
        @DisplayName(
                "refuse a level or layer that does not exist, a depth texture not made to be sampled, and mips for one level")
        void refusals() {
            try (var texture = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, 4, 4)
                            .withMipLevels(2));
                    var sampledOnly = device.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 4, 4)
                            .withMipLevels(2));
                    var flat = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, 4, 4));
                    var depth = device.createTexture(TextureSpec.depth(TextureFormat.D16_UNORM, 4, 4));
                    var frame = device.beginFrame()) {
                assertThrows(IllegalArgumentException.class, () -> texture.level(2));
                assertThrows(IllegalArgumentException.class, () -> texture.layer(1));
                assertThrows(IllegalArgumentException.class, () -> texture.view(-1, 0));
                assertThrows(IllegalArgumentException.class, () -> frame.readback(depth), "not sampled");
                assertThrows(IllegalArgumentException.class, () -> frame.generateMipmaps(flat), "one level");
                assertThrows(IllegalArgumentException.class, () -> frame.generateMipmaps(sampledOnly), "no target");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> frame.renderPass(sampledOnly.level(1), Load.keep(), pass -> {}),
                        "not a colour target");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> frame.readback(texture.level(1), new PhysicalRect(0, 0, 4, 4)),
                        "level 1 is 2x2");
                frame.copyPass(copy -> assertThrows(
                        IllegalArgumentException.class,
                        () -> copy.upload(texture.level(1), ByteBuffer.allocate(4)),
                        "level 1 takes 16 bytes"));
            }
        }
    }

    @Nested
    @DisplayName("compute and storage")
    class Compute {

        /// A quad over pixels 4 to 20, as six `float4` positions in clip space,
        /// halved: what the compute pass scales back up.
        private static ByteBuffer halfQuad() {
            float left = (2f * 4 / SIZE - 1) / 2;
            float right = (2f * 20 / SIZE - 1) / 2;
            float top = (1 - 2f * 4 / SIZE) / 2;
            float bottom = (1 - 2f * 20 / SIZE) / 2;
            float[][] corners = {
                {left, top}, {right, top}, {left, bottom}, {left, bottom}, {right, top}, {right, bottom}
            };
            var bytes = ByteBuffer.allocate(6 * 16).order(ByteOrder.nativeOrder());
            for (var corner : corners) {
                bytes.putFloat(corner[0]).putFloat(corner[1]).putFloat(0f).putFloat(1f);
            }
            return bytes.flip();
        }

        @Test
        @DisplayName("a compute pass scales a storage buffer by a uniform, and a draw reads the result as its vertices")
        void scalesAndDraws() {
            try (var input = buffer(EnumSet.of(BufferUsage.COMPUTE_STORAGE_READ), halfQuad());
                    var output = device.createBuffer(
                            EnumSet.of(BufferUsage.COMPUTE_STORAGE_WRITE, BufferUsage.GRAPHICS_STORAGE_READ), 6 * 16);
                    var scale = device.createComputePipeline(TestShaders.scaleCompute());
                    var storageVertex = device.createShader(TestShaders.storageVertex());
                    var pipeline = device.createPipeline(
                            PipelineSpec.builder(storageVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var target = renderTarget();
                    var frame = device.beginFrame()) {
                assertEquals(1, storageVertex.storageBuffers());
                assertEquals(64, scale.code().threadsX());
                frame.computePass(output, pass -> {
                    pass.bindPipeline(scale);
                    pass.bindStorageBuffers(input);
                    pass.pushUniforms(0, 2f, 0f, 0f, 0f);
                    pass.dispatch(1);
                });
                frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(pipeline);
                    pass.bindVertexStorageBuffers(output);
                    pass.draw(6);
                });
                var readback = frame.readback(target);
                frame.submit();
                var pixels = readback.awaitPixels();
                assertEquals(RED, argb(pixels, 6, 6), "inside the scaled quad, outside the half one");
                assertEquals(RED, argb(pixels, 12, 12));
                assertEquals(BLACK, argb(pixels, 28, 28));
                assertEquals(BLACK, argb(pixels, 2, 2));
            }
        }

        @Test
        @DisplayName("additive blending sums two translucent quads")
        void additive() {
            try (var quadBuffer = vertexBuffer(quad(4, 4, 28, 28, 0.5f, 0.25f, 0f, 0f));
                    var additive = device.createPipeline(
                            PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .vertexBuffer(TestShaders.meshLayout())
                                    .blend(BlendMode.ADDITIVE)
                                    .build())) {
                var pixels = draw(pass -> {
                    pass.bindPipeline(additive);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.bindVertexBuffer(0, quadBuffer);
                    pass.draw(6);
                    pass.draw(6);
                });
                var pixel = argb(pixels, 16, 16);
                assertEquals(127.5, (pixel >> 16) & 0xFF, 1, "0.25 + 0.25 of red");
                assertEquals(0, (pixel >> 8) & 0xFF);
                assertEquals(255, pixel >>> 24, "alpha: 1 from the clear, plus 1 + 1, saturated");
            }
        }

        @Test
        @DisplayName("refuse a write target not made for compute, storage of the wrong count or usage, and nesting")
        void refusals() {
            try (var vertexOnly = device.createBuffer(BufferUsage.VERTEX, 64);
                    var written = device.createBuffer(BufferUsage.COMPUTE_STORAGE_WRITE, 64);
                    var readable = device.createBuffer(BufferUsage.COMPUTE_STORAGE_READ, 64);
                    var scale = device.createComputePipeline(TestShaders.scaleCompute());
                    var frame = device.beginFrame()) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> frame.computePass(vertexOnly, pass -> {}),
                        "not made to be written by compute");
                frame.computePass(written, pass -> {
                    assertThrows(IllegalStateException.class, () -> pass.bindStorageBuffers(readable), "no pipeline");
                    assertThrows(IllegalStateException.class, () -> pass.dispatch(1), "no pipeline");
                    pass.bindPipeline(scale);
                    assertThrows(IllegalArgumentException.class, pass::bindStorageBuffers, "one is declared");
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> pass.bindStorageBuffers(vertexOnly),
                            "not made to be read by compute");
                    assertThrows(IllegalStateException.class, () -> pass.dispatch(1), "storage not bound");
                    pass.bindStorageBuffers(readable);
                    assertThrows(IllegalArgumentException.class, () -> pass.dispatch(0));
                    assertThrows(IllegalArgumentException.class, () -> pass.pushUniforms(1, 1f), "one block");
                    assertThrows(IllegalStateException.class, () -> frame.copyPass(copy -> {}), "passes do not nest");
                });
                frame.computePass(
                        List.of(),
                        List.of(),
                        pass -> assertThrows(
                                IllegalArgumentException.class, () -> pass.bindPipeline(scale), "writes one buffer"));
                try (var target = renderTarget()) {
                    frame.renderPass(target, Load.dontCare(), pass -> {
                        pass.bindPipeline(mesh);
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> pass.bindVertexStorageBuffers(readable),
                                "the mesh shader reads none");
                    });
                }
            }
        }
    }

    @Nested
    @DisplayName("multisampling")
    class Multisampling {

        @Test
        @DisplayName("a 4x target resolves a triangle's edge to coverage, where one sample gives all or nothing")
        void resolves() {
            // A triangle whose hypotenuse crosses pixel centres at an angle, so
            // its edge pixels are partly covered.
            try (var vertices = vertexBuffer(triangleVertices(2, 2, 30, 2, 2, 30, 0.5f, 1, 0, 0));
                    var msaa = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, SIZE, SIZE)
                            .withUsages(EnumSet.of(TextureUsage.COLOR_TARGET))
                            .withSamples(4));
                    var resolved = renderTarget();
                    var pipeline = device.createPipeline(
                            PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .vertexBuffer(TestShaders.meshLayout())
                                    .samples(4)
                                    .build());
                    var frame = device.beginFrame()) {
                assertEquals(4, msaa.samples());
                assertEquals(4, pipeline.spec().samples());
                frame.renderPass(msaa, Load.clear(0, 0, 0, 1), resolved.level(0), pass -> {
                    assertThrows(IllegalArgumentException.class, () -> pass.bindPipeline(mesh), "one sample");
                    pass.bindPipeline(pipeline);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.bindVertexBuffer(0, vertices);
                    pass.draw(3);
                });
                var readback = frame.readback(resolved);
                frame.submit();
                var pixels = readback.awaitPixels();
                assertEquals(RED, argb(pixels, 6, 6), "inside");
                assertEquals(BLACK, argb(pixels, 28, 28), "outside");
                var partial = 0;
                for (var i = 3; i < 29; i++) {
                    // The hypotenuse runs from (30, 2) to (2, 30): x + y = 32,
                    // through the centre of every pixel (i, 31 - i).
                    var red = (argb(pixels, i, 31 - i) >> 16) & 0xFF;
                    if (red > 0 && red < 255) {
                        partial++;
                    }
                }
                assertTrue(partial > 10, "edge pixels are partly covered: " + partial);
            }
            try (var vertices = vertexBuffer(triangleVertices(2, 2, 30, 2, 2, 30, 0.5f, 1, 0, 0))) {
                var aliased = draw(pass -> {
                    pass.bindPipeline(mesh);
                    pass.pushVertexUniforms(0, IDENTITY);
                    pass.bindVertexBuffer(0, vertices);
                    pass.draw(3);
                });
                for (var i = 3; i < 29; i++) {
                    var red = (argb(aliased, i, 31 - i) >> 16) & 0xFF;
                    assertTrue(red == 0 || red == 255, "one sample is all or nothing: " + red);
                }
            }
        }

        @Test
        @DisplayName(
                "refuses a resolve from one sample, into a multisampled or mismatched texture, and a mismatched depth")
        void refusals() {
            try (var msaa = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, SIZE, SIZE)
                            .withUsages(EnumSet.of(TextureUsage.COLOR_TARGET))
                            .withSamples(4));
                    var otherMsaa =
                            device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, SIZE, SIZE)
                                    .withUsages(EnumSet.of(TextureUsage.COLOR_TARGET))
                                    .withSamples(2));
                    var single = renderTarget();
                    var small = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                    var rgba =
                            device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, SIZE, SIZE));
                    var depth = device.createTexture(TextureSpec.depth(TextureFormat.D16_UNORM, SIZE, SIZE));
                    var frame = device.beginFrame()) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> frame.renderPass(single, Load.dontCare(), small.level(0), pass -> {}),
                        "one sample");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> frame.renderPass(msaa, Load.dontCare(), otherMsaa.level(0), pass -> {}),
                        "multisampled");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> frame.renderPass(msaa, Load.dontCare(), small.level(0), pass -> {}),
                        "size");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> frame.renderPass(msaa, Load.dontCare(), rgba.level(0), pass -> {}),
                        "format");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> frame.renderPass(
                                msaa, Load.dontCare(), single.level(0), DepthTarget.clear(depth), pass -> {}),
                        "one-sample depth");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PipelineSpec.builder(meshVertex, meshFragment, TextureFormat.B8G8R8A8_UNORM)
                                .samples(3)
                                .build());
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
                    assertSame(target, pass.target().orElseThrow());
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
    private static PixelBuffer draw(Consumer<RenderPass> body) {
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
        return buffer(EnumSet.of(usage), data);
    }

    /// A buffer for `usages` holding `data`, uploaded in a frame of its own.
    private static GpuBuffer buffer(java.util.Set<BufferUsage> usages, ByteBuffer data) {
        var buffer = device.createBuffer(usages, data.remaining());
        try (var frame = device.beginFrame()) {
            frame.copyPass(copy -> copy.upload(buffer, 0, data));
            frame.submit();
        }
        return buffer;
    }
}
