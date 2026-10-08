package dev.goldberry.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

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

/// The texture formats and shapes past plain 2D colour, on a real device: sRGB
/// colour, block-compressed formats, cubes, volumes, and anisotropic samplers.
@Tag(GpuTestLauncher.TAG)
@DisplayName("texture formats and shapes, on a real device")
class GpuTextureShapesTest {

    private static final EnumSet<TextureUsage> SAMPLED = EnumSet.of(TextureUsage.SAMPLER);

    private static SdlGpuDevice sdl;
    private static GpuDevice device;
    private static Shader fullscreen;
    private static int baseline;

    @BeforeAll
    static void createDevice() {
        sdl = GpuDeviceRequirement.enforce();
        device = GpuDevice.wrap(sdl);
        fullscreen = device.createShader(TestShaders.fullscreenVertex());
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
    @DisplayName("sRGB")
    class Srgb {

        @Test
        @DisplayName("a target encodes what it is cleared to, and a sampler decodes it to linear")
        void encodesAndDecodes() {
            assertTrue(device.supports(
                    TextureFormat.R8G8B8A8_UNORM_SRGB, EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER)));
            try (var target = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM_SRGB, 2, 2));
                    var encoded = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM_SRGB, 1, 1));
                    var sample = device.createShader(TestShaders.sampleFragment());
                    var pipeline =
                            device.createPipeline(PipelineSpec.builder(fullscreen, sample, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var nearest = device.createSampler(SamplerSpec.nearest());
                    var linear = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 1, 1));
                    var frame = device.beginFrame()) {
                assertTrue(target.format().isSrgb());
                frame.renderPass(target, Load.clear(0.5f, 0.5f, 0.5f, 1f), pass -> {});
                var cleared = frame.readback(target);
                frame.copyPass(copy -> copy.upload(
                        encoded, ByteBuffer.wrap(new byte[] {(byte) 188, (byte) 188, (byte) 188, (byte) 255})));
                frame.renderPass(linear, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(pipeline);
                    pass.bindFragmentSamplers(nearest, encoded);
                    pass.draw(3);
                });
                var decoded = frame.readback(linear);
                frame.submit();
                var bytes = cleared.await();
                assertEquals(188, Byte.toUnsignedInt(bytes.get(0)), 1, "linear 0.5 is stored as sRGB 188");
                assertEquals(255, Byte.toUnsignedInt(bytes.get(3)), "alpha is not encoded");
                var red = (decoded.awaitPixels().pixels().getInt(0) >> 16) & 0xFF;
                assertEquals(128, red, 1, "sRGB 188 is sampled as linear 0.5");
            }
        }

        @Test
        @DisplayName("generated mip levels average in linear: black and white make sRGB 188, not 128")
        void mipmapsAverageInLinear() {
            // A checker of black and white texels: each texel of level 1 is the
            // mean of two of each.
            var checker = new byte[4 * 4 * 4];
            for (var y = 0; y < 4; y++) {
                for (var x = 0; x < 4; x++) {
                    var at = (y * 4 + x) * 4;
                    var value = (byte) ((x + y) % 2 == 0 ? 0xFF : 0);
                    checker[at] = checker[at + 1] = checker[at + 2] = value;
                    checker[at + 3] = (byte) 0xFF;
                }
            }
            try (var srgb = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM_SRGB, 4, 4)
                            .withMipLevels(2));
                    var plain = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, 4, 4)
                            .withMipLevels(2));
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> {
                    copy.upload(srgb, ByteBuffer.wrap(checker));
                    copy.upload(plain, ByteBuffer.wrap(checker));
                });
                frame.generateMipmaps(srgb);
                frame.generateMipmaps(plain);
                var srgbLevel1 = frame.readback(srgb.level(1));
                var plainLevel1 = frame.readback(plain.level(1));
                frame.submit();
                var encoded = srgbLevel1.await();
                for (var i = 0; i < 4; i++) {
                    assertEquals(188, Byte.toUnsignedInt(encoded.get(i * 4)), 2, "sRGB level 1 texel " + i);
                }
                assertEquals(127.5, Byte.toUnsignedInt(plainLevel1.await().get(0)), 1, "a plain format, as stored");
            }
        }
    }

    @Nested
    @DisplayName("block compression")
    class Compressed {

        @Test
        @DisplayName("a BC7 texture of 2x2 blocks is uploaded whole and a block at a time, and sampled")
        void bc7() {
            assumeTrue(device.supports(TextureFormat.BC7_RGBA_UNORM, SAMPLED), "this device decodes no BC7");
            var blocks = new byte[4 * 16];
            var reds = new int[] {255, 171, 85, 1};
            for (var i = 0; i < 4; i++) {
                System.arraycopy(bc7Solid(reds[i]), 0, blocks, i * 16, 16);
            }
            try (var texture = device.createTexture(TextureSpec.sampled(TextureFormat.BC7_RGBA_UNORM, 8, 8));
                    var sample = device.createShader(TestShaders.sampleFragment());
                    var pipeline =
                            device.createPipeline(PipelineSpec.builder(fullscreen, sample, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var nearest = device.createSampler(SamplerSpec.nearest());
                    var whole = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                    var patched = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                    var frame = device.beginFrame()) {
                assertEquals(32L, texture.format().bytesPerRow(8));
                frame.copyPass(copy -> copy.upload(texture, ByteBuffer.wrap(blocks)));
                frame.renderPass(whole, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(pipeline);
                    pass.bindFragmentSamplers(nearest, texture);
                    pass.draw(3);
                });
                var asUploaded = frame.readback(whole);
                // The bottom-right block alone, from an image of the whole level:
                // block row 1 starts one row of blocks, 32 bytes, in.
                var image = new byte[64];
                System.arraycopy(bc7Solid(129), 0, image, 48, 16);
                frame.copyPass(copy ->
                        copy.upload(texture, ByteBuffer.wrap(image), 32, List.of(new PhysicalRect(4, 4, 4, 4))));
                frame.renderPass(patched, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(pipeline);
                    pass.bindFragmentSamplers(nearest, texture);
                    pass.draw(3);
                });
                var afterPatch = frame.readback(patched);
                frame.submit();
                var pixels = asUploaded.awaitPixels();
                assertEquals(255, red(pixels, 1, 1));
                assertEquals(171, red(pixels, 6, 1));
                assertEquals(85, red(pixels, 1, 6));
                assertEquals(1, red(pixels, 6, 6));
                var patchedPixels = afterPatch.awaitPixels();
                assertEquals(129, red(patchedPixels, 6, 6), "the uploaded block");
                assertEquals(85, red(patchedPixels, 1, 6), "the rest kept");
                assertEquals(171, red(patchedPixels, 6, 1), "the rest kept");
            }
        }

        @Test
        @DisplayName("a mip chain's levels below a block are uploaded as one block each")
        void levelsBelowABlock() {
            var format = device.supports(TextureFormat.BC1_RGBA_UNORM, SAMPLED)
                    ? TextureFormat.BC1_RGBA_UNORM
                    : TextureFormat.ASTC_4x4_UNORM;
            assumeTrue(device.supports(format, SAMPLED), "this device decodes neither BC1 nor ASTC");
            try (var texture = device.createTexture(
                            TextureSpec.sampled(format, 8, 8).withMipChain());
                    var frame = device.beginFrame()) {
                assertEquals(4, texture.mipLevels());
                frame.copyPass(copy -> {
                    for (var level = 0; level < texture.mipLevels(); level++) {
                        var view = texture.level(level);
                        var size = format.byteSize(view.width(), view.height());
                        copy.upload(view, ByteBuffer.allocate((int) size));
                    }
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.upload(texture.level(2), ByteBuffer.allocate(format.bytesPerBlock() - 1)),
                            "a 2x2 level is a whole block");
                });
                frame.submit();
            }
        }

        @Test
        @DisplayName("an ASTC 4x4 block of one colour is sampled as that colour, where the device decodes ASTC")
        void astc() {
            assumeTrue(device.supports(TextureFormat.ASTC_4x4_UNORM, SAMPLED), "this device decodes no ASTC");
            try (var texture = device.createTexture(TextureSpec.sampled(TextureFormat.ASTC_4x4_UNORM, 4, 4));
                    var sample = device.createShader(TestShaders.sampleFragment());
                    var pipeline =
                            device.createPipeline(PipelineSpec.builder(fullscreen, sample, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var nearest = device.createSampler(SamplerSpec.nearest());
                    var target = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 4, 4));
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> copy.upload(texture, ByteBuffer.wrap(astcSolid(0xFFFF))));
                frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(pipeline);
                    pass.bindFragmentSamplers(nearest, texture);
                    pass.draw(3);
                });
                var readback = frame.readback(target);
                frame.submit();
                assertEquals(255, red(readback.awaitPixels(), 2, 2));
            }
        }

        @Test
        @DisplayName("refuses a region off its blocks, a readback, and generated mip levels")
        void refusals() {
            var format = device.supports(TextureFormat.BC7_RGBA_UNORM, SAMPLED)
                    ? TextureFormat.BC7_RGBA_UNORM
                    : TextureFormat.ASTC_4x4_UNORM;
            assumeTrue(device.supports(format, SAMPLED), "this device decodes neither BC7 nor ASTC");
            try (var texture = device.createTexture(
                            TextureSpec.sampled(format, 8, 8).withMipChain());
                    var frame = device.beginFrame()) {
                frame.copyPass(copy -> {
                    var image = ByteBuffer.allocate(64);
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.upload(texture, image, 32, List.of(new PhysicalRect(2, 0, 4, 4))),
                            "starts inside a block");
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.upload(texture, image, 32, List.of(new PhysicalRect(0, 0, 6, 4))),
                            "ends inside a block");
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> copy.upload(texture, image, 16, List.of(new PhysicalRect(0, 0, 8, 8))),
                            "a row of two blocks is 32 bytes");
                });
                assertThrows(IllegalArgumentException.class, () -> frame.readback(texture));
                assertThrows(IllegalArgumentException.class, () -> frame.generateMipmaps(texture));
                frame.submit();
            }
        }
    }

    @Nested
    @DisplayName("cubes")
    class Cubes {

        @Test
        @DisplayName("six faces cleared and uploaded one at a time are each read along their axis")
        void sixFaces() {
            Map<CubeFace, Integer> colours = new EnumMap<>(CubeFace.class);
            colours.put(CubeFace.POSITIVE_X, 0xFFFF0000);
            colours.put(CubeFace.NEGATIVE_X, 0xFF00FF00);
            colours.put(CubeFace.POSITIVE_Y, 0xFF0000FF);
            colours.put(CubeFace.NEGATIVE_Y, 0xFFFFFFFF);
            colours.put(CubeFace.POSITIVE_Z, 0xFFFF00FF);
            colours.put(CubeFace.NEGATIVE_Z, 0xFFFFFF00);
            var yellow = new byte[4 * 4 * 4];
            for (var i = 0; i < 16; i++) {
                yellow[i * 4 + 1] = (byte) 0xFF;
                yellow[i * 4 + 2] = (byte) 0xFF;
                yellow[i * 4 + 3] = (byte) 0xFF;
            }
            try (var cube = device.createTexture(
                            TextureSpec.cube(TextureFormat.B8G8R8A8_UNORM, 4).withMipChain());
                    var cubeFragment = device.createShader(TestShaders.cubeFragment());
                    var pipeline = device.createPipeline(
                            PipelineSpec.builder(fullscreen, cubeFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var nearest = device.createSampler(SamplerSpec.nearest());
                    var target = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 2, 2));
                    var frame = device.beginFrame()) {
                assertEquals(TextureType.CUBE, cube.type());
                assertEquals(6, cube.layers());
                assertEquals(3, cube.mipLevels());
                assertEquals(1, cube.face(CubeFace.NEGATIVE_Y, 2).width());
                for (var face : CubeFace.values()) {
                    if (face == CubeFace.NEGATIVE_Z) {
                        continue;
                    }
                    int argb = colours.get(face);
                    frame.renderPass(
                            cube.face(face, 0),
                            Load.clear(
                                    ((argb >> 16) & 0xFF) / 255f,
                                    ((argb >> 8) & 0xFF) / 255f,
                                    (argb & 0xFF) / 255f,
                                    1f),
                            pass -> {});
                }
                frame.copyPass(copy -> copy.upload(cube.face(CubeFace.NEGATIVE_Z, 0), ByteBuffer.wrap(yellow)));
                var faceRead = frame.readback(cube.face(CubeFace.POSITIVE_Y, 0));
                var directions = Map.of(
                        CubeFace.POSITIVE_X, new float[] {1, 0, 0},
                        CubeFace.NEGATIVE_X, new float[] {-1, 0, 0},
                        CubeFace.POSITIVE_Y, new float[] {0, 1, 0},
                        CubeFace.NEGATIVE_Y, new float[] {0, -1, 0},
                        CubeFace.POSITIVE_Z, new float[] {0, 0, 1},
                        CubeFace.NEGATIVE_Z, new float[] {0, 0, -1});
                Map<CubeFace, Readback> sampled = new EnumMap<>(CubeFace.class);
                for (var face : CubeFace.values()) {
                    var direction = directions.get(face);
                    frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                        pass.bindPipeline(pipeline);
                        pass.bindFragmentSamplers(nearest, cube);
                        pass.pushFragmentUniforms(0, direction[0], direction[1], direction[2], 0f);
                        pass.draw(3);
                    });
                    sampled.put(face, frame.readback(target));
                }
                frame.submit();
                assertEquals(0xFF0000FF, faceRead.awaitPixels().pixels().getInt(0), "+Y read back as a face");
                for (var face : CubeFace.values()) {
                    assertEquals(
                            (int) colours.get(face),
                            sampled.get(face).awaitPixels().pixels().getInt(0),
                            face::toString);
                }
            }
        }

        @Test
        @DisplayName("names faces only of a cube, and only of its levels")
        void refusals() {
            try (var cube = device.createTexture(TextureSpec.cube(TextureFormat.B8G8R8A8_UNORM, 4));
                    var flat = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 4, 4))) {
                assertEquals(5, cube.face(CubeFace.NEGATIVE_Z, 0).layer());
                assertThrows(IllegalArgumentException.class, () -> cube.face(CubeFace.POSITIVE_X, 1));
                assertThrows(IllegalArgumentException.class, () -> flat.face(CubeFace.POSITIVE_X, 0));
                assertThrows(IllegalArgumentException.class, () -> cube.slice(0, 0));
                assertThrows(IllegalArgumentException.class, () -> cube.layer(6));
            }
        }
    }

    @Nested
    @DisplayName("volumes")
    class Volumes {

        @Test
        @DisplayName("slices are uploaded one at a time, read back, and filtered between when sampled")
        void slices() {
            var colours = new int[][] {{255, 0, 0}, {0, 255, 0}, {0, 0, 255}, {255, 255, 255}};
            try (var volume = device.createTexture(TextureSpec.volume(TextureFormat.R8G8B8A8_UNORM, 4, 4, 4));
                    var volumeFragment = device.createShader(TestShaders.volumeFragment());
                    var pipeline = device.createPipeline(
                            PipelineSpec.builder(fullscreen, volumeFragment, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var linear = device.createSampler(SamplerSpec.linear());
                    var target = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 4, 4));
                    var frame = device.beginFrame()) {
                assertEquals(TextureType.THREE_D, volume.type());
                assertEquals(4, volume.depth());
                frame.copyPass(copy -> {
                    for (var z = 0; z < 4; z++) {
                        var slice = new byte[4 * 4 * 4];
                        for (var i = 0; i < 16; i++) {
                            slice[i * 4] = (byte) colours[z][0];
                            slice[i * 4 + 1] = (byte) colours[z][1];
                            slice[i * 4 + 2] = (byte) colours[z][2];
                            slice[i * 4 + 3] = (byte) 0xFF;
                        }
                        copy.upload(volume.slice(0, z), ByteBuffer.wrap(slice));
                    }
                });
                var slice2 = frame.readback(volume.slice(0, 2));
                // Slice z's texels are centred at (z + 0.5) / 4.
                var reads = new ArrayList<Readback>();
                for (var w : new float[] {0.125f, 0.375f, 0.5f}) {
                    frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                        pass.bindPipeline(pipeline);
                        pass.bindFragmentSamplers(linear, volume);
                        pass.pushFragmentUniforms(0, w, 0f, 0f, 0f);
                        pass.draw(3);
                    });
                    reads.add(frame.readback(target));
                }
                frame.submit();
                assertEquals(0xFF0000FF, slice2.awaitPixels().pixels().getInt(0), "slice 2 is blue");
                assertEquals(0xFFFF0000, reads.get(0).awaitPixels().pixels().getInt(0), "slice 0 kept: red");
                assertEquals(0xFF00FF00, reads.get(1).awaitPixels().pixels().getInt(0), "slice 1: green");
                var between = reads.get(2).awaitPixels().pixels().getInt(0);
                assertEquals(0, (between >> 16) & 0xFF, "no red between green and blue");
                assertEquals(127.5, (between >> 8) & 0xFF, 2, "half green");
                assertEquals(127.5, between & 0xFF, 2, "half blue");
            }
        }

        @Test
        @DisplayName("names slices only of a volume and of a level's depth, and is not rendered into")
        void refusals() {
            try (var volume = device.createTexture(TextureSpec.volume(TextureFormat.R8G8B8A8_UNORM, 4, 4, 4)
                            .withMipChain());
                    var flat = device.createTexture(TextureSpec.sampled(TextureFormat.R8G8B8A8_UNORM, 4, 4));
                    var frame = device.beginFrame()) {
                assertEquals(2, volume.slice(1, 1).height());
                assertThrows(IllegalArgumentException.class, () -> volume.slice(1, 2), "level 1 is two deep");
                assertThrows(IllegalArgumentException.class, () -> volume.slice(0, 4));
                assertThrows(IllegalArgumentException.class, () -> flat.slice(0, 0));
                assertThrows(IllegalArgumentException.class, () -> volume.face(CubeFace.POSITIVE_X, 0));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> frame.renderPass(volume.slice(0, 0), Load.keep(), pass -> {}),
                        "not a colour target");
                frame.submit();
            }
        }
    }

    @Nested
    @DisplayName("anisotropic samplers")
    class Anisotropy {

        @Test
        @DisplayName("a trilinear sampler with 8x anisotropy is made and reads a mipmapped texture")
        void eightTimes() {
            var grey = new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0xFF};
            try (var texture = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, 1, 1));
                    var sample = device.createShader(TestShaders.sampleFragment());
                    var pipeline =
                            device.createPipeline(PipelineSpec.builder(fullscreen, sample, TextureFormat.B8G8R8A8_UNORM)
                                    .build());
                    var anisotropic = device.createSampler(SamplerSpec.trilinear()
                            .withAddressMode(AddressMode.REPEAT)
                            .withAnisotropy(8));
                    var target = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 4, 4));
                    var frame = device.beginFrame()) {
                assertEquals(8f, anisotropic.spec().maxAnisotropy());
                frame.copyPass(copy -> copy.upload(texture, ByteBuffer.wrap(grey)));
                frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
                    pass.bindPipeline(pipeline);
                    pass.bindFragmentSamplers(anisotropic, texture);
                    pass.draw(3);
                });
                var readback = frame.readback(target);
                frame.submit();
                assertEquals(0x80, red(readback.awaitPixels(), 1, 1));
            }
        }
    }

    private static int red(PixelBuffer pixels, int x, int y) {
        return (pixels.pixels().getInt(y * pixels.stride() + x * 4) >> 16) & 0xFF;
    }

    /// A BC7 block in mode 6 whose every texel is `red`, an odd value, with
    /// green and blue 1 and alpha 255: both endpoints the same colour, and
    /// every index 0.
    private static byte[] bc7Solid(int red) {
        if (red % 2 == 0) {
            throw new IllegalArgumentException("mode 6 shares one low bit across the channels; " + red + " is even");
        }
        var block = new byte[16];
        setBits(block, 0, 7, 0b1000000);
        var channels = new int[] {red >> 1, red >> 1, 0, 0, 0, 0, 127, 127};
        for (var i = 0; i < channels.length; i++) {
            setBits(block, 7 + i * 7, 7, channels[i]);
        }
        setBits(block, 63, 1, 1);
        setBits(block, 64, 1, 1);
        return block;
    }

    /// An ASTC block of one colour (a void-extent block): `red` in 16 bits,
    /// green and blue 0, alpha opaque.
    private static byte[] astcSolid(int red) {
        var block = new byte[16];
        // Block mode 0x1FC, LDR, two reserved ones, and no extent.
        var header = new int[] {0xFC, 0xFD, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
        for (var i = 0; i < header.length; i++) {
            block[i] = (byte) header[i];
        }
        block[8] = (byte) red;
        block[9] = (byte) (red >> 8);
        block[14] = (byte) 0xFF;
        block[15] = (byte) 0xFF;
        return block;
    }

    private static void setBits(byte[] block, int at, int count, int value) {
        for (var i = 0; i < count; i++) {
            if (((value >> i) & 1) != 0) {
                var bit = at + i;
                block[bit / 8] |= (byte) (1 << (bit % 8));
            }
        }
    }
}
