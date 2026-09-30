package io.github.digitalsmile.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("FfmpegPlatform")
class FfmpegPlatformTest {

    @ParameterizedTest(name = "{0}/{1} is {2}")
    @CsvSource({
        "Linux, amd64, linux-x64",
        "Linux, aarch64, linux-aarch64",
        "Windows 11, amd64, windows-x64",
        "Mac OS X, aarch64, macos-aarch64",
    })
    @DisplayName("names the same four classifiers :natives publishes (ADR-0041)")
    void classifiers(String osName, String osArch, String classifier) {
        assertEquals(classifier, FfmpegPlatform.of(osName, osArch).classifier());
    }

    @Test
    @DisplayName("refuses the two pairs nothing is built for, and an unknown OS")
    void refuses() {
        assertThrows(UnsupportedOperationException.class, () -> FfmpegPlatform.of("Mac OS X", "x86_64"));
        assertThrows(UnsupportedOperationException.class, () -> FfmpegPlatform.of("Windows 11", "aarch64"));
        assertThrows(UnsupportedOperationException.class, () -> FfmpegPlatform.of("SunOS", "amd64"));
        assertThrows(UnsupportedOperationException.class, () -> FfmpegPlatform.of("Linux", "riscv64"));
    }

    @Test
    @DisplayName("names each library by its major and the build suffix, as the other libraries link against it")
    void fileNames() {
        assertEquals(
                "libavcodec-goldberry.so.62",
                FfmpegPlatform.of("Linux", "amd64").fileName(FfmpegLibrary.AVCODEC));
        assertEquals(
                "libavutil-goldberry.60.dylib",
                FfmpegPlatform.of("Mac OS X", "aarch64").fileName(FfmpegLibrary.AVUTIL));
        assertEquals(
                "swscale-goldberry-9.dll",
                FfmpegPlatform.of("Windows 11", "amd64").fileName(FfmpegLibrary.SWSCALE));
    }

    @Test
    @DisplayName("keeps a platform's files in a directory named for its classifier")
    void resourceDirectory() {
        assertEquals(
                "/io/github/digitalsmile/goldberry/media/natives/linux-aarch64/",
                FfmpegPlatform.of("Linux", "arm64").resourceDirectory());
    }
}
