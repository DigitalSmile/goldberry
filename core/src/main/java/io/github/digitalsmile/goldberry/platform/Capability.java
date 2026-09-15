package io.github.digitalsmile.goldberry.platform;

/// One thing the toolkit's platform layer can do **in this build**, whatever its
/// API says.
///
/// ## Why a toolkit has to admit this
///
/// Every method behind these exists on every platform. What does not always
/// exist is the implementation: on Linux, SDL compiles its system-theme
/// detection, its portal file dialog, its screensaver inhibit and its X11 input
/// method in only if the matching development headers were installed on the
/// machine that built the native library — and compiles them out silently if they
/// were not. The calls then do not fail. They answer *"the desktop does not
/// say"*, on a desktop that does, on every session, for ever.
///
/// That is `docs/gaps.md` G32, and it cost an application a settings screen that
/// confidently told its user their desktop had no light-or-dark setting. The
/// build refuses to produce such a library by accident now
/// ([ADR-0325](../../../../../book/src/adr/0325-a-build-says-what-it-can-ask-the-desktop.md)),
/// and this is what an application reads when it wants to be sure:
///
/// ```java
/// if (!Goldberry.capabilities().contains(Capability.SYSTEM_THEME)) {
///     LOG.warn("this build cannot read the desktop's theme; following the user's choice instead");
/// }
/// ```
///
/// ## What the answer is about
///
/// The **library**, not the session it is running in. A build that can ask
/// reports [#SYSTEM_THEME] even on a desktop that has no such setting, because
/// "could not ask" and "asked and was told nothing" are different facts and only
/// the first one is fixable. The second is what an empty
/// [io.github.digitalsmile.goldberry.Host#systemTheme()] means, and the two
/// together are what let an application tell its user something true.
public enum Capability {

    /// The desktop's light-or-dark setting, and being told when it changes —
    /// [io.github.digitalsmile.goldberry.Host#systemTheme()] and
    /// [io.github.digitalsmile.goldberry.Host#onSystemThemeChanged].
    ///
    /// On Linux this is the XDG settings portal over D-Bus and there is no second
    /// path; on macOS `NSUserDefaults`, on Windows the `AppsUseLightTheme`
    /// registry value.
    SYSTEM_THEME,

    /// Composing text through an input method — the candidate window a Japanese,
    /// Chinese or Korean user types through.
    ///
    /// **On Linux this reports X11 only.** A Wayland session drives
    /// `zwp_text_input_v3` from the compositor and needs nothing built in, so a
    /// build without this composes fine there and not at all under X11 — which is
    /// why its absence hides for so long.
    INPUT_METHOD,

    /// Noticing an input device being plugged in or unplugged.
    DEVICE_HOTPLUG,

    /// The desktop's own file dialogs —
    /// [io.github.digitalsmile.goldberry.Host#fileDialogs()].
    ///
    /// On Linux this is the XDG desktop portal. SDL has a second path there — it
    /// shells out to `zenity` — so a build without this may still open a dialog on
    /// a machine that happens to have that binary, and opens nothing at all on one
    /// that does not.
    FILE_DIALOG,

    /// Keeping the screensaver off a window that is playing something.
    SCREENSAVER_INHIBIT
}
