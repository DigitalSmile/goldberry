package io.github.digitalsmile.goldberry.natives.webview;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.NativePlatform;

/// Locates and loads `libgoldberry-webview`, the one native library of this
/// project that is **allowed to be missing**.
///
/// ## Why it is not in `libgoldberry`
///
/// Everything else the toolkit binds is statically linked into one shared
/// library (`docs/ARCHITECTURE.md` §3.2), and a web view cannot join it. The
/// engine behind [Webview] is the desktop's own — WebKitGTK, WebView2,
/// WKWebView — and linking it into `libgoldberry` would make GTK and WebKit a
/// **load-time** dependency of every Goldberry application on Linux, including
/// the overwhelming majority that never open a page. An application on a machine
/// without them would then fail to load the toolkit at all, rather than fail to
/// open a web view ([ADR-0441]).
///
/// So this is a second library, linked into nothing, opened the first time a page
/// is asked for and never otherwise. Its absence is an ordinary state with an
/// ordinary answer: [#isAvailable()] is false,
/// `Capability.WEB_VIEW` is not reported, and `WebViews.open` hands back empty.
public final class WebviewLibrary {

    /// Overrides discovery with an explicit path, the way
    /// [NativeLibrary#LIBRARY_PATH_PROPERTY] does for the main library — and for
    /// the same reason: the build tests against what it just compiled rather than
    /// against a published jar.
    public static final String LIBRARY_PATH_PROPERTY = "goldberry.webview.library";

    /// The library's name without prefix or extension.
    public static final String LIBRARY_STEM = "goldberry-webview";

    private static final Logger LOG = Logs.of(WebviewLibrary.class);

    private static final class Holder {
        private static final Optional<WebviewLibrary> INSTANCE = load();
    }

    private final SymbolLookup lookup;
    private final Path path;

    private WebviewLibrary(SymbolLookup lookup, Path path) {
        this.lookup = lookup;
        this.path = path;
    }

    /// The loaded library, or empty where this build has none.
    ///
    /// Unlike [NativeLibrary#get()] this **never throws**. A missing web view
    /// library is not a broken installation — it is a build that was made
    /// somewhere without WebKit's development headers, which is most machines.
    public static Optional<WebviewLibrary> get() {
        return Holder.INSTANCE;
    }

    /// Whether a page can be opened at all in this process.
    ///
    /// Loads the library on the first call, because there is no cheaper way to
    /// answer honestly: a file that exists and will not `dlopen` — WebKit itself
    /// missing at run time, which is the ordinary case on a server — is
    /// indistinguishable from one that is not there until it has been tried.
    public static boolean isAvailable() {
        return get().isPresent();
    }

    /// Where the library lives inside its classifier jar.
    ///
    /// The same directory `libgoldberry` is unpacked from, because it is the same
    /// jar: one classifier artifact per platform carries both, and an application
    /// that has the natives jar has whichever of them that build produced.
    public static String resourcePath(NativePlatform platform) {
        return "/io/github/digitalsmile/goldberry/natives/" + platform.classifier() + "/"
                + platform.sharedLibraryFileName(LIBRARY_STEM);
    }

    /// The symbol lookup every binding here is built from.
    public SymbolLookup lookup() {
        return lookup;
    }

    /// The file the library was loaded from.
    public Path path() {
        return path;
    }

    // Restricted for the reason NativeLibrary.load is: opening a native library
    // is what this module exists to do. Suppressed per call site so an
    // unintended new crossing still shows up as a build failure.
    @SuppressWarnings("restricted")
    private static Optional<WebviewLibrary> load() {
        var platform = NativePlatform.current();
        var explicit = System.getProperty(LIBRARY_PATH_PROPERTY);
        var libraryPath = explicit != null
                ? Optional.of(Path.of(explicit))
                : besideTheMainLibrary(platform).or(() -> unpack(platform));
        if (libraryPath.isEmpty()) {
            LOG.debug(
                    "no {} for {} — this build has no web view support",
                    platform.sharedLibraryFileName(LIBRARY_STEM),
                    platform.classifier());
            return Optional.empty();
        }
        var file = libraryPath.get();
        if (!Files.isRegularFile(file)) {
            LOG.debug("no web view library at {}", file);
            return Optional.empty();
        }
        try {
            // Arena.global() for NativeLibrary's reason: unloading would
            // invalidate every downcall handle taken against it, and a page
            // outlives any scope this could be given.
            var lookup = SymbolLookup.libraryLookup(file, Arena.global());
            LOG.info("loaded {} from {}", platform.sharedLibraryFileName(LIBRARY_STEM), file);
            return Optional.of(new WebviewLibrary(lookup, file));
        } catch (IllegalArgumentException e) {
            // The file is there and will not open, which on Linux means WebKitGTK
            // or GTK is missing at *run* time. `info` rather than `warn`: an
            // application that never opens a page is not having a problem, and
            // the one that does gets an empty Optional and says so itself.
            LOG.info("{} is present but could not be loaded ({}); no web view support", file, e.getMessage());
            return Optional.empty();
        }
    }

    /// The library the superbuild puts **next to** `libgoldberry`.
    ///
    /// Both come out of one CMake `install()` into one directory, so a run that
    /// points at a locally built `libgoldberry` with
    /// [NativeLibrary#LIBRARY_PATH_PROPERTY] means the one beside it — which is
    /// every test, every `:example:run` and every IDE launch in this repository.
    /// Without this they would each need a second `-D` naming a path they could
    /// have worked out, and the first one written forgot it: the showcase reported
    /// no web view support on a machine that had just built one.
    ///
    /// Reads the **property** rather than asking [NativeLibrary] for its path, and
    /// that is deliberate: asking would load `libgoldberry`, and
    /// `Goldberry.capabilities()` promises an answer before any window is open and
    /// on a machine with no native library at all. A string is a string.
    static Optional<Path> besideTheMainLibrary(NativePlatform platform) {
        var main = System.getProperty(NativeLibrary.LIBRARY_PATH_PROPERTY);
        if (main == null) {
            return Optional.empty();
        }
        var directory = Path.of(main).getParent();
        if (directory == null) {
            return Optional.empty();
        }
        var sibling = directory.resolve(platform.sharedLibraryFileName(LIBRARY_STEM));
        return Files.isRegularFile(sibling) ? Optional.of(sibling) : Optional.empty();
    }

    private static Optional<Path> unpack(NativePlatform platform) {
        var resource = resourcePath(platform);
        // The two lookups NativeLibrary documents: a class in a named module
        // searches only that module, and the system class loader is what finds an
        // ordinary classifier jar beside it.
        try (InputStream in = open(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            var target = Files.createTempDirectory("goldberry-webview")
                    .resolve(platform.sharedLibraryFileName(LIBRARY_STEM));
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            // The directory before the file, because `deleteOnExit` runs its queue
            // in reverse — the same ordering trap NativeLibrary documents.
            target.getParent().toFile().deleteOnExit();
            target.toFile().deleteOnExit();
            return Optional.of(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to unpack " + resource, e);
        }
    }

    private static InputStream open(String resource) {
        var own = WebviewLibrary.class.getResourceAsStream(resource);
        if (own != null) {
            return own;
        }
        return ClassLoader.getSystemResourceAsStream(resource.substring(1));
    }
}
