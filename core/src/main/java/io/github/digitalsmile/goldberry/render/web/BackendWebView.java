package io.github.digitalsmile.goldberry.render.web;

/// A web page, in a window the engine owns.
///
/// The handle `WebViews.open` hands back, and the whole of what an application
/// may do to a page after opening it. It is the shape
/// [io.github.digitalsmile.goldberry.render.tray.BackendTray]
/// has, for the reason that one has it: what is on screen belongs to somebody
/// else, and this is the remote control.
///
/// ## What it is not
///
/// **Not a widget, and not in any tree.** A page is a top-level window of
/// WebKitGTK's, WebView2's or WKWebView's making — it has no box, no cascade, no
/// hit test and no place in a layout. That is
/// [ADR-0441](../../../../../../../../book/src/adr/0441-a-web-page-is-a-window-not-a-box.md):
/// `webview/webview` cannot render offscreen, and Wayland permits neither
/// reparenting a foreign surface nor placing a window where a widget is, so
/// there is no honest way to make a page into a box on every platform this
/// toolkit ships to.
///
/// An application that wants a page beside its widgets opens one and lets the
/// desktop arrange the two.
///
/// ## Closing it is the application's job
///
/// [io.github.digitalsmile.goldberry.Goldberry#run()] returns when the last
/// **Goldberry** window closes, and a page's window is not one of those. An
/// application that opens a page and then closes its own window leaves the page
/// standing, which is why this is `AutoCloseable` and why the launcher closes
/// what it opened.
///
/// UI-thread confined, like every other platform handle here.
public interface BackendWebView extends AutoCloseable {

    /// Points the page at `url`.
    ///
    /// @param url an absolute URL. `file:` and `data:` behave as they do in a
    ///        browser; a relative one has nothing to be relative to, because a
    ///        page opened here starts from no document
    void navigate(String url);

    /// Replaces the page's contents with `html`.
    void html(String html);

    /// Sets the window's title.
    void title(String title);

    /// Sizes the window, in the desktop's own pixels.
    ///
    /// **Not logical pixels.** A page's window is not a Goldberry window and
    /// inherits no scale from one; the engine applies the desktop's scaling to
    /// the page itself, exactly as a browser does.
    ///
    /// @param width  the width, positive
    /// @param height the height, positive
    /// @param size   what the numbers mean
    void size(int width, int height, WebSize size);

    /// Moves and resizes an **embedded** page within the window it is inside.
    ///
    /// Called whenever the widget's box changes — a window resize, a scroll, a
    /// tab change. One platform request and no round trip, which is what makes it
    /// affordable every frame.
    ///
    /// Does nothing for a page that is not embedded.
    ///
    /// @param x      the left edge, in the parent window's own pixels
    /// @param y      the top edge, in the same pixels
    /// @param width  the width, positive
    /// @param height the height, positive
    void bounds(int x, int y, int width, int height);

    /// Runs `script` in the page.
    ///
    /// Nothing comes back: the engine's call is asynchronous and its result
    /// reaches a host through a binding rather than a return value. No binding is
    /// exposed yet, because nothing has asked for one.
    void eval(String script);

    /// Whether this page has been closed.
    boolean isClosed();

    /// Closes the window and frees the page. Idempotent.
    @Override
    void close();
}
