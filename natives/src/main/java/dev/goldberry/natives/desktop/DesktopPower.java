package dev.goldberry.natives.desktop;

import java.util.Locale;
import java.util.Optional;

import dev.goldberry.natives.desktop.calls.MacLowPower;
import dev.goldberry.natives.desktop.calls.PowerProfiles;
import dev.goldberry.natives.desktop.calls.WindowsPowerSaver;

/// Whether the desktop has been asked to save power: macOS's Low Power Mode,
/// Windows' battery saver, a Linux desktop's power-saver profile.
///
/// The question SDL does not ask. `SDL_GetPowerInfo` says where the power comes
/// from and how much battery is left; whether the user turned the saving mode
/// on is a switch of each platform's own, and like [DesktopMotion] it is three
/// read-only queries against libraries the process already has:
///
/// | | asked | of |
/// |---|---|---|
/// | macOS | `NSProcessInfo.isLowPowerModeEnabled` | `libobjc` |
/// | Windows | `GetSystemPowerStatus`, `SystemStatusFlag` | `kernel32.dll` |
/// | Linux | power-profiles-daemon's `ActiveProfile` | the system bus, over libdbus |
///
/// **Not cached.** The switch is turned during a session, and whoever asks asks
/// again every so often; each answer is one call.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class DesktopPower {

    /// Overrides the query: `on`, `off`, or anything else for "do not ask". For
    /// a test, and for a desktop whose answer is wrong.
    public static final String PROPERTY = "goldberry.power.saving";

    private DesktopPower() {}

    /// Whether the saving mode is on, or empty where the desktop does not say.
    public static Optional<Boolean> savingMode() {
        var override = System.getProperty(PROPERTY);
        if (override != null) {
            return switch (override.toLowerCase(Locale.ROOT)) {
                case "on", "true" -> Optional.of(true);
                case "off", "false" -> Optional.of(false);
                default -> Optional.empty();
            };
        }
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return WindowsPowerSaver.read();
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return MacLowPower.read();
        }
        return PowerProfiles.read();
    }
}
