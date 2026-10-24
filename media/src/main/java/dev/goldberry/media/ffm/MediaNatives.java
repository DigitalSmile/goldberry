package dev.goldberry.media.ffm;

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
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.ffm.calls.Dav1dCalls;
import dev.goldberry.media.ffm.calls.FlacCalls;
import dev.goldberry.media.ffm.calls.MediaCalls;
import dev.goldberry.media.ffm.calls.Mp3Calls;
import dev.goldberry.media.ffm.calls.OpusCalls;
import dev.goldberry.media.ffm.calls.VorbisCalls;
import dev.goldberry.media.ffm.calls.VpxCalls;
import dev.goldberry.media.ffm.calls.YuvCalls;

/// Loads `libgoldberry-media` once per process, and checks it before anything
/// native is called.
///
/// Where it is found:
///
/// 1. `-Dgoldberry.media.libdir=<dir>`: a directory holding the library and its
///    `media-layout.properties`. What the tests set, pointing at the
///    superbuild's install directory.
/// 2. Otherwise the natives jar on the class or module path,
///    `goldberry-media`'s `natives-<target>` classifier, whose files are
///    unpacked into a temporary directory first.
///
/// What is checked, in order, each with a reason a person can act on:
///
/// 1. the layout file is there and readable;
/// 2. the library loads;
/// 3. `goldberry_media_abi()` answers [MediaLibrary#ABI], and so does the layout
///    file's `abi=` line;
/// 4. every struct in [NativeStructs] agrees with the probe's record;
/// 5. every constant in [NativeConstants] was reported.
///
/// A failure of any of them is **not** fatal to the application: the module
/// answers [MediaError.NativesUnavailable] from every open, and plays nothing,
/// rather than crashing on a struct read at an offset that belongs to another
/// build. `Arena.global()` holds the library: unloading would invalidate every
/// handle bound against it, and they live as long as the process does.
public final class MediaNatives {

    /// Names a directory to load the library from, instead of the natives jar.
    public static final String LIBRARY_DIRECTORY_PROPERTY = "goldberry.media.libdir";

    private static final Logger LOG = Logs.of(MediaNatives.class);

    /// Loaded, or why not.
    sealed interface State {
        /// @param library the checked library
        record Loaded(MediaLibrary library) implements State {}

        /// @param reason what was missing or wrong, for [MediaError.NativesUnavailable]
        record Unavailable(String reason) implements State {}
    }

    private static final class Holder {
        private static final State STATE = load();
    }

    private MediaNatives() {}

    /// The loaded library, loading it on the first call.
    ///
    /// @throws MediaException holding [MediaError.NativesUnavailable] when it
    ///                        cannot be loaded or fails a check
    public static MediaLibrary get() {
        return switch (Holder.STATE) {
            case State.Loaded(var library) -> library;
            case State.Unavailable(var reason) -> throw new MediaException(new MediaError.NativesUnavailable(reason));
        };
    }

    /// Whether the library loaded and passed every check. Loads it on the first
    /// call.
    public static boolean isAvailable() {
        return Holder.STATE instanceof State.Loaded;
    }

    /// Why the library is unavailable, or empty when it loaded.
    public static Optional<String> unavailableReason() {
        return Holder.STATE instanceof State.Unavailable(var reason) ? Optional.of(reason) : Optional.empty();
    }

    private static State load() {
        MediaPlatform platform;
        try {
            platform = MediaPlatform.current();
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
                return new State.Unavailable("no media natives for " + platform.target()
                        + " on the class path; add dev.goldberry:goldberry-media::" + platform.classifier()
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
            case State.Unavailable(var reason) -> LOG.info("the media natives are not available: {}", reason);
        }
        return state;
    }

    /// Loads and checks the library in `directory`.
    ///
    /// Package-private for the tests, which point it at the superbuild's output
    /// and at directories that are wrong in chosen ways.
    @SuppressWarnings("restricted")
    static State load(Path directory, MediaPlatform platform) {
        var layoutFile = directory.resolve(NativeLayout.FILE_NAME);
        if (!Files.isRegularFile(layoutFile)) {
            return new State.Unavailable("no " + NativeLayout.FILE_NAME + " in " + directory);
        }
        NativeLayout layout;
        try (var reader = Files.newBufferedReader(layoutFile, StandardCharsets.UTF_8)) {
            layout = NativeLayout.parse(reader);
        } catch (IOException | IllegalArgumentException e) {
            return new State.Unavailable("unreadable " + layoutFile + ": " + reason(e));
        }
        if (layout.abi().isEmpty()) {
            return new State.Unavailable(layoutFile + " does not say which ABI it describes");
        }
        if (layout.abi().getAsInt() != MediaLibrary.ABI) {
            return new State.Unavailable(layoutFile + " describes ABI "
                    + layout.abi().getAsInt() + ", but these bindings are written against ABI " + MediaLibrary.ABI);
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

        MediaCalls media;
        try {
            media = MediaCalls.bind(lookup);
        } catch (UnsatisfiedLinkError e) {
            return new State.Unavailable(reason(e));
        }
        var abi = media.abi().call();
        if (abi != MediaLibrary.ABI) {
            return new State.Unavailable(
                    file.getFileName() + " is ABI " + abi + ", but these bindings are written against ABI "
                            + MediaLibrary.ABI + "; rebuild it with media/src/main/cmake of this version");
        }

        var wrongLayouts = LayoutCheck.verify(layout, NativeStructs.ALL);
        if (!wrongLayouts.isEmpty()) {
            return new State.Unavailable(
                    "the native structs do not match these bindings:\n  " + String.join("\n  ", wrongLayouts));
        }

        NativeConstants constants;
        try {
            constants = NativeConstants.from(layout);
        } catch (IllegalArgumentException e) {
            return new State.Unavailable(reason(e));
        }

        try {
            var library = new MediaLibrary(
                    lookup,
                    media,
                    Dav1dCalls.bind(lookup),
                    VpxCalls.bind(lookup),
                    OpusCalls.bind(lookup),
                    VorbisCalls.bind(lookup),
                    FlacCalls.bind(lookup),
                    YuvCalls.bind(lookup),
                    Mp3Calls.bind(lookup),
                    constants,
                    media.versions().call().reinterpret(Long.MAX_VALUE).getString(0),
                    directory);
            return new State.Loaded(library);
        } catch (UnsatisfiedLinkError e) {
            return new State.Unavailable(reason(e));
        }
    }

    private static String reason(Throwable e) {
        return Objects.requireNonNullElse(e.getMessage(), e.toString());
    }

    /// Copies the natives jar's files for `platform` into a new temporary
    /// directory, or answers null when the jar is not there.
    private static @Nullable Path unpack(MediaPlatform platform) {
        var base = platform.resourceDirectory();
        var names = List.of(NativeLayout.FILE_NAME, platform.fileName());
        try (var probe = open(base + NativeLayout.FILE_NAME)) {
            if (probe == null) {
                return null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            var directory = Files.createTempDirectory("goldberry-media");
            // The directory is registered first so that it is deleted last:
            // `deleteOnExit` runs in reverse order of registration, and a directory
            // that still holds files cannot be deleted (the trap NativeLibrary
            // documents).
            directory.toFile().deleteOnExit();
            for (var name : names) {
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
            throw new UncheckedIOException("could not unpack the media natives for " + platform.target(), e);
        }
    }

    /// The two lookups `NativeLibrary` makes, for the same reason: a class in a
    /// named module searches only its own module, and the natives jar is a
    /// different one.
    private static @Nullable InputStream open(String resource) {
        var own = MediaNatives.class.getResourceAsStream(resource);
        return own != null ? own : ClassLoader.getSystemResourceAsStream(resource.substring(1));
    }
}
