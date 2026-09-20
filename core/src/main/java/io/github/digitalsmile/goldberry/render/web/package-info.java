/// §9's `web-view`: a page, in a window the engine owns.
///
/// [io.github.digitalsmile.goldberry.render.web.WebViewSpec] describes one and
/// [io.github.digitalsmile.goldberry.render.web.BackendWebView] is the handle
/// that drives it — the split `tray` has, because opening is not a value.
/// [io.github.digitalsmile.goldberry.render.web.WebViewEngine] is the seam onto
/// `:natives` and is named by the backend and the frame loop alone.
///
/// **Nothing here is a widget.** A page is a top-level window of WebKitGTK's,
/// WebView2's or WKWebView's making, with no box and no place in the element
/// tree. `webview/webview` cannot render offscreen, and Wayland permits neither
/// reparenting a foreign surface nor placing a window where a widget is — so
/// there is no shape a page could take that would be the same on all four
/// platforms *and* live in a layout. See
/// [ADR-0441](../../../../../../../../book/src/adr/0441-a-web-page-is-a-window-not-a-box.md),
/// and `WebViews` in `:widgets` for the door an application uses.
package io.github.digitalsmile.goldberry.render.web;
