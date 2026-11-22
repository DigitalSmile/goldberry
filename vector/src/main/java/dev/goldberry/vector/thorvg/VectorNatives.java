package dev.goldberry.vector.thorvg;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.vector.thorvg.calls.CanvasCalls;
import dev.goldberry.vector.thorvg.calls.EngineCalls;
import dev.goldberry.vector.thorvg.calls.PictureCalls;
import dev.goldberry.vector.thorvg.calls.VectorCalls;

/// Finds, loads and checks `libgoldberry-vector`, once per process.
///
/// The library is the one in the directory `-Dgoldberry.vector.libdir` names,
/// or else the one in goldberry-vector's `natives-<target>` jar on the class
/// path, unpacked into a temporary directory deleted on exit. Before anything
/// is bound, `vector.properties` beside it and the library itself must both
/// say the ABI these bindings are written against. A library that is missing
/// or wrong is not an exception here: it is a reason, which
/// `VectorCapabilities` reports and a draw raises.
public final class VectorNatives {

    /// Names a directory to load the library from, instead of the natives jar.
    public static final String LIBRARY_DIRECTORY_PROPERTY = "goldberry.vector.libdir";

    private static final Logger LOG = Logs.of(VectorNatives.class);

    /// Loaded, or why not.
    public sealed interface State {
        /// @param library the checked library
        record Loaded(VectorLibrary library) implements State {}

        /// @param reason what was missing or wrong
        record Unavailable(String reason) implements State {}
    }

    private static final class Holder {
        private static final State STATE = load();
    }

    private VectorNatives() {}

    /// The loaded library, or why there is none. Loads it on the first call.
    public static State state() {
        return Holder.STATE;
    }

    /// The loaded library, loading it on the first call.
    ///
    /// @throws IllegalStateException naming the reason when it cannot be loaded
    public static VectorLibrary get() {
        return switch (Holder.STATE) {
            case State.Loaded(var library) -> library;
            case State.Unavailable(var reason) ->
                throw new IllegalStateException("libgoldberry-vector is not available: " + reason);
        };
    }

    /// Why the library is unavailable, or empty when it loaded.
    public static Optional<String> unavailableReason() {
        return Holder.STATE instanceof State.Unavailable(var reason) ? Optional.of(reason) : Optional.empty();
    }

    private static State load() {
        VectorPlatform platform;
        try {
            platform = VectorPlatform.current();
        } catch (UnsupportedOperationException e) {
            return new State.Unavailable(reason(e));
        }
        var explicit = System.getProperty(LIBRARY_DIRECTORY_PROPERTY);
        Path directory;
        if (explicit != null) {
            directory = Path.of(explicit);
        } else {
            var unpacked = unpack(platform);
            if (unpacked == null) {
                return new State.Unavailable("no vector natives for " + platform.target()
                        + " on the class path; add dev.goldberry:goldberry-vector::" + platform.classifier()
                        + ", or set -D" + LIBRARY_DIRECTORY_PROPERTY + "=<dir>");
            }
            directory = unpacked;
        }
        var state = load(directory, platform);
        switch (state) {
            case State.Loaded(var library) ->
                LOG.info(
                        "loaded {} for {} from {}: {}",
                        platform.fileName(),
                        platform.target(),
                        directory,
                        library.versions());
            case State.Unavailable(var reason) -> LOG.info("the vector natives are not available: {}", reason);
        }
        return state;
    }

    /// Loads and checks the library in `directory`.
    ///
    /// Package-private for the tests, which point it at the superbuild's output
    /// and at directories that are wrong in chosen ways.
    @SuppressWarnings("restricted")
    static State load(Path directory, VectorPlatform platform) {
        var propertiesFile = directory.resolve(BuildProperties.FILE_NAME);
        if (!Files.isRegularFile(propertiesFile)) {
            return new State.Unavailable("no " + BuildProperties.FILE_NAME + " in " + directory);
        }
        BuildProperties build;
        try (var reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
            build = BuildProperties.parse(reader);
        } catch (IOException | IllegalArgumentException e) {
            return new State.Unavailable("unreadable " + propertiesFile + ": " + reason(e));
        }
        if (build.abi() != VectorLibrary.ABI) {
            return new State.Unavailable(propertiesFile + " describes ABI " + build.abi()
                    + ", but these bindings are written against ABI " + VectorLibrary.ABI);
        }
        if (!build.target().equals(platform.target())) {
            return new State.Unavailable(
                    propertiesFile + " describes a library for " + build.target() + ", not " + platform.target());
        }

        var file = directory.resolve(platform.fileName());
        if (!Files.isRegularFile(file)) {
            return new State.Unavailable("no " + file.getFileName() + " in " + directory);
        }
        SymbolLookup lookup;
        try {
            lookup = SymbolLookup.libraryLookup(file, Arena.global());
        } catch (IllegalArgumentException e) {
            return new State.Unavailable(file + " would not load: " + reason(e));
        }

        try {
            var vector = VectorCalls.bind(lookup);
            var abi = vector.abi().call();
            if (abi != VectorLibrary.ABI) {
                return new State.Unavailable(file.getFileName() + " is ABI " + abi
                        + ", but these bindings are written against ABI " + VectorLibrary.ABI
                        + "; rebuild it with vector/src/main/cmake of this version");
            }
            var versions = vector.versions().call().reinterpret(Long.MAX_VALUE).getString(0);
            return new State.Loaded(new VectorLibrary(
                    vector,
                    EngineCalls.bind(lookup),
                    CanvasCalls.bind(lookup),
                    PictureCalls.bind(lookup),
                    build,
                    versions,
                    directory));
        } catch (UnsatisfiedLinkError e) {
            return new State.Unavailable(reason(e));
        }
    }

    private static String reason(Throwable e) {
        return Objects.requireNonNullElse(e.getMessage(), e.toString());
    }

    /// Copies the natives jar's files for `platform` into a new temporary
    /// directory, or answers null when the jar is not there.
    private static @Nullable Path unpack(VectorPlatform platform) {
        var base = platform.resourceDirectory();
        try (var probe = open(base + BuildProperties.FILE_NAME)) {
            if (probe == null) {
                return null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            var directory = Files.createTempDirectory("goldberry-vector");
            // The directory first, so that it is deleted last: `deleteOnExit`
            // runs in reverse order of registration.
            directory.toFile().deleteOnExit();
            for (var name : List.of(BuildProperties.FILE_NAME, platform.fileName())) {
                try (var in = open(base + name)) {
                    if (in == null) {
                        throw new IOException("the natives jar has no " + name);
                    }
                    var target = directory.resolve(name);
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    target.toFile().deleteOnExit();
                }
            }
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException("could not unpack the vector natives for " + platform.target(), e);
        }
    }

    /// The two lookups `:media`'s loader makes, for the same reason: a class in
    /// a named module searches only its own module, and the natives jar is a
    /// different one. The caller closes the stream.
    @SuppressWarnings("resource")
    private static @Nullable InputStream open(String resource) {
        var own = VectorNatives.class.getResourceAsStream(resource);
        return own != null ? own : ClassLoader.getSystemResourceAsStream(resource.substring(1));
    }
}
