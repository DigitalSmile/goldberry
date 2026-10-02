/// The backend SPI's web page: a page in a window the engine owns, or embedded
/// in the application's where the window system allows it.
///
/// [dev.goldberry.render.web.WebViewSpec] describes one and
/// [dev.goldberry.render.web.BackendWebView] is the handle
/// that drives it — the split `tray` has, because opening is not a value.
/// [dev.goldberry.render.web.WebViewEngine] is the seam onto
/// `:natives` and is named by the backend and the frame loop alone.
///
/// **Nothing here is a widget.** A page is a top-level window of WebKitGTK's,
/// WebView2's or WKWebView's making, with no box and no place in the element
/// tree. `webview/webview` cannot render offscreen, and Wayland permits neither
/// reparenting a foreign surface nor placing a window where a widget is — so
/// there is no shape a page could take that would be the same on all four
/// platforms *and* live in a layout. `WebViews` in `:widgets` is the door an
/// application uses. Exported to every module.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html#the-web-view).
@NullMarked
package dev.goldberry.render.web;

import org.jspecify.annotations.NullMarked;
