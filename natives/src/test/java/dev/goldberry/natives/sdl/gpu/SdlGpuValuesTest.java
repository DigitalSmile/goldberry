package dev.goldberry.natives.sdl.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuAddressMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBufferUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuCompareOp;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuCullMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFrontFace;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuIndexSize;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuPrimitiveType;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuSampleCount;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureType;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuVertexFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuVertexInputRate;

/// The parts of the GPU wrappers that are values: no library, no device.
@DisplayName("SDL_GPU values")
class SdlGpuValuesTest {

    @Test
    @DisplayName("shader formats round-trip through SDL's bits, and unknown bits are ignored")
    void shaderFormatBits() {
        var all = EnumSet.allOf(SdlGpuShaderFormat.class);
        assertEquals(all, SdlGpuShaderFormat.decode(SdlGpuShaderFormat.mask(all)));
        // SDL_GPU_SHADERFORMAT_PRIVATE, bit 0, is the consoles' and not modelled.
        assertEquals(Set.of(), SdlGpuShaderFormat.decode(1));
        assertEquals(EnumSet.of(SdlGpuShaderFormat.MSL), SdlGpuShaderFormat.decode(SdlGpuShaderFormat.MSL.bit() | 1));
    }

    @ParameterizedTest
    @EnumSource(SdlGpuShaderFormat.class)
    @DisplayName("each shader format asks for itself by SDL's property name")
    void shaderFormatProperty(SdlGpuShaderFormat format) {
        assertEquals(
                "SDL.gpu.device.create.shaders." + format.name().toLowerCase(java.util.Locale.ROOT),
                format.createProperty());
    }

    @Test
    @DisplayName("texture usages or together")
    void usageMask() {
        assertEquals(
                0b11, SdlGpuTextureUsage.mask(EnumSet.of(SdlGpuTextureUsage.SAMPLER, SdlGpuTextureUsage.COLOR_TARGET)));
        assertEquals(0, SdlGpuTextureUsage.mask(Set.of()));
    }

    @ParameterizedTest
    @EnumSource(SdlGpuTextureFormat.class)
    @DisplayName("every texture format has a size and SDL's name")
    void textureFormats(SdlGpuTextureFormat format) {
        assertTrue(format.bytesPerBlock() > 0);
        if (format.isCompressed()) {
            assertThrows(UnsupportedOperationException.class, format::bytesPerPixel);
            assertEquals(4, format.blockWidth());
            assertEquals(4, format.blockHeight());
        } else {
            assertEquals(format.bytesPerBlock(), format.bytesPerPixel());
            assertEquals(1, format.blockWidth());
        }
        assertTrue(format.nativeName().startsWith("SDL_GPU_TEXTUREFORMAT_"));
    }

    @Test
    @DisplayName("compressed formats size regions in whole blocks, and sRGB formats say so")
    void blocksAndSrgb() {
        assertEquals(8, SdlGpuTextureFormat.BC1_RGBA_UNORM.bytesPerBlock());
        assertEquals(16, SdlGpuTextureFormat.BC7_RGBA_UNORM.bytesPerBlock());
        assertEquals(16, SdlGpuTextureFormat.BC7_RGBA_UNORM.byteSize(4, 4));
        assertEquals(16, SdlGpuTextureFormat.BC7_RGBA_UNORM.byteSize(1, 2), "a partial block is a block");
        assertEquals(8 * 3 * 2, SdlGpuTextureFormat.BC1_RGBA_UNORM.byteSize(12, 5));
        assertEquals(4 * 3 * 2, SdlGpuTextureFormat.R8G8B8A8_UNORM.byteSize(3, 2));
        assertTrue(SdlGpuTextureFormat.B8G8R8A8_UNORM_SRGB.isSrgb());
        assertTrue(SdlGpuTextureFormat.ASTC_4x4_UNORM_SRGB.isSrgb());
        assertFalse(SdlGpuTextureFormat.B8G8R8A8_UNORM.isSrgb());
        assertFalse(SdlGpuTextureFormat.R8G8B8A8_UNORM_SRGB.isCompressed());
        assertEquals("SDL_GPU_TEXTURETYPE_2D_ARRAY", SdlGpuTextureType.TWO_D_ARRAY.nativeName());
        assertEquals("SDL_GPU_TEXTURETYPE_3D", SdlGpuTextureType.THREE_D.nativeName());
        assertEquals("SDL_GPU_TEXTURETYPE_CUBE", SdlGpuTextureType.CUBE.nativeName());
    }

    @Test
    @DisplayName("a sampler description has no anisotropy unless asked, and takes 1 to 16")
    void samplerAnisotropy() {
        var plain = SdlGpuSamplerDescription.of(SdlGpuFilter.LINEAR, SdlGpuAddressMode.REPEAT);
        assertEquals(1f, plain.maxAnisotropy());
        assertFalse(plain.isAnisotropic());
        var slanted = new SdlGpuSamplerDescription(
                SdlGpuFilter.LINEAR, SdlGpuAddressMode.REPEAT, Optional.of(SdlGpuFilter.LINEAR), Optional.empty(), 16f);
        assertTrue(slanted.isAnisotropic());
        for (var bad : new float[] {Float.NaN, 0.5f, 16.5f}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SdlGpuSamplerDescription(
                            SdlGpuFilter.LINEAR, SdlGpuAddressMode.REPEAT, Optional.empty(), Optional.empty(), bad),
                    () -> Float.toString(bad));
        }
    }

    @Test
    @DisplayName("only the depth formats are depth formats")
    void depthFormats() {
        for (var format : SdlGpuTextureFormat.values()) {
            assertEquals(format.name().startsWith("D"), format.isDepth(), format::toString);
        }
    }

    @Test
    @DisplayName("the pipeline enumerators name themselves as SDL's header does")
    void pipelineEnumeratorNames() {
        assertEquals("SDL_GPU_PRIMITIVETYPE_TRIANGLESTRIP", SdlGpuPrimitiveType.TRIANGLE_STRIP.nativeName());
        assertEquals("SDL_GPU_INDEXELEMENTSIZE_32BIT", SdlGpuIndexSize.UINT32.nativeName());
        assertEquals("SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM", SdlGpuVertexFormat.UBYTE4_NORM.nativeName());
        assertEquals("SDL_GPU_COMPAREOP_LESS_OR_EQUAL", SdlGpuCompareOp.LESS_OR_EQUAL.nativeName());
        assertEquals("SDL_GPU_SAMPLERADDRESSMODE_MIRRORED_REPEAT", SdlGpuAddressMode.MIRRORED_REPEAT.nativeName());
        assertEquals("SDL_GPU_CULLMODE_BACK", SdlGpuCullMode.BACK.nativeName());
        assertEquals("SDL_GPU_FRONTFACE_CLOCKWISE", SdlGpuFrontFace.CLOCKWISE.nativeName());
        assertEquals("SDL_GPU_VERTEXINPUTRATE_INSTANCE", SdlGpuVertexInputRate.INSTANCE.nativeName());
        assertEquals("SDL_GPU_BUFFERUSAGE_INDEX", SdlGpuBufferUsage.INDEX.nativeName());
    }

    @Test
    @DisplayName("vertex formats and index sizes know their width, and buffer usages or together")
    void sizes() {
        assertEquals(12, SdlGpuVertexFormat.FLOAT3.bytes());
        assertEquals(4, SdlGpuVertexFormat.UBYTE4_NORM.bytes());
        assertEquals(2, SdlGpuIndexSize.UINT16.bytes());
        assertEquals(4, SdlGpuIndexSize.UINT32.bytes());
        assertEquals(3, SdlGpuBufferUsage.mask(EnumSet.of(SdlGpuBufferUsage.VERTEX, SdlGpuBufferUsage.INDEX)));
        assertEquals(0b111011, SdlGpuBufferUsage.mask(EnumSet.allOf(SdlGpuBufferUsage.class)));
    }

    @Test
    @DisplayName("storage usages, sample counts and the float formats name SDL's constants")
    void textureModelNames() {
        assertEquals("SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_WRITE", SdlGpuBufferUsage.COMPUTE_STORAGE_WRITE.nativeName());
        assertEquals(
                "SDL_GPU_TEXTUREUSAGE_GRAPHICS_STORAGE_READ", SdlGpuTextureUsage.GRAPHICS_STORAGE_READ.nativeName());
        assertEquals("SDL_GPU_SAMPLECOUNT_4", SdlGpuSampleCount.FOUR.nativeName());
        assertEquals(SdlGpuSampleCount.EIGHT, SdlGpuSampleCount.of(8));
        assertEquals(1, SdlGpuSampleCount.ONE.samples());
        assertThrows(IllegalArgumentException.class, () -> SdlGpuSampleCount.of(3));
        assertEquals(8, SdlGpuTextureFormat.R16G16B16A16_FLOAT.bytesPerPixel());
        assertTrue(SdlGpuTextureFormat.R32_FLOAT.isFloat());
        assertTrue(SdlGpuTextureFormat.D32_FLOAT.isFloat());
        assertFalse(SdlGpuTextureFormat.R11G11B10_UFLOAT.isDepth());
        assertFalse(SdlGpuTextureFormat.B8G8R8A8_UNORM.isFloat());
    }

    @Test
    @DisplayName("a region is never empty or before the origin, and knows whether it fits")
    void regions() {
        var region = new SdlGpuRegion(2, 3, 4, 5);
        assertTrue(region.fitsIn(6, 8));
        assertFalse(region.fitsIn(5, 8));
        assertFalse(region.fitsIn(6, 7));
        assertFalse(new SdlGpuRegion(1, 1, Integer.MAX_VALUE, 1).fitsIn(Integer.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class, () -> new SdlGpuRegion(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SdlGpuRegion(-1, 0, 1, 1));
    }

    @Test
    @DisplayName("options need a shader format, and the defaults are what the toolkit ships")
    void options() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SdlGpuDevice.Options(Set.of(), false, false, Optional.empty()));
        var defaults = SdlGpuDevice.Options.defaults();
        assertEquals(
                EnumSet.of(SdlGpuShaderFormat.SPIRV, SdlGpuShaderFormat.DXIL, SdlGpuShaderFormat.MSL),
                defaults.shaderFormats());
        assertFalse(defaults.debugMode());
        assertTrue(defaults.preferLowPower());
        assertTrue(defaults.withDebugMode(true).debugMode());
        assertEquals(Optional.of("metal"), defaults.withDriver("metal").driver());
        assertThrows(
                UnsupportedOperationException.class,
                () -> defaults.shaderFormats().clear());
    }

    @Test
    @DisplayName("a missing device skips unless the run required one")
    void requirement() {
        assertInstanceOf(GpuDeviceRequirement.Skip.class, GpuDeviceRequirement.decide(null, "no driver", false));
        var fail =
                assertInstanceOf(GpuDeviceRequirement.Fail.class, GpuDeviceRequirement.decide(null, "no driver", true));
        assertTrue(fail.reason().contains("no driver"));
    }
}
