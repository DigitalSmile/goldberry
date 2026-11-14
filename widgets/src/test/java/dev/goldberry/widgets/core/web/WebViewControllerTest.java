package dev.goldberry.widgets.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.shell.web.WebPage;

/// What an application says to a page: script run in it, a message posted
/// to it, and scripts run before its own.
@DisplayName("a web view's controller")
class WebViewControllerTest {

    @Test
    @DisplayName("runs script in an open page, and says when there is none")
    void evaluates() {
        var controller = new WebViewController();
        assertFalse(controller.eval("go()"), "no page yet");

        var page = new TestHost.FakeWebView();
        controller.attach(page);
        assertTrue(controller.eval("go()"));
        assertEquals("eval(go())", page.calls().getLast());

        controller.detach(page);
        assertFalse(controller.postMessage("{}"), "gone again");
    }

    @Test
    @DisplayName("posts a message as a message event whose data is the JSON as it is")
    void postsMessages() {
        var controller = new WebViewController();
        var page = new TestHost.FakeWebView();
        controller.attach(page);

        assertTrue(controller.postMessage("{\"type\":\"theme\",\"dark\":true}"));

        assertEquals(
                "eval(window.dispatchEvent(new MessageEvent(\"message\", { data: {\"type\":\"theme\",\"dark\":true} }));)",
                page.calls().getLast());
    }

    @Test
    @DisplayName("hands the page's init scripts to the engine in order, and they do not change what it shows")
    void initScripts() {
        var page = WebPage.of("https://app.example.org/")
                .initScript("window.A = 1;")
                .initScript("window.B = 2;");
        var widget = new WebView(page, null).initScript("window.C = 3;");

        assertEquals(
                List.of("window.A = 1;", "window.B = 2;", "window.C = 3;"),
                widget.page().spec().initScripts());
        assertTrue(page.showsSameAs(WebPage.of("https://app.example.org/")), "a script is not content");
    }
}
