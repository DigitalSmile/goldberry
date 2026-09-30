package io.github.digitalsmile.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuTestLauncher;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuGraphicsPipeline;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuLoad;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuRegion;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuSampler;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuShader;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTexture;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuBlend;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;

/// The Y'CbCr shaders against [YuvConversion], for every layout, matrix and
/// range the frame contract can carry: media phase 4's colour half
/// (`docs/gpu-plan.md`, phase 6).
///
/// A picture of one colour, drawn 1:1, must come back as the reference's bytes
/// within one step; a picture whose chroma differs left and right must keep
/// each side's colour away from the edge between them.
@Tag(GpuTestLauncher.TAG)
@DisplayName("Y'CbCr to RGB on the GPU")
class YuvDrawTest {

    private static final int WIDTH = 16;
    private static final int HEIGHT = 8;

    /// 8-bit codes: limited black and white, the two familiar reds, a green, a
    /// blue, a grey and an out-of-gamut value the clamp has to catch.
    private static final int[][] COLOURS = {
        {16, 128, 128},
        {235, 128, 128},
        {81, 90, 240},
        {63, 102, 240},
        {145, 54, 34},
        {41, 240, 110},
        {126, 128, 128},
        {200, 20, 250}
    };

    private static SdlGpuDevice device;
    private static SdlGpuSampler sampler;
    private static final Map<YuvLayout, SdlGpuGraphicsPipeline> PIPELINES = new EnumMap<>(YuvLayout.class);
    private static final List<SdlGpuShader> SHADERS = new ArrayList<>();

    @BeforeAll
    static void createDevice() {
        device = GpuDeviceRequirement.enforce();
        sampler = device.createSampler(SdlGpuFilter.LINEAR);
        var quad = ShaderLibrary.create(device, BuiltInShader.QUAD_VERTEX);
        SHADERS.add(quad);
        for (var layout : YuvLayout.values()) {
            var fragment = ShaderLibrary.create(device, layout.shader());
            SHADERS.add(fragment);
            PIPELINES.put(
                    layout,
                    device.createGraphicsPipeline(
                            quad, fragment, SdlGpuTextureFormat.R8G8B8A8_UNORM, SdlGpuBlend.REPLACE));
        }
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

    @Test
    @DisplayName("every layout, matrix and range gives the reference's bytes, within one step")
    void matchesTheReference() {
        var worst = 0;
        for (var layout : YuvLayout.values()) {
            for (var matrix : YuvMatrix.values()) {
                for (var fullRange : new boolean[] {false, true}) {
                    var conversion = new YuvConversion(layout, matrix, fullRange);
                    for (var colour : COLOURS) {
                        var codes = scaled(colour, layout);
                        var pixels = draw(conversion, (x, y) -> codes);
                        var expected = conversion.toRgbBytes(codes[0], codes[1], codes[2]);
                        for (var i = 0; i < WIDTH * HEIGHT; i++) {
                            for (var channel = 0; channel < 3; channel++) {
                                var actual = Byte.toUnsignedInt(pixels[i * 4 + channel]);
                                var off = Math.abs(actual - expected[channel]);
                                worst = Math.max(worst, off);
                                assertTrue(
                                        off <= 1,
                                        conversion + " codes " + java.util.Arrays.toString(codes) + " channel "
                                                + channel + ": expected " + expected[channel] + ", was " + actual);
                            }
                            assertEquals(255, Byte.toUnsignedInt(pixels[i * 4 + 3]), "alpha");
                        }
                    }
                }
            }
        }
        System.out.println("Y'CbCr on the GPU: worst difference from the reference " + worst + " in 255");
    }

    @Test
    @DisplayName("keeps each half's chroma on its own side, for every layout")
    void chromaStaysInPlace() {
        for (var layout : YuvLayout.values()) {
            var conversion = new YuvConversion(layout, YuvMatrix.BT709, false);
            var red = scaled(new int[] {63, 102, 240}, layout);
            var blue = scaled(new int[] {32, 240, 118}, layout);
            var pixels = draw(conversion, (x, y) -> x < WIDTH / 2 ? red : blue);
            var left = conversion.toRgbBytes(red[0], red[1], red[2]);
            var right = conversion.toRgbBytes(blue[0], blue[1], blue[2]);
            for (var y = 0; y < HEIGHT; y++) {
                for (var x : new int[] {0, 1, 2, 3, 4, 5}) {
                    assertNear(left, pixels, y * WIDTH + x, layout + " left at " + x);
                }
                for (var x : new int[] {10, 11, 12, 13, 14, 15}) {
                    assertNear(right, pixels, y * WIDTH + x, layout + " right at " + x);
                }
            }
        }
    }

    /// Codes for a pixel: its Y', Cb and Cr.
    @FunctionalInterface
    private interface Picture {
        int[] codes(int x, int y);
    }

    /// An 8-bit colour's codes at the layout's depth.
    private static int[] scaled(int[] colour, YuvLayout layout) {
        var shift = layout.bitDepth() - 8;
        return new int[] {colour[0] << shift, colour[1] << shift, colour[2] << shift};
    }

    /// Uploads `picture`'s planes, draws them 1:1, and reads the result back.
    private static byte[] draw(YuvConversion conversion, Picture picture) {
        var layout = conversion.layout();
        var textures = new ArrayList<SdlGpuTexture>();
        try (var target = device.createTexture(
                SdlGpuTextureFormat.R8G8B8A8_UNORM,
                WIDTH,
                HEIGHT,
                EnumSet.of(SdlGpuTextureUsage.COLOR_TARGET, SdlGpuTextureUsage.SAMPLER))) {
            var planes = planes(layout, picture);
            for (var plane = 0; plane < planes.size(); plane++) {
                textures.add(upload(
                        layout.planeFormats().get(plane),
                        layout.planeWidth(plane, WIDTH),
                        layout.planeHeight(plane, HEIGHT),
                        planes.get(plane)));
            }
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginRenderPass(target, SdlGpuLoad.clear(0, 0, 0, 0))) {
                pass.bindPipeline(PIPELINES.get(layout));
                pass.bindFragmentSamplers(sampler, textures.toArray(SdlGpuTexture[]::new));
                pass.pushVertexUniforms(
                        0, Quad.of(0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT).uniforms());
                pass.pushFragmentUniforms(0, conversion.uniforms(layout.planeWidth(1, WIDTH)));
                pass.draw(Quad.VERTICES);
            }
            commands.submit();
            return readBack(target);
        } finally {
            textures.forEach(SdlGpuTexture::close);
        }
    }

    /// The picture's planes, stored as the layout stores them.
    private static List<byte[]> planes(YuvLayout layout, Picture picture) {
        var wide = layout.bitDepth() > 8;
        var shift = layout == YuvLayout.P010 ? 6 : 0;
        var chromaWidth = layout.planeWidth(1, WIDTH);
        var chromaHeight = layout.planeHeight(1, HEIGHT);
        var bytes = wide ? 2 : 1;
        var luma = ByteBuffer.allocate(WIDTH * HEIGHT * bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                put(luma, picture.codes(x, y)[0] << shift, wide);
            }
        }
        var interleaved = layout.planeFormats().size() == 2;
        var cb = ByteBuffer.allocate(chromaWidth * chromaHeight * bytes * (interleaved ? 2 : 1))
                .order(ByteOrder.LITTLE_ENDIAN);
        var cr = ByteBuffer.allocate(chromaWidth * chromaHeight * bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (var y = 0; y < chromaHeight; y++) {
            for (var x = 0; x < chromaWidth; x++) {
                // One chroma sample for each two-by-two block of luma.
                var codes = picture.codes(x * 2, y * 2);
                put(cb, codes[1] << shift, wide);
                put(interleaved ? cb : cr, codes[2] << shift, wide);
            }
        }
        return interleaved ? List.of(luma.array(), cb.array()) : List.of(luma.array(), cb.array(), cr.array());
    }

    private static void put(ByteBuffer buffer, int value, boolean wide) {
        if (wide) {
            buffer.putShort((short) value);
        } else {
            buffer.put((byte) value);
        }
    }

    private static SdlGpuTexture upload(SdlGpuTextureFormat format, int width, int height, byte[] bytes) {
        var texture = device.createTexture(format, width, height, Set.of(SdlGpuTextureUsage.SAMPLER));
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

    private static void assertNear(int[] expected, byte[] pixels, int index, String where) {
        for (var channel = 0; channel < 3; channel++) {
            var actual = Byte.toUnsignedInt(pixels[index * 4 + channel]);
            assertTrue(
                    Math.abs(actual - expected[channel]) <= 1,
                    where + " channel " + channel + ": expected " + expected[channel] + ", was " + actual);
        }
    }
}
