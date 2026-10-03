package dev.goldberry.build.toolchain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ToolLookup")
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "the fixtures are POSIX executables")
class ToolLookupTest {

    @TempDir
    Path bin;

    private Path cmake;

    @BeforeEach
    void installATool() throws IOException {
        cmake = Files.createFile(bin.resolve("cmake"), PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwxr-xr-x")));
    }

    private ToolLookup lookup(Map<String, String> overrides) {
        return new ToolLookup(bin.toString(), "Linux", "/nonexistent", name -> Optional.ofNullable(overrides.get(name)));
    }

    @Test
    @DisplayName("finds a tool on the PATH it was given, by absolute path")
    void findsOnThePath() {
        var found = lookup(Map.of()).find("cmake");
        assertAll(
                () -> assertEquals(Optional.of(cmake), found),
                () -> assertTrue(found.orElseThrow().isAbsolute()));
    }

    @Test
    @DisplayName("takes an override at its word, and does not fall back when it names nothing")
    void overridesAreTakenAtTheirWord() {
        var elsewhere = bin.resolve("no-such-cmake").toString();
        var lookup = lookup(Map.of("cmake", elsewhere));
        assertAll(
                () -> assertEquals(Optional.of(elsewhere), lookup.override("cmake")),
                () -> assertEquals(Optional.empty(), lookup.find("cmake")),
                () -> assertNull(lookup.fileOrNull("cmake")));
    }

    @Test
    @DisplayName("reads a blank override as no override")
    void blankIsUnset() {
        var lookup = lookup(Map.of("cmake", " "));
        assertAll(
                () -> assertEquals(Optional.empty(), lookup.override("cmake")),
                () -> assertEquals(cmake.toFile(), lookup.fileOrNull("cmake")));
    }

    @Test
    @DisplayName("hands a Groovy script null for a tool that is not installed")
    void absentIsNull() {
        assertNull(lookup(Map.of()).fileOrNull("meson-" + File.separatorChar + "nothing"));
    }
}
