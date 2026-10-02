package dev.goldberry.render.web;

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import dev.goldberry.natives.webview.Webview;

/// [BackendWebView] over `:natives`' [Webview].
///
/// Thin on purpose. The wrapper below already owns the handle, keeps the
/// UI-thread rule and turns a failed call into a logged warning rather than an
/// exception; what this class adds is the toolkit's own vocabulary, so that
/// nothing above `:core` ever names a type of `:natives`.
final class NativeWebView implements BackendWebView {

    private final Webview webview;

    NativeWebView(Webview webview) {
        this.webview = Objects.requireNonNull(webview, "webview");
    }

    @Override
    public void navigate(String url) {
        webview.navigate(url);
    }

    @Override
    public void html(String html) {
        webview.html(html);
    }

    @Override
    public void title(String title) {
        webview.title(title);
    }

    @Override
    public void size(int width, int height, WebSize size) {
        webview.size(width, height, WebViewEngine.translate(size));
    }

    @Override
    public void bounds(int x, int y, int width, int height) {
        webview.bounds(x, y, width, height);
    }

    @Override
    public void eval(String script) {
        webview.eval(script);
    }

    @Override
    public CompletionStage<List<HttpCookie>> cookies(URI url) {
        Objects.requireNonNull(url, "url");
        if (isClosed()) {
            return CompletableFuture.failedFuture(new IllegalStateException("this page is closed"));
        }
        return webview.cookies(url.toString());
    }

    @Override
    public WebLoad loadState() {
        return isClosed() ? WebLoad.UNKNOWN : WebViewEngine.translate(webview.loadState());
    }

    @Override
    public boolean hasKeyboardFocus() {
        return !isClosed() && webview.hasFocus();
    }

    @Override
    public void blur() {
        if (!isClosed()) {
            webview.blur();
        }
    }

    @Override
    public boolean isClosed() {
        return webview.isClosed();
    }

    @Override
    public void close() {
        webview.close();
    }
}
