package dev.goldberry.build.natives;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.build.repository.Repository;

@DisplayName("NativeTarget")
class NativeTargetTest {

    @ParameterizedTest(name = "{0} {1} builds {2}")
    @CsvSource({
        "Linux,      amd64,   linux-x64,     libgoldberry.so",
        "Linux,      aarch64, linux-aarch64, libgoldberry.so",
        "Mac OS X,   aarch64, macos-aarch64, libgoldberry.dylib",
        "Mac OS X,   arm64,   macos-aarch64, libgoldberry.dylib",
        "Windows 11, amd64,   windows-x64,   goldberry.dll",
        "Windows 11, x86_64,  windows-x64,   goldberry.dll",
    })
    @DisplayName("names the host's row the way NativePlatform does on the Java side")
    void host(String osName, String osArch, String id, String library) {
        var target = NativeTarget.host(osName, osArch);
        assertAll(() -> assertEquals(id, target.id()), () -> assertEquals(library, target.library()));
    }

    @ParameterizedTest(name = "{0} {1} is refused")
    @CsvSource({"Mac OS X, amd64", "Windows 11, aarch64", "FreeBSD, amd64", "Linux, riscv64"})
    @DisplayName("refuses the pairs nothing is published for")
    void unpublishedPairs(String osName, String osArch) {
        assertThrows(IllegalStateException.class, () -> NativeTarget.host(osName, osArch));
    }

    @Test
    @DisplayName("is four rows, each with a web view, matching the jars CI publishes")
    void theMatrix() {
        assertAll(
                () -> assertEquals(
                        List.of("linux-x64", "linux-aarch64", "windows-x64", "macos-aarch64"),
                        NativeTarget.ALL.stream().map(NativeTarget::id).toList()),
                () -> assertTrue(NativeTarget.ALL.stream().allMatch(NativeTarget::webviewRequired)));
    }

    @Test
    @DisplayName("spells task suffixes, the first thread and the install paths the scripts use")
    void derivedNames() {
        var mac = NativeTarget.byId("macos-aarch64");
        var linux = NativeTarget.byId("linux-x64");
        var repository = Path.of("/repo");
        assertAll(
                () -> assertEquals("MacosAarch64", mac.taskSuffix()),
                () -> assertEquals("LinuxX64", linux.taskSuffix()),
                () -> assertTrue(mac.needsFirstThread()),
                () -> assertFalse(linux.needsFirstThread()),
                () -> assertEquals(NativeTarget.OperatingSystem.WINDOWS, NativeTarget.byId("windows-x64").system()),
                () -> assertEquals(
                        Path.of("/repo/natives/build/native/linux-x64/install/lib/libgoldberry.so"),
                        linux.localLibrary(repository)),
                () -> assertEquals(
                        Path.of("/repo/media/build/ffmpeg/linux-x64/install/lib"), linux.localFfmpeg(repository)));
    }

    @Test
    @DisplayName("is where :natives installs, so every module loads the library that was built")
    void nativesInstallsWhereTheOthersLook() {
        var script = Repository.read("natives/build.gradle");
        assertAll(
                () -> assertTrue(
                        script.contains("layout.buildDirectory.dir(hostTarget.nativesInstallDir())"),
                        "natives/build.gradle installs somewhere NativeTarget.localLibrary does not look"),
                () -> assertTrue(
                        Repository.read("media/build.gradle")
                                .contains("layout.buildDirectory.dir(hostTarget.ffmpegInstallDir())"),
                        "media/build.gradle installs FFmpeg somewhere NativeTarget.localFfmpeg does not look"));
    }
}
