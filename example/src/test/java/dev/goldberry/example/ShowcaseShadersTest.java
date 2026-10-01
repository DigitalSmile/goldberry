package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.gpu.Cube;

/// The GPU screen's committed shader bytecode is the bytecode of its committed
/// HLSL, in all three formats (`docs/gpu-plan.md`, D7): the check `:gpu` makes of
/// its own, made of an application's. Needs no device and no DXC.
@DisplayName("the showcase's shaders")
class ShowcaseShadersTest {

    /// Gradle runs tests in the project directory.
    private static final Path SOURCES = Path.of("src/main/shaders");

    @Test
    @DisplayName("were compiled from the sources as they are now, into SPIR-V, DXIL and MSL")
    void compiledFromTheseSources() throws IOException, NoSuchAlgorithmException {
        var manifest = new Properties();
        try (var in = Cube.class.getResourceAsStream("shaders.properties")) {
            assertNotNull(in, "shaders.properties is missing: run ./gradlew :gpu:compileShaders");
            manifest.load(in);
        }
        var sources = new TreeSet<String>();
        try (var files = Files.list(SOURCES)) {
            for (var source : files.filter(f -> f.toString().endsWith(".hlsl")).toList()) {
                var file = source.getFileName().toString();
                var name = file.substring(0, file.length() - ".hlsl".length());
                sources.add(name);
                var digest = HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
                assertEquals(
                        manifest.getProperty(name + ".sha256"),
                        digest,
                        file + " changed since it was compiled: run ./gradlew :gpu:compileShaders");
                for (var format : new String[] {".spv", ".dxil", ".msl"}) {
                    assertNotNull(Cube.class.getResource(name + format), name + format + " is missing");
                }
            }
        }
        var recorded = manifest.stringPropertyNames().stream()
                .filter(key -> key.endsWith(".sha256"))
                .map(key -> key.replace(".sha256", ""))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(sources, recorded, "a shader was compiled with no source, or a source never compiled");
        assertTrue(manifest.getProperty("tools", "").contains("spirv-cross"), "the tools are recorded");
    }
}
