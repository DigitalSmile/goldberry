package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NativeLibraryTest {

    /// The path here must match the `into(...)` in the `nativeJar*` tasks in
    /// `natives/build.gradle.kts`. Asserting the literal means a rename on either
    /// side shows up as a failing test rather than as an UnsatisfiedLinkError on
    /// one platform in production.
    @ParameterizedTest
    @CsvSource({
        "Linux,    amd64,   /io/github/digitalsmile/goldberry/natives/linux-x64/libgoldberry.so",
        "Linux,    aarch64, /io/github/digitalsmile/goldberry/natives/linux-aarch64/libgoldberry.so",
        "Mac OS X, x86_64,  /io/github/digitalsmile/goldberry/natives/macos-x64/libgoldberry.dylib",
        "Mac OS X, aarch64, /io/github/digitalsmile/goldberry/natives/macos-aarch64/libgoldberry.dylib",
        "Windows,  amd64,   /io/github/digitalsmile/goldberry/natives/windows-x64/goldberry.dll",
        "Windows,  aarch64, /io/github/digitalsmile/goldberry/natives/windows-aarch64/goldberry.dll",
    })
    @DisplayName("resource path matches where the classifier jars put the library")
    void resourcePathMatchesPackaging(String osName, String osArch, String expected) {
        assertEquals(expected, NativeLibrary.resourcePath(NativePlatform.of(osName, osArch)));
    }

    @Test
    @DisplayName("resource path is absolute and contains the classifier")
    void resourcePathIsAbsolute() {
        var platform = NativePlatform.of("Linux", "amd64");
        var path = NativeLibrary.resourcePath(platform);

        assertTrue(path.startsWith("/"), () -> "not an absolute resource path: " + path);
        assertTrue(path.contains(platform.classifier()), () -> "classifier missing from " + path);
        assertTrue(path.endsWith(platform.libraryFileName()), () -> "library name missing from " + path);
    }

    @Test
    @DisplayName("availability can be checked without loading anything")
    void availabilityCheckDoesNotThrow() {
        // Whether the library is present depends on whether the superbuild has
        // run; that it can be asked without exploding must always hold.
        NativeLibrary.isAvailable();
    }
}
