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
    SCREENSAVER_INHIBIT,

    /// Whether this build can ask the desktop for a titlebar and a resize edge.
    ///
    /// **On Linux this is libdecor at build time.** Without it SDL compiles no
    /// client-side decoration support at all, and a window on a GNOME/Wayland
    /// session opens with no titlebar and no resize edge however the session is
    /// configured — which is two consecutive shipped bugs' worth of history
    /// ([ADR-0083](../../../../../book/src/adr/0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)).
    /// On macOS and Windows the window server draws them, so this is always
    /// present there.
    ///
    /// **It does not promise a titlebar.** libdecor's default plugin refuses to
    /// start off the process's initial thread and a JVM is never on it, so a build
    /// that reports this can still open a bare window at run time
    /// ([ADR-0084](../../../../../book/src/adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)).
    /// Built able to ask is the claim.
    WINDOW_DECORATIONS,

    /// Whether SDL compiled a Wayland video driver into this build.
    ///
    /// Linux only; never present on macOS or Windows, where there is no Wayland to
    /// have a driver for.
    ///
    /// Worth asking because losing it is **silent**. SDL decides the entire driver
    /// with one `pkg_check_modules` over five specs, so a build machine missing any
    /// one of them — EGL's headers being the one that has actually happened —
    /// produces a library whose every session falls back to X11 or XWayland, with
    /// no error anywhere. XWayland resizes visibly worse, which is what
    /// [ADR-0027](../../../../../book/src/adr/0027-prefer-wayland-fall-back-to-x11.md)
    /// chose against.
    ///
    /// Like every other value here this describes the **library**: a build with
    /// the driver still runs on X11 when that is what the desktop is.
    WAYLAND,

    /// Whether this process can open a web page — §9's `web-view`,
    /// [ADR-0441](../../../../../book/src/adr/0441-a-web-page-is-a-window-not-a-box.md).
    ///
    /// **The one value here that is not a bit in `libgoldberry`**, and the reason
    /// is the whole of that ADR. The engine behind a page is the desktop's own —
    /// WebKitGTK, WebView2, WKWebView — so it lives in a second library,
    /// `libgoldberry-webview`, which is linked into nothing and opened on demand.
    /// Linking it into `libgoldberry` would put GTK and WebKit in the toolkit's
    /// own `NEEDED`, and every application on Linux would then require them to
    /// *start*.
    ///
    /// So this is answered by trying to open that library rather than by reading a
    /// compiled-in flag, and it covers two absences an application cannot tell
    /// apart and should not have to: a build made somewhere without WebKit's
    /// development headers, and a machine without WebKit installed to run it. Both
    /// mean no page will open.
    ///
    /// Unlike every other capability here, absence is **expected**. A toolkit that
    /// cannot ask the desktop its theme is a build that went wrong; a toolkit that
    /// cannot open a web page is the ordinary case, and an application that wants
    /// one asks first:
    ///
    /// ```java
    /// if (!Goldberry.capabilities().contains(Capability.WEB_VIEW)) {
    ///     // Offer the user their own browser instead.
    /// }
    /// ```
    WEB_VIEW
}
