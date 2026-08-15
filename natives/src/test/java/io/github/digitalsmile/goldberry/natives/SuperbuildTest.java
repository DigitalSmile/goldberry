package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/// Guards the two properties ADR-0029 rests on: the superbuild's download says
/// what it is doing, and it is not thrown away by `clean`.
///
/// Both live in build files rather than in Java, so this test reads them as text
/// -- the same trick [NativeLibraryTest] uses to pin the classifier-jar layout
/// from the Java side. It is a cheap guard against a slow, invisible regression:
/// a sixth upstream added without `GIT_PROGRESS` reintroduces exactly the silent
/// three-minute stall that ADR-0029 exists to remove, and nothing else in the
/// build would notice.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuperbuildTest {

    /// One `FetchContent_Declare` or `ExternalProject_Add` block.
    ///
    /// @param name the upstream's name, as CMake knows it
    /// @param body everything between the name and the closing parenthesis
    private record Declaration(String name, String body) {

        boolean fetchesFromGit() {
            return body.contains("GIT_REPOSITORY");
        }

        boolean reportsProgress() {
            return body.contains("GIT_PROGRESS TRUE");
        }
    }

    private final Path projectDir = locateProjectDir();
    private final String cmakeLists = read(projectDir.resolve("src/main/cmake/CMakeLists.txt"));
    private final String buildGradle = read(projectDir.resolve("build.gradle"));

    /// The `:natives` project directory.
    ///
    /// Gradle runs tests with the project directory as the working directory, so
    /// that is the first guess. An IDE may use the repository root instead, hence
    /// the fallback.
    private static Path locateProjectDir() {
        var working = Path.of(System.getProperty("user.dir"));
        return Files.isRegularFile(working.resolve("src/main/cmake/CMakeLists.txt"))
                ? working
                : working.resolve("natives");
    }

    private static String read(Path path) {
        // Returns empty rather than throwing: the assumption in each test turns a
        // build file this test cannot see into a skip, not into a spurious
        // failure. Being run from an unexpected directory is not a defect in the
        // superbuild.
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    /// Extracts every `FetchContent_Declare(...)` and `ExternalProject_Add(...)`
    /// block, matching parentheses rather than scanning to the first `)` so that
    /// a future declaration containing one is not silently truncated.
    private static List<Declaration> declarationsIn(String cmake) {
        var found = new ArrayList<Declaration>();
        for (var call : List.of("FetchContent_Declare(", "ExternalProject_Add(")) {
            var from = 0;
            while ((from = cmake.indexOf(call, from)) >= 0) {
                var open = from + call.length();
                var depth = 1;
                var cursor = open;
                while (cursor < cmake.length() && depth > 0) {
                    switch (cmake.charAt(cursor)) {
                        case '(' -> depth++;
                        case ')' -> depth--;
                        default -> { }
                    }
                    cursor++;
                }
                var block = cmake.substring(open, cursor - 1).stripLeading();
                var split = block.indexOf('\n');
                found.add(new Declaration(
                        (split < 0 ? block : block.substring(0, split)).strip(),
                        block));
                from = cursor;
            }
        }
        return found;
    }

    @Test
    @DisplayName("every git-fetched upstream reports clone progress")
    void everyGitUpstreamReportsProgress() {
        assumeTrue(!cmakeLists.isEmpty(), "CMakeLists.txt not readable from " + projectDir);

        var declarations = declarationsIn(cmakeLists);
        assertFalse(declarations.isEmpty(), "no FetchContent/ExternalProject declarations found");

        var silent = declarations.stream()
                .filter(Declaration::fetchesFromGit)
                .filter(declaration -> !declaration.reportsProgress())
                .map(Declaration::name)
                .toList();

        // Without --progress, git writes nothing at all into a pipe -- and under
        // Gradle's Exec it is always a pipe. That is the silence ADR-0029 is about.
        assertTrue(silent.isEmpty(), () -> """
                These upstreams clone without GIT_PROGRESS TRUE, so they download \
                in silence and the build looks hung: %s
                See ADR-0029.""".formatted(silent));
    }

    @Test
    @DisplayName("the populate step is not muted")
    void fetchContentIsNotQuiet() {
        assumeTrue(!cmakeLists.isEmpty(), "CMakeLists.txt not readable from " + projectDir);

        // FETCHCONTENT_QUIET defaults to TRUE, which swallows the populate step's
        // output even when git itself is willing to report.
        assertTrue(
                cmakeLists.contains("set(FETCHCONTENT_QUIET FALSE)"),
                "FETCHCONTENT_QUIET must be set FALSE or the download is silent -- see ADR-0029");
    }

    @Test
    @DisplayName("the clone cache lives outside build/, so clean does not discard it")
    void cloneCacheSurvivesClean() {
        assumeTrue(!buildGradle.isEmpty(), "build.gradle not readable from " + projectDir);

        assertTrue(
                buildGradle.contains("FETCHCONTENT_BASE_DIR"),
                "the superbuild's base directory must be pinned, not left under build/");

        // `layout.buildDirectory` is what would put the 330 MB back inside build/,
        // where `clean` deletes it and the next build re-downloads it.
        var depsDeclaration = buildGradle.lines()
                .dropWhile(line -> !line.contains("def depsDir ="))
                .takeWhile(line -> !line.contains("def upstreamRefs"))
                .toList();

        assertFalse(depsDeclaration.isEmpty(), "no depsDir declaration found in build.gradle");
        assertTrue(
                depsDeclaration.stream().noneMatch(line -> line.contains("layout.buildDirectory")),
                () -> "depsDir must not resolve under build/ -- see ADR-0029:\n"
                        + String.join("\n", depsDeclaration));
    }

    @Test
    @DisplayName("every pinned ref reaches CMake and is declared as a task input")
    void pinnedRefsAreTaskInputs() {
        assumeTrue(!buildGradle.isEmpty(), "build.gradle not readable from " + projectDir);

        // The refs used to reach CMake as -D arguments while being declared
        // nowhere, so a version bump left cmakeConfigure up to date and the build
        // quietly kept the old revision. Declaring them from one map is what makes
        // the two sides impossible to get out of step.
        assertTrue(
                buildGradle.contains("def upstreamRefs = ["),
                "the pinned refs must come from one map, so the command line and the inputs agree");
        assertTrue(
                buildGradle.contains("""
                        upstreamRefs.each { name, ref -> inputs.property("ref.${name}", ref) }"""),
                "each pinned ref must be a cmakeConfigure input -- see ADR-0029");
        assertTrue(
                buildGradle.contains("""
                        *upstreamRefs.collect { name, ref -> "-DGOLDBERRY_${name}_REF=${ref}" }"""),
                "the same map must build the CMake command line");
    }
}
