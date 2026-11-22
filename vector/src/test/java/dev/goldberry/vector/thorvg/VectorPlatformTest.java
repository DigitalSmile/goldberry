package dev.goldberry.vector.thorvg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.natives.NativePlatform;

@DisplayName("VectorPlatform")
class VectorPlatformTest {

    @ParameterizedTest(name = "{0}/{1} is {2}")
    @CsvSource({
        "Linux, amd64, linux-x64, libgoldberry-vector.so",
        "Linux, aarch64, linux-aarch64, libgoldberry-vector.so",
        "Mac OS X, aarch64, macos-aarch64, libgoldberry-vector.dylib",
        "Windows 11, amd64, windows-x64, goldberry-vector.dll",
    })
    @DisplayName("names the four targets, and the library on each")
    void targets(String os, String arch, String target, String file) {
        var platform = VectorPlatform.of(os, arch);
        assertEquals(target, platform.target());
        assertEquals("natives-" + target, platform.classifier());
        assertEquals(file, platform.fileName());
        assertEquals("/dev/goldberry/vector/natives/" + target + "/", platform.resourceDirectory());
    }

    @ParameterizedTest(name = "{0}/{1}")
    @CsvSource({"Mac OS X, x86_64", "Windows 11, aarch64", "FreeBSD, amd64", "Linux, riscv64"})
    @DisplayName("refuses a platform with no natives, naming it")
    void unsupported(String os, String arch) {
        var e = assertThrows(UnsupportedOperationException.class, () -> VectorPlatform.of(os, arch));
        assertTrue(e.getMessage().contains("no vector natives"), e.getMessage());
    }

    @Test
    @DisplayName("agrees with :natives' NativePlatform on this machine's target")
    void agreesWithNatives() {
        assertEquals(
                NativePlatform.current().classifier(), VectorPlatform.current().target());
    }
}
