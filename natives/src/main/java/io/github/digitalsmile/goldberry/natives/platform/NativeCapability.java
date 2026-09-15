package io.github.digitalsmile.goldberry.natives.platform;

/// One thing the platform layer in **this** `libgoldberry` can do —
/// a bit of `goldberry_platform_capabilities`.
///
/// ## Why a library reports what it can do
///
/// An API is not a capability. `SDL_GetSystemTheme()` is declared on every
/// platform and compiled into every build of SDL, and on Linux its entire
/// implementation sits behind `SDL_USE_LIBDBUS` — which SDL's CMake defines only
/// when the D-Bus *headers* were installed on the machine that compiled it. A
/// library built without them answers `SDL_SYSTEM_THEME_UNKNOWN` on a desktop set
/// to dark, on every session, for ever, and reports nothing anywhere: not on the
/// desktop, not in the build log, not at run time. The same probe gates the XDG
/// portal file dialog and the screensaver inhibit; a second gates the input
/// method on X11 and a third input-device hotplug (`docs/gaps.md` G32).
///
/// So the superbuild now records what it found, and this is how it is read back.
/// The bits describe the **library**, not the session it is loaded into: a build
/// that can ask reports [#SYSTEM_THEME] even on a desktop that has no such
/// setting, because "could not ask" and "asked and was told nothing" are
/// different facts and only the first is fixable (ADR-0325).
///
/// Bit values are hard-coded here and checked against the compiled library by the
/// layout probe (ADR-0010), like every other constant the bindings carry.
public enum NativeCapability {

    /// The desktop's light-or-dark setting, and the event when it changes —
    /// `SDL_GetSystemTheme` and `SDL_EVENT_SYSTEM_THEME_CHANGED`, which is what
    /// `Host.systemTheme()` is.
    ///
    /// On Linux this is the XDG settings portal over D-Bus and there is no second
    /// path; on macOS `NSUserDefaults`, on Windows the `AppsUseLightTheme`
    /// registry value.
    SYSTEM_THEME(0x1, "GOLDBERRY_CAP_SYSTEM_THEME"),

    /// Composing text through an input method.
    ///
    /// **On Linux this bit is about X11 only.** SDL drives `zwp_text_input_v3`
    /// from the compositor on Wayland and needs neither IBus nor Fcitx there, so a
    /// library without this bit composes Japanese perfectly well on a Wayland
    /// session and not at all on an X11 one. That is also why its absence hides:
    /// the machine that built it and the machine that reports the bug can both be
    /// right.
    INPUT_METHOD(0x2, "GOLDBERRY_CAP_INPUT_METHOD"),

    /// Noticing an input device being plugged in or unplugged — `libudev` on
    /// Linux, IOKit on macOS, `WM_DEVICECHANGE` on Windows.
    DEVICE_HOTPLUG(0x4, "GOLDBERRY_CAP_DEVICE_HOTPLUG"),

    /// The desktop's own file dialogs — `SDL_ShowOpenFileDialog` and its two
    /// neighbours.
    ///
    /// On Linux this is the XDG desktop portal specifically. SDL has a second path
    /// there — it shells out to `zenity` — so a library without this bit may still
    /// open a dialog on a machine that happens to have that binary installed, and
    /// will open nothing at all on one that does not. The bit reports the portal,
    /// which is the half the build decides.
    FILE_DIALOG(0x8, "GOLDBERRY_CAP_FILE_DIALOG"),

    /// Keeping the screensaver off a window that is playing something.
    SCREENSAVER_INHIBIT(0x10, "GOLDBERRY_CAP_SCREENSAVER_INHIBIT");

    private final int bit;
    private final String nativeName;

    NativeCapability(int bit, String nativeName) {
        this.bit = bit;
        this.nativeName = nativeName;
    }

    /// The bit this capability occupies in `goldberry_platform_capabilities`.
    public int bit() {
        return bit;
    }

    /// The name the C shim reports this bit under, for the layout probe.
    public String nativeName() {
        return nativeName;
    }
}
