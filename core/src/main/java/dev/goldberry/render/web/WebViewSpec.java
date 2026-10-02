package dev.goldberry.render.web;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/// What a page is opened with: somewhere to start, a title, and a window size.
///
/// ```java
/// var spec = WebViewSpec.of("https://goldberry.dev").title("Goldberry").sized(800, 600, WebSize.INITIAL);
/// ```
///
/// The value half of a page, as [dev.goldberry.render.tray.TraySpec]
/// is the value half of a tray — and for its reason: opening is not a value, so
/// what can be described ahead of time is separated from the act.
///
/// ## `url` and `html` are alternatives
///
/// Exactly one of them says what the page starts as. A spec with neither opens a
/// blank page, which is legitimate — an application that will `navigate` a moment
/// later has nothing to put here — and a spec with **both** is rejected, because
/// the two would race and which one won would be an implementation detail.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html#the-web-view).
///
/// @param url        where to start, or null
/// @param html       the document to start with, or null
/// @param title      the window's title
/// @param width      the window's width in the desktop's pixels, positive
/// @param height     the window's height in the desktop's pixels, positive
/// @param size       what `width` and `height` mean
/// @param debug      whether to enable the engine's own inspector — WebKit's Web
///        Inspector or Edge's DevTools. A parameter rather than a system property
///        because an application may legitimately ship it on
/// @param callbacks  the functions the page's script may call, by name
/// @param onNavigate asked before the page goes anywhere, or null to let it go
///        everywhere — see [#onNavigate(Predicate)]
public record WebViewSpec(
        @Nullable String url,
        @Nullable String html,
        String title,
        int width,
        int height,
        WebSize size,
        boolean debug,
        Map<String, WebCallback> callbacks,
        @Nullable Predicate<URI> onNavigate) {

    /// The size a page opens at when nothing says otherwise.
    ///
    /// A browser's default rather than a number of this toolkit's own: what opens
    /// here is a window on the desktop, and it should look like the other windows
    /// on it.
    public static final int DEFAULT_WIDTH = 1024;

    /// @see #DEFAULT_WIDTH
    public static final int DEFAULT_HEIGHT = 768;

    /// Written out so that each parameter that takes null for a default can say so.
    public WebViewSpec(
            @Nullable String url,
            @Nullable String html,
            String title,
            int width,
            int height,
            WebSize size,
            boolean debug,
            @Nullable Map<String, WebCallback> callbacks,
            @Nullable Predicate<URI> onNavigate) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(size, "size");
        // Copied and ordered, so the names are bound in the order they were
        // declared -- which is the order a duplicate is reported in, and the
        // only thing about a map of handlers that is worth being stable.
        callbacks = callbacks == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(callbacks));
        if (url != null && html != null) {
            throw new IllegalArgumentException(
                    "a page starts at a URL or from a document, not both — pass one and navigate afterwards"
                            + " if the other is wanted");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a page's window is " + width + "x" + height + ", and both must be > 0");
        }
        this.url = url;
        this.html = html;
        this.title = title;
        this.width = width;
        this.height = height;
        this.size = size;
        this.debug = debug;
        this.callbacks = callbacks;
        this.onNavigate = onNavigate;
    }

    /// A page that is not asked about its navigations — the shape this record
    /// had before it could be, kept so that adding that changed nothing that
    /// already worked.
    public WebViewSpec(
            @Nullable String url,
            @Nullable String html,
            String title,
            int width,
            int height,
            WebSize size,
            boolean debug,
            @Nullable Map<String, WebCallback> callbacks) {
        this(url, html, title, width, height, size, debug, callbacks, null);
    }

    /// A page with no callbacks — the shape this record had before a page could
    /// call back, kept so that adding that changed nothing that already worked.
    public WebViewSpec(
            @Nullable String url,
            @Nullable String html,
            String title,
            int width,
            int height,
            WebSize size,
            boolean debug) {
        this(url, html, title, width, height, size, debug, Map.of(), null);
    }

    /// The same page, with `name` callable from its own script.
    ///
    /// See [WebCallback] for what crosses and what may be returned. Bindings are
    /// applied **before** the page is navigated, because the engine injects its
    /// glue at document start.
    public WebViewSpec on(String name, WebCallback handler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        var next = new LinkedHashMap<>(callbacks);
        next.put(name, handler);
        return new WebViewSpec(url, html, title, width, height, size, debug, next, onNavigate);
    }

    /// The same page, asking `decide` before it goes anywhere.
    ///
    /// ```java
    /// WebViewSpec.of(login).onNavigate(uri -> {
    ///     if ("myapp".equals(uri.getScheme())) {
    ///         signedIn(uri);
    ///         return false;
    ///     }
    ///     return true;
    /// })
    /// ```
    ///
    /// False cancels the navigation, and the page stays on the document it had.
    /// Every navigation of the main frame is asked about — a link, a form, a
    /// script, a server's redirect — so a redirect to a scheme no engine can
    /// load, which is how an OAuth sign-in comes back to a desktop application,
    /// arrives here before the engine tries. On Linux a frame's own navigations
    /// are asked about too, because WebKitGTK does not say which frame a
    /// decision is for.
    ///
    /// Installed before the first navigation, like the callbacks, and called on
    /// the UI thread inside the engine's own decision, so it must answer at
    /// once. A URI the engine reports in a form [URI] will not parse is let
    /// through, and so is every navigation when this throws.
    ///
    /// @param decide what to ask, or null to stop asking
    public WebViewSpec onNavigate(@Nullable Predicate<URI> decide) {
        return new WebViewSpec(url, html, title, width, height, size, debug, callbacks, decide);
    }

    /// A page that opens at `url`, at the default size.
    public static WebViewSpec of(String url) {
        Objects.requireNonNull(url, "url");
        return new WebViewSpec(url, null, "", DEFAULT_WIDTH, DEFAULT_HEIGHT, WebSize.INITIAL, false);
    }

    /// A page that opens showing `html`, at the default size.
    public static WebViewSpec ofHtml(String html) {
        Objects.requireNonNull(html, "html");
        return new WebViewSpec(null, html, "", DEFAULT_WIDTH, DEFAULT_HEIGHT, WebSize.INITIAL, false);
    }

    /// A blank page, at the default size.
    public static WebViewSpec blank() {
        return new WebViewSpec(null, null, "", DEFAULT_WIDTH, DEFAULT_HEIGHT, WebSize.INITIAL, false);
    }

    /// The same page with a window title.
    public WebViewSpec title(String value) {
        return new WebViewSpec(
                url, html, Objects.requireNonNull(value, "title"), width, height, size, debug, callbacks, onNavigate);
    }

    /// The same page at a different window size.
    public WebViewSpec sized(int value, int height, WebSize mode) {
        return new WebViewSpec(url, html, title, value, height, mode, debug, callbacks, onNavigate);
    }

    /// The same page with the engine's inspector on.
    public WebViewSpec debug(boolean value) {
        return new WebViewSpec(url, html, title, width, height, size, value, callbacks, onNavigate);
    }
}
