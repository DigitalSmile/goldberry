package io.github.digitalsmile.goldberry.build;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ToolResolver}.
 *
 * <p>These use real files in a temporary directory rather than a mocked
 * filesystem seam. The bug this class exists for is entirely about what is
 * really on disk and really executable, so a test that stubs those two
 * predicates would be testing the stub.
 */
@DisplayName("ToolResolver")
class ToolResolverTest {

    /** A real, executable, do-nothing file -- what the resolver is looking for. */
    private static Path executable(Path directory, String name) throws IOException {
        Files.createDirectories(directory);
        var file = Files.writeString(directory.resolve(name), "#!/bin/sh\nexit 0\n");
        assertTrue(file.toFile().setExecutable(true), "could not make " + file + " executable");
        return file;
    }

    private static String path(Path... directories) {
        var joined = new StringBuilder();
        for (var directory : directories) {
            if (!joined.isEmpty()) {
                joined.append(File.pathSeparator);
            }
            joined.append(directory);
        }
        return joined.toString();
    }

    @Nested
    @DisplayName("familyOf")
    class FamilyOf {

        @Test
        @DisplayName("recognises the three families from os.name")
        void recognisesTheThreeFamilies() {
            assertEquals(ToolResolver.Family.MACOS, ToolResolver.familyOf("Mac OS X"));
            assertEquals(ToolResolver.Family.MACOS, ToolResolver.familyOf("Darwin"));
            assertEquals(ToolResolver.Family.LINUX, ToolResolver.familyOf("Linux"));
            assertEquals(ToolResolver.Family.WINDOWS, ToolResolver.familyOf("Windows 11"));
        }

        @Test
        @DisplayName("treats an unknown or absent os.name as Linux rather than failing")
        void fallsBackToLinux() {
            // Deciding a platform is unsupported belongs to the native-target
            // derivation in natives/build.gradle, not to locating a tool.
            assertEquals(ToolResolver.Family.LINUX, ToolResolver.familyOf("SunOS"));
            assertEquals(ToolResolver.Family.LINUX, ToolResolver.familyOf(null));
            assertEquals(ToolResolver.Family.LINUX, ToolResolver.familyOf(""));
        }
    }

    @Nested
    @DisplayName("candidateFileNames")
    class CandidateFileNames {

        @Test
        @DisplayName("adds Windows executable extensions, and nothing elsewhere")
        void addsWindowsExtensions() {
            assertEquals(List.of("cmake"),
                    ToolResolver.candidateFileNames("cmake", ToolResolver.Family.LINUX));
            assertEquals(List.of("cmake"),
                    ToolResolver.candidateFileNames("cmake", ToolResolver.Family.MACOS));
            assertEquals(List.of("meson.exe", "meson.cmd", "meson.bat", "meson"),
                    ToolResolver.candidateFileNames("meson", ToolResolver.Family.WINDOWS));
        }
    }

    @Nested
    @DisplayName("conventionalDirectories")
    class ConventionalDirectories {

        @Test
        @DisplayName("lists Homebrew before the Intel prefix on macOS")
        void listsHomebrewFirstOnMacOs() {
            var directories = ToolResolver.conventionalDirectories(ToolResolver.Family.MACOS, "/Users/x");

            // The daemon-PATH bug this class fixes is specifically a Homebrew
            // machine, so this entry is load-bearing rather than decorative.
            assertEquals(Path.of("/opt/homebrew/bin"), directories.getFirst());
            assertTrue(directories.contains(Path.of("/Applications/CMake.app/Contents/bin")));
        }

        @Test
        @DisplayName("appends the pip --user directory the README recommends")
        void appendsPipUserDirectory() {
            for (var family : ToolResolver.Family.values()) {
                assertEquals(Path.of("/home/x", ".local", "bin"),
                        ToolResolver.conventionalDirectories(family, "/home/x").getLast(),
                        "missing ~/.local/bin for " + family);
            }
        }

        @Test
        @DisplayName("omits the pip --user directory when user.home is unusable")
        void omitsPipUserDirectoryWithoutHome() {
            var withoutHome = ToolResolver.conventionalDirectories(ToolResolver.Family.LINUX, "  ");

            assertFalse(withoutHome.stream().anyMatch(d -> d.toString().contains(".local")));
            assertFalse(ToolResolver.conventionalDirectories(ToolResolver.Family.LINUX, null).isEmpty());
        }
    }

    @Nested
    @DisplayName("resolve")
    @DisabledOnOs(value = OS.WINDOWS,
            disabledReason = "the POSIX executable bit is what is under test; Windows decides by extension")
    class Resolve {

        @Test
        @DisplayName("finds a tool on the PATH and returns an absolute path")
        void findsOnPath(@TempDir Path temporary) throws IOException {
            var bin = temporary.resolve("bin");
            var cmake = executable(bin, "cmake");

            var resolved = ToolResolver.resolve("cmake", path(bin), List.of(), ToolResolver.Family.LINUX);

            assertTrue(resolved.isPresent());
            assertEquals(cmake.toAbsolutePath().normalize(), resolved.get());
            assertTrue(resolved.get().isAbsolute(), "an absolute path is the entire point");
        }

        @Test
        @DisplayName("falls back to a conventional directory when the PATH does not have it")
        void fallsBackToConventionalDirectory(@TempDir Path temporary) throws IOException {
            var launchdBin = temporary.resolve("usr-bin");   // the daemon's whole PATH
            var homebrewBin = temporary.resolve("homebrew-bin");
            Files.createDirectories(launchdBin);
            var cmake = executable(homebrewBin, "cmake");

            // This is the reported failure in miniature: a PATH inherited from a
            // GUI launcher, and the tool installed somewhere it never mentions.
            var resolved = ToolResolver.resolve(
                    "cmake", path(launchdBin), List.of(homebrewBin), ToolResolver.Family.LINUX);

            assertEquals(cmake.toAbsolutePath().normalize(), resolved.orElseThrow());
        }

        @Test
        @DisplayName("prefers the PATH over the conventional directories")
        void prefersPath(@TempDir Path temporary) throws IOException {
            var preferred = temporary.resolve("preferred");
            var conventional = temporary.resolve("conventional");
            var onPath = executable(preferred, "cmake");
            executable(conventional, "cmake");

            var resolved = ToolResolver.resolve(
                    "cmake", path(preferred), List.of(conventional), ToolResolver.Family.LINUX);

            // A contributor who put a newer CMake ahead on their PATH meant it.
            assertEquals(onPath.toAbsolutePath().normalize(), resolved.orElseThrow());
        }

        @Test
        @DisplayName("searches PATH entries in order")
        void searchesPathInOrder(@TempDir Path temporary) throws IOException {
            var first = temporary.resolve("first");
            var second = temporary.resolve("second");
            var winner = executable(first, "ninja");
            executable(second, "ninja");

            var resolved = ToolResolver.resolve(
                    "ninja", path(first, second), List.of(), ToolResolver.Family.LINUX);

            assertEquals(winner.toAbsolutePath().normalize(), resolved.orElseThrow());
        }

        @Test
        @DisplayName("reports nothing when the tool is not installed anywhere")
        void reportsNothingWhenAbsent(@TempDir Path temporary) throws IOException {
            var bin = temporary.resolve("bin");
            executable(bin, "cmake");

            assertTrue(ToolResolver.resolve("meson", path(bin), List.of(), ToolResolver.Family.LINUX)
                    .isEmpty());
        }

        @Test
        @DisplayName("ignores a match that is not executable")
        void ignoresNonExecutable(@TempDir Path temporary) throws IOException {
            var bin = temporary.resolve("bin");
            Files.createDirectories(bin);
            Files.writeString(bin.resolve("cmake"), "not a program");

            assertTrue(ToolResolver.resolve("cmake", path(bin), List.of(), ToolResolver.Family.LINUX)
                    .isEmpty());
        }

        @Test
        @DisplayName("ignores a directory that shares the tool's name")
        void ignoresDirectory(@TempDir Path temporary) throws IOException {
            var bin = temporary.resolve("bin");
            Files.createDirectories(bin.resolve("cmake"));   // e.g. a share/cmake tree

            assertTrue(ToolResolver.resolve("cmake", path(bin), List.of(), ToolResolver.Family.LINUX)
                    .isEmpty());
        }

        @Test
        @DisplayName("ignores empty PATH entries rather than searching the working directory")
        void ignoresEmptyPathEntries(@TempDir Path temporary) throws IOException {
            var bin = temporary.resolve("bin");
            var cmake = executable(bin, "cmake");

            // "::" and a trailing ":" both mean the working directory to a shell.
            var resolved = ToolResolver.resolve(
                    "cmake",
                    File.pathSeparator + File.pathSeparator + bin + File.pathSeparator,
                    List.of(),
                    ToolResolver.Family.LINUX);

            assertEquals(cmake.toAbsolutePath().normalize(), resolved.orElseThrow());
        }

        @Test
        @DisplayName("survives a missing or empty PATH")
        void survivesMissingPath(@TempDir Path temporary) throws IOException {
            var bin = temporary.resolve("bin");
            var cmake = executable(bin, "cmake");

            assertEquals(cmake.toAbsolutePath().normalize(),
                    ToolResolver.resolve("cmake", null, List.of(bin), ToolResolver.Family.LINUX)
                            .orElseThrow());
            assertEquals(cmake.toAbsolutePath().normalize(),
                    ToolResolver.resolve("cmake", "", List.of(bin), ToolResolver.Family.LINUX)
                            .orElseThrow());
        }

        @Test
        @DisplayName("takes an explicit path at its word instead of searching")
        void honoursExplicitPath(@TempDir Path temporary) throws IOException {
            var chosen = temporary.resolve("chosen");
            var onPath = temporary.resolve("bin");
            var cmake = executable(chosen, "cmake");
            executable(onPath, "cmake");

            var resolved = ToolResolver.resolve(
                    cmake.toString(), path(onPath), List.of(), ToolResolver.Family.LINUX);

            // An override that quietly resolved to a different copy would be
            // worse than one that fails.
            assertEquals(cmake.toAbsolutePath().normalize(), resolved.orElseThrow());
        }

        @Test
        @DisplayName("reports nothing for an explicit path that is not an executable")
        void rejectsBadExplicitPath(@TempDir Path temporary) throws IOException {
            var bin = temporary.resolve("bin");
            executable(bin, "cmake");

            assertTrue(ToolResolver.resolve(
                            temporary.resolve("nowhere/cmake").toString(),
                            path(bin),
                            List.of(),
                            ToolResolver.Family.LINUX)
                    .isEmpty());
        }

        @Test
        @DisplayName("reports nothing for a blank tool name")
        void rejectsBlankName(@TempDir Path temporary) throws IOException {
            var bin = temporary.resolve("bin");
            executable(bin, "cmake");

            assertTrue(ToolResolver.resolve(null, path(bin), List.of(), ToolResolver.Family.LINUX)
                    .isEmpty());
            assertTrue(ToolResolver.resolve("  ", path(bin), List.of(), ToolResolver.Family.LINUX)
                    .isEmpty());
        }

        @Test
        @DisplayName("resolves through a symlink without pinning the versioned target")
        void keepsTheSymlink(@TempDir Path temporary) throws IOException {
            var cellar = temporary.resolve("Cellar/cmake/4.3.4/bin");
            var bin = temporary.resolve("bin");
            var real = executable(cellar, "cmake");
            Files.createDirectories(bin);
            var link = Files.createSymbolicLink(bin.resolve("cmake"), real);

            var resolved = ToolResolver.resolve("cmake", path(bin), List.of(), ToolResolver.Family.LINUX);

            // Homebrew's layout. Reporting the Cellar path would pin the build to
            // a version `brew upgrade` is about to remove.
            assertEquals(link.toAbsolutePath().normalize(), resolved.orElseThrow());
        }
    }
}
