package io.github.digitalsmile.goldberry.widgets.shell.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.web.BackendWebView;
import io.github.digitalsmile.goldberry.render.web.WebSize;
import io.github.digitalsmile.goldberry.widgets.TestHost;

/// Opening a page — §9's `web-view`, ADR-0441.
///
/// What a test can assert here is deliberately narrow, and the narrowness is the
/// design rather than a gap in the suite. Every pixel of a page belongs to
/// WebKit: there is no box, no cascade, no hit test and no golden image, so what
/// is left to be right or wrong is the **description** that crosses the boundary
/// and what happens when no page can be opened — which, on most machines, is what
/// happens.
///
/// A real page is never opened. Doing so would put a WebKit window on the desktop
/// of whoever ran the suite.
@DisplayName("opening a web page")
class WebViewsTest {

    @Test
    @DisplayName("a desktop that cannot open one answers empty rather than failing")
    void absenceIsAnAnswer() {
        // The ordinary case, and the reason this is an Optional at all: most
        // builds carry no libgoldberry-webview, because it is separate so that GTK
        // and WebKit are not load-time dependencies of the toolkit.
        var host = new TestHost();

        var page = WebViews.open(host, WebPage.of("https://example.org"));

        assertTrue(page.isEmpty());
    }

    @Test
    @DisplayName("what is asked for crosses even when nothing can be opened")
    void theSpecIsStillHandedOver() {
        // Worth pinning: an application that gets empty should still have asked
        // for the right thing, so that the same call on a machine with WebKit
        // opens what it meant to.
        var host = new TestHost();

        WebViews.open(host, WebPage.of("https://example.org").title("Docs").sized(1280, 800));

        var asked = host.lastWebView();
        assertEquals("https://example.org", asked.url());
        assertEquals("Docs", asked.title());
        assertEquals(1280, asked.width());
        assertEquals(800, asked.height());
    }

    @Test
    @DisplayName("a build that can open one hands back a page")
    void aPageIsOpened() {
        var host = new TestHost().webViewAvailable(true);

        var page = WebViews.open(host, WebPage.of("https://example.org"));

        assertTrue(page.isPresent());
        assertFalse(page.get().isClosed());
    }

    @Test
    @DisplayName("a page closes once, however often it is asked")
    void closingIsIdempotent() {
        // The rule every platform handle here keeps, and it matters more for this
        // one: the caller owns closing a page, because Goldberry.run() ends with
        // the last *Goldberry* window and a page's window is not one.
        var host = new TestHost().webViewAvailable(true);
        var page = WebViews.open(host, WebPage.blank()).orElseThrow();

        page.close();
        page.close();

        assertTrue(page.isClosed());
        assertEquals(
                1,
                ((TestHost.FakeWebView) page)
                        .calls().stream().filter("close()"::equals).count());
    }

    @Test
    @DisplayName("a page is driven after it is open")
    void thePageTakesInstructions() {
        var host = new TestHost().webViewAvailable(true);
        var page = WebViews.open(host, WebPage.blank()).orElseThrow();

        page.navigate("https://example.org");
        page.title("Docs");
        page.size(800, 600, WebSize.FIXED);
        page.eval("document.title");

        assertEquals(
                java.util.List.of(
                        "navigate(https://example.org)", "title(Docs)", "size(800,600,FIXED)", "eval(document.title)"),
                ((TestHost.FakeWebView) page).calls());
    }

    @Test
    @DisplayName("a page starts one way, and saying two is rejected where it is written")
    void aPageStartsOneWay() {
        // The check lives on WebViewSpec and is delegated to from the record's
        // compact constructor, so that the two cannot disagree. This asserts the
        // delegation actually happens -- a `WebPage` built the long way round is
        // the shape an author reaches for when they are being clever.
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebPage("https://example.org", "<p>hi</p>", "", 800, 600, WebSize.INITIAL, false));
    }

    @Test
    @DisplayName("a page value converts to the spec that crosses the boundary")
    void theValueBecomesASpec() {
        var spec = WebPage.ofHtml("<p>hello</p>")
                .title("Notes")
                .sized(640, 480, WebSize.MINIMUM)
                .debug(true)
                .spec();

        assertEquals("<p>hello</p>", spec.html());
        assertNull(spec.url());
        assertEquals("Notes", spec.title());
        assertEquals(640, spec.width());
        assertEquals(480, spec.height());
        assertEquals(WebSize.MINIMUM, spec.size());
        assertTrue(spec.debug());
    }

    @Test
    @DisplayName("a page is not a widget, and there is no markup node for one")
    void aPageIsNotAWidget() {
        // The claim ADR-0441 turns on, asserted rather than left to the prose: if
        // `WebPage` ever became a Widget, it would have acquired a box the toolkit
        // cannot lay out and a cascade that cannot reach WebKit.
        assertFalse(io.github.digitalsmile.goldberry.widget.Widget.class.isAssignableFrom(WebPage.class));
        assertFalse(BackendWebView.class.isAssignableFrom(WebPage.class));
    }
}
