package dev.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Guards the size decisions of the media superbuild (FFmpeg and dav1d are built
/// at `-O2` to fit the size gate), which live in
/// `media/src/main/cmake/CMakeLists.txt` rather than in Java, so this reads it as
/// text.
///
/// The gate in `package.cmake` catches a build that grew, but only on a machine
/// that builds FFmpeg, and only after the minutes that takes. Putting `-O3` back
/// costs 2.2 MB on linux-x64 and still fits on macOS, where the gate was first
/// measured; this fails on every machine instead.
@DisplayName("the media superbuild")
class FfmpegSuperbuildTest {

    /// An `ExternalProject_Add(name ...)` block, up to the parenthesis that
    /// closes it: nothing inside holds one, `${...}` being braces.
    private static final String BLOCK = "ExternalProject_Add\\(%s\\b([^)]*)\\)";

    private static final Pattern CONFIGURE = Pattern.compile("set\\(GOLDBERRY_FFMPEG_CONFIGURE\\b([^)]*)\\)");

    private static final Pattern SIZE_LIMIT = Pattern.compile("set\\(GOLDBERRY_MEDIA_SIZE_LIMIT (\\d+) CACHE");

    private static final Pattern BUILD_SUFFIX = Pattern.compile("set\\(GOLDBERRY_FFMPEG_BUILD_SUFFIX \"([^\"]*)\"\\)");

    private final String cmakeLists = read(locateCmakeLists());

    @Test
    @DisplayName("configures FFmpeg at -O2, not configure's -O3, which GCC grows past the gate")
    void ffmpegIsBuiltAtO2() {
        var configure = group(CONFIGURE, "the GOLDBERRY_FFMPEG_CONFIGURE list");
        assertTrue(configure.contains("--optflags=-O2"), configure);
        assertFalse(configure.contains("-O3"), configure);
    }

    @Test
    @DisplayName("builds dav1d at -O2 and still as a release, for NDEBUG and trim_dsp")
    void dav1dIsBuiltAtO2AsARelease() {
        var dav1d = group(Pattern.compile(BLOCK.formatted("dav1d")), "ExternalProject_Add(dav1d");
        assertTrue(dav1d.contains("-Doptimization=2"), dav1d);
        assertTrue(dav1d.contains("--buildtype=release"), dav1d);
    }

    @Test
    @DisplayName("names the libraries with the suffix the loader looks for, so no other FFmpeg is mistaken for them")
    void buildSuffixIsTheLoaders() {
        var matcher = BUILD_SUFFIX.matcher(cmakeLists);
        assertTrue(matcher.find(), "no GOLDBERRY_FFMPEG_BUILD_SUFFIX in the media superbuild");
        assertEquals(FfmpegPlatform.BUILD_SUFFIX, matcher.group(1));
        assertFalse(
                FfmpegPlatform.BUILD_SUFFIX.isEmpty(),
                "an empty suffix is FFmpeg's own sonames, which a distribution's FFmpeg in the same process"
                        + " would collide with; keep the suffix");
        var configure = group(CONFIGURE, "the GOLDBERRY_FFMPEG_CONFIGURE list");
        assertTrue(configure.contains("--build-suffix=${GOLDBERRY_FFMPEG_BUILD_SUFFIX}"), configure);
    }

    @Test
    @DisplayName("fails a build above 7 MB, the size gate")
    void sizeGateIsSevenMegabytes() {
        assertEquals(7L * 1024 * 1024, Long.parseLong(group(SIZE_LIMIT, "GOLDBERRY_MEDIA_SIZE_LIMIT")));
    }

    private String group(Pattern pattern, String what) {
        var matcher = pattern.matcher(cmakeLists);
        // An error rather than a skip: a guard that cannot find what it guards
        // would pass over nothing.
        if (!matcher.find()) {
            throw new IllegalStateException("no " + what + " in the media superbuild's CMakeLists.txt");
        }
        return matcher.group(1);
    }

    /// Found by walking up from wherever the tests were started: Gradle runs them
    /// in `media/`, an IDE may use the repository root.
    private static Path locateCmakeLists() {
        for (var directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            for (var candidate : new Path[] {
                directory.resolve("src/main/cmake/CMakeLists.txt"),
                directory.resolve("media/src/main/cmake/CMakeLists.txt")
            }) {
                if (Files.isRegularFile(candidate) && read(candidate).contains("project(goldberry_media")) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("cannot find media/src/main/cmake/CMakeLists.txt at or above "
                + Path.of("").toAbsolutePath());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }
}
