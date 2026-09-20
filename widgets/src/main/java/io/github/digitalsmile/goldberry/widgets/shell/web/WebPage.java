package io.github.digitalsmile.goldberry.widgets.shell.web;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.web.WebCallback;
import io.github.digitalsmile.goldberry.render.web.WebSize;
import io.github.digitalsmile.goldberry.render.web.WebViewSpec;

/// A page this application asks the desktop to show — `docs/core-widgets.md`
/// §9's `web-view`.
///
/// **A value, not a widget**, and the second such thing in `widget.shell` after
/// [io.github.digitalsmile.goldberry.widgets.shell.tray.TrayIcon]. The argument
/// is that one's, taken further: a tray is at least a menu Goldberry describes,
/// and a page is not described by Goldberry at all. WebKit owns the pixels, the
/// fonts, the scrolling, the selection and the input. There is no box to lay out,
/// no `ComputedStyle` to compute and no pointer event to route.
///
/// ## Why it could not be a box
///
/// Because no shape would have been the same on every platform this ships to.
/// `webview/webview` cannot render offscreen, so a page is always a real platform
/// window — and a Wayland session permits neither reparenting a foreign surface
/// into another client's window nor placing a window where a widget is. A
/// `web-view` that sat in a layout on X11, Windows and macOS and became a loose
/// window on Wayland would be two behaviours wearing one name, with the broken
/// one on the common Linux desktop. See
/// [ADR-0441](../../../../../../../../book/src/adr/0441-a-web-page-is-a-window-not-a-box.md),
/// which also records what CEF would have bought and what it would have cost.
///
/// So there is **no `web-view` node in markup** either: there is nothing for a
/// `row` to size and nowhere for KDL to put it.
///
/// ```java
/// var page = WebViews.open(host, WebPage.of("https://example.org")
///         .title("Documentation")
///         .sized(1280, 800));
/// // ...
/// page.ifPresent(BackendWebView::close);
/// ```
///
/// ## Most desktops cannot show one
///
/// [WebViews#open] answers empty far more often than `Trays.show` does, and that
/// is expected rather than unlucky: a page needs `libgoldberry-webview`, a
/// separate library that exists so GTK and WebKit are not load-time dependencies
/// of the toolkit, and most builds do not carry it. An application that wants to
/// know before it draws the button asks
/// [io.github.digitalsmile.goldberry.Goldberry#capabilities()] for
/// [io.github.digitalsmile.goldberry.platform.Capability#WEB_VIEW].
///
/// @param url    where the page starts, or null
/// @param html   the document it starts with, or null. A page names one of these
///               or neither; naming both is rejected, because which won would be
///               an implementation detail
/// @param title  the window's title
/// @param width  the window's width. **Physical pixels**: a page's window is not
///               a Goldberry window and inherits no scale, and the engine applies
///               the desktop's own scaling to the page exactly as a browser does
/// @param height the window's height, in the same pixels
/// @param size   whether those numbers are an opening size, a floor, a ceiling or
///               a fixed size
/// @param debug  whether the engine's inspector is available in the page
public record WebPage(
        @Nullable String url,
        @Nullable String html,
        String title,
        int width,
        int height,
        WebSize size,
        boolean debug,
        java.util.Map<String, WebCallback> callbacks) {

    public WebPage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(size, "size");
        callbacks = callbacks == null
                ? java.util.Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(callbacks));
        // Delegated rather than repeated: the spec is what the backend is handed,
        // and two copies of "a page starts one way" would be two chances to
        // disagree.
        new WebViewSpec(url, html, title, width, height, size, debug, callbacks);
    }

    /// A page with no callbacks — the shape this record had before a page could
    /// call back into the application.
    public WebPage(
            @Nullable String url,
            @Nullable String html,
            String title,
            int width,
            int height,
            WebSize size,
            boolean debug) {
        this(url, html, title, width, height, size, debug, java.util.Map.of());
    }

    /// The same page, with `name` callable from its own script.
    ///
    /// ```java
    /// WebPage.of(url).on("save", arguments -> { store(arguments); return "true"; })
    /// ```
    /// ```js
    /// const ok = await window.save({title: "note"});
    /// ```
    ///
    /// The arguments arrive as a JSON **array** and are not parsed, the return
    /// value must be valid JSON or empty, and throwing rejects the page's
    /// promise. [WebCallback] has the whole of it.
    ///
    /// ## Bindings are read when the page **opens**
    ///
    /// The engine injects each one's glue at document start, so they have to be
    /// in place before anything loads — which means a handler added to a page
    /// that is already open does not take effect. Declare them where the page is
    /// declared.
    ///
    /// ## And they are why two pages are rarely equal
    ///
    /// A handler is a lambda, and lambdas have no value equality: two rebuilds
    /// of the same `on(...)` expression produce different instances, so this
    /// record's generated `equals` says two otherwise identical pages differ.
    /// That is why [#showsSameAs] exists and why `web-view` navigates on *it*
    /// rather than on `equals` — a page that re-navigated on every rebuild would
    /// reload itself for ever.
    public WebPage on(String name, WebCallback handler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        var next = new java.util.LinkedHashMap<>(callbacks);
        next.put(name, handler);
        return new WebPage(url, html, title, width, height, size, debug, next);
    }

    /// Whether this page and `other` show the **same content**.
    ///
    /// The url and the document, and deliberately nothing else. A title, a
    /// window size, the inspector and the handlers are all things about a page
    /// that do not change what is on it — and asking `equals` would be asking
    /// about the handlers too, which is a question with no useful answer (see
    /// [#on]).
    ///
    /// This is what `web-view` compares to decide whether the application has
    /// asked for somewhere else.
    public boolean showsSameAs(@Nullable WebPage other) {
        return other != null && Objects.equals(url, other.url) && Objects.equals(html, other.html);
    }

    /// A page that opens at `url`.
    public static WebPage of(String url) {
        Objects.requireNonNull(url, "url");
        return new WebPage(
                url, null, "", WebViewSpec.DEFAULT_WIDTH, WebViewSpec.DEFAULT_HEIGHT, WebSize.INITIAL, false);
    }

    /// A page that opens showing `html`.
    ///
    /// The document is loaded as-is and is the application's own — no cascade, no
    /// theme and no stylesheet of the toolkit's reaches it, because it is not in
    /// the toolkit's tree. An author who wants a page to follow the desktop's
    /// light-or-dark setting reads
    /// [io.github.digitalsmile.goldberry.Host#systemTheme()] and writes the CSS.
    public static WebPage ofHtml(String html) {
        Objects.requireNonNull(html, "html");
        return new WebPage(
                null, html, "", WebViewSpec.DEFAULT_WIDTH, WebViewSpec.DEFAULT_HEIGHT, WebSize.INITIAL, false);
    }

    /// A page that opens blank, for an application that will navigate it later.
    public static WebPage blank() {
        return new WebPage(
                null, null, "", WebViewSpec.DEFAULT_WIDTH, WebViewSpec.DEFAULT_HEIGHT, WebSize.INITIAL, false);
    }

    /// The same page, with a window title.
    public WebPage title(String value) {
        return new WebPage(url, html, Objects.requireNonNull(value, "title"), width, height, size, debug, callbacks);
    }

    /// The same page, opening at a given size the user may then change.
    public WebPage sized(int value, int height) {
        return new WebPage(url, html, title, value, height, WebSize.INITIAL, debug, callbacks);
    }

    /// The same page, at a size that means what `mode` says.
    public WebPage sized(int value, int height, WebSize mode) {
        return new WebPage(url, html, title, value, height, Objects.requireNonNull(mode, "mode"), debug, callbacks);
    }

    /// The same page, with the engine's own inspector reachable in it.
    ///
    /// WebKit's Web Inspector or Edge's DevTools. Off by default and a value
    /// rather than a system property, because an application may legitimately
    /// ship it on — a page that *is* a developer tool wants it.
    public WebPage debug(boolean value) {
        return new WebPage(url, html, title, width, height, size, value, callbacks);
    }

    /// This page as the backend's own word for it.
    public WebViewSpec spec() {
        return new WebViewSpec(url, html, title, width, height, size, debug, callbacks);
    }
}
