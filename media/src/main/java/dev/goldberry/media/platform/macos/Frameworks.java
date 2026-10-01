package dev.goldberry.media.platform.macos;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// The five frameworks, bound: opened once per process, the first time a
/// provider is asked about a track.
///
/// A provider is constructed by `ServiceLoader` on every operating system, so
/// nothing is opened until a provider is actually asked. On anything but macOS,
/// and on a Mac whose frameworks lack a function the bindings need, the state is
/// [Unavailable] and every provider supports nothing.
///
/// @param cf Core Foundation
/// @param cm Core Media
/// @param cv Core Video
/// @param vt VideoToolbox
/// @param at AudioToolbox
record Frameworks(CoreFoundation cf, CoreMedia cm, CoreVideo cv, VideoToolbox vt, AudioToolbox at) {

    private static final Logger LOG = Logs.of(Frameworks.class);

    Frameworks {
        Objects.requireNonNull(cf, "cf");
        Objects.requireNonNull(cm, "cm");
        Objects.requireNonNull(cv, "cv");
        Objects.requireNonNull(vt, "vt");
        Objects.requireNonNull(at, "at");
    }

    /// Whether the frameworks were bound, and why not.
    sealed interface State {

        /// Bound, and ready.
        record Loaded(Frameworks frameworks) implements State {}

        /// Not bound: not macOS, or a macOS without what the bindings need.
        record Unavailable(String reason) implements State {}
    }

    /// The frameworks, once bound, or empty when they cannot be.
    static Optional<Frameworks> get() {
        return Holder.STATE instanceof State.Loaded(var frameworks) ? Optional.of(frameworks) : Optional.empty();
    }

    /// Why the frameworks are not available, or empty when they are.
    static Optional<String> unavailableReason() {
        return Holder.STATE instanceof State.Unavailable(var reason) ? Optional.of(reason) : Optional.empty();
    }

    /// Whether `osName`, the `os.name` property, is macOS.
    static boolean isMac(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("mac");
    }

    /// Opens and binds the frameworks. Package-private for the tests, which see
    /// what an ordinary run sees through [#get].
    static State load(String osName) {
        if (!isMac(osName)) {
            return new State.Unavailable("the system decoders are macOS's, and this is " + osName);
        }
        try {
            var cf = new CoreFoundation(Framework.CORE_FOUNDATION.open());
            var frameworks = new Frameworks(
                    cf,
                    new CoreMedia(Framework.CORE_MEDIA.open(), cf),
                    new CoreVideo(Framework.CORE_VIDEO.open(), cf),
                    new VideoToolbox(Framework.VIDEO_TOOLBOX.open()),
                    new AudioToolbox(Framework.AUDIO_TOOLBOX.open()));
            return new State.Loaded(frameworks);
        } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
            return new State.Unavailable("the system frameworks could not be bound: " + e.getMessage());
        }
    }

    /// The lazy holder idiom: the class is initialised, and the frameworks opened,
    /// on the first call that reads [#STATE].
    private static final class Holder {
        static final State STATE = loadAndLog();

        private static State loadAndLog() {
            var state = load(System.getProperty("os.name", ""));
            switch (state) {
                case State.Loaded _ -> LOG.info("bound VideoToolbox and AudioToolbox for the platform decoders");
                case State.Unavailable(var reason) -> LOG.debug("no platform decoders: {}", reason);
            }
            return state;
        }
    }
}
