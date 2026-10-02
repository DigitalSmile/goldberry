package dev.goldberry.media.ffi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.ffi.calls.AvCodecCalls;
import dev.goldberry.media.ffi.calls.AvFormatCalls;
import dev.goldberry.media.ffi.calls.AvUtilCalls;
import dev.goldberry.media.ffi.calls.SwResampleCalls;
import dev.goldberry.media.ffi.calls.SwScaleCalls;

/// Finds, loads and checks FFmpeg's five libraries, once per process.
///
/// ## Where they come from
///
/// 1. `-Dgoldberry.media.libdir=<dir>`. The LGPL says a user may replace the
///    library, and this is how. The build's tests
///    also use it to point at what `:media:ffmpegBuild` just made.
/// 2. `goldberry-media`'s `ffmpeg-<classifier>` jar on the class or module
///    path, which an application names among its dependencies. Its libraries are
///    unpacked into a temporary directory, because a shared library has to be a
///    file to be loaded.
///
/// Either place holds the five libraries under the names the other libraries link
/// against ([FfmpegPlatform#fileName]) and the probe's
/// [`ffmpeg-layout.properties`][FfmpegLayout#FILE_NAME].
///
/// ## What is checked, and when
///
/// No struct is touched until all of this has passed:
///
/// 1. every library loads, in dependency order ([FfmpegLibrary]);
/// 2. each library's `*_version()` major equals its pinned major;
/// 3. the layout file describes those majors, and every struct and field in it
///    agrees with [FfmpegStructs] ([FfmpegLayoutCheck]);
/// 4. every constant the Engine reads is in the layout file.
///
/// A failure is kept, not thrown. Missing FFmpeg is an ordinary state for an
/// application that has not added the natives jar, so [#isAvailable()] answers
/// false, and [#get()] throws a
/// [MediaException] holding [MediaError.NativesUnavailable], which says what was
/// missing or wrong.
public final class FfmpegLibraries {

    /// Names a directory to load the libraries from, instead of the natives jar.
    public static final String LIBRARY_DIRECTORY_PROPERTY = "goldberry.media.libdir";

    /// Set to `true` to let FFmpeg's own warnings through to stderr. Off, FFmpeg is
    /// silent and every failure reaches the application as a [MediaError].
    public static final String FFMPEG_LOG_PROPERTY = "goldberry.media.ffmpegLog";

    private static final Logger LOG = Logs.of(FfmpegLibraries.class);

    /// Loaded, or why not.
    sealed interface State {
        /// @param ffmpeg the checked libraries
        record Loaded(Ffmpeg ffmpeg) implements State {}

        /// @param reason what was missing or wrong, for [MediaError.NativesUnavailable]
        record Unavailable(String reason) implements State {}
    }

    private static final class Holder {
        private static final State STATE = load();
    }

    private FfmpegLibraries() {}

    /// The loaded libraries, loading them on the first call.
    ///
    /// @throws MediaException holding [MediaError.NativesUnavailable] when they
    ///                        cannot be loaded or fail a check
    public static Ffmpeg get() {
        return switch (Holder.STATE) {
            case State.Loaded(var ffmpeg) -> ffmpeg;
            case State.Unavailable(var reason) -> throw new MediaException(new MediaError.NativesUnavailable(reason));
        };
    }

    /// Whether FFmpeg loaded and passed every check. Loads it on the first call.
    public static boolean isAvailable() {
        return Holder.STATE instanceof State.Loaded;
    }

    /// Why FFmpeg is unavailable, or empty when it loaded.
    public static Optional<String> unavailableReason() {
        return Holder.STATE instanceof State.Unavailable(var reason) ? Optional.of(reason) : Optional.empty();
    }

    private static State load() {
        FfmpegPlatform platform;
        try {
            platform = FfmpegPlatform.current();
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
                return new State.Unavailable("no FFmpeg for " + platform.classifier()
                        + " on the class path; add dev.goldberry:goldberry-media::ffmpeg-"
                        + platform.classifier() + ", or set -D" + LIBRARY_DIRECTORY_PROPERTY + "=<dir>");
            }
            directory = unpacked;
        }
        var state = load(directory, platform);
        switch (state) {
            case State.Loaded _ -> LOG.info("loaded FFmpeg for {} from {}", platform.classifier(), directory);
            case State.Unavailable(var reason) -> LOG.info("FFmpeg is not available: {}", reason);
        }
        return state;
    }

    /// Loads and checks the libraries in `directory`.
    ///
    /// Package-private for the tests, which point it at the superbuild's output and
    /// at directories that are wrong in chosen ways.
    @SuppressWarnings("restricted")
    static State load(Path directory, FfmpegPlatform platform) {
        var layoutFile = directory.resolve(FfmpegLayout.FILE_NAME);
        if (!Files.isRegularFile(layoutFile)) {
            return new State.Unavailable("no " + FfmpegLayout.FILE_NAME + " in " + directory);
        }
        FfmpegLayout layout;
        try (var reader = Files.newBufferedReader(layoutFile, StandardCharsets.UTF_8)) {
            layout = FfmpegLayout.parse(reader);
        } catch (IOException | IllegalArgumentException e) {
            return new State.Unavailable("unreadable " + layoutFile + ": " + reason(e));
        }

        // 1. Load, in dependency order. Arena.global(): unloading would invalidate
        //    every handle bound against the library, and they live as long as the
        //    process does.
        var lookups = new EnumMap<FfmpegLibrary, SymbolLookup>(FfmpegLibrary.class);
        for (var library : FfmpegLibrary.values()) {
            var file = directory.resolve(platform.fileName(library));
            if (!Files.isRegularFile(file)) {
                return new State.Unavailable("no " + file.getFileName() + " in " + directory);
            }
            try {
                lookups.put(library, SymbolLookup.libraryLookup(file, Arena.global()));
            } catch (IllegalArgumentException e) {
                return new State.Unavailable(file + " would not load: " + reason(e));
            }
        }

        // 2. Majors, before anything reads a struct.
        AvUtilCalls util;
        AvFormatCalls format;
        AvCodecCalls codec;
        SwResampleCalls swResample;
        SwScaleCalls swScale;
        try {
            util = AvUtilCalls.bind(lookup(lookups, FfmpegLibrary.AVUTIL));
            format = AvFormatCalls.bind(lookup(lookups, FfmpegLibrary.AVFORMAT));
            codec = AvCodecCalls.bind(lookup(lookups, FfmpegLibrary.AVCODEC));
            swResample = SwResampleCalls.bind(lookup(lookups, FfmpegLibrary.SWRESAMPLE));
            swScale = SwScaleCalls.bind(lookup(lookups, FfmpegLibrary.SWSCALE));
        } catch (UnsatisfiedLinkError e) {
            return new State.Unavailable(reason(e));
        }
        var versions = Map.of(
                FfmpegLibrary.AVUTIL, util.version().call(),
                FfmpegLibrary.SWRESAMPLE, swResample.version().call(),
                FfmpegLibrary.SWSCALE, swScale.version().call(),
                FfmpegLibrary.AVCODEC, codec.version().call(),
                FfmpegLibrary.AVFORMAT, format.version().call());
        var wrongVersions = versionMismatches(versions);
        if (!wrongVersions.isEmpty()) {
            return new State.Unavailable(String.join("; ", wrongVersions));
        }

        // 3. Layouts.
        var wrongLayouts = FfmpegLayoutCheck.verify(layout, FfmpegStructs.ALL);
        if (!wrongLayouts.isEmpty()) {
            return new State.Unavailable(
                    "the FFmpeg structs do not match these bindings:\n  " + String.join("\n  ", wrongLayouts));
        }

        // 4. Constants.
        FfmpegConstants constants;
        try {
            constants = FfmpegConstants.from(layout);
        } catch (IllegalArgumentException e) {
            return new State.Unavailable(reason(e));
        }

        util.logSetLevel()
                .call(Boolean.getBoolean(FFMPEG_LOG_PROPERTY) ? constants.logWarning() : constants.logQuiet());
        return new State.Loaded(new Ffmpeg(util, format, codec, swResample, swScale, constants, directory));
    }

    private static SymbolLookup lookup(Map<FfmpegLibrary, SymbolLookup> lookups, FfmpegLibrary library) {
        return Objects.requireNonNull(lookups.get(library), library.stem());
    }

    private static String reason(Throwable e) {
        return Objects.requireNonNullElse(e.getMessage(), e.toString());
    }

    /// One sentence per library whose runtime major is not the pinned one.
    static List<String> versionMismatches(Map<FfmpegLibrary, Integer> versions) {
        var problems = new ArrayList<String>();
        for (var library : FfmpegLibrary.values()) {
            var version = versions.get(library);
            if (version == null) {
                problems.add("no version for lib" + library.stem());
            } else if (FfmpegLibrary.majorOf(version) != library.pinnedMajor()) {
                problems.add("lib" + library.stem() + " is " + FfmpegLibrary.describe(version) + ", expected major "
                        + library.pinnedMajor());
            }
        }
        return problems;
    }

    /// Copies the natives jar's files for `platform` into a new temporary
    /// directory, or answers null when the jar is not there.
    private static @Nullable Path unpack(FfmpegPlatform platform) {
        var base = platform.resourceDirectory();
        var names = new ArrayList<String>();
        names.add(FfmpegLayout.FILE_NAME);
        for (var library : FfmpegLibrary.values()) {
            names.add(platform.fileName(library));
        }
        try (var probe = open(base + FfmpegLayout.FILE_NAME)) {
            if (probe == null) {
                return null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            var directory = Files.createTempDirectory("goldberry-ffmpeg");
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
            throw new UncheckedIOException("could not unpack FFmpeg for " + platform.classifier(), e);
        }
    }

    /// The two lookups `NativeLibrary` makes, for the same reason: a class in a
    /// named module searches only its own module, and the natives jar is a
    /// different one.
    private static @Nullable InputStream open(String resource) {
        var own = FfmpegLibraries.class.getResourceAsStream(resource);
        return own != null ? own : ClassLoader.getSystemResourceAsStream(resource.substring(1));
    }
}
