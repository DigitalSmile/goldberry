package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativePlatform.Architecture;
import io.github.digitalsmile.goldberry.natives.NativePlatform.OperatingSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NativePlatformTest {

    @ParameterizedTest
    @CsvSource({
        "Linux,               amd64,   LINUX,   X64",
        "Linux,               x86_64,  LINUX,   X64",
        "Linux,               aarch64, LINUX,   AARCH64",
        "Mac OS X,            aarch64, MACOS,   AARCH64",
        "Darwin,              arm64,   MACOS,   AARCH64",
        "Windows 11,          amd64,   WINDOWS, X64",
        "Windows Server 2022, x86_64,  WINDOWS, X64",
    })
    @DisplayName("maps os.name and os.arch onto the distribution matrix")
    void mapsSystemProperties(String osName, String osArch, OperatingSystem os, Architecture arch) {
        assertEquals(new NativePlatform(os, arch), NativePlatform.of(osName, osArch));
    }

    @ParameterizedTest
    @CsvSource({
        "Linux,    amd64,   linux-x64,     libgoldberry.so",
        "Linux,    aarch64, linux-aarch64, libgoldberry.so",
        "Mac OS X, aarch64, macos-aarch64, libgoldberry.dylib",
        "Windows,  amd64,   windows-x64,   goldberry.dll",
    })
    @DisplayName("classifier and library file name match the published artifacts")
    void classifierAndLibraryName(String osName, String osArch, String classifier, String libraryName) {
        var platform = NativePlatform.of(osName, osArch);

        assertEquals(classifier, platform.classifier());
        assertEquals(libraryName, platform.libraryFileName());
    }

    @Test
    @DisplayName("C long is 4 bytes on Windows and 8 everywhere else")
    void cLongSizeFollowsTheDataModel() {
        assertEquals(4, NativePlatform.of("Windows 11", "amd64").cLongSize());
        assertEquals(8, NativePlatform.of("Linux", "amd64").cLongSize());
        assertEquals(8, NativePlatform.of("Linux", "aarch64").cLongSize());
        assertEquals(8, NativePlatform.of("Mac OS X", "aarch64").cLongSize());
    }

    @ParameterizedTest
    @CsvSource({
        "Mac OS X,   x86_64",
        "Darwin,     amd64",
        "Windows 11, aarch64",
        "Windows 11, arm64",
    })
    @DisplayName("pairs outside the four-row matrix are rejected at construction")
    void rejectsUnpublishedPairs(String osName, String osArch) {
        // Not an UnsatisfiedLinkError three layers later: there is no such jar,
        // and saying so where the pair is named is the only place the message can
        // still explain which four rows exist.
        var thrown = assertThrows(
                UnsupportedOperationException.class, () -> NativePlatform.of(osName, osArch));

        assertTrue(thrown.getMessage().contains("macos-aarch64"), thrown::getMessage);
    }

    @Test
    @DisplayName("the record constructor rejects an unpublished pair too, not just of()")
    void rejectsUnpublishedPairsFromTheConstructor() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> new NativePlatform(OperatingSystem.MACOS, Architecture.X64));
        assertThrows(
                UnsupportedOperationException.class,
                () -> new NativePlatform(OperatingSystem.WINDOWS, Architecture.AARCH64));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SunOS", "FreeBSD", "AIX", ""})
    @DisplayName("unsupported operating systems are rejected, not guessed at")
    void rejectsUnsupportedOperatingSystems(String osName) {
        assertThrows(UnsupportedOperationException.class, () -> NativePlatform.of(osName, "amd64"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"i386", "x86", "arm", "riscv64", "ppc64le"})
    @DisplayName("unsupported architectures are rejected, not guessed at")
    void rejectsUnsupportedArchitectures(String osArch) {
        assertThrows(UnsupportedOperationException.class, () -> NativePlatform.of("Linux", osArch));
    }

    @Test
    @DisplayName("current() resolves on the machine running the tests")
    void currentResolves() {
        var platform = NativePlatform.current();

        assertEquals(platform, NativePlatform.of(
                System.getProperty("os.name"), System.getProperty("os.arch")));
    }
}
