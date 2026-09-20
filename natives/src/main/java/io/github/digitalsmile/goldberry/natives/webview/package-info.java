/// `webview/webview`, behind §9's `web-view` — the one native dependency of this
/// project that is allowed to be absent.
///
/// [io.github.digitalsmile.goldberry.natives.webview.Webview] is the owning
/// wrapper and [io.github.digitalsmile.goldberry.natives.webview.WebviewLibrary]
/// is what finds the library it binds. Everything here traffics in Java types; the
/// `calls` package beside it holds the downcalls and is not exported.
///
/// Unlike every other wrapper in this module, the library behind this one lives
/// outside `libgoldberry` and is opened on demand — see
/// [io.github.digitalsmile.goldberry.natives.webview.WebviewLibrary] for why GTK
/// and WebKit must not become load-time dependencies of the toolkit, and
/// [ADR-0441] for why a page is a window rather than a widget.
package io.github.digitalsmile.goldberry.natives.webview;
