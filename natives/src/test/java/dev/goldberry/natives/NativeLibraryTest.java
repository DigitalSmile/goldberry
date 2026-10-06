package dev.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    /// The natives are classifier jars of one artifact. The message names that
    /// artifact the way a build file does, and not a `goldberry-natives-linux-x64`
    /// artifact, which does not exist and which the first run of an application
    /// went looking for.
    @Test
    @DisplayName("the missing-natives message names the artifact and its classifier")
    void missingMessageNamesTheClassifierJar() {
        var platform = NativePlatform.of("Linux", "amd64");
        var message = NativeLibrary.missingMessage(platform, NativeLibrary.resourcePath(platform));
        assertTrue(message.contains("dev.goldberry:goldberry-natives::linux-x64"), message);
        assertTrue(message.contains("classifier"), message);
        assertTrue(message.contains("-D" + NativeLibrary.LIBRARY_PATH_PROPERTY), message);
        assertFalse(message.contains("goldberry-natives-linux-x64"), message);
    }
}
