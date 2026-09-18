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

    /// Every symbol this module's sources ask `libgoldberry` for.
    private TreeSet<String> bound() {
        var sources = projectDir.resolve("src/main/java");
        assumeTrue(Files.isDirectory(sources), () -> "not readable from " + projectDir + ": " + sources);
        var symbols = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
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
}
