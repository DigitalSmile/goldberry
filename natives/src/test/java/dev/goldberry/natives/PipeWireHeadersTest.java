package dev.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// PipeWire's headers, staged for SDL where the system's PipeWire is older than
/// SDL compiles against.
///
/// SDL's PipeWire audio and camera drivers need `libpipewire-0.3 >= 0.3.44` to
/// compile and load the library at run time. The release container's AlmaLinux
/// 8 ships 0.3.6, so without the staging both drivers drop out and the camera
/// check stops the build. The superbuild's half is read as text, as
/// [SdlPatchTest] reads the patch's; `pipewire_headers.cmake` itself is run with
/// `cmake -P` over a small tree laid out as PipeWire's is.
@DisplayName("PipeWire's staged headers")
class PipeWireHeadersTest {

    private final Path cmakeDir = locateCmakeDir();

    /// `:natives/src/main/cmake`, from wherever the tests were started, as
    /// [SuperbuildTest] finds it.
    private static Path locateCmakeDir() {
        var working = Path.of(System.getProperty("user.dir"));
        var own = working.resolve("src/main/cmake");
        return Files.isRegularFile(own.resolve("CMakeLists.txt")) ? own : working.resolve("natives/src/main/cmake");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    @Test
    @DisplayName("are staged on Linux, below SDL's floor only, before SDL is configured, keeping the system's library")
    void declared() {
        var cmakeLists = read(cmakeDir.resolve("CMakeLists.txt"));
        var start = cmakeLists.indexOf("set(GOLDBERRY_SDL_PIPEWIRE_FLOOR");
        assertTrue(start >= 0, "the superbuild does not stage PipeWire's headers");
        var block = cmakeLists.substring(start, cmakeLists.indexOf("\nendif()", start));
        var sdl = cmakeLists.indexOf("FetchContent_MakeAvailable(blend2d yoga harfbuzz sdl3");
        assertAll(
                () -> assertTrue(block.contains("\"0.3.44\""), "the floor is SDL's own PipeWire spec: " + block),
                () -> assertTrue(block.contains("CMAKE_SYSTEM_NAME STREQUAL \"Linux\""), block),
                () -> assertTrue(
                        block.contains("VERSION_LESS GOLDBERRY_SDL_PIPEWIRE_FLOOR"),
                        "a system PipeWire that meets the floor is used as it is"),
                () -> assertTrue(block.contains("GIT_TAG ${GOLDBERRY_PIPEWIRE_REF}"), "the release is the catalog's"),
                () -> assertTrue(
                        block.contains("SOURCE_SUBDIR goldberry-headers-only"),
                        "nothing of PipeWire's own build is added"),
                () -> assertTrue(
                        block.contains("SYSTEM_LIBDIR \"${GOLDBERRY_SYSTEM_PIPEWIRE_LIBDIR}\""),
                        "SDL must still find the system's library for the name it loads"),
                () -> assertTrue(block.contains("set(ENV{PKG_CONFIG_PATH}"), "SDL's pkg-config must see the staging"),
                () -> assertTrue(sdl > start, "the staging has to come before SDL's configure"),
                () -> assertTrue(
                        cmakeLists.contains("goldberry_pin(pipewire GOLDBERRY_PIPEWIRE_REF)"),
                        "the ref is read out of the catalog"));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("are laid out as PipeWire installs them, with its version, and .pc files naming the system's libdir")
    void staged(@TempDir Path directory) throws Exception {
        var source = pipewireTree(directory.resolve("pipewire"), "9.8.7");
        var staging = directory.resolve("staging");

        var version = stage(directory, source, staging);

        var include = staging.resolve("include");
        var pipewire = include.resolve("pipewire-0.3/pipewire");
        var versionHeader = read(pipewire.resolve("version.h"));
        var libpipewire = read(staging.resolve("lib/pkgconfig/libpipewire-0.3.pc"));
        var libspa = read(staging.resolve("lib/pkgconfig/libspa-0.2.pc"));
        assertAll(
                () -> assertEquals("9.8.7", version),
                () -> assertTrue(Files.isRegularFile(include.resolve("spa-0.2/spa/utils/defs.h"))),
                () -> assertFalse(Files.exists(include.resolve("spa-0.2/spa/meson.build")), "only headers come across"),
                () -> assertTrue(Files.isRegularFile(pipewire.resolve("pipewire.h"))),
                () -> assertTrue(Files.isRegularFile(pipewire.resolve("extensions/metadata.h"))),
                () -> assertFalse(Files.exists(pipewire.resolve("pipewire.c")), "only headers come across"),
                () -> assertFalse(Files.exists(pipewire.resolve("version.h.in"))),
                () -> assertTrue(versionHeader.contains("#define PW_MAJOR 9"), versionHeader),
                () -> assertTrue(versionHeader.contains("#define PW_MINOR 8"), versionHeader),
                () -> assertTrue(versionHeader.contains("#define PW_MICRO 7"), versionHeader),
                () -> assertTrue(versionHeader.contains("#define PW_API_VERSION \"0.3\""), versionHeader),
                () -> assertTrue(libpipewire.contains("\nVersion: 9.8.7\n"), libpipewire),
                () -> assertTrue(libpipewire.contains("\nlibdir=/usr/lib64\n"), "the system's libdir: " + libpipewire),
                () -> assertTrue(libpipewire.contains("\nRequires: libspa-0.2\n"), libpipewire),
                () -> assertTrue(libpipewire.contains("-I${includedir}/pipewire-0.3"), libpipewire),
                () -> assertTrue(libpipewire.contains("prefix=" + staging), libpipewire),
                () -> assertTrue(libspa.contains("-I${includedir}/spa-0.2"), libspa));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("are left alone for the same release, and replaced whole for another")
    void restaged(@TempDir Path directory) throws Exception {
        var staging = directory.resolve("staging");
        var first = pipewireTree(directory.resolve("first"), "1.0.0");
        write(first.resolve("src/pipewire/dropped.h"), "/* gone in the next release */\n");
        // A whole second, an hour back: CMake keeps a copy's time to the second.
        var old = FileTime.from(Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS));
        var sourceHeader = first.resolve("src/pipewire/pipewire.h");
        Files.setLastModifiedTime(sourceHeader, old);
        stage(directory, first, staging);

        // What ninja compares: a copied header carries its source's time rather
        // than the configure's, and an unchanged version.h is not rewritten, so
        // SDL's PipeWire drivers are not rebuilt by a second configure.
        var header = staging.resolve("include/pipewire-0.3/pipewire/pipewire.h");
        var versionHeader = staging.resolve("include/pipewire-0.3/pipewire/version.h");
        Files.setLastModifiedTime(versionHeader, old);
        stage(directory, first, staging);
        assertAll(
                () -> assertEquals(
                        old, Files.getLastModifiedTime(header), "a staged header must keep its source's time"),
                () -> assertEquals(
                        old, Files.getLastModifiedTime(versionHeader), "an unchanged version.h must not be rewritten"));

        var second = pipewireTree(directory.resolve("second"), "1.2.0");
        stage(directory, second, staging);
        var dropped = staging.resolve("include/pipewire-0.3/pipewire/dropped.h");
        assertAll(
                () -> assertFalse(Files.exists(dropped), "a header the new release dropped must not linger"),
                () -> assertTrue(read(staging.resolve("lib/pkgconfig/libpipewire-0.3.pc"))
                        .contains("\nVersion: 1.2.0\n")));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("stop the configure for a tree that is not laid out as PipeWire's")
    void refusesAnotherLayout(@TempDir Path directory) throws Exception {
        var source = pipewireTree(directory.resolve("pipewire"), "1.6.2");
        Files.delete(source.resolve("src/pipewire/version.h.in"));

        var run = cmake(directory, source, directory.resolve("staging"));

        assertAll(
                () -> assertTrue(run.exitCode() != 0, "the script must fail: " + run.output()),
                () -> assertTrue(run.output().contains("version.h.in"), run.output()));
    }

    /// A tree with the files `pipewire_headers.cmake` reads, laid out as a
    /// PipeWire release is, plus one non-header in each place it copies from.
    private static Path pipewireTree(Path root, String version) throws IOException {
        write(root.resolve("meson.build"), """
                project('pipewire', ['c' ],
                  version : '%s',
                  license : [ 'MIT', 'LGPL-2.1-or-later', 'GPL-2.0-only' ],
                )
                """.formatted(version));
        write(root.resolve("spa/include/spa/utils/defs.h"), "#define SPA_EXPORT\n");
        write(root.resolve("spa/include/spa/meson.build"), "# not a header\n");
        write(root.resolve("src/pipewire/pipewire.h"), "#include <pipewire/version.h>\n");
        write(root.resolve("src/pipewire/pipewire.c"), "/* not a header */\n");
        write(root.resolve("src/pipewire/extensions/metadata.h"), "#define PW_TYPE_INTERFACE_Metadata\n");
        write(root.resolve("src/pipewire/version.h.in"), """
                #define pw_get_headers_version() ("@PIPEWIRE_VERSION_MAJOR@.@PIPEWIRE_VERSION_MINOR@.@PIPEWIRE_VERSION_MICRO@")
                #define PW_API_VERSION @PIPEWIRE_API_VERSION@
                #define PW_MAJOR @PIPEWIRE_VERSION_MAJOR@
                #define PW_MINOR @PIPEWIRE_VERSION_MINOR@
                #define PW_MICRO @PIPEWIRE_VERSION_MICRO@
                """);
        return root;
    }

    /// Stages `source` into `staging` and answers the version the script
    /// reported; fails the test when the script fails.
    private String stage(Path directory, Path source, Path staging) throws Exception {
        var run = cmake(directory, source, staging);
        assertEquals(0, run.exitCode(), run.output());
        var marker = "staged=";
        var at = run.output().indexOf(marker);
        assertTrue(at >= 0, run.output());
        return run.output()
                .substring(at + marker.length())
                .lines()
                .findFirst()
                .orElseThrow()
                .strip();
    }

    private Cmake cmake(Path directory, Path source, Path staging) throws Exception {
        var script = directory.resolve("stage.cmake");
        Files.writeString(script, """
                include("%s")
                goldberry_stage_pipewire_headers(
                    SOURCE_DIR "%s"
                    DESTINATION "%s"
                    SYSTEM_LIBDIR "/usr/lib64"
                    OUT_VERSION staged)
                message(STATUS "staged=${staged}")
                """.formatted(
                        cmakePath(cmakeDir.resolve("pipewire_headers.cmake")), cmakePath(source), cmakePath(staging)));
        Process process;
        try {
            process = new ProcessBuilder(List.of("cmake", "-P", script.toString()))
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            assumeTrue(false, "no cmake on the PATH: " + e.getMessage());
            throw e;
        }
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Cmake(process.waitFor(), output);
    }

    /// CMake reads `\` in a quoted argument as an escape, so a Windows path
    /// goes in with forward slashes.
    private static String cmakePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private record Cmake(int exitCode, String output) {}
}
