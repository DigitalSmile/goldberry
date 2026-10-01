package dev.goldberry.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuAddressMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBlend;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBufferUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuCompareOp;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuCullMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFrontFace;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuIndexSize;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuPrimitiveType;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderStage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuVertexFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuVertexInputRate;

/// The public enums against the SDL ones they map onto: every constant maps,
/// no two map to one, and each SDL constant the bindings model has its public
/// face. A constant added on one side and not the other fails here, before
/// the exhaustive switch that maps it is reached with it.
@DisplayName("the GPU API's enums")
class GpuEnumsTest {

    /// Checks `mapping` is a bijection from `publicType`'s constants onto
    /// `sdlType`'s.
    private static <P extends Enum<P>, S extends Enum<S>> void bijection(
            Class<P> publicType, Class<S> sdlType, Function<P, S> mapping) {
        var images = Arrays.stream(publicType.getEnumConstants()).map(mapping).collect(Collectors.toSet());
        assertEquals(
                publicType.getEnumConstants().length,
                images.size(),
                publicType.getSimpleName() + ": two constants map to one");
        assertEquals(
                Set.copyOf(EnumSet.allOf(sdlType)),
                images,
                publicType.getSimpleName() + " and " + sdlType.getSimpleName() + " differ");
    }

    @Test
    @DisplayName("map one to one onto SDL's, every constant of each")
    void mapOneToOne() {
        bijection(TextureFormat.class, SdlGpuTextureFormat.class, TextureFormat::sdl);
        bijection(TextureUsage.class, SdlGpuTextureUsage.class, TextureUsage::sdl);
        bijection(BufferUsage.class, SdlGpuBufferUsage.class, BufferUsage::sdl);
        bijection(ShaderStage.class, SdlGpuShaderStage.class, ShaderStage::sdl);
        bijection(ShaderFormat.class, SdlGpuShaderFormat.class, ShaderFormat::sdl);
        bijection(Filter.class, SdlGpuFilter.class, Filter::sdl);
        bijection(AddressMode.class, SdlGpuAddressMode.class, AddressMode::sdl);
        bijection(BlendMode.class, SdlGpuBlend.class, BlendMode::sdl);
        bijection(PrimitiveType.class, SdlGpuPrimitiveType.class, PrimitiveType::sdl);
        bijection(CullMode.class, SdlGpuCullMode.class, CullMode::sdl);
        bijection(FrontFace.class, SdlGpuFrontFace.class, FrontFace::sdl);
        bijection(CompareOp.class, SdlGpuCompareOp.class, CompareOp::sdl);
        bijection(VertexFormat.class, SdlGpuVertexFormat.class, VertexFormat::sdl);
        bijection(VertexInputRate.class, SdlGpuVertexInputRate.class, VertexInputRate::sdl);
        bijection(IndexFormat.class, SdlGpuIndexSize.class, IndexFormat::sdl);
    }

    @Test
    @DisplayName("map by name where the names agree, so no constant is crossed with its neighbour")
    void mapByName() {
        for (var format : TextureFormat.values()) {
            assertEquals(format.name(), format.sdl().name());
        }
        for (var op : CompareOp.values()) {
            assertEquals(op.name(), op.sdl().name());
        }
        for (var format : VertexFormat.values()) {
            assertEquals(format.name(), format.sdl().name());
        }
        for (var type : PrimitiveType.values()) {
            assertEquals(type.name(), type.sdl().name());
        }
        assertEquals(SdlGpuTextureUsage.DEPTH_STENCIL_TARGET, TextureUsage.DEPTH_TARGET.sdl());
    }

    @Test
    @DisplayName("shader formats come back from SDL's as themselves")
    void shaderFormatsRoundTrip() {
        for (var format : ShaderFormat.values()) {
            assertEquals(format, ShaderFormat.of(format.sdl()));
        }
        assertEquals(
                EnumSet.of(ShaderFormat.MSL, ShaderFormat.METALLIB),
                ShaderFormat.of(EnumSet.of(SdlGpuShaderFormat.MSL, SdlGpuShaderFormat.METALLIB)));
    }

    @Test
    @DisplayName("sizes are SDL's: bytes per pixel, per attribute, per index")
    void sizes() {
        assertEquals(4, TextureFormat.B8G8R8A8_UNORM.bytesPerPixel());
        assertEquals(2, TextureFormat.R16_UNORM.bytesPerPixel());
        assertEquals(2, TextureFormat.D16_UNORM.bytesPerPixel());
        assertEquals(16, VertexFormat.FLOAT4.bytes());
        assertEquals(2, IndexFormat.UINT16.bytes());
        assertEquals(4, IndexFormat.UINT32.bytes());
        assertEquals(
                EnumSet.of(TextureFormat.D16_UNORM, TextureFormat.D32_FLOAT),
                Arrays.stream(TextureFormat.values())
                        .filter(TextureFormat::isDepth)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(TextureFormat.class))));
    }

    @Test
    @DisplayName("shader formats carry the extension the build writes and the entry point it produces")
    void shaderFormatFiles() {
        assertEquals(".spv", ShaderFormat.SPIRV.extension());
        assertEquals(".dxil", ShaderFormat.DXIL.extension());
        assertEquals(".msl", ShaderFormat.MSL.extension());
        assertEquals("main", ShaderFormat.SPIRV.defaultEntryPoint());
        assertEquals("main0", ShaderFormat.MSL.defaultEntryPoint());
    }
}
