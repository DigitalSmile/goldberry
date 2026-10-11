package dev.goldberry.natives.desktop;

import java.util.Locale;

import dev.goldberry.natives.desktop.calls.MacAccessibility;
import dev.goldberry.natives.desktop.calls.PortalSettings;
import dev.goldberry.natives.desktop.calls.WindowsContrast;

/// Whether the desktop asks for more contrast, forces its own colours, or asks
/// for less transparency, **detected** rather than set.
///
/// The same arrangement as [DesktopMotion], for its reasons: SDL has no call
/// for any of these, so each is a read-only query through FFM against a library
/// the process already has, asked once and kept.
///
/// | | asked | of |
/// |---|---|---|
/// | Linux | `org.freedesktop.appearance` `contrast` | the settings portal, over libdbus |
/// | Windows | `SystemParametersInfoW(SPI_GETHIGHCONTRAST)` | `user32.dll` |
/// | macOS | `NSWorkspace` `accessibilityDisplayShouldIncreaseContrast` and `…ShouldReduceTransparency` | `libobjc` |
///
/// Windows' high-contrast mode is both forced colours and more contrast. The
/// portal says nothing of forced colours or transparency, and Windows nothing
/// of transparency here, so those answer [Setting#UNKNOWN].
///
/// Three system properties override the desktop, for a test and for a user who
/// wants to see a sheet's other branch: [#CONTRAST], [#FORCED_COLORS] and
/// [#TRANSPARENCY], each `true` or `false`.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class DesktopAccessibility {

    /// `true` for more contrast, `false` for none asked.
    public static final String CONTRAST = "goldberry.contrast.more";

    /// `true` for forced colours, `false` for none.
    public static final String FORCED_COLORS = "goldberry.colors.forced";

    /// `true` for less transparency, `false` for none asked.
    public static final String TRANSPARENCY = "goldberry.transparency.reduced";

    private DesktopAccessibility() {}

    private static final class Holder {
        private static final DisplayAccessibility ANSWER = ask();
    }

    /// What the desktop says, asked the first time and kept.
    public static DisplayAccessibility settings() {
        return Holder.ANSWER;
    }

    static DisplayAccessibility ask() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var asked = os.contains("win")
                ? WindowsContrast.read()
                : os.contains("mac") || os.contains("darwin") ? MacAccessibility.read() : linux();
        return new DisplayAccessibility(
                override(CONTRAST, asked.increasedContrast()),
                override(FORCED_COLORS, asked.forcedColors()),
                override(TRANSPARENCY, asked.reducedTransparency()));
    }

    private static Setting override(String property, Setting asked) {
        var value = System.getProperty(property);
        if (value == null) {
            return asked;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "on" -> Setting.ON;
            case "false", "off" -> Setting.OFF;
            default -> asked;
        };
    }

    private static DisplayAccessibility linux() {
        if (!PortalSettings.isAvailable()) {
            return DisplayAccessibility.UNKNOWN;
        }
        // The portal's contrast is an enumeration: 0 is no preference, 1 is
        // high contrast. `read` answers REDUCED for "a preference expressed".
        var contrast = switch (PortalSettings.read("org.freedesktop.appearance", "contrast", false)) {
            case REDUCED -> Setting.ON;
            case FULL -> Setting.OFF;
            case UNKNOWN -> Setting.UNKNOWN;
        };
        return new DisplayAccessibility(contrast, Setting.UNKNOWN, Setting.UNKNOWN);
    }
}
