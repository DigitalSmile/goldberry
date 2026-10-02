/// `webview/webview`, behind the `web-view` widget — the one native dependency of
/// this project that is allowed to be absent.
///
/// [dev.goldberry.natives.webview.Webview] is the owning
/// wrapper and [dev.goldberry.natives.webview.WebviewLibrary]
/// is what finds the library it binds. Everything here traffics in Java types; the
/// `calls` package beside it holds the downcalls and is not exported.
///
/// Unlike every other wrapper in this module, the library behind this one lives
/// outside `libgoldberry` and is opened on demand — see
/// [dev.goldberry.natives.webview.WebviewLibrary] for why GTK
/// and WebKit must not become load-time dependencies of the toolkit. A page is a
/// window rather than a widget because Wayland permits neither reparenting a
/// foreign surface nor placing a window where a widget is.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.webview;

import org.jspecify.annotations.NullMarked;
