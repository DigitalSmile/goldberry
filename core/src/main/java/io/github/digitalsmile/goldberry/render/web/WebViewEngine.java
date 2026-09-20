package io.github.digitalsmile.goldberry.render.web;

import java.util.Objects;
import java.util.Optional;

import io.github.digitalsmile.goldberry.natives.sdl.window.NativeWindowHandle;
import io.github.digitalsmile.goldberry.natives.webview.SizeHint;
import io.github.digitalsmile.goldberry.natives.webview.Webview;
import io.github.digitalsmile.goldberry.render.window.NativeHandle;

/// Opens pages, and gives the open ones a turn.
///
/// The one place `:core` names `:natives`' web view wrapper, and the boundary
/// where `SizeHint` becomes [WebSize] — the arrangement `PlatformCapabilities`
/// has with `NativeCapability`.
///
/// A backend calls [#open] from `createWebView`, and the frame loop calls
/// [#pump()]. Nothing else here should name this class.
public final class WebViewEngine {

    private WebViewEngine() {}

    /// Whether a page can be opened in this process at all.
    ///
    /// False where the build produced no `libgoldberry-webview`, and where it did
    /// but the engine behind it is not installed — those are one answer here
    /// because they are one answer to an application: no page will open.
    public static boolean isAvailable() {
        return Webview.isAvailable();
    }

    /// Opens a page.
    ///
    /// @param spec what to open
    /// @return the page, or empty where this build has no web view support or the
    ///         engine would not start
    public static Optional<BackendWebView> open(WebViewSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return Webview.open(spec.debug()).map(webview -> {
            var page = new NativeWebView(webview);
            // Size before content, so the first frame the engine paints is the
            // size it will stay — a page loaded into a default-sized window and
            // then resized reflows in front of the user.
            page.size(spec.width(), spec.height(), spec.size());
            if (!spec.title().isEmpty()) {
                page.title(spec.title());
            }
            if (spec.url() != null) {
                page.navigate(spec.url());
            } else if (spec.html() != null) {
                page.html(spec.html());
            }
            return page;
        });
    }

    /// Opens a page **inside** `parent`, at `bounds` in that window's own pixels.
    ///
    /// The embedded half of §9's `web-view` ([ADR-0442]). The page keeps its own
    /// platform window and that window becomes a child of the application's, so
    /// it takes part in the layout rather than floating beside it.
    ///
    /// **Empty on Wayland, and that is permanent.** No cross-client surface
    /// embedding exists there, so a caller is expected to say so rather than to
    /// open a loose window.
    ///
    /// @param spec   where the page starts and whether its inspector is on. The
    ///               spec's own width and height are ignored: an embedded page is
    ///               the size of the box it is in
    /// @param parent the window to go inside
    /// @return the page, or empty where it could not be opened embedded
    public static Optional<BackendWebView> openEmbedded(
            WebViewSpec spec, NativeHandle parent, int x, int y, int width, int height) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(parent, "parent");
        return Webview.openEmbedded(spec.debug(), parent.value(), translate(parent.kind()), x, y, width, height)
                .map(webview -> {
                    var page = new NativeWebView(webview);
                    if (spec.url() != null) {
                        page.navigate(spec.url());
                    } else if (spec.html() != null) {
                        page.html(spec.html());
                    }
                    return page;
                });
    }

    /// [NativeHandle.Kind] in `:natives`' word for it — the exhaustive switch the
    /// two separate enums are kept in step by.
    static NativeWindowHandle.Kind translate(NativeHandle.Kind kind) {
        return switch (kind) {
            case X11 -> NativeWindowHandle.Kind.X11;
            case WIN32 -> NativeWindowHandle.Kind.WIN32;
            case COCOA -> NativeWindowHandle.Kind.COCOA;
        };
    }

    /// Gives every open page's event loop a turn.
    ///
    /// Called once per frame. Does nothing — and loads nothing — when no page is
    /// open, which is what makes it safe to call unconditionally from the loop of
    /// an application that has never heard of web views.
    public static void pump() {
        Webview.pump();
    }

    /// Whether any page is open.
    ///
    /// What the frame loop asks to decide how long it may block for. A loop parked
    /// for its one-second heartbeat is not calling [#pump()], and a page whose
    /// GLib context is drained once a second is a page that does not scroll — so
    /// while this is true the wait is capped, and while it is false nothing
    /// changes for the applications that never open one.
    ///
    /// Reads a counter, so asking never loads anything.
    public static boolean hasOpenPages() {
        return Webview.hasOpenPages();
    }

    /// [WebSize] in `:natives`' word for it.
    ///
    /// An exhaustive switch rather than an ordinal or a name lookup: the two
    /// enums are deliberately separate types, and this is the line that fails to
    /// compile when a constant is added to one and not the other.
    static SizeHint translate(WebSize size) {
        return switch (size) {
            case INITIAL -> SizeHint.NONE;
            case MINIMUM -> SizeHint.MIN;
            case MAXIMUM -> SizeHint.MAX;
            case FIXED -> SizeHint.FIXED;
        };
    }
}
