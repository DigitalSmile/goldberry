package dev.goldberry.widgets.shell.web;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.web.WebCallback;
import dev.goldberry.render.web.WebSize;
import dev.goldberry.render.web.WebViewSpec;

/// A web page, described as a value: where it starts, what its window is
/// called and how big it opens, and the functions its script may call.
///
/// ```java
/// var page = WebViews.open(host, WebPage.of("https://example.org")
///         .title("Documentation")
///         .sized(1280, 800));
/// // ...
/// page.ifPresent(BackendWebView::close);
/// ```
///
/// `WebPage.of(url)`, [#ofHtml] or [#blank] makes one; [#title], [#sized],
/// [#debug] and [#on] return a changed copy. [WebViews#open] shows the page in a
/// platform window of its own, and the `web-view` widget shows one inside a
/// window's box where the window system allows a child window.
///
/// It is a value and not a widget because the desktop's engine, not the
/// toolkit, draws the page: it owns the pixels, the fonts, the scrolling, the
/// selection and the input, so there is no box to lay out, no style to compute
/// and no event to route. For the same reason there is no `web-view` node in
/// markup. Most machines cannot open a page at all, because the engine is
/// driven through a separate optional library; ask
/// [dev.goldberry.Goldberry#capabilities()] for
/// [dev.goldberry.platform.Capability#WEB_VIEW] before offering one.
///
/// Read more:
/// [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html#the-web-view).
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

    /// Written out so that the parameters taking null for a default can say so.
    public WebPage(
            @Nullable String url,
            @Nullable String html,
            String title,
            int width,
            int height,
            WebSize size,
            boolean debug,
            java.util.@Nullable Map<String, WebCallback> callbacks) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(size, "size");
        callbacks = callbacks == null
                ? java.util.Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(callbacks));
        // Delegated rather than repeated: the spec is what the backend is handed,
        // and two copies of "a page starts one way" would be two chances to
        // disagree.
        new WebViewSpec(url, html, title, width, height, size, debug, callbacks);
        this.url = url;
        this.html = html;
        this.title = title;
        this.width = width;
        this.height = height;
        this.size = size;
        this.debug = debug;
        this.callbacks = callbacks;
    }

    /// A page with no callbacks.
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
    /// Bindings are read when the page **opens**: the engine injects each one's
    /// glue at document start, so a handler added to a page that is already open
    /// does not take effect. Declare them where the page is declared.
    ///
    /// A handler is a lambda, and lambdas have no value equality, so two
    /// rebuilds of the same `on(...)` expression make pages that are not
    /// `equals`. That is why [#showsSameAs] exists and why `web-view` navigates
    /// on it rather than on `equals`: a page that re-navigated on every rebuild
    /// would reload itself for ever.
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
    /// [dev.goldberry.Host#systemTheme()] and writes the CSS.
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
