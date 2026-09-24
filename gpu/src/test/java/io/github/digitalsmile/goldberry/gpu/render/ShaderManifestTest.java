package io.github.digitalsmile.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuShaderFormat;

/// The committed shader bytecode is the bytecode of the committed sources, and
/// every shader the code names is there in the three formats (`docs/gpu-plan.md`,
/// D7). Needs no device and no DXC: it reads files.
@DisplayName("the shipped shaders")
class ShaderManifestTest {

    /// Gradle runs tests in the project directory.
    private static final Path SOURCES = Path.of("src/main/shaders");

    private static Properties manifest() throws IOException {
        var properties = new Properties();
        try (var in = ShaderLibrary.class.getResourceAsStream(ShaderLibrary.DIRECTORY + "shaders.properties")) {
            assertTrue(in != null, "shaders.properties is missing: run ./gradlew :gpu:compileShaders");
            properties.load(in);
        }
        return properties;
    }

    @Test
    @DisplayName("were compiled from the sources as they are now")
    void compiledFromTheseSources() throws IOException, NoSuchAlgorithmException {
        var manifest = manifest();
        var sources = new TreeSet<String>();
        try (var files = Files.list(SOURCES)) {
            for (var source : files.filter(f -> f.toString().endsWith(".hlsl")).toList()) {
                var name = source.getFileName().toString().replace(".hlsl", "");
                sources.add(name);
                var digest = HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
                assertEquals(
                        manifest.getProperty(name + ".sha256"),
                        digest,
                        name + ".hlsl changed since it was compiled: run ./gradlew :gpu:compileShaders");
            }
        }
        var recorded = manifest.stringPropertyNames().stream()
                .filter(key -> key.endsWith(".sha256"))
                .map(key -> key.replace(".sha256", ""))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(sources, recorded, "a shader was compiled with no source, or a source never compiled");
        assertTrue(manifest.getProperty("tools", "").contains("spirv-cross"), "the tools are recorded");
    }

    @Test
    @DisplayName("are named in BuiltInShader, each of them, and nothing else is")
    void everySourceHasAnEntry() throws IOException {
        var names = Arrays.stream(BuiltInShader.values())
                .map(BuiltInShader::fileName)
                .collect(Collectors.toCollection(TreeSet::new));
        try (var files = Files.list(SOURCES)) {
            var sources = files.map(f -> f.getFileName().toString())
                    .filter(name -> name.endsWith(".hlsl"))
                    .map(name -> name.replace(".hlsl", ""))
                    .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(sources, names);
        }
    }

    @ParameterizedTest
    @EnumSource(BuiltInShader.class)
    @DisplayName("load in each format the toolkit ships, with its declared counts and entry point")
    void loadsInEveryFormat(BuiltInShader shader) {
        for (var format : ShaderLibrary.PREFERENCE) {
            var code = ShaderLibrary.code(shader, format);
            assertTrue(code.size() > 0);
            assertEquals(shader.stage(), code.stage());
            assertEquals(shader.samplers(), code.samplers());
            assertEquals(shader.uniformBuffers(), code.uniformBuffers());
            assertEquals(format == SdlGpuShaderFormat.MSL ? "main0" : "main", code.entryPoint());
        }
        var msl = new String(
                ShaderLibrary.code(shader, SdlGpuShaderFormat.MSL).code(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(msl.contains(" main0("), "SPIRV-Cross names the entry point main0");
    }

    @Test
    @DisplayName("choose the format a device takes, and none it does not")
    void choosesAFormat() {
        assertEquals(Optional.of(SdlGpuShaderFormat.MSL), ShaderLibrary.formatFor(EnumSet.of(SdlGpuShaderFormat.MSL)));
        assertEquals(
                Optional.of(SdlGpuShaderFormat.SPIRV), ShaderLibrary.formatFor(EnumSet.of(SdlGpuShaderFormat.SPIRV)));
        assertEquals(
                Optional.of(SdlGpuShaderFormat.DXIL),
                ShaderLibrary.formatFor(EnumSet.of(SdlGpuShaderFormat.DXBC, SdlGpuShaderFormat.DXIL)));
        assertEquals(Optional.empty(), ShaderLibrary.formatFor(EnumSet.of(SdlGpuShaderFormat.METALLIB)));
        assertThrows(IllegalArgumentException.class, () -> ShaderLibrary.extension(SdlGpuShaderFormat.DXBC));
    }
}
