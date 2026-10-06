package dev.goldberry.build.shaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("MetalBindings")
class MetalBindingsTest {

    private static final Function<String, String> NO_INCLUDES = name -> {
        throw new IllegalStateException("unexpected include " + name);
    };

    private static List<String> arguments(String hlsl) {
        return MetalBindings.dxcArguments(hlsl, NO_INCLUDES);
    }

    @Test
    @DisplayName("a compute shader's uniform comes first, then its read-only buffer, then its read-write one")
    void computeOrder() {
        var hlsl = """
                StructuredBuffer<float> input : register(t0, space0);
                RWStructuredBuffer<float> output : register(u0, space1);
                cbuffer Scale : register(b0, space2) { float scale; };
                """;
        assertEquals(
                List.of(
                        "-fvk-bind-register", "t0", "0", "1", "1",
                        "-fvk-bind-register", "u0", "1", "2", "1",
                        "-fvk-bind-register", "b0", "2", "0", "1"),
                arguments(hlsl));
    }

    @Test
    @DisplayName("a texture and its sampler share an index and a set, so a combined pair stays a pair")
    void combinedPair() {
        var hlsl = """
                #define COMBINED [[vk::combinedImageSampler]]
                COMBINED Texture2D<float> luma : register(t0, space2);
                COMBINED SamplerState lumaSampler : register(s0, space2);
                COMBINED Texture2D<float2> chroma : register(t1, space2);
                COMBINED SamplerState chromaSampler : register(s1, space2);
                cbuffer Yuv : register(b0, space3) { float4x4 matrix; };
                """;
        assertEquals(
                List.of(
                        "-fvk-bind-register", "t0", "2", "0", "0",
                        "-fvk-bind-register", "s0", "2", "0", "0",
                        "-fvk-bind-register", "t1", "2", "1", "0",
                        "-fvk-bind-register", "s1", "2", "1", "0",
                        "-fvk-bind-register", "b0", "3", "0", "1"),
                arguments(hlsl));
    }

    @Test
    @DisplayName("a storage buffer in the t registers is numbered past the textures, and bound past the uniforms")
    void storageBufferAfterTextures() {
        var hlsl = """
                Texture2D<float4> image : register(t0, space0);
                StructuredBuffer<float4> vertices : register(t1, space0);
                cbuffer A : register(b0, space1) { float a; };
                cbuffer B : register(b1, space1) { float b; };
                """;
        var bindings = MetalBindings.bindings(MetalBindings.resources(hlsl, NO_INCLUDES));
        assertEquals(0, bindings.get(0).index(), "the texture");
        assertEquals(2, bindings.get(1).index(), "the buffer, after two uniforms");
        assertEquals(List.of(0, 1), List.of(bindings.get(2).index(), bindings.get(3).index()));
    }

    @Test
    @DisplayName("a compute shader's read-write texture follows its read-only ones, and its read-write buffer every buffer")
    void computeReadWrite() {
        var hlsl = """
                Texture2D<float4> source : register(t0, space0);
                StructuredBuffer<float> weights : register(t1, space0);
                RWTexture2D<float4> target : register(u0, space1);
                RWStructuredBuffer<float> sums : register(u1, space1);
                cbuffer Pass : register(b0, space2) { float strength; };
                """;
        var bindings = MetalBindings.bindings(MetalBindings.resources(hlsl, NO_INCLUDES));
        assertEquals(List.of(0, 1, 1, 2, 0), bindings.stream().map(MetalBindings.Binding::index).toList());
    }

    @Test
    @DisplayName("an included file's declarations count, in the place of the include")
    void includes() {
        var hlsl = """
                #include "yuv.hlsli"
                Texture2D<float> luma : register(t0, space2);
                """;
        var included = Map.of("yuv.hlsli", "cbuffer Yuv : register(b0, space3) { float4x4 m; };");
        assertEquals(
                List.of("-fvk-bind-register", "b0", "3", "0", "1", "-fvk-bind-register", "t0", "2", "0", "0"),
                MetalBindings.dxcArguments(hlsl, included::get));
    }

    @Test
    @DisplayName("a commented-out declaration is not one")
    void comments() {
        var hlsl = """
                // Texture2D<float> gone : register(t5, space2);
                /* cbuffer Old : register(b3, space3) { float x; }; */
                cbuffer Colour : register(b0, space3) { float4 colour; };
                """;
        assertEquals(List.of("-fvk-bind-register", "b0", "3", "0", "1"), arguments(hlsl));
    }

    @Test
    @DisplayName("a shader with no registers needs no arguments")
    void none() {
        assertTrue(arguments("float4 main() : SV_Target { return 1; }").isEmpty());
    }

    @Test
    @DisplayName("refuses a storage buffer numbered before a texture, which SDL_GPU does not bind")
    void refusesBufferBeforeTexture() {
        var hlsl = """
                StructuredBuffer<float4> vertices : register(t0, space0);
                Texture2D<float4> image : register(t1, space0);
                """;
        var refused = assertThrows(IllegalArgumentException.class, () -> arguments(hlsl));
        assertTrue(refused.getMessage().contains("t0"), refused.getMessage());
    }

    @Test
    @DisplayName("refuses a declaration whose type it does not know, rather than guessing a slot")
    void refusesUnknownType() {
        var refused = assertThrows(IllegalArgumentException.class, () -> arguments("RaytracingAccelerationStructure s : register(t0);"));
        assertTrue(refused.getMessage().contains("t0"), refused.getMessage());
    }

    @Test
    @DisplayName("refuses a uniform in a texture register")
    void refusesWrongRegister() {
        assertThrows(IllegalArgumentException.class, () -> arguments("cbuffer C : register(t0, space1) { float a; };"));
    }

    @Test
    @DisplayName("reads a file and the includes beside it")
    void fromAFile(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("yuv.hlsli"), "cbuffer Yuv : register(b0, space3) { float4x4 m; };");
        var source = directory.resolve("yuv2.frag.hlsl");
        Files.writeString(source, "#include \"yuv.hlsli\"\nTexture2D<float> luma : register(t0, space2);\n");
        assertEquals(
                List.of("-fvk-bind-register", "b0", "3", "0", "1", "-fvk-bind-register", "t0", "2", "0", "0"),
                MetalBindings.dxcArguments(source));
    }
}
