package dev.goldberry.widgets.core.web;

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.web.BackendWebView;

/// A handle on the page a [WebView] has open, for what can only be asked of the
/// page itself: its cookies.
///
/// ```java
/// private final WebViewController signIn = new WebViewController();
/// // …
/// new WebView(WebPage.of(grafana).onNavigate(this::arrived)).controller(signIn)
/// // …
/// signIn.cookies(URI.create(grafana)).thenAccept(this::keepSession);
/// ```
///
/// An object rather than a widget for `ScrollController`'s reason: the widget
/// is a value rebuilt every frame and the page is a platform handle, so the
/// part an application holds on to has to be something that outlives both.
///
/// ## Lifetime
///
/// Create one and keep it — an application field, or a state's. It is inert
/// until the widget it is given to has opened its page, which is a frame or two
/// after the widget is first built, and inert again once that widget is gone.
/// Asking an inert controller is not an error: the answer is a stage that has
/// already failed, saying so.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html#the-web-view).
public final class WebViewController {

    /// The open page, or null before one is open and after it has closed. Set
    /// only by the widget's state.
    private @Nullable BackendWebView page;

    /// A controller with no page yet.
    public WebViewController() {}

    /// Whether a page is open under this controller.
    public boolean isOpen() {
        return page != null && !page.isClosed();
    }

    /// The cookies the page's engine would send to `url`, HttpOnly ones
    /// included — see `BackendWebView.cookies`, which this asks.
    ///
    /// @param url an absolute URL
    /// @return the cookies, completing on the UI thread; failed with
    ///         [IllegalStateException] while no page is open
    public CompletionStage<List<HttpCookie>> cookies(URI url) {
        Objects.requireNonNull(url, "url");
        var current = page;
        if (current == null || current.isClosed()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("no page is open under this controller yet"));
        }
        return current.cookies(url);
    }

    /// Runs `script` in the page: what the application says to a page that
    /// listens. Nothing comes back; an answer reaches the application through
    /// a callback the page calls (`WebPage.on`).
    ///
    /// @return false while no page is open, and nothing was run
    public boolean eval(String script) {
        Objects.requireNonNull(script, "script");
        var current = page;
        if (current == null || current.isClosed()) {
            return false;
        }
        current.eval(script);
        return true;
    }

    /// Hands `json` to the page as a `message` event on its `window`, as a
    /// browser's `postMessage` does: a page listens with
    /// `window.addEventListener("message", e => …)` and reads `e.data`.
    ///
    /// @param json a JSON value — an object, an array, a string in quotes, a
    ///        number — which becomes the event's `data` as it is, with no
    ///        second encoding
    /// @return false while no page is open, and nothing was sent
    public boolean postMessage(String json) {
        Objects.requireNonNull(json, "json");
        return eval(dispatch(json));
    }

    /// The script that dispatches `json` as a message event.
    static String dispatch(String json) {
        return "window.dispatchEvent(new MessageEvent(\"message\", { data: " + json + " }));";
    }

    /// Called by the widget's state when its page opens.
    void attach(BackendWebView opened) {
        page = Objects.requireNonNull(opened, "opened");
    }

    /// Called by the widget's state when its page closes or it lets go of this
    /// controller. Only `closing`'s own: a controller handed on to another
    /// widget meanwhile keeps that one's page.
    void detach(BackendWebView closing) {
        if (page == closing) {
            page = null;
        }
    }
}
