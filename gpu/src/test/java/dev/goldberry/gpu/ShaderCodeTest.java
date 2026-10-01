package dev.goldberry.gpu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [ShaderCode], without a device: how it is built, loaded, copied and how it
/// picks the format a device takes.
@DisplayName("shader code")
class ShaderCodeTest {

    private static final byte[] CODE = {1, 2, 3};

    @Test
    @DisplayName("is built format by format, with each format's default entry point unless one is named")
    void builds() {
        var code = ShaderCode.builder(ShaderStage.FRAGMENT)
                .samplers(2)
                .uniformBuffers(1)
                .code(ShaderFormat.SPIRV, CODE)
                .code(ShaderFormat.MSL, CODE)
                .code(ShaderFormat.DXIL, CODE, "PSMain")
                .build();
        assertEquals(ShaderStage.FRAGMENT, code.stage());
        assertEquals(2, code.samplers());
        assertEquals(1, code.uniformBuffers());
        assertEquals(EnumSet.of(ShaderFormat.SPIRV, ShaderFormat.MSL, ShaderFormat.DXIL), code.formats());
        assertEquals("main", code.bytecode().get(ShaderFormat.SPIRV).entryPoint());
        assertEquals("main0", code.bytecode().get(ShaderFormat.MSL).entryPoint());
        assertEquals("PSMain", code.bytecode().get(ShaderFormat.DXIL).entryPoint());
    }

    @Test
    @DisplayName("picks the device's format, a precompiled metallib before MSL, and none it does not have")
    void picksAFormat() {
        var code = ShaderCode.builder(ShaderStage.VERTEX)
                .code(ShaderFormat.MSL, CODE)
                .code(ShaderFormat.METALLIB, CODE)
                .code(ShaderFormat.SPIRV, CODE)
                .build();
        assertEquals(
                Optional.of(ShaderFormat.METALLIB),
                code.formatFor(EnumSet.of(ShaderFormat.MSL, ShaderFormat.METALLIB)));
        assertEquals(Optional.of(ShaderFormat.MSL), code.formatFor(EnumSet.of(ShaderFormat.MSL)));
        assertEquals(Optional.of(ShaderFormat.SPIRV), code.formatFor(EnumSet.of(ShaderFormat.SPIRV)));
        assertEquals(Optional.empty(), code.formatFor(EnumSet.of(ShaderFormat.DXIL, ShaderFormat.DXBC)));
    }

    @Test
    @DisplayName("copies its bytes in and out, and compares them by content")
    void copies() {
        var bytes = CODE.clone();
        var bytecode = new ShaderCode.Bytecode(bytes, "main");
        bytes[0] = 99;
        assertArrayEquals(CODE, bytecode.code());
        bytecode.code()[0] = 99;
        assertArrayEquals(CODE, bytecode.code());
        assertEquals(new ShaderCode.Bytecode(CODE, "main"), bytecode);
        assertEquals(new ShaderCode.Bytecode(CODE, "main").hashCode(), bytecode.hashCode());
        assertNotEquals(new ShaderCode.Bytecode(CODE, "main0"), bytecode);
        assertEquals(3, bytecode.size());
    }

    @Test
    @DisplayName("refuses no code, no format, an empty entry point and negative counts")
    void refusals() {
        assertThrows(IllegalArgumentException.class, () -> new ShaderCode.Bytecode(new byte[0], "main"));
        assertThrows(IllegalArgumentException.class, () -> new ShaderCode.Bytecode(CODE, " "));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShaderCode.builder(ShaderStage.VERTEX).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> ShaderCode.builder(ShaderStage.VERTEX)
                        .samplers(-1)
                        .code(ShaderFormat.SPIRV, CODE)
                        .build());
        var map = new java.util.EnumMap<ShaderFormat, ShaderCode.Bytecode>(ShaderFormat.class);
        map.put(ShaderFormat.SPIRV, new ShaderCode.Bytecode(CODE, "main"));
        var code = new ShaderCode(ShaderStage.VERTEX, map, 0, 0);
        map.clear();
        assertEquals(1, code.formats().size(), "the map was copied");
    }

    @Test
    @DisplayName("loads every format there is a file for, and says so when there is none")
    void loads() {
        var files = Map.of(
                "/shaders/cube.vert.spv",
                CODE,
                "/shaders/cube.vert.msl",
                "vertex main0".getBytes(StandardCharsets.UTF_8));
        var code = ShaderCode.load(ShaderStage.VERTEX, "/shaders/cube.vert", 0, 1, name -> {
            var bytes = files.get(name);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        });
        assertEquals(EnumSet.of(ShaderFormat.SPIRV, ShaderFormat.MSL), code.formats());
        assertEquals(1, code.uniformBuffers());
        assertEquals("main0", code.bytecode().get(ShaderFormat.MSL).entryPoint());

        var missing = assertThrows(
                IllegalArgumentException.class,
                () -> ShaderCode.load(ShaderStage.VERTEX, "/nothing", 0, 0, name -> null));
        assertTrue(missing.getMessage().contains("/nothing"), missing.getMessage());
        assertThrows(
                UncheckedIOException.class,
                () -> ShaderCode.load(ShaderStage.VERTEX, "/broken", 0, 0, name -> {
                    throw new IOException("unreadable");
                }));
    }

    @Test
    @DisplayName("loads the tests' own compiled shaders from resources, in all three formats")
    void loadsCompiledResources() {
        var code = TestShaders.meshVertex();
        assertEquals(EnumSet.of(ShaderFormat.SPIRV, ShaderFormat.DXIL, ShaderFormat.MSL), code.formats());
        assertEquals(1, code.uniformBuffers());
    }
}
