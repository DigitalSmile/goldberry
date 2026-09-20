package io.github.digitalsmile.goldberry.platform;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.platform.NativeCapabilities;
import io.github.digitalsmile.goldberry.natives.platform.NativeCapability;
import io.github.digitalsmile.goldberry.render.web.WebViewEngine;

/// What the native library this process loaded can actually do.
///
/// [io.github.digitalsmile.goldberry.Goldberry#capabilities()] is the door; this
/// is where the answer comes from, and the one place the toolkit translates
/// `:natives`' word for it into its own ([ADR-0174]).
///
/// Asked once and kept. The answer is a compile-time constant on the other side
/// of the boundary — a build either has the platform integration or it does not,
/// and nothing that happens afterwards changes which.
public final class PlatformCapabilities {

    private static final Logger LOG = Logs.of(PlatformCapabilities.class);

    private static final class Holder {
        private static final Set<Capability> INSTANCE = read();
    }

    private PlatformCapabilities() {}

    /// What this build of the toolkit can do, read from the loaded library on
    /// first call.
    ///
    /// Empty when there is no native library at all — a Java-only run, or a test
    /// on the headless backend. That is the same answer as a library that can do
    /// none of it, and deliberately so: both mean an application must not expect
    /// any of these to work here.
    public static Set<Capability> get() {
        return Holder.INSTANCE;
    }

    /// Whether `capability` is one this build has.
    ///
    /// @param capability the capability to ask about
    /// @return whether it is available
    public static boolean has(Capability capability) {
        return get().contains(capability);
    }

    /// The toolkit's word for one of `:natives`' capabilities.
    ///
    /// An exhaustive switch rather than a name lookup: the two enums are
    /// deliberately separate types, and the compiler is what notices when a
    /// capability is added to one and not the other.
    ///
    /// @param capability what the native layer reported
    /// @return the public capability it means
    static Capability translate(NativeCapability capability) {
        return switch (capability) {
            case SYSTEM_THEME -> Capability.SYSTEM_THEME;
            case INPUT_METHOD -> Capability.INPUT_METHOD;
            case DEVICE_HOTPLUG -> Capability.DEVICE_HOTPLUG;
            case FILE_DIALOG -> Capability.FILE_DIALOG;
            case SCREENSAVER_INHIBIT -> Capability.SCREENSAVER_INHIBIT;
            case WINDOW_DECORATIONS -> Capability.WINDOW_DECORATIONS;
            case WAYLAND -> Capability.WAYLAND;
        };
    }

    /// Translates a whole set, preserving declaration order.
    ///
    /// @param capabilities what the native layer reported
    /// @return the toolkit's own words for them
    static Set<Capability> translate(Set<NativeCapability> capabilities) {
        var translated = EnumSet.noneOf(Capability.class);
        for (var capability : capabilities) {
            translated.add(translate(capability));
        }
        // An unmodifiable view of an EnumSet rather than `Set.copyOf`, which
        // would hash the constants and hand back an order that reads at random in
        // the log line below.
        return Collections.unmodifiableSet(translated);
    }

    /// Whether a web page can be opened, which is **not** a bit in
    /// `libgoldberry`.
    ///
    /// Every other capability here is a compile-time constant on the other side of
    /// the boundary. [Capability#WEB_VIEW] is the presence of a second library —
    /// `libgoldberry-webview`, which exists so that GTK and WebKit are not
    /// load-time dependencies of the toolkit — and answering it means trying to
    /// open that library, which is why it is asked here, once, rather than on
    /// every call.
    ///
    /// Package-private and taken as a parameter by [#read] so a test can ask what
    /// the set looks like both ways without a native library of either kind.
    static Set<Capability> read(Set<NativeCapability> native_, boolean webView) {
        var capabilities = EnumSet.noneOf(Capability.class);
        capabilities.addAll(translate(native_));
        if (webView) {
            capabilities.add(Capability.WEB_VIEW);
        }
        return Collections.unmodifiableSet(capabilities);
    }

    /// Whether a page can be opened, as a question that cannot throw.
    ///
    /// [WebViewEngine#isAvailable()] is built not to, and this is the belt to
    /// that pair of braces: it is reached from
    /// [io.github.digitalsmile.goldberry.Goldberry#capabilities()], which
    /// promises an answer before any window is open and on a machine with no
    /// native library at all. An optional feature must not be able to break the
    /// call that asks which features exist.
    private static boolean hasWebView() {
        try {
            return WebViewEngine.isAvailable();
        } catch (LinkageError | RuntimeException e) {
            LOG.debug("could not ask whether a web view is available: {}", e.toString());
            return false;
        }
    }

    private static Set<Capability> read() {
        // Asked ONCE, outside the try, and never again on the way out of it. A
        // class initialiser that throws is poisoned for the life of the JVM: the
        // first call reports the real cause and every later one gets a bare
        // `NoClassDefFoundError`. So a second ask in the catch below would turn a
        // handled absence into an unhandled error -- which is what it did.
        var webView = hasWebView();
        try {
            var capabilities = read(NativeCapabilities.get(), webView);
            // The line that answers "why is this application in the wrong theme
            // on my machine" without rebuilding anything. `debug` rather than
            // `info`: a complete build has nothing to report here, and the backend
            // warns by itself about the one absence that matters to it.
            LOG.debug("platform capabilities: {}", capabilities);
            return capabilities;
        } catch (LinkageError e) {
            // No library, which is an ordinary state: `./gradlew build` skips the
            // superbuild, and the headless backend needs none of this. Not a
            // warning, because nothing is wrong.
            //
            // `LinkageError` rather than `UnsatisfiedLinkError`: the same absence
            // arrives as either, depending on who asked first. A library too old
            // to export the function lands here too, and reports the same thing it
            // can do -- none of this, as far as anything can tell.
            LOG.debug("no platform capabilities from libgoldberry: {}", e.getMessage());
            // Not `Set.of()`. The two libraries are independent — a web view is
            // the one thing here that does not come out of `libgoldberry` — so a
            // process that has one and not the other can still open a page, and
            // saying otherwise would be the "asked and was told nothing" mistake
            // this class exists to avoid.
            return read(Set.of(), webView);
        }
    }
}
