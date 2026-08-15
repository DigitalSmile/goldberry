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

/// Guards the two properties ADR-0038 rests on: the superbuild's download says
/// what it is doing, and it is not thrown away by `clean`.
///
/// Both live in build files rather than in Java, so this test reads them as text
/// -- the same trick [NativeLibraryTest] uses to pin the classifier-jar layout
/// from the Java side. It is a cheap guard against a slow, invisible regression:
/// a sixth upstream added without `GIT_PROGRESS` reintroduces exactly the silent
/// three-minute stall that ADR-0038 exists to remove, and nothing else in the
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
        // Gradle's Exec it is always a pipe. That is the silence ADR-0038 is about.
        assertTrue(silent.isEmpty(), () -> """
                These upstreams clone without GIT_PROGRESS TRUE, so they download \
                in silence and the build looks hung: %s
                See ADR-0038.""".formatted(silent));
    }

    @Test
    @DisplayName("the populate step is not muted")
    void fetchContentIsNotQuiet() {
        assumeTrue(!cmakeLists.isEmpty(), "CMakeLists.txt not readable from " + projectDir);

        // FETCHCONTENT_QUIET defaults to TRUE, which swallows the populate step's
        // output even when git itself is willing to report.
        assertTrue(
                cmakeLists.contains("set(FETCHCONTENT_QUIET FALSE)"),
                "FETCHCONTENT_QUIET must be set FALSE or the download is silent -- see ADR-0038");
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
        // Bounded by the blank line that ends the statement, rather than by the
        // declaration that used to follow it: `upstreamRefs` was removed by
        // ADR-0035, and naming a neighbour made this read on past the end of
        // what it meant to check -- as far as artifactsDir, which resolves under
        // build/ for perfectly good reasons of its own.
        var depsDeclaration = buildGradle.lines()
                .dropWhile(line -> !line.contains("def depsDir ="))
                .takeWhile(line -> !line.isBlank())
                .toList();

        assertFalse(depsDeclaration.isEmpty(), "no depsDir declaration found in build.gradle");
        assertTrue(
                depsDeclaration.stream().noneMatch(line -> line.contains("layout.buildDirectory")),
                () -> "depsDir must not resolve under build/ -- see ADR-0038:\n"
                        + String.join("\n", depsDeclaration));
    }

    @Test
    @DisplayName("a bumped ref re-configures, because the catalog is a task input")
    void bumpingARefReconfigures() {
        assumeTrue(!buildGradle.isEmpty(), "build.gradle not readable from " + projectDir);

        // The property this guards has not changed and the mechanism has. The
        // refs used to reach CMake as -D arguments while being declared as
        // inputs nowhere, so a version bump left cmakeConfigure up to date and
        // the build quietly kept the old revision; the fix was to mirror them
        // into an inputs.property each.
        //
        // ADR-0035 then removed the -D arguments altogether -- CMake reads
        // gradle/libs.versions.toml itself -- so there is nothing left to
        // mirror. Declaring the catalog as an input is what carries the
        // guarantee now, and it carries it for a ref this build file has never
        // heard of, which the per-ref properties could not.
        assertTrue(
                buildGradle.contains("inputs.file catalogFile"),
                "the version catalog must be a cmakeConfigure input, or a bumped ref"
                        + " leaves the configuration stale -- see ADR-0035 and ADR-0038");
        assertFalse(
                buildGradle.contains("-DGOLDBERRY_") && buildGradle.contains("_REF="),
                "refs must not reach CMake as -D arguments any more; the superbuild reads"
                        + " the catalog itself -- see ADR-0035");
    }

    @Test
    @DisplayName("the superbuild reads the catalog, and refuses a floating ref")
    void theSuperbuildReadsTheCatalog() {
        assumeTrue(!cmakeLists.isEmpty(), "CMakeLists.txt not readable from " + projectDir);

        assertTrue(
                cmakeLists.contains("GOLDBERRY_VERSION_CATALOG"),
                "CMake must read gradle/libs.versions.toml itself -- see ADR-0035");
        // The other half of ADR-0035: a pin, not a range. This is the check that
        // would have caught example.yml pinning Blend2D to a floating `master`.
        assertTrue(
                cmakeLists.contains("master|main|HEAD|latest|trunk"),
                "a floating ref must be refused at configure time -- see ADR-0030");
    }

    @Test
    @DisplayName("a SHA-pinned upstream is not cloned shallow")
    void shaPinnedUpstreamsAreNotShallow() {
        assumeTrue(!cmakeLists.isEmpty(), "CMakeLists.txt not readable from " + projectDir);

        // Blend2D and AsmJit are pinned by commit SHA (ADR-0030), and CMake's own
        // documentation says GIT_SHALLOW "works only with branch names and tags.
        // A commit hash is not allowed."
        //
        // It appears to work anyway today, which is the trap: a shallow clone
        // fetches every branch *tip*, and these SHAs are the tips of master right
        // now. It starts failing the day either upstream commits anything -- on a
        // clean clone, in CI, as "Failed to checkout tag", with nothing pointing
        // at the cause. This nearly landed once, in a merge from a branch where
        // both were still pinned to `master` and shallow was legal.
        for (var name : List.of("asmjit", "blend2d")) {
            // Comments stripped first: the declaration says in prose that it is
            // deliberately not shallow, and a check that could not tell the
            // explanation from the setting would fail on the very comment that
            // exists to prevent the mistake.
            var declaration = withoutComments(declarationOf(name).body());
            assertFalse(
                    declaration.contains("GIT_SHALLOW"),
                    () -> name + " is pinned by commit SHA, so GIT_SHALLOW is not allowed"
                            + " -- see ADR-0030");
        }
    }

    /// CMake source with its `#` comments removed, so an assertion reads the
    /// settings rather than the prose about them.
    private static String withoutComments(String cmake) {
        return cmake.lines()
                .map(line -> {
                    var hash = line.indexOf('#');
                    return hash < 0 ? line : line.substring(0, hash);
                })
                .reduce("", (a, b) -> a + "\n" + b);
    }

    /// The declaration block for one upstream, for the assertions that care about
    /// a single named one rather than all of them.
    private Declaration declarationOf(String name) {
        return declarationsIn(cmakeLists).stream()
                .filter(declaration -> declaration.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the superbuild declares no upstream named " + name));
    }
}
