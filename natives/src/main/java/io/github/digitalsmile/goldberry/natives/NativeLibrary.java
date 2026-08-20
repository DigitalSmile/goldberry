package io.github.digitalsmile.goldberry.natives;

import io.github.digitalsmile.goldberry.natives.log.Logs;
import io.github.digitalsmile.goldberry.natives.log.Startup;
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

/// Locates and loads `libgoldberry`, and hands out the symbol lookup every
/// binding is built from.
///
/// One shared library holds every native dependency (`docs/ARCHITECTURE.md` §3.2),
/// so this is the only place Goldberry touches the filesystem to find native
/// code, and the only `SymbolLookup` in the toolkit.
public final class NativeLibrary {

    /// Overrides discovery with an explicit path — used by the build to test
    /// against a locally cross-built library instead of a published jar.
    public static final String LIBRARY_PATH_PROPERTY = "goldberry.native.library";

    private static final Logger LOG = Logs.of(NativeLibrary.class);

    private static final class Holder {
        private static final NativeLibrary INSTANCE = load();
    }

    private final SymbolLookup lookup;
    private final NativePlatform platform;
    private final Path path;

    private NativeLibrary(SymbolLookup lookup, NativePlatform platform, Path path) {
        this.lookup = lookup;
        this.platform = platform;
        this.path = path;
    }

    /// The loaded library, loading it on first call.
    ///
    /// @throws UnsatisfiedLinkError if it cannot be found or loaded
    public static NativeLibrary get() {
        return Holder.INSTANCE;
    }

    /// Where the library lives inside its classifier jar.
    ///
    /// Kept in step with the `nativeJar*` tasks in `natives/build.gradle`;
    /// tested on both sides so a rename cannot silently break loading.
    public static String resourcePath(NativePlatform platform) {
        return "/io/github/digitalsmile/goldberry/natives/"
                + platform.classifier() + "/" + platform.libraryFileName();
    }

    /// Opens the classifier jar's library resource, from wherever it is.
    ///
    /// Two lookups, because this class lives in a **named module** and the jar
    /// holding the library does not. `Class.getResourceAsStream` on a class in a
    /// named module searches *that module* and never the class path, so the first
    /// call answers only when the library has been packaged into the natives
    /// module itself. The system class loader is what finds an ordinary
    /// `goldberry-natives-<classifier>` jar beside it.
    ///
    /// The second lookup was missing, and the gap was invisible: every
    /// module-path run in this repository points at a locally built library with
    /// `-Dgoldberry.native.library`, so nothing ever took this branch on the
    /// module path. A native image does — it carries the classifier jar's
    /// resource and has no file to point at — which is where it surfaced
    /// ([ADR-0159](../../../../../../book/src/adr/0159-a-native-image-carries-its-own-library.md)).
    ///
    /// @param resource an absolute resource name, leading slash and all
    /// @return the open stream, or null when neither lookup finds it
    private static InputStream openClassifierResource(String resource) {
        var own = NativeLibrary.class.getResourceAsStream(resource);
        if (own != null) {
            return own;
        }
        // Without the leading slash: a ClassLoader resource name is always
        // absolute, and one that starts with `/` matches nothing — silently.
        return ClassLoader.getSystemResourceAsStream(resource.substring(1));
    }

    public SymbolLookup lookup() {
        return lookup;
    }

    public NativePlatform platform() {
        return platform;
    }

    /// The file the library was loaded from.
    public Path path() {
        return path;
    }

    /// Whether the library is present without attempting to load it.
    ///
    /// Lets tests that need real native code skip cleanly on a machine where the
    /// superbuild has not run, rather than failing the build.
    public static boolean isAvailable() {
        var explicit = System.getProperty(LIBRARY_PATH_PROPERTY);
        if (explicit != null && Files.isRegularFile(Path.of(explicit))) {
            return true;
        }
        try {
            // The same two lookups `load` does, or this answers "no" for a library
            // that is there -- which is what it did for a module-path run before
            // the system-class-loader fallback existed.
            try (var in = openClassifierResource(resourcePath(NativePlatform.current()))) {
                return in != null;
            } catch (IOException e) {
                return false;
            }
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    // Restricted: opening a native library is exactly what this module exists to
    // do. Suppressed per call site rather than module-wide, so an unintended new
    // crossing still shows up as a build failure.
    @SuppressWarnings("restricted")
    private static NativeLibrary load() {
        var platform = NativePlatform.current();
        var explicit = Optional.ofNullable(System.getProperty(LIBRARY_PATH_PROPERTY)).map(Path::of);

        var libraryPath = explicit.orElseGet(() -> extractFromClasspath(platform));
        if (!Files.isRegularFile(libraryPath)) {
            throw new UnsatisfiedLinkError(
                    "libgoldberry not found at " + libraryPath
                            + " (set -D" + LIBRARY_PATH_PROPERTY + " to override)");
        }

        // Arena.global(): the library stays mapped for the life of the JVM.
        // Unloading it would invalidate every downcall handle in the toolkit,
        // so this is deliberately not scoped.
        var lookup = Startup.time(
                "libgoldberry mapped",
                () -> SymbolLookup.libraryLookup(libraryPath, Arena.global()));
        // The first question asked of any bug report that starts "it works on my
        // machine": which library, from where.
        LOG.info("loaded libgoldberry for {} from {}", platform.classifier(), libraryPath);
        return new NativeLibrary(lookup, platform, libraryPath);
    }

    private static Path extractFromClasspath(NativePlatform platform) {
        var resource = resourcePath(platform);
        try (InputStream in = openClassifierResource(resource)) {
            if (in == null) {
                throw new UnsatisfiedLinkError(
                        "No libgoldberry for " + platform.classifier() + " on the classpath"
                                + " (expected resource " + resource + ")."
                                + " Add the goldberry-natives-" + platform.classifier()
                                + " artifact, or set -D" + LIBRARY_PATH_PROPERTY + ".");
            }
            // A shared library must be a real file to be dlopen-ed; it cannot be
            // loaded from inside a jar.
            var target = Files.createTempDirectory("goldberry-natives")
                    .resolve(platform.libraryFileName());
            LOG.debug("unpacking {} to {}", resource, target);
            Startup.mark("unpacking libgoldberry from the classifier jar");
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            // The directory **before** the file it contains, because
            // `deleteOnExit` runs its queue in reverse order of registration --
            // so registering the file first means the directory is attempted
            // first, fails because it is not empty, and is left behind. An empty
            // directory per run is not much, and it is per run forever.
            target.getParent().toFile().deleteOnExit();
            target.toFile().deleteOnExit();
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to unpack " + resource, e);
        }
    }
}
