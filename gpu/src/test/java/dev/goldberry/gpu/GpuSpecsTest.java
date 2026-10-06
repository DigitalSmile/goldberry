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
        @DisplayName("a load keeps, clears to a colour, or does not care")
        void loads() {
            assertInstanceOf(Load.Keep.class, Load.keep());
            assertInstanceOf(Load.DontCare.class, Load.dontCare());
            assertEquals(new Load.Clear(0, 0, 0, 0), Load.clearTransparent());
            assertEquals(new Load.Clear(1, 0.5f, 0, 1), Load.clear(1, 0.5f, 0, 1));
        }
    }
}
