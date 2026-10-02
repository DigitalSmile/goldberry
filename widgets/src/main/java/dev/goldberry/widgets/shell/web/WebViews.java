package dev.goldberry.widgets.shell.web;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goldberry.Goldberry;
import dev.goldberry.Host;
import dev.goldberry.platform.Capability;
import dev.goldberry.render.web.BackendWebView;

/// Opens a [WebPage] in a platform window of its own.
///
/// ```java
/// if (WebViews.isAvailable()) {
///     button("Open the handbook", () -> WebViews.open(host, WebPage.of(HANDBOOK)));
/// } else {
///     button("Open the handbook", () -> Desktop.browse(HANDBOOK));
/// }
/// ```
///
/// A page is a value and showing one is a separate call, the same split the
/// tray and the menus make. What this adds over calling [Host#webView] directly
/// is a log line saying why nothing happened, when nothing happens.
///
/// Empty is the ordinary answer: a page needs `libgoldberry-webview`, a
/// separate library that is built only where WebKit's development headers were
/// present and loads only where WebKit is installed. It is separate so that
/// GTK and WebKit are not load-time dependencies of every application that
/// uses the toolkit, and most builds do not carry it. So an application asks
/// [#isAvailable] before it offers a page.
///
/// The caller owns the page's window. [Goldberry#run()] ends when the last
/// Goldberry window closes, and a page's window is not one of those, so an
/// application that opens a page and then closes its own window leaves the
/// page standing on the desktop until it closes it.
///
/// Read more:
/// [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html#the-web-view).
public final class WebViews {

    private static final Logger LOG = LoggerFactory.getLogger(WebViews.class);

    private WebViews() {}

    /// Opens `page`, if this build can.
    ///
    /// @param host the application's host
    /// @param page what to open
    /// @return the page, or empty when no page can be opened here — see
    ///         [#isAvailable()], which is the question to ask *first*
    public static Optional<BackendWebView> open(Host host, WebPage page) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(page, "page");
        var opened = host.webView(page.spec());
        if (opened.isEmpty()) {
            // Said once, here, rather than left as an empty Optional an
            // application may not have thought to look inside. This is the one
            // absence in the catalog that an author is likely to hit on their own
            // machine while writing the code that depends on it.
            LOG.info("no web page was opened: this build has no web view support."
                    + " Ask Goldberry.capabilities() for WEB_VIEW before offering one;"
                    + " the engine is a separate optional library, see"
                    + " https://goldberry.dev/docs/components/content.html#the-web-view");
        }
        return opened;
    }

    /// Whether a page can be opened in this process.
    ///
    /// The same answer as asking [Goldberry#capabilities()] for
    /// [Capability#WEB_VIEW], spelled for the one caller that is about to open a
    /// page rather than describe a build.
    public static boolean isAvailable() {
        return Goldberry.capabilities().contains(Capability.WEB_VIEW);
    }
}
