package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

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

        /// A download by archive rather than by clone — the WebView2 SDK, which
        /// is a NuGet package and has no repository (ADR-0450).
        boolean fetchesFromUrl() {
            return !fetchesFromGit() && body.contains("URL ");
        }

        /// The `GOLDBERRY_*_REF` names this declaration interpolates, from
        /// wherever in the block they appear: `GIT_TAG` for a clone, the `URL`
        /// itself for an archive.
        List<String> refsUsed() {
            var refs = new ArrayList<String>();
            var matcher = REF_USE.matcher(withoutComments(body));
            while (matcher.find()) {
                refs.add(matcher.group(1));
            }
            return refs;
        }
    }

    /// `${GOLDBERRY_WEBVIEW2_REF}` where an upstream says which version it wants.
    private static final Pattern REF_USE = Pattern.compile("\\$\\{(GOLDBERRY_\\w+_REF)}");

    /// `goldberry_pin(webview2 GOLDBERRY_WEBVIEW2_REF)` — the catalog key and the
    /// variable it is read into.
    private static final Pattern PIN =
            Pattern.compile("(?m)^[ \t]*goldberry_pin\\(\\s*([\\w-]+)\\s+(GOLDBERRY_\\w+_REF)\\s*\\)");

    private final Path projectDir = locateProjectDir();
    private final String cmakeLists = read(projectDir.resolve("src/main/cmake/CMakeLists.txt"));
    private final String buildGradle = read(projectDir.resolve("build.gradle"));

    /// The catalog CMake reads the refs out of — the path `GOLDBERRY_VERSION_CATALOG`
    /// resolves to, spelled from `:natives` rather than from the CMake directory.
    private final String versionCatalog = read(projectDir.resolve("../gradle/libs.versions.toml"));

    /// The `:natives` project directory, found by walking up from wherever the
    /// tests were started.
    ///
    /// Gradle runs them with the project directory as the working directory; an
    /// IDE may use the repository root, or a module directory below it.
    ///
    /// Deliberately not a guess that falls back to an empty string: a drift guard
    /// that skips when it cannot find what it guards is a green tick over an
    /// unchecked invariant, which is the one outcome worse than a red one — the
    /// reasoning `Repository.root()` states in `build-logic`'s test sources.
    private static Path locateProjectDir() {
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("src/main/cmake/CMakeLists.txt"))) {
                return directory;
            }
            if (Files.isRegularFile(directory.resolve("natives/src/main/cmake/CMakeLists.txt"))) {
                return directory.resolve("natives");
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("cannot find natives/src/main/cmake/CMakeLists.txt at or above "
                + Path.of("").toAbsolutePath());
    }

    private static String read(Path path) {
        // An error rather than an empty string: every assertion below reads this
        // text, and an unreadable build file has to stop them rather than let them
        // pass over nothing.
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("cannot read " + path + ", so nothing below checks anything");
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
                        default -> {}
                    }
                    cursor++;
                }
                var block = cmake.substring(open, cursor - 1).stripLeading();
                var split = block.indexOf('\n');
                found.add(new Declaration((split < 0 ? block : block.substring(0, split)).strip(), block));
                from = cursor;
            }
        }
        return found;
    }

    @Test
    @DisplayName("every git-fetched upstream reports clone progress")
    void everyGitUpstreamReportsProgress() {
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
        // FETCHCONTENT_QUIET defaults to TRUE, which swallows the populate step's
        // output even when git itself is willing to report.
        assertTrue(
                cmakeLists.contains("set(FETCHCONTENT_QUIET FALSE)"),
                "FETCHCONTENT_QUIET must be set FALSE or the download is silent -- see ADR-0038");
    }

    @Test
    @DisplayName("the clone cache lives outside build/, so clean does not discard it")
    void cloneCacheSurvivesClean() {
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
        var depsDeclaration = buildGradle
                .lines()
                .dropWhile(line -> !line.contains("def depsDir ="))
                .takeWhile(line -> !line.isBlank())
                .toList();

        assertFalse(depsDeclaration.isEmpty(), "no depsDir declaration found in build.gradle");
        assertTrue(
                depsDeclaration.stream().noneMatch(line -> line.contains("layout.buildDirectory")),
                () -> "depsDir must not resolve under build/ -- see ADR-0038:\n" + String.join("\n", depsDeclaration));
    }

    @Test
    @DisplayName("a bumped ref re-configures, because the catalog is a task input")
    void bumpingARefReconfigures() {
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
    }

    @Test
    @DisplayName("the superbuild reads the catalog, and refuses a floating ref")
    void theSuperbuildReadsTheCatalog() {
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
                    () -> name + " is pinned by commit SHA, so GIT_SHALLOW is not allowed" + " -- see ADR-0030");
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

    @Test
    @DisplayName("every fetched upstream takes its version from the catalog")
    void everyUpstreamIsPinnedFromTheCatalog() {
        var pinned = new HashMap<String, String>();
        var pins = PIN.matcher(cmakeLists);
        while (pins.find()) {
            pinned.put(pins.group(2), pins.group(1));
        }
        assertFalse(pinned.isEmpty(), "the superbuild calls goldberry_pin nowhere");

        for (var declaration : declarationsIn(cmakeLists)) {
            // asmjit is declared without a ref of its own: Blend2D adds it, and
            // the pin is applied where it is populated further up.
            var refs = declaration.refsUsed();
            if (refs.isEmpty()) {
                continue;
            }
            for (var ref : refs) {
                // A version written into the CMake instead of the catalog is the
                // failure ADR-0035 exists about, and it is invisible: the build
                // works, and the one list of what this project fetches is wrong.
                assertTrue(
                        pinned.containsKey(ref),
                        () -> declaration.name() + " interpolates " + ref
                                + ", which no goldberry_pin() sets -- see ADR-0035");
                var key = pinned.get(ref);
                assertTrue(
                        Pattern.compile("(?m)^[ \t]*" + Pattern.quote(key) + "[ \t]*=[ \t]*\"")
                                .matcher(versionCatalog)
                                .find(),
                        () -> "gradle/libs.versions.toml has no `" + key + " = \"...\"`, so configuring "
                                + declaration.name() + " fails -- see ADR-0035");
            }
        }
    }

    @Test
    @DisplayName("an upstream fetched as an archive names the file it downloads")
    void archiveUpstreamsNameTheirDownload() {
        for (var declaration : declarationsIn(cmakeLists)) {
            if (!declaration.fetchesFromUrl()) {
                continue;
            }
            // The WebView2 package's URL ends in a bare version -- no `.zip`, no
            // `.tar.gz` -- and CMake will not extract an archive whose name tells
            // it no format. It does not fail either: it copies the file and the
            // include directory below it is simply never there (ADR-0450).
            assertTrue(
                    withoutComments(declaration.body()).contains("DOWNLOAD_NAME"),
                    () -> declaration.name() + " is fetched by URL without DOWNLOAD_NAME, so CMake may not"
                            + " recognise the archive and will not extract it -- see ADR-0450");
        }
    }

    @Test
    @DisplayName("the Windows web view SDK is fetched, not assumed to be installed")
    void theWindowsWebViewSdkIsFetched() {
        // The regression ADR-0450 is about. WebView2's *runtime* ships with
        // Windows; `WebView2.h` ships only in a NuGet package, and the CMake used
        // to conclude from the first fact that there was nothing to probe for --
        // so `master` broke with C1083 on the one file that includes it.
        var cmake = withoutComments(cmakeLists);
        assertTrue(
                cmake.contains("Microsoft.Web.WebView2"),
                "the Windows leg must fetch the WebView2 SDK headers -- see ADR-0450");
        assertTrue(
                cmake.contains("find_path(GOLDBERRY_WEBVIEW2_INCLUDE_DIR WebView2.h"),
                "the Windows leg must probe for WebView2.h rather than assume it -- see ADR-0450");

        // Headers only, and this is the assertion that says so: the package also
        // carries import libraries, and linking one would put a load-time
        // dependency into a library whose whole point is not having any.
        assertTrue(
                cmake.contains("target_include_directories(goldberry-webview PRIVATE"
                        + " \"${GOLDBERRY_WEBVIEW2_INCLUDE_DIR}\")"),
                "the SDK must reach the shim as an include directory -- see ADR-0450");
        assertFalse(
                cmake.contains("WebView2Loader"),
                "WebView2Loader.dll is opened by name at run time and must not be linked -- see ADR-0450");
    }

    @Test
    @DisplayName("a build without the web view says why, in terms of the platform it is on")
    void theWebViewOffMessageIsPlatformSpecific() {
        // The old message named a Debian package on every platform, which is a
        // wrong instruction rather than a missing one for a Windows reader.
        var cmake = withoutComments(cmakeLists);
        assertTrue(
                cmake.contains("GOLDBERRY_WEBVIEW_UNAVAILABLE_REASON"),
                "the `web view: OFF` line must carry the reason for this platform -- see ADR-0450");
    }

    /// The declaration block for one upstream, for the assertions that care about
    /// a single named one rather than all of them.
    private Declaration declarationOf(String name) {
        return declarationsIn(cmakeLists).stream()
                .filter(declaration -> declaration.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the superbuild declares no upstream named " + name));
    }
}
