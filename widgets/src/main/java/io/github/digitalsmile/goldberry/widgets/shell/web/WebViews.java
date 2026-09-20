package io.github.digitalsmile.goldberry.widgets.shell.web;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.platform.Capability;
import io.github.digitalsmile.goldberry.render.web.BackendWebView;

/// Opens a [WebPage] on the desktop.
///
/// The split `tray` has and `menu` has before it: a page is a value and
/// **showing** one is not (ADR-0106, ADR-0191). What this adds over calling
/// [Host#webView] directly is the one thing worth saying out loud — why nothing
/// happened, when nothing happens.
///
/// ## Empty is the ordinary answer
///
/// `Trays.show` returns empty on a desktop with no notification area, which is
/// unusual. This returns empty on **most machines**, which is not: a page needs
/// `libgoldberry-webview`, and that library is built only where WebKit's
/// development headers were present and loads only where WebKit is installed. It
/// is separate precisely so that GTK and WebKit are not load-time dependencies of
/// every application that ever used the toolkit
/// ([ADR-0441](../../../../../../../../book/src/adr/0441-a-web-page-is-a-window-not-a-box.md)).
///
/// So an application asks before it offers:
///
/// ```java
/// if (Goldberry.capabilities().contains(Capability.WEB_VIEW)) {
///     button("Open the handbook", () -> WebViews.open(host, WebPage.of(HANDBOOK)));
/// } else {
///     button("Open the handbook", () -> Desktop.browse(HANDBOOK));
/// }
/// ```
///
/// ## What the caller owns
///
/// **Closing it.** [Goldberry#run()] ends when the last *Goldberry* window
/// closes, and a page's window is not one of those — it belongs to WebKit. An
/// application that opens a page and then closes its own window leaves the page
/// standing on the desktop.
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
                    + " Ask Goldberry.capabilities() for WEB_VIEW before offering one,"
                    + " and see ADR-0441 for why it is a separate library");
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
