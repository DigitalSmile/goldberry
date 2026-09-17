package io.github.digitalsmile.goldberry.natives.desktop;

import java.util.Locale;

import io.github.digitalsmile.goldberry.natives.desktop.calls.MacMotion;
import io.github.digitalsmile.goldberry.natives.desktop.calls.PortalSettings;
import io.github.digitalsmile.goldberry.natives.desktop.calls.WindowsMotion;

/// Whether the desktop asks for less movement — §13's reduce-motion switch,
/// **detected** rather than set.
///
/// ## Why this is not an SDL call
///
/// It would be one if SDL had one. `SDL_GetSystemTheme` answers the other
/// preference with a single call on all three platforms, which is exactly why
/// ADR-0322 bound it and did not go to the platform; there is no
/// `SDL_GetReducedMotion` and no request open for one. So this is the three
/// platform integrations that decision was glad to avoid, taken now because the
/// alternative is a switch every application has to find for itself
/// ([ADR-0383]).
///
/// Each of them is a **read-only query through FFM against a library the process
/// already has**: no new native code, nothing added to the superbuild, nothing
/// new to ship.
///
/// | | asked | of |
/// |---|---|---|
/// | Linux | `org.freedesktop.portal.Settings.Read` | the XDG portal, over libdbus |
/// | Windows | `SystemParametersInfoW(SPI_GETCLIENTAREAANIMATION)` | `user32.dll` |
/// | macOS | `NSWorkspace.accessibilityDisplayShouldReduceMotion` | `libobjc` |
///
/// ## Asked once
///
/// The answer is cached for the life of the process, and the reason is the
/// difference between this and the theme: SDL keeps the theme up to date and
/// raises an event when it changes, and nothing here is listening to anything. A
/// desktop that turns reduced motion on mid-session is not obeyed until the
/// application restarts, which is written down rather than hidden — the portal
/// has a `SettingChanged` signal and taking it means a D-Bus main loop, which is
/// a much larger thing than one blocking read.
public final class DesktopMotion {

    /// Overrides the query: `reduce`, `full`, or anything else for "do not ask".
    ///
    /// For a test, for a screenshot, and for an application on a desktop whose
    /// answer is wrong.
    public static final String PROPERTY = "goldberry.motion.reduced";

    private DesktopMotion() {}

    private static final class Holder {
        private static final MotionPreference ANSWER = ask();
    }

    /// What the desktop says, asked once per process.
    public static MotionPreference preference() {
        return Holder.ANSWER;
    }

    /// The question, without the cache — for a test that wants to ask twice.
    static MotionPreference ask() {
        var override = System.getProperty(PROPERTY);
        if (override != null) {
            return switch (override.toLowerCase(Locale.ROOT)) {
                case "reduce", "reduced", "true" -> MotionPreference.REDUCED;
                case "full", "false" -> MotionPreference.FULL;
                default -> MotionPreference.UNKNOWN;
            };
        }
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return WindowsMotion.read();
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return MacMotion.read();
        }
        return linux();
    }

    /// Linux, and its two keys.
    ///
    /// The specified one first: `org.freedesktop.appearance` gained
    /// `reduced-motion` as a `uint32` enumeration, 0 meaning no preference. A
    /// portal that predates it answers nothing, and GNOME's own
    /// `org.gnome.desktop.interface`/`enable-animations` is the same question
    /// asked the other way round — which is why the second call says the boolean
    /// means *full* motion.
    private static MotionPreference linux() {
        if (!PortalSettings.isAvailable()) {
            return MotionPreference.UNKNOWN;
        }
        var specified = PortalSettings.read("org.freedesktop.appearance", "reduced-motion", false);
        if (specified != MotionPreference.UNKNOWN) {
            return specified;
        }
        return PortalSettings.read("org.gnome.desktop.interface", "enable-animations", true);
    }
}
