package dev.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// Where a classifier jar's library is read from when the jar is a module.
///
/// The jar names itself, so a modular build keeps it on the module path, and
/// nothing `requires` a platform's module, so the module system does not
/// resolve it. The library is still on the path, and these hold that it is
/// read from there.
@DisplayName("reading a classifier jar")
class ClassifierJarTest {

    private static final String LIBRARY = "dev/goldberry/natives/linux-x64/libgoldberry.so";

    private static final byte[] CONTENT = "not really a library".getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest
    @CsvSource({
        "Linux,    amd64,   dev.goldberry.natives.linux_x64",
        "Linux,    aarch64, dev.goldberry.natives.linux_aarch64",
        "Mac OS X, aarch64, dev.goldberry.natives.macos_aarch64",
        "Windows,  amd64,   dev.goldberry.natives.windows_x64",
    })
    @DisplayName("each platform's jar has a module name of its own")
    void moduleNames(String osName, String osArch, String expected) {
        assertEquals(expected, NativePlatform.of(osName, osArch).moduleName());
    }

    @Test
    @DisplayName("the build writes the same module name into the jar's manifest")
    void buildAgrees() throws IOException {
        // The other side of the contract, read from the build script so that a
        // rename on either side fails here rather than as a missing library.
        var script = Files.readString(Path.of("build.gradle"));
        assertTrue(
                script.contains("'Automatic-Module-Name': \"dev.goldberry.natives.${target.id().replace('-', '_')}\""),
                "natives/build.gradle names each classifier jar the way NativePlatform.moduleName() does");
    }

    @Test
    @DisplayName("a jar with the manifest is an automatic module by that name, with no package to open")
    void isAModule(@TempDir Path dir) throws IOException {
        var jar = classifierJar(dir, "dev.goldberry.natives.linux_x64");

        var module =
                ModuleFinder.of(jar).find("dev.goldberry.natives.linux_x64").orElseThrow();

        assertTrue(module.descriptor().isAutomatic());
        assertTrue(
                module.descriptor().packages().isEmpty(),
                "the library's directory is not a package name, so no module encapsulates it");
    }

    @Test
    @DisplayName("the library is read from the module path when its module was not resolved")
    void readsFromTheModulePath(@TempDir Path dir) throws IOException {
        var jar = classifierJar(dir, "dev.goldberry.natives.linux_x64");
        var elsewhere = Files.createDirectories(dir.resolve("elsewhere"));
        var path = elsewhere + File.pathSeparator + jar;

        try (var in = ClassifierJar.fromModulePath(path, "dev.goldberry.natives.linux_x64", LIBRARY)) {
            assertNotNull(in);
            assertArrayEquals(CONTENT, in.readAllBytes());
        }
    }

    @Test
    @DisplayName("a module path without the platform's module, or without the file in it, has nothing")
    void absent(@TempDir Path dir) throws IOException {
        var jar = classifierJar(dir, "dev.goldberry.natives.linux_x64").toString();

        assertNull(ClassifierJar.fromModulePath(null, "dev.goldberry.natives.linux_x64", LIBRARY));
        assertNull(ClassifierJar.fromModulePath("", "dev.goldberry.natives.linux_x64", LIBRARY));
        assertNull(ClassifierJar.fromModulePath(jar, "dev.goldberry.natives.windows_x64", LIBRARY));
        assertNull(ClassifierJar.fromModulePath(
                jar, "dev.goldberry.natives.linux_x64", "dev/goldberry/natives/linux-x64/missing.so"));
    }

    @Test
    @DisplayName("a resource name without its leading slash is refused rather than silently not found")
    void absoluteOnly() {
        assertThrows(
                IllegalArgumentException.class, () -> ClassifierJar.open(NativePlatform.of("Linux", "amd64"), LIBRARY));
    }

    /// A jar laid out the way `nativeJar*` lays one out, with the manifest it writes.
    private static Path classifierJar(Path dir, String moduleName) throws IOException {
        var jar = dir.resolve("goldberry-natives-2026.3-linux-x64.jar");
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("Automatic-Module-Name"), moduleName);
        try (var out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry(LIBRARY));
            out.write(CONTENT);
            out.closeEntry();
        }
        return jar;
    }
}
