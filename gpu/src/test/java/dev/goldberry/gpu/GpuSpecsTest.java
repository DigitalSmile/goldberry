package dev.goldberry.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;

/// The records resources are made from, checked without a device: what they
/// refuse, and what their factories make.
@DisplayName("the GPU API's specs")
class GpuSpecsTest {

    @Nested
    @DisplayName("a texture spec")
    class Textures {

        @Test
        @DisplayName("makes sampled, render-target and depth textures with the usages each needs")
        void factories() {
            var sampled = TextureSpec.sampled(TextureFormat.R8_UNORM, 4, 2);
            assertEquals(EnumSet.of(TextureUsage.SAMPLER), sampled.usages());
            assertEquals(new PhysicalSize(4, 2), sampled.size());
            assertEquals(
                    EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER),
                    TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 1, 1).usages());
            assertEquals(
                    EnumSet.of(TextureUsage.DEPTH_TARGET),
                    TextureSpec.depth(TextureFormat.D32_FLOAT, 1, 1).usages());
        }

        @Test
        @DisplayName("refuses an empty size, no usage, and usages the format cannot have")
        void refusals() {
            assertThrows(IllegalArgumentException.class, () -> TextureSpec.sampled(TextureFormat.R8_UNORM, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> TextureSpec.sampled(TextureFormat.R8_UNORM, 1, -1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TextureSpec(TextureFormat.R8_UNORM, 1, 1, EnumSet.noneOf(TextureUsage.class)));
            assertThrows(IllegalArgumentException.class, () -> TextureSpec.sampled(TextureFormat.D16_UNORM, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> TextureSpec.depth(TextureFormat.B8G8R8A8_UNORM, 1, 1));
        }

        @Test
        @DisplayName("has one layer, one level and one sample unless asked, and names each level's size")
        void counts() {
            var plain = TextureSpec.sampled(TextureFormat.R8_UNORM, 16, 8);
            assertEquals(1, plain.layers());
            assertEquals(1, plain.mipLevels());
            assertEquals(1, plain.samples());
            assertFalse(plain.isArray());
            var array = TextureSpec.array(TextureFormat.B8G8R8A8_UNORM, 16, 8, 64, EnumSet.of(TextureUsage.SAMPLER));
            assertEquals(64, array.layers());
            assertTrue(array.isArray());
            assertEquals(5, TextureSpec.maxMipLevels(16, 8));
            assertEquals(1, TextureSpec.maxMipLevels(1, 1));
            var chain = plain.withMipChain();
            assertEquals(5, chain.mipLevels());
            assertEquals(16, chain.levelWidth(0));
            assertEquals(2, chain.levelWidth(3));
            assertEquals(1, chain.levelHeight(3), "a level is never below one texel");
            assertEquals(1, chain.levelWidth(4));
            assertEquals(3, plain.withMipLevels(3).mipLevels());
            var msaa = TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 16, 8)
                    .withUsages(EnumSet.of(TextureUsage.COLOR_TARGET))
                    .withSamples(4);
            assertEquals(4, msaa.samples());
            assertEquals(
                    EnumSet.of(TextureUsage.DEPTH_TARGET, TextureUsage.SAMPLER),
                    TextureSpec.sampledDepth(TextureFormat.D32_FLOAT, 4, 4).usages());
        }

        @Test
        @DisplayName(
                "refuses counts the size cannot hold, and a multisampled texture that is sampled, layered or mipmapped")
        void refusesCounts() {
            var plain = TextureSpec.sampled(TextureFormat.R8_UNORM, 16, 8);
            assertThrows(IllegalArgumentException.class, () -> plain.withMipLevels(0));
            assertThrows(IllegalArgumentException.class, () -> plain.withMipLevels(6));
            assertThrows(IllegalArgumentException.class, () -> plain.withLayers(0));
            assertThrows(IllegalArgumentException.class, () -> plain.withSamples(3));
            assertThrows(IllegalArgumentException.class, () -> plain.withSamples(4), "sampled");
            var target = TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 16, 8)
                    .withUsages(EnumSet.of(TextureUsage.COLOR_TARGET));
            assertThrows(
                    IllegalArgumentException.class, () -> target.withSamples(2).withLayers(2));
            assertThrows(
                    IllegalArgumentException.class, () -> target.withSamples(2).withMipLevels(2));
            assertThrows(
                    IllegalArgumentException.class, () -> plain.withMipChain().levelWidth(5));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TextureSpec(
                            TextureFormat.D16_UNORM,
                            1,
                            1,
                            EnumSet.of(TextureUsage.DEPTH_TARGET, TextureUsage.COLOR_TARGET)),
                    "a depth format is never a colour target");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TextureSpec(TextureFormat.D16_UNORM, 1, 1, EnumSet.of(TextureUsage.SAMPLER)),
                    "a depth format sampled alone is not a depth target");
        }

        @Test
        @DisplayName("copies its usages, so the caller's set changing does not change it")
        void copiesUsages() {
            var usages = EnumSet.of(TextureUsage.SAMPLER);
            var spec = new TextureSpec(TextureFormat.R8_UNORM, 1, 1, usages);
            usages.add(TextureUsage.COLOR_TARGET);
            assertEquals(EnumSet.of(TextureUsage.SAMPLER), spec.usages());
            assertThrows(
                    UnsupportedOperationException.class, () -> spec.usages().add(TextureUsage.COLOR_TARGET));
        }

        @Test
        @DisplayName("is 2D, or an array of more than one layer, as the factories and withLayers choose")
        void types() {
            assertEquals(
                    TextureType.TWO_D,
                    TextureSpec.sampled(TextureFormat.R8_UNORM, 4, 4).type());
            assertEquals(
                    TextureType.TWO_D,
                    new TextureSpec(TextureFormat.R8_UNORM, 4, 4, 1, 1, 1, EnumSet.of(TextureUsage.SAMPLER)).type());
            assertEquals(
                    TextureType.TWO_D_ARRAY,
                    new TextureSpec(TextureFormat.R8_UNORM, 4, 4, 3, 1, 1, EnumSet.of(TextureUsage.SAMPLER)).type());
            assertEquals(
                    TextureType.TWO_D_ARRAY,
                    TextureSpec.array(TextureFormat.R8_UNORM, 4, 4, 1, EnumSet.of(TextureUsage.SAMPLER))
                            .type(),
                    "an array of one layer is still an array");
            var layered = TextureSpec.sampled(TextureFormat.R8_UNORM, 4, 4).withLayers(2);
            assertEquals(TextureType.TWO_D_ARRAY, layered.type());
            assertTrue(layered.isArray());
            assertEquals(1, TextureSpec.sampled(TextureFormat.R8_UNORM, 4, 4).depth());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TextureSpec(
                            TextureType.TWO_D,
                            TextureFormat.R8_UNORM,
                            4,
                            4,
                            1,
                            2,
                            1,
                            1,
                            EnumSet.of(TextureUsage.SAMPLER)),
                    "a 2D texture of two layers");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TextureSpec(
                            TextureType.TWO_D,
                            TextureFormat.R8_UNORM,
                            4,
                            4,
                            2,
                            1,
                            1,
                            1,
                            EnumSet.of(TextureUsage.SAMPLER)),
                    "only a volume has a depth");
        }

        @Test
        @DisplayName("makes a cube of six square faces, rendered into and sampled, with a mip chain on request")
        void cubes() {
            var cube = TextureSpec.cube(TextureFormat.R16G16B16A16_FLOAT, 128);
            assertEquals(TextureType.CUBE, cube.type());
            assertEquals(6, cube.layers());
            assertEquals(128, cube.width());
            assertEquals(128, cube.height());
            assertEquals(EnumSet.of(TextureUsage.SAMPLER, TextureUsage.COLOR_TARGET), cube.usages());
            assertFalse(cube.isArray());
            assertEquals(8, cube.withMipChain().mipLevels());
            assertEquals(TextureType.CUBE, cube.withMipChain().type());
            var compressed = TextureSpec.cube(TextureFormat.BC7_RGBA_UNORM, 64, EnumSet.of(TextureUsage.SAMPLER));
            assertEquals(TextureType.CUBE, compressed.type());
            assertThrows(IllegalArgumentException.class, () -> cube.withLayers(5));
            assertThrows(IllegalArgumentException.class, () -> cube.withLayers(12), "no cube arrays");
            assertThrows(IllegalArgumentException.class, () -> cube.withSamples(4));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TextureSpec(
                            TextureType.CUBE,
                            TextureFormat.R8G8B8A8_UNORM,
                            8,
                            4,
                            1,
                            6,
                            1,
                            1,
                            EnumSet.of(TextureUsage.SAMPLER)),
                    "not square");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TextureSpec.cube(
                            TextureFormat.R8G8B8A8_UNORM,
                            8,
                            EnumSet.of(TextureUsage.SAMPLER, TextureUsage.COMPUTE_STORAGE_READ)),
                    "storage");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TextureSpec.cube(TextureFormat.BC7_RGBA_UNORM, 64),
                    "a compressed cube is not rendered into");
            assertThrows(IllegalArgumentException.class, () -> TextureSpec.cube(TextureFormat.D32_FLOAT, 64));
            assertThrows(IllegalArgumentException.class, () -> TextureSpec.cube(TextureFormat.R8_UNORM, 0));
        }

        @Test
        @DisplayName("makes a volume of one layer and a depth, sampled, whose levels halve the depth too")
        void volumes() {
            var lut = TextureSpec.volume(TextureFormat.R8G8B8A8_UNORM, 32, 32, 32);
            assertEquals(TextureType.THREE_D, lut.type());
            assertEquals(32, lut.depth());
            assertEquals(1, lut.layers());
            assertEquals(EnumSet.of(TextureUsage.SAMPLER), lut.usages());
            assertEquals(4, TextureSpec.maxMipLevels(4, 4, 8), "the depth is the longest axis");
            var tall = TextureSpec.volume(TextureFormat.R8_UNORM, 4, 4, 16).withMipChain();
            assertEquals(5, tall.mipLevels());
            assertEquals(16, tall.levelDepth(0));
            assertEquals(4, tall.levelDepth(2));
            assertEquals(1, tall.levelWidth(2));
            assertEquals(1, tall.levelDepth(4));
            assertEquals(1, TextureSpec.sampled(TextureFormat.R8_UNORM, 4, 4).levelDepth(0));
            assertThrows(IllegalArgumentException.class, () -> TextureSpec.volume(TextureFormat.R8_UNORM, 4, 4, 0));
            assertThrows(IllegalArgumentException.class, () -> lut.withLayers(2));
            assertThrows(IllegalArgumentException.class, () -> lut.withSamples(2));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> lut.withUsages(EnumSet.of(TextureUsage.SAMPLER, TextureUsage.COLOR_TARGET)),
                    "a volume is not rendered into");
            assertThrows(IllegalArgumentException.class, () -> TextureSpec.volume(TextureFormat.D16_UNORM, 4, 4, 4));
        }

        @Test
        @DisplayName("takes a block-compressed format sampled alone, in whole blocks, never multisampled")
        void compressed() {
            var bc7 = TextureSpec.sampled(TextureFormat.BC7_RGBA_UNORM, 2048, 1024)
                    .withMipChain();
            assertEquals(12, bc7.mipLevels(), "levels below a block are allowed");
            assertEquals(1, bc7.levelWidth(11));
            TextureSpec.array(TextureFormat.BC5_RG_UNORM, 8, 8, 4, EnumSet.of(TextureUsage.SAMPLER));
            TextureSpec.volume(TextureFormat.BC1_RGBA_UNORM, 8, 8, 3);
            for (var usage : List.of(
                    TextureUsage.COLOR_TARGET,
                    TextureUsage.DEPTH_TARGET,
                    TextureUsage.GRAPHICS_STORAGE_READ,
                    TextureUsage.COMPUTE_STORAGE_READ,
                    TextureUsage.COMPUTE_STORAGE_WRITE)) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new TextureSpec(
                                TextureFormat.BC3_RGBA_UNORM, 8, 8, EnumSet.of(TextureUsage.SAMPLER, usage)),
                        usage::toString);
            }
            assertThrows(
                    IllegalArgumentException.class, () -> TextureSpec.renderTarget(TextureFormat.BC7_RGBA_UNORM, 8, 8));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TextureSpec(
                            TextureFormat.BC7_RGBA_UNORM, 8, 8, 1, 1, 4, EnumSet.of(TextureUsage.SAMPLER)),
                    "multisampled");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TextureSpec.sampled(TextureFormat.ASTC_4x4_UNORM, 6, 8),
                    "level 0 is not whole blocks");
            assertThrows(IllegalArgumentException.class, () -> TextureSpec.sampled(TextureFormat.BC1_RGBA_UNORM, 4, 2));
        }
    }

    @Nested
    @DisplayName("a texture format")
    class Formats {

        @Test
        @DisplayName("knows its block: 4x4 and 8 or 16 bytes when compressed, one pixel otherwise")
        void blocks() {
            assertEquals(new PhysicalSize(1, 1), TextureFormat.B8G8R8A8_UNORM.blockSize());
            assertEquals(4, TextureFormat.B8G8R8A8_UNORM.bytesPerBlock());
            assertEquals(new PhysicalSize(4, 4), TextureFormat.BC7_RGBA_UNORM.blockSize());
            assertEquals(8, TextureFormat.BC1_RGBA_UNORM.bytesPerBlock());
            assertEquals(8, TextureFormat.BC1_RGBA_UNORM_SRGB.bytesPerBlock());
            assertEquals(16, TextureFormat.BC3_RGBA_UNORM.bytesPerBlock());
            assertEquals(16, TextureFormat.BC5_RG_UNORM.bytesPerBlock());
            assertEquals(16, TextureFormat.BC7_RGBA_UNORM_SRGB.bytesPerBlock());
            assertEquals(16, TextureFormat.ASTC_4x4_UNORM.bytesPerBlock());
            for (var format : TextureFormat.values()) {
                var name = format.name();
                assertEquals(name.startsWith("BC") || name.startsWith("ASTC"), format.isCompressed(), name);
                assertEquals(name.endsWith("_SRGB"), format.isSrgb(), name);
                if (format.isCompressed()) {
                    assertThrows(UnsupportedOperationException.class, format::bytesPerPixel, name);
                    assertFalse(format.isDepth() || format.isFloat(), name);
                } else {
                    assertEquals(format.bytesPerPixel(), format.bytesPerBlock(), name);
                    assertEquals(new PhysicalSize(1, 1), format.blockSize(), name);
                }
            }
        }

        @Test
        @DisplayName("sizes rows and images in whole blocks, rounding a partial block up")
        void sizes() {
            assertEquals(4L * 4, TextureFormat.R8G8B8A8_UNORM.bytesPerRow(4));
            assertEquals(4L * 3 * 2, TextureFormat.R8G8B8A8_UNORM_SRGB.byteSize(3, 2));
            assertEquals(16L * 2, TextureFormat.BC7_RGBA_UNORM.bytesPerRow(8));
            assertEquals(16L, TextureFormat.BC7_RGBA_UNORM.bytesPerRow(2), "a 2-texel mip is one block");
            assertEquals(16L, TextureFormat.BC7_RGBA_UNORM.byteSize(1, 1));
            assertEquals(8L * 512 * 512, TextureFormat.BC1_RGBA_UNORM.byteSize(2048, 2048));
            assertEquals(16L * 3 * 2, TextureFormat.ASTC_4x4_UNORM.byteSize(9, 5));
            // A 2048² BC7 texture with its whole chain: a quarter of the bytes of
            // RGBA8, block rounding included.
            var chain = TextureSpec.sampled(TextureFormat.BC7_RGBA_UNORM, 2048, 2048)
                    .withMipChain();
            var bytes = 0L;
            for (var level = 0; level < chain.mipLevels(); level++) {
                bytes += TextureFormat.BC7_RGBA_UNORM.byteSize(chain.levelWidth(level), chain.levelHeight(level));
            }
            assertEquals(5_592_432L, bytes);
            assertThrows(IllegalArgumentException.class, () -> TextureFormat.BC7_RGBA_UNORM.bytesPerRow(0));
            assertThrows(IllegalArgumentException.class, () -> TextureFormat.R8_UNORM.byteSize(1, 0));
        }

        @Test
        @DisplayName("turns a texel region into whole blocks, and refuses one that is not on them")
        void uploadBlocks() {
            var level = new PhysicalSize(16, 8);
            var plain = new PhysicalRect(3, 1, 5, 2);
            assertEquals(plain, CopyPass.blocks(TextureFormat.B8G8R8A8_UNORM, level, plain));
            assertEquals(
                    new PhysicalRect(1, 0, 2, 2),
                    CopyPass.blocks(TextureFormat.BC7_RGBA_UNORM, level, new PhysicalRect(4, 0, 8, 8)));
            assertEquals(
                    new PhysicalRect(0, 0, 1, 1),
                    CopyPass.blocks(TextureFormat.BC1_RGBA_UNORM, new PhysicalSize(2, 2), new PhysicalRect(0, 0, 2, 2)),
                    "a level below a block is one block");
            assertEquals(
                    new PhysicalRect(2, 0, 1, 1),
                    CopyPass.blocks(
                            TextureFormat.BC1_RGBA_UNORM, new PhysicalSize(10, 4), new PhysicalRect(8, 0, 2, 4)),
                    "a region may end at the level's edge");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CopyPass.blocks(TextureFormat.BC7_RGBA_UNORM, level, new PhysicalRect(2, 0, 4, 4)),
                    "starts inside a block");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CopyPass.blocks(TextureFormat.BC7_RGBA_UNORM, level, new PhysicalRect(0, 0, 6, 4)),
                    "ends inside a block, not at the edge");
        }
    }

    @Nested
    @DisplayName("vertex input")
    class VertexInput {

        @Test
        @DisplayName("a buffer holds attributes that fit its stride, at distinct locations")
        void layouts() {
            var layout = VertexBufferLayout.perVertex(
                    0,
                    28,
                    VertexAttribute.of(0, VertexFormat.FLOAT3, 0),
                    VertexAttribute.of(1, VertexFormat.FLOAT4, 12));
            assertEquals(VertexInputRate.VERTEX, layout.rate());
            assertEquals(2, layout.attributes().size());
            assertEquals(
                    VertexInputRate.INSTANCE,
                    VertexBufferLayout.perInstance(1, 8, VertexAttribute.of(2, VertexFormat.FLOAT2, 0))
                            .rate());
        }

        @Test
        @DisplayName("refuses an attribute past the stride, a location twice, none at all, and negative numbers")
        void refusals() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VertexBufferLayout.perVertex(0, 12, VertexAttribute.of(0, VertexFormat.FLOAT4, 0)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VertexBufferLayout.perVertex(
                            0,
                            32,
                            VertexAttribute.of(0, VertexFormat.FLOAT3, 0),
                            VertexAttribute.of(0, VertexFormat.FLOAT3, 12)));
            assertThrows(IllegalArgumentException.class, () -> VertexBufferLayout.perVertex(0, 16));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VertexBufferLayout.perVertex(-1, 16, VertexAttribute.of(0, VertexFormat.FLOAT, 0)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VertexBufferLayout.perVertex(0, 0, VertexAttribute.of(0, VertexFormat.FLOAT, 0)));
            assertThrows(IllegalArgumentException.class, () -> VertexAttribute.of(-1, VertexFormat.FLOAT, 0));
            assertThrows(IllegalArgumentException.class, () -> VertexAttribute.of(0, VertexFormat.FLOAT, -4));
        }

        @Test
        @DisplayName("copies its attribute list")
        void copies() {
            var attributes = new java.util.ArrayList<>(List.of(VertexAttribute.of(0, VertexFormat.FLOAT, 0)));
            var layout = new VertexBufferLayout(0, 4, VertexInputRate.VERTEX, attributes);
            attributes.clear();
            assertEquals(1, layout.attributes().size());
        }
    }

    @Nested
    @DisplayName("a sampler spec")
    class Samplers {

        @Test
        @DisplayName("reads level 0 alone and compares nothing unless asked")
        void defaults() {
            var linear = SamplerSpec.linear();
            assertTrue(linear.mipFilter().isEmpty());
            assertTrue(linear.compare().isEmpty());
            assertEquals(Optional.of(Filter.LINEAR), SamplerSpec.trilinear().mipFilter());
            assertEquals(
                    Optional.of(Filter.NEAREST),
                    linear.withMipFilter(Filter.NEAREST).mipFilter());
            var shadow = SamplerSpec.linear().withCompare(CompareOp.LESS_OR_EQUAL);
            assertEquals(Optional.of(CompareOp.LESS_OR_EQUAL), shadow.compare());
            assertEquals(
                    AddressMode.REPEAT,
                    shadow.withAddressMode(AddressMode.REPEAT).addressMode());
            assertEquals(
                    shadow.compare(), shadow.withAddressMode(AddressMode.REPEAT).compare(), "kept");
        }
    }

    @Nested
    @DisplayName("compute code")
    class Compute {

        @Test
        @DisplayName("carries its counts and workgroup, and refuses no code, a negative count and an empty workgroup")
        void builds() {
            var code = ComputeCode.builder(64, 1, 1)
                    .readOnlyStorageBuffers(1)
                    .readWriteStorageBuffers(2)
                    .uniformBuffers(1)
                    .code(ShaderFormat.SPIRV, new byte[] {1, 2, 3})
                    .build();
            assertEquals(64, code.threadsX());
            assertEquals(2, code.readWriteStorageBuffers());
            assertEquals(Optional.of(ShaderFormat.SPIRV), code.formatFor(EnumSet.of(ShaderFormat.SPIRV)));
            assertTrue(code.formatFor(EnumSet.of(ShaderFormat.MSL)).isEmpty());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ComputeCode.builder(1, 1, 1).build(),
                    "no code");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ComputeCode.builder(0, 1, 1)
                            .code(ShaderFormat.SPIRV, new byte[] {1})
                            .build());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ComputeCode.builder(1, 1, 1)
                            .samplers(-1)
                            .code(ShaderFormat.SPIRV, new byte[] {1})
                            .build());
            var graphics = ShaderCode.builder(ShaderStage.VERTEX)
                    .storageBuffers(1)
                    .code(ShaderFormat.SPIRV, new byte[] {1})
                    .build();
            assertEquals(1, graphics.storageBuffers());
            assertEquals(0, graphics.storageTextures());
        }
    }

    @Nested
    @DisplayName("the smaller specs")
    class Small {

        @Test
        @DisplayName("a depth test is of a depth format, and the usual one is LESS with writes")
        void depthTests() {
            var test = DepthTest.less(TextureFormat.D16_UNORM);
            assertEquals(CompareOp.LESS, test.compare());
            assertEquals(true, test.write());
            assertThrows(IllegalArgumentException.class, () -> DepthTest.less(TextureFormat.R8_UNORM));
        }

        @Test
        @DisplayName("a sampler spec is nearest or linear, clamped unless told otherwise")
        void samplers() {
            assertEquals(new SamplerSpec(Filter.NEAREST, AddressMode.CLAMP_TO_EDGE), SamplerSpec.nearest());
            assertEquals(
                    new SamplerSpec(Filter.LINEAR, AddressMode.REPEAT),
                    SamplerSpec.linear().withAddressMode(AddressMode.REPEAT));
        }

        @Test
        @DisplayName("a sampler's anisotropy is 1 unless asked, kept by every with, and clamped to 16")
        void anisotropy() {
            assertEquals(1f, SamplerSpec.trilinear().maxAnisotropy());
            assertFalse(SamplerSpec.trilinear().isAnisotropic());
            var ground = SamplerSpec.trilinear().withAnisotropy(8);
            assertEquals(8f, ground.maxAnisotropy());
            assertTrue(ground.isAnisotropic());
            assertEquals(8f, ground.withAddressMode(AddressMode.REPEAT).maxAnisotropy());
            assertEquals(8f, ground.withMipFilter(Filter.NEAREST).maxAnisotropy());
            assertEquals(8f, ground.withCompare(CompareOp.LESS).maxAnisotropy());
            assertEquals(16f, SamplerSpec.trilinear().withAnisotropy(64).maxAnisotropy());
            assertEquals(
                    SamplerSpec.MAX_ANISOTROPY,
                    SamplerSpec.linear().withAnisotropy(Float.POSITIVE_INFINITY).maxAnisotropy());
            assertEquals(SamplerSpec.trilinear(), ground.withAnisotropy(1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SamplerSpec.trilinear().withAnisotropy(Float.NaN));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SamplerSpec.trilinear().withAnisotropy(0.5f));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SamplerSpec.trilinear().withAnisotropy(-4));
        }

        @Test
        @DisplayName("a load keeps, clears to a colour, or does not care")
        void loads() {
            assertInstanceOf(Load.Keep.class, Load.keep());
            assertInstanceOf(Load.DontCare.class, Load.dontCare());
            assertEquals(new Load.Clear(0, 0, 0, 0), Load.clearTransparent());
            assertEquals(new Load.Clear(1, 0.5f, 0, 1), Load.clear(1, 0.5f, 0, 1));
        }
    }
}
