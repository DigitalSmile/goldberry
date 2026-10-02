package dev.goldberry.widgets.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.panel.Described;
import dev.goldberry.widgets.shell.web.WebPage;

/// A `web-view` that a user signs in through: the application is asked where
/// the page is going, and can read the page's cookies through a controller.
@DisplayName("a web-view that signs in")
class WebViewSignInTest {

    private static final String ID = "sign-in";

    private static final URI CALLBACK = URI.create("myapp://callback?code=x");

    private static final URI ELSEWHERE = URI.create("https://example.org/next");

    private TestHost host;
    private ElementTree tree;
    private final WebViewController controller = new WebViewController();

    @BeforeEach
    void mount() {
        RendererRequirement.enforce();
        host = new TestHost().webViewAvailable(true).anchoring(ID, 0, 0, 640, 400);
    }

    @AfterEach
    void unmount() {
        if (tree != null) {
            tree.unmount();
        }
    }

    private WebView widget(Predicate<URI> decide) {
        return new WebView(WebPage.of("https://example.org/login"))
                .onNavigate(decide)
                .controller(controller)
                .withAttributes(Attributes.NONE.id(ID));
    }

    /// Mounts `widget` and paints once, which is what opens the page.
    private void open(WebView widget) {
        tree = new ElementTree(widget, host);
        tree.flush();
        var buffer = PixelBuffer.allocate(new PhysicalSize(16, 16), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = Frame.over(buffer, DisplayScale.ONE);
        try {
            Described.first(tree, Canvas.class).painter().paint(frame, new LogicalSize(640, 400));
        } finally {
            frame.end();
        }
        assertNotNull(host.lastEmbeddedPage(), "the first painted frame opens the page");
    }

    @Test
    @DisplayName("asks the page value's predicate, and cancels what it refuses")
    void asksBeforeNavigating() {
        open(widget(uri -> !"myapp".equals(uri.getScheme())));

        var hook = host.lastWebView().onNavigate();
        assertNotNull(hook, "the page was opened with no navigation hook");
        assertFalse(hook.test(CALLBACK), "the custom-scheme redirect went ahead");
        assertTrue(hook.test(ELSEWHERE));
    }

    @Test
    @DisplayName("asks the predicate the widget has now, without reopening the page")
    void followsARebuild() {
        open(widget(uri -> true));
        var hook = host.lastWebView().onNavigate();
        var page = host.lastEmbeddedPage();

        tree.update(widget(uri -> false));
        tree.flush();

        assertFalse(hook.test(ELSEWHERE), "the hook still asks the predicate the page opened with");
        assertEquals(page, host.lastEmbeddedPage(), "a new predicate reopened the page");
    }

    @Test
    @DisplayName("lets every navigation through when the page has no predicate")
    void noPredicate() {
        open(new WebView(WebPage.of("https://example.org/")).withAttributes(Attributes.NONE.id(ID)));

        assertTrue(host.lastWebView().onNavigate().test(CALLBACK));
    }

    @Test
    @DisplayName("reads the open page's cookies through its controller, HttpOnly ones included")
    void readsCookies() throws ExecutionException, InterruptedException {
        var session = new HttpCookie("grafana_session", "abc");
        session.setHttpOnly(true);
        open(widget(uri -> true));
        host.lastEmbeddedPage().holding(List.of(session));

        var cookies = controller
                .cookies(URI.create("https://example.org/"))
                .toCompletableFuture()
                .get();

        assertEquals(List.of(session), cookies);
        assertTrue(cookies.getFirst().isHttpOnly());
        assertTrue(host.lastEmbeddedPage().calls().contains("cookies(https://example.org/)"));
    }

    @Test
    @DisplayName("says so, rather than waiting, when no page is open under the controller")
    void inertController() {
        var before = controller.cookies(URI.create("https://example.org/")).toCompletableFuture();
        assertTrue(before.isCompletedExceptionally());

        open(widget(uri -> true));
        assertTrue(controller.isOpen());
        tree.unmount();
        tree = null;

        assertFalse(controller.isOpen(), "the controller still holds a page its widget closed");
        var after = controller.cookies(URI.create("https://example.org/")).toCompletableFuture();
        var failure = assertThrows(ExecutionException.class, after::get);
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }
}
