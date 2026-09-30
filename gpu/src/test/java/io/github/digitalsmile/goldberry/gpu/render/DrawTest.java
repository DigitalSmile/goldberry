package io.github.digitalsmile.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuTestLauncher;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuLoad;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuRegion;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuShader;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTexture;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuBlend;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;

/// The toolkit's shaders drawing into a texture that is read back: phase 2's
/// exit (`docs/gpu-plan.md`). On Metal here, on lavapipe on the GPU lane.
///
/// The UI quad is the one that matters most: a texture copied 1:1 through
/// [BuiltInShader#TEXTURE_FRAGMENT] with a nearest sampler must come back byte
/// for byte, or the composited window would not look like the CPU one.
@Tag(GpuTestLauncher.TAG)
@DisplayName("drawing with the built-in shaders")
class DrawTest {

    private static final Set<SdlGpuTextureUsage> TARGET =
            EnumSet.of(SdlGpuTextureUsage.COLOR_TARGET, SdlGpuTextureUsage.SAMPLER);

    private static SdlGpuDevice device;
    private static SdlGpuShader quad;
    private static SdlGpuShader texture;
    private static SdlGpuShader solid;

    @BeforeAll
    static void createDevice() {
        device = GpuDeviceRequirement.enforce();
        quad = ShaderLibrary.create(device, BuiltInShader.QUAD_VERTEX);
        texture = ShaderLibrary.create(device, BuiltInShader.TEXTURE_FRAGMENT);
        solid = ShaderLibrary.create(device, BuiltInShader.SOLID_FRAGMENT);
    }

    @AfterAll
    static void destroyDevice() {
        // Skipped before SDL was reached: nothing to give back, and no library to
        // call. Calling it anyway failed the class, and a build without it (ADR-0495).
        if (device == null) {
            return;
        }
        if (device != null) {
            device.close();
        }
        Sdl.get().quit();
    }

    @AfterEach
    void onlyTheShadersStayOpen() {
        assertEquals(3, device.openResources(), "a test left a resource open");
    }

    @Test
    @DisplayName("a solid quad fills exactly its pixels, the top left at the top left")
    void solidQuad() {
        try (var target = device.createTexture(SdlGpuTextureFormat.R8G8B8A8_UNORM, 32, 16, TARGET);
                var pipeline = device.createGraphicsPipeline(quad, solid, target.format(), SdlGpuBlend.REPLACE)) {
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginRenderPass(target, SdlGpuLoad.clear(0, 0, 0, 1))) {
                pass.bindPipeline(pipeline);
                pass.pushVertexUniforms(0, Quad.of(4, 2, 8, 6, 32, 16).uniforms());
                pass.pushFragmentUniforms(0, 1f, 0f, 0f, 1f);
                pass.draw(Quad.VERTICES);
            }
            commands.submit();
            var pixels = readBack(target);
            for (var y = 0; y < 16; y++) {
                for (var x = 0; x < 32; x++) {
                    var inside = x >= 4 && x < 12 && y >= 2 && y < 8;
                    var at = (y * 32 + x) * 4;
                    assertEquals(inside ? 255 : 0, Byte.toUnsignedInt(pixels[at]), "red at " + x + "," + y);
                    assertEquals(255, Byte.toUnsignedInt(pixels[at + 3]), "alpha at " + x + "," + y);
                }
            }
        }
    }

    @Test
    @DisplayName("a texture drawn 1:1 with a nearest sampler comes back byte for byte")
    void textureOneToOne() {
        var bytes = new byte[24 * 12 * 4];
        new Random(24).nextBytes(bytes);
        try (var source = upload(SdlGpuTextureFormat.B8G8R8A8_UNORM, 24, 12, bytes);
                var target = device.createTexture(SdlGpuTextureFormat.B8G8R8A8_UNORM, 24, 12, TARGET);
                var sampler = device.createSampler(SdlGpuFilter.NEAREST);
                var pipeline = device.createGraphicsPipeline(quad, texture, target.format(), SdlGpuBlend.REPLACE)) {
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginRenderPass(target, SdlGpuLoad.clear(0, 0, 0, 0))) {
                pass.bindPipeline(pipeline);
                pass.bindFragmentSamplers(sampler, source);
                pass.pushVertexUniforms(0, Quad.of(0, 0, 24, 12, 24, 12).uniforms());
                pass.draw(Quad.VERTICES);
            }
            commands.submit();
            assertArrayEquals(bytes, readBack(target));
        }
    }

    @Test
    @DisplayName("premultiplied over puts a half-covered pixel over what is there")
    void premultipliedOver() {
        // Half-transparent red, premultiplied: 128 red, 128 alpha, over opaque blue.
        var pixel = new byte[] {0, 0, (byte) 128, (byte) 128};
        try (var source = upload(SdlGpuTextureFormat.B8G8R8A8_UNORM, 1, 1, pixel);
                var target = device.createTexture(SdlGpuTextureFormat.B8G8R8A8_UNORM, 4, 4, TARGET);
                var sampler = device.createSampler(SdlGpuFilter.NEAREST);
                var pipeline =
                        device.createGraphicsPipeline(quad, texture, target.format(), SdlGpuBlend.PREMULTIPLIED_OVER)) {
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginRenderPass(target, SdlGpuLoad.clear(0, 0, 1, 1))) {
                pass.bindPipeline(pipeline);
                pass.bindFragmentSamplers(sampler, source);
                pass.pushVertexUniforms(0, Quad.of(0, 0, 4, 4, 4, 4).uniforms());
                pass.draw(Quad.VERTICES);
            }
            commands.submit();
            var back = readBack(target);
            // B = 255 × (1 − 128/255) = 127, R = 128, A = 128 + 255 × (1 − 128/255) = 255,
            // within ADR-0050's tolerance of 2 in 256.
            assertNear(127, back[0], "blue");
            assertNear(0, back[1], "green");
            assertNear(128, back[2], "red");
            assertNear(255, back[3], "alpha");
        }
    }

    @Test
    @DisplayName("refuses a draw with its sampler unbound, and a pipeline for another format")
    void refusesIncompleteDraws() {
        try (var target = device.createTexture(SdlGpuTextureFormat.B8G8R8A8_UNORM, 4, 4, TARGET);
                var other = device.createTexture(SdlGpuTextureFormat.R8G8B8A8_UNORM, 4, 4, TARGET);
                var pipeline = device.createGraphicsPipeline(quad, texture, target.format(), SdlGpuBlend.REPLACE)) {
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginRenderPass(target, SdlGpuLoad.keep())) {
                assertThrows(IllegalStateException.class, () -> pass.draw(Quad.VERTICES));
                pass.bindPipeline(pipeline);
                assertThrows(IllegalStateException.class, () -> pass.draw(Quad.VERTICES));
                assertThrows(IllegalArgumentException.class, () -> pass.setScissor(new SdlGpuRegion(2, 2, 4, 4)));
            }
            try (var pass = commands.beginRenderPass(other, SdlGpuLoad.keep())) {
                assertThrows(IllegalArgumentException.class, () -> pass.bindPipeline(pipeline));
            }
            commands.cancel();
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> device.createGraphicsPipeline(
                        texture, quad, SdlGpuTextureFormat.B8G8R8A8_UNORM, SdlGpuBlend.REPLACE));
    }

    @Test
    @DisplayName("a scissor keeps a quad inside a clip")
    void scissor() {
        try (var target = device.createTexture(SdlGpuTextureFormat.R8G8B8A8_UNORM, 16, 16, TARGET);
                var pipeline = device.createGraphicsPipeline(quad, solid, target.format(), SdlGpuBlend.REPLACE)) {
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginRenderPass(target, SdlGpuLoad.clear(0, 0, 0, 1))) {
                pass.bindPipeline(pipeline);
                pass.setScissor(new SdlGpuRegion(4, 4, 4, 4));
                pass.pushVertexUniforms(0, Quad.of(0, 0, 16, 16, 16, 16).uniforms());
                pass.pushFragmentUniforms(0, 0f, 1f, 0f, 1f);
                pass.draw(Quad.VERTICES);
            }
            commands.submit();
            var pixels = readBack(target);
            var green = 0;
            for (var y = 0; y < 16; y++) {
                for (var x = 0; x < 16; x++) {
                    var lit = Byte.toUnsignedInt(pixels[(y * 16 + x) * 4 + 1]) == 255;
                    assertEquals(x >= 4 && x < 8 && y >= 4 && y < 8, lit, "green at " + x + "," + y);
                    green += lit ? 1 : 0;
                }
            }
            assertEquals(16, green);
        }
    }

    private static SdlGpuTexture upload(SdlGpuTextureFormat format, int width, int height, byte[] bytes) {
        var texture = device.createTexture(format, width, height, EnumSet.of(SdlGpuTextureUsage.SAMPLER));
        try (var buffer = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, bytes.length)) {
            buffer.map(false).put(bytes);
            buffer.unmap();
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginCopyPass()) {
                pass.upload(buffer, 0, texture, SdlGpuRegion.of(texture), false);
            }
            commands.submit();
        }
        return texture;
    }

    private static byte[] readBack(SdlGpuTexture target) {
        var region = SdlGpuRegion.of(target);
        var size = (int) target.byteSize(region);
        try (var download = device.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, size)) {
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginCopyPass()) {
                pass.download(target, region, download, 0);
            }
            try (var fence = commands.submitWithFence()) {
                fence.await();
            }
            var bytes = new byte[size];
            download.map(false).get(bytes);
            download.unmap();
            return bytes;
        }
    }

    private static void assertNear(int expected, byte actual, String channel) {
        var value = Byte.toUnsignedInt(actual);
        assertTrue(Math.abs(expected - value) <= 2, channel + ": expected " + expected + " ± 2, was " + value);
    }
}
