package io.github.digitalsmile.goldberry.media.platform.windows;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;

/// Media Foundation, bound: its two libraries opened and Media Foundation
/// started once per process, the first time a provider is asked about a track.
///
/// A provider is constructed by `ServiceLoader` on every operating system, so
/// nothing is opened until a provider is actually asked. On anything but
/// Windows, and on a Windows without Media Foundation (the N editions, until the
/// Media Feature Pack is installed), the state is [State.Unavailable] and every
/// provider supports nothing.
///
/// COM has to be initialised on every thread that calls into it, and a decoder
/// is opened on one thread and used on another: [#enterThread] does it, once
/// per thread, and every decoder method calls it first.
///
/// @param mfplat `mfplat.dll`
/// @param ole32  `ole32.dll`
record MediaFoundation(MfPlat mfplat, Ole32 ole32) {

    private static final Logger LOG = Logs.of(MediaFoundation.class);

    /// Whether this thread has joined COM's multi-threaded apartment.
    private static final ThreadLocal<Boolean> ENTERED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /// Whether a decoder takes each input subtype, found once per process.
    private static final ConcurrentMap<Guid, Boolean> DECODERS = new ConcurrentHashMap<>();

    MediaFoundation {
        Objects.requireNonNull(mfplat, "mfplat");
        Objects.requireNonNull(ole32, "ole32");
    }

    /// Whether Media Foundation was bound, and why not.
    sealed interface State {

        /// Bound, started, and ready.
        record Loaded(MediaFoundation mediaFoundation) implements State {}

        /// Not bound: not Windows, or a Windows without Media Foundation.
        record Unavailable(String reason) implements State {}
    }

    /// Media Foundation, once bound, or empty when it cannot be.
    static Optional<MediaFoundation> get() {
        return Holder.STATE instanceof State.Loaded(var mf) ? Optional.of(mf) : Optional.empty();
    }

    /// Why Media Foundation is not available, or empty when it is.
    static Optional<String> unavailableReason() {
        return Holder.STATE instanceof State.Unavailable(var reason) ? Optional.of(reason) : Optional.empty();
    }

    /// Whether `osName`, the `os.name` property, is Windows.
    static boolean isWindows(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /// Opens the libraries and starts Media Foundation. Package-private for the
    /// tests, which see what an ordinary run sees through [#get].
    static State load(String osName) {
        if (!isWindows(osName)) {
            return new State.Unavailable("the Media Foundation decoders are Windows's, and this is " + osName);
        }
        try {
            var ole32 = new Ole32(WindowsLibrary.OLE32.open());
            var mf = new MediaFoundation(new MfPlat(WindowsLibrary.MFPLAT.open(), ole32), ole32);
            mf.enterThread();
            mf.mfplat().startup();
            return new State.Loaded(mf);
        } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
            return new State.Unavailable("Media Foundation could not be bound: " + e.getMessage());
        } catch (HResult.Failure e) {
            return new State.Unavailable("Media Foundation did not start: " + e.getMessage());
        }
    }

    /// Joins the calling thread to COM's multi-threaded apartment, the first
    /// time it calls. Never undone: the thread stays joined until it ends.
    void enterThread() {
        if (!ENTERED.get()) {
            ole32.initializeMultithreaded();
            ENTERED.set(Boolean.TRUE);
        }
    }

    /// Whether the system has a decoder in `category` for `major` and `subtype`,
    /// asked once per subtype. HEVC's, for one, is an optional download (HEVC
    /// Video Extensions), so a Windows without it declines HEVC here rather than
    /// failing at open.
    boolean hasDecoder(Guid category, Guid major, Guid subtype) {
        return DECODERS.computeIfAbsent(subtype, _ -> {
            try {
                enterThread();
                var activates = mfplat.enumerate(category, major, subtype);
                activates.forEach(Com::release);
                return !activates.isEmpty();
            } catch (HResult.Failure e) {
                LOG.debug("MFTEnumEx for {} failed", subtype, e);
                return false;
            }
        });
    }

    /// The lazy holder idiom: the class is initialised, and Media Foundation
    /// started, on the first call that reads [#STATE].
    private static final class Holder {
        static final State STATE = loadAndLog();

        private static State loadAndLog() {
            var state = load(System.getProperty("os.name", ""));
            switch (state) {
                case State.Loaded _ -> LOG.info("started Media Foundation for the platform decoders");
                case State.Unavailable(var reason) -> LOG.debug("no Media Foundation decoders: {}", reason);
            }
            return state;
        }
    }
}
