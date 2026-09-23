package io.github.digitalsmile.goldberry.widgets.core.web;

import java.util.Locale;

/// Why a `web-view` has no page, in the two forms the widget needs: a line for
/// the log and a paragraph for the box.
///
/// ## Why there is more than one
///
/// The widget used to have one message, and it was the Wayland one. That was
/// right on the desktop it was written on and wrong everywhere else: a Mac was
/// told to run under XWayland, and a build with no web view library at all was
/// told the same thing. Each of these has a different fix, and the message is
/// the only place a user finds out which one applies.
///
/// The widget cannot see the real cause. The shim answers a bare NULL, and the
/// natives layer logs its own reason one level down. What the widget *can* tell
/// apart is whether the library is there at all and which platform this is, and
/// that is enough to point at the right fix.
///
/// A pure function of those two facts, so each case is testable without a
/// window, a library or the platform it describes.
enum WebViewRefusal {

    /// No `libgoldberry-webview` in this process: not built, not packaged, or
    /// present but refusing to load. Same on every platform.
    NO_LIBRARY(
            "no web view library is loaded, so no page can be opened. Check that Capability.WEB_VIEW is"
                    + " present; see ADR-0441 for why the web view is a separate library",
            "This build has no web view support, so no page can be opened here. The web view is a separate"
                    + " library, libgoldberry-webview, and this application was built or packaged without it."),

    /// Linux, where the one thing that refuses an embedded page is a session that
    /// is not X11 ([ADR-0442]).
    WAYLAND(
            "no page could be put inside the window. Embedding needs a native window handle to reparent"
                    + " into, which X11 gives and Wayland does not. Run on X11 or XWayland"
                    + " (-Dgoldberry.backend.videoDriver=x11)",
            "This session cannot put a web page inside a window. Wayland has no cross-client surface"
                    + " embedding and no protocol proposes one, so the page is not opened at all rather than"
                    + " dropped on the desktop where the layout cannot reach it. Running under X11 or XWayland"
                    + " — -Dgoldberry.backend.videoDriver=x11 — is what makes it work."),

    /// macOS and Windows, where nothing about the session forbids embedding — a
    /// `WKWebView` subview ([ADR-0458]) and a WebView2 child window
    /// ([ADR-0459]) — so a refusal is the engine failing, and the line
    /// `Webview` logs just before this one says how.
    ENGINE_FAILED(
            "the web engine could not be put inside the window; the line logged by Webview just before"
                    + " this one says why",
            "The web engine did not start inside this window, so the page was not opened. The application's"
                    + " log says why.");

    private final String log;
    private final String notice;

    WebViewRefusal(String log, String notice) {
        this.log = log;
        this.notice = notice;
    }

    /// Which refusal this is.
    ///
    /// The library comes first, because it is the one reason that holds on every
    /// platform and hides every other: with no library, nothing below it was
    /// asked.
    ///
    /// @param library whether `libgoldberry-webview` is loaded — `WebViews.isAvailable()`
    /// @param osName  the `os.name` system property
    /// @return why there is no page
    static WebViewRefusal of(boolean library, String osName) {
        if (!library) {
            return NO_LIBRARY;
        }
        var os = osName.toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin") || os.contains("windows") ? ENGINE_FAILED : WAYLAND;
    }

    /// The refusal for this process.
    static WebViewRefusal current(boolean library) {
        return of(library, System.getProperty("os.name", ""));
    }

    /// What the log says: the cause and what to do, for whoever reads it.
    String log() {
        return log;
    }

    /// What the widget paints in its box: the same, for whoever is looking at it.
    String notice() {
        return notice;
    }
}
