package io.github.digitalsmile.goldberry.render.web;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// What a page is opened with: somewhere to start, a title, and a window size.
///
/// The value half of a page, as [io.github.digitalsmile.goldberry.render.tray.TraySpec]
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
/// @param url    where to start, or null
/// @param html   the document to start with, or null
/// @param title  the window's title
/// @param width  the window's width in the desktop's pixels, positive
/// @param height the window's height in the desktop's pixels, positive
/// @param size   what `width` and `height` mean
/// @param debug  whether to enable the engine's own inspector — WebKit's Web
///        Inspector or Edge's DevTools. A parameter rather than a system property
///        because an application may legitimately ship it on
public record WebViewSpec(
        @Nullable String url, @Nullable String html, String title, int width, int height, WebSize size, boolean debug) {

    /// The size a page opens at when nothing says otherwise.
    ///
    /// A browser's default rather than a number of this toolkit's own: what opens
    /// here is a window on the desktop, and it should look like the other windows
    /// on it.
    public static final int DEFAULT_WIDTH = 1024;

    /// @see #DEFAULT_WIDTH
    public static final int DEFAULT_HEIGHT = 768;

    public WebViewSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(size, "size");
        if (url != null && html != null) {
            throw new IllegalArgumentException(
                    "a page starts at a URL or from a document, not both — pass one and navigate afterwards"
                            + " if the other is wanted");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a page's window is " + width + "x" + height + ", and both must be > 0");
        }
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
        return new WebViewSpec(url, html, Objects.requireNonNull(value, "title"), width, height, size, debug);
    }

    /// The same page at a different window size.
    public WebViewSpec sized(int value, int height, WebSize mode) {
        return new WebViewSpec(url, html, title, value, height, mode, debug);
    }

    /// The same page with the engine's inspector on.
    public WebViewSpec debug(boolean value) {
        return new WebViewSpec(url, html, title, width, height, size, value);
    }
}
