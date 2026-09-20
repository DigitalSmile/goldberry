package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/// `exports/goldberry.symbols` against the bindings that are supposed to need it.
///
/// The file states its own rule in its header: *a symbol bound in Java but
/// missing here fails at load time, and a symbol here that nothing binds is dead
/// weight.* Only the first half of that was ever enforced — by the program
/// crashing. The second half is invisible, so it rotted: `SDL_PumpEvents` and
/// `WebPAnimDecoderReset` were exported, force-linked out of their static
/// archives with `-u`, and called by nothing at all, and `bl_context_flush` and
/// `bl_context_clear_all` had bindings with no callers keeping them alive.
///
/// Both halves are checkable by reading two files, which is what this does. It
/// needs no `libgoldberry`: the question is what the source says, not what the
/// library exports, and asking the library would only find the symbols that are
/// there because this list put them there.
///
/// Reading the Java as **text** is deliberate and is the one thing here that
/// could drift on its own. The alternative is binding every holder at run time
/// and asking each for its symbol, which needs the library, needs every optional
/// symbol to be present, and cannot see a name at all until something calls it.
/// The guard is [#theScanFoundTheBindings()]: a regex that stops matching finds
/// nothing rather than finding a discrepancy, and finding nothing fails.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("the export list")
class ExportListTest {

    /// `Downcalls.symbol(lookup, "SDL_Init")`, and its optional twin.
    private static final Pattern BOUND =
            Pattern.compile("Downcalls\\.(?:optionalSymbol|symbol)\\(\\s*\\w+\\s*,\\s*\"(\\w+)\"");

    /// Yoga's eleven length properties, whose two or three C names are composed
    /// rather than written out — `LengthCalls.bind(lookup, "Width", true)`.
    private static final Pattern COMPOSED =
            Pattern.compile("(?:Keyed)?LengthCalls\\.bind\\(\\s*\\w+\\s*,\\s*\"(\\w+)\"\\s*,\\s*(true|false)\\)");

    private final Path projectDir = locateProjectDir();

    /// `GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_abi(void)` in the shim.
    private static final Pattern SHIM_EXPORT =
            Pattern.compile("GOLDBERRY_WEBVIEW_EXPORT\\s+[\\w *]+?\\s*\\*?(goldberry_webview_\\w+)\\s*\\(");

    /// The `:natives` project directory. Gradle runs tests with the project
    /// directory as the working directory; an IDE may use the repository root,
    /// hence the fallback. [SuperbuildTest] locates its build files the same way.
    private static Path locateProjectDir() {
        var working = Path.of(System.getProperty("user.dir"));
        return Files.isRegularFile(working.resolve("src/main/cmake/exports/goldberry.symbols"))
                ? working
                : working.resolve("natives");
    }

    /// Every symbol on the export list, by the rule the CMake parser uses:
    /// one per line, blank lines and `#` comments ignored.
    private TreeSet<String> exported() {
        var file = projectDir.resolve("src/main/cmake/exports/goldberry.symbols");
        assumeTrue(Files.isRegularFile(file), () -> "not readable from " + projectDir + ": " + file);
        try {
            var symbols = new TreeSet<String>();
            for (var line : Files.readAllLines(file)) {
                var symbol = line.strip();
                if (!symbol.isEmpty() && !symbol.startsWith("#")) {
                    symbols.add(symbol);
                }
            }
            return symbols;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    /// The package whose bindings are **not** against `libgoldberry`.
    ///
    /// `exports/goldberry.symbols` is the export list of one library, and since
    /// ADR-0441 this module binds two. `libgoldberry-webview` is a separate shared
    /// object — built only where WebKit's headers were present, linked into
    /// nothing, opened on demand — so its nine `goldberry_webview_*` symbols are
    /// exported by *it*, from the visibility attribute in `goldberry_webview.cc`,
    /// and would be nine dead entries in the list this test guards.
    ///
    /// Named as a **path fragment** rather than as a symbol prefix on purpose: a
    /// prefix would also exempt a `goldberry_webview_*` binding accidentally added
    /// to a class that really does talk to `libgoldberry`, which is the mistake
    /// this exemption could hide.
    private static final String SECOND_LIBRARY = "natives" + java.io.File.separator + "webview";

    /// Every symbol this module's sources ask `libgoldberry` for.
    private TreeSet<String> bound() {
        var sources = projectDir.resolve("src/main/java");
        assumeTrue(Files.isDirectory(sources), () -> "not readable from " + projectDir + ": " + sources);
        var symbols = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (var file : files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.toString().contains(SECOND_LIBRARY))
                    .toList()) {
                var source = Files.readString(file);
                BOUND.matcher(source).results().forEach(match -> symbols.add(match.group(1)));
                COMPOSED.matcher(source).results().forEach(match -> {
                    var prefix = "YGNodeStyleSet" + match.group(1);
                    symbols.add(prefix);
                    symbols.add(prefix + "Percent");
                    if (Boolean.parseBoolean(match.group(2))) {
                        symbols.add(prefix + "Auto");
                    }
                });
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + sources, e);
        }
        return symbols;
    }

    @Test
    @DisplayName("the scan found the bindings it is supposed to compare against")
    void theScanFoundTheBindings() {
        // Every assertion below is an equality between two sets, and a broken
        // scan makes one of them empty rather than wrong. This is the assertion
        // that would fail first.
        assertTrue(bound().size() > 200, () -> "found only " + bound().size() + " bound symbols in the sources");
    }

    @Nested
    @DisplayName("holds every symbol something binds")
    class Bound {

        @Test
        @DisplayName("or the library fails to load on the first call through it")
        void everyBoundSymbolIsExported() {
            var missing = new TreeSet<>(bound());
            missing.removeAll(exported());

            assertEquals(
                    List.of(),
                    List.copyOf(missing),
                    "these are bound in Java and not on the export list, so libgoldberry does not export them: "
                            + "the binding raises an UnsatisfiedLinkError the first time anything reaches it");
        }
    }

    @Nested
    @DisplayName("holds nothing else")
    class Unbound {

        @Test
        @DisplayName("because a symbol is exported when something binds it, and not otherwise")
        void everyExportedSymbolIsBound() {
            var dead = new TreeSet<>(exported());
            dead.removeAll(bound());

            assertEquals(
                    List.of(),
                    List.copyOf(dead),
                    "these are on the export list and bound by nothing. Each one is pulled out of its static "
                            + "archive with -u, kept out of the linker's reach for dead-strip, and exported into "
                            + "libgoldberry's dynamic symbol table, for nobody. Delete the line, or bind it.");
        }
    }

    /// The same two obligations for the **second** library — ADR-0441.
    ///
    /// `libgoldberry-webview` has no export list to check against, because it
    /// needs none: its nine functions are the shim's own and carry the visibility
    /// attribute themselves, so there is no static archive to force symbols out of
    /// and nothing to enumerate. What can still drift is the pair of files — a
    /// binding for a function the shim does not define fails at the first call,
    /// and a function the shim exports that nothing binds is dead code in a
    /// library that exists to be small.
    ///
    /// So the shim's own source is the list, and this is the same equality the two
    /// classes above assert, against a different pair of files.
    @Nested
    @DisplayName("the web view shim")
    class WebViewShim {

        /// Every `goldberry_webview_*` the shim defines.
        private TreeSet<String> shimExports() {
            var file = projectDir.resolve("src/main/cmake/goldberry_webview.cc");
            assumeTrue(Files.isRegularFile(file), () -> "not readable from " + projectDir + ": " + file);
            try {
                var symbols = new TreeSet<String>();
                SHIM_EXPORT.matcher(Files.readString(file)).results().forEach(m -> symbols.add(m.group(1)));
                return symbols;
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
        }

        /// Every `goldberry_webview_*` the Java side asks for.
        private TreeSet<String> shimBindings() {
            var sources = projectDir.resolve("src/main/java");
            assumeTrue(Files.isDirectory(sources), () -> "not readable from " + projectDir + ": " + sources);
            var symbols = new TreeSet<String>();
            try (Stream<Path> files = Files.walk(sources)) {
                for (var file : files.filter(f -> f.toString().endsWith(".java"))
                        .filter(f -> f.toString().contains(SECOND_LIBRARY))
                        .toList()) {
                    BOUND.matcher(Files.readString(file)).results().forEach(m -> symbols.add(m.group(1)));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + sources, e);
            }
            return symbols;
        }

        @Test
        @DisplayName("is scanned by a regex that still matches something")
        void theScanFoundBoth() {
            // The guard the outer class has, for the same reason: a regex that
            // stopped matching would make both sets empty and both equalities
            // below pass.
            assertTrue(shimExports().size() >= 9, () -> "found only " + shimExports() + " in the shim");
            assertTrue(shimBindings().size() >= 9, () -> "found only " + shimBindings() + " bound");
        }

        @Test
        @DisplayName("defines everything Java binds, and Java binds everything it defines")
        void theTwoSidesAgree() {
            // One assertion rather than two, because for this library the two
            // failures have the same fix and the same cause: somebody edited one
            // file of the pair. There is no third party here -- no static archive,
            // no -u, no version script -- so the sets are simply equal.
            assertEquals(
                    List.copyOf(shimExports()),
                    List.copyOf(shimBindings()),
                    "goldberry_webview.cc and the Java bindings in natives.webview disagree. A binding with no "
                            + "shim function raises an UnsatisfiedLinkError on the first call through it; a shim "
                            + "function nothing binds is dead code in a library whose whole point is to be small.");
        }
    }
}
