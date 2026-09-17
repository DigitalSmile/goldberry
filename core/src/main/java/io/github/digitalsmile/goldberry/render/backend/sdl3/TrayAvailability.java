package io.github.digitalsmile.goldberry.render.backend.sdl3;

import java.util.Locale;
import java.util.Optional;

/// Whether a tray can be asked for at all under the video driver SDL is running.
///
/// SDL's macOS tray is Cocoa's status bar, and `SDL_CreateTray` reaches
/// `[NSStatusBar systemStatusBar]` *before* it touches `NSApplication`. Under the
/// Cocoa video driver that is fine: `SDL_Init` created the application object.
/// Under the `dummy` driver nothing did, the status bar's first call into the
/// window server fails an assertion inside CoreGraphics —
///
/// ```text
/// Assertion failed: (CGAtomicGet(&is_initialized)), function CGSConnectionByID
/// ```
///
/// — and the process is aborted rather than told. That is how a headless
/// native-image trace on a macOS runner died with no Java frame in sight
/// (ADR-0338). SDL returns NULL for every other reason a tray cannot exist, so
/// this is the one absence that has to be known before the call.
///
/// Elsewhere the dummy driver and a tray coexist: the Linux tray is a D-Bus
/// conversation and the Windows tray is a hidden window of SDL's own, and neither
/// needs the video driver.
final class TrayAvailability {

    /// SDL's name for the driver that draws nothing.
    static final String DUMMY_DRIVER = "dummy";

    private TrayAvailability() {}

    /// Why no tray can be created under this driver on this system, or empty
    /// when SDL may be asked.
    ///
    /// @param osName the value of `os.name`
    /// @param videoDriver what `SDL_GetCurrentVideoDriver` reports
    /// @return the reason, phrased for the debug log
    static Optional<String> absenceReason(String osName, String videoDriver) {
        var name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        var macOs = name.contains("mac") || name.contains("darwin");
        if (macOs && DUMMY_DRIVER.equals(videoDriver)) {
            return Optional.of("the " + DUMMY_DRIVER + " video driver on macOS starts no Cocoa application,"
                    + " and SDL's status-bar tray aborts the process without one");
        }
        return Optional.empty();
    }
}
