package dev.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NativeLibraryTest {

    /// The path here must match the `into(...)` in the `nativeJar*` tasks in
    /// `natives/build.gradle`. Asserting the literal means a rename on either
    /// side shows up as a failing test rather than as an UnsatisfiedLinkError on
    /// one platform in production.
    @ParameterizedTest
    @CsvSource({
        "Linux,    amd64,   /dev/goldberry/natives/linux-x64/libgoldberry.so",
        "Linux,    aarch64, /dev/goldberry/natives/linux-aarch64/libgoldberry.so",
        "Mac OS X, aarch64, /dev/goldberry/natives/macos-aarch64/libgoldberry.dylib",
        "Windows,  amd64,   /dev/goldberry/natives/windows-x64/goldberry.dll",
    })
    @DisplayName("resource path matches where the classifier jars put the library")
    void resourcePathMatchesPackaging(String osName, String osArch, String expected) {
        assertEquals(expected, NativeLibrary.resourcePath(NativePlatform.of(osName, osArch)));
    }
}
