package io.github.digitalsmile.goldberry.natives.sdl.gpu;

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
        assertTrue(format.bytesPerPixel() > 0);
        assertTrue(format.nativeName().startsWith("SDL_GPU_TEXTUREFORMAT_"));
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
        assertEquals(3, SdlGpuBufferUsage.mask(EnumSet.allOf(SdlGpuBufferUsage.class)));
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
