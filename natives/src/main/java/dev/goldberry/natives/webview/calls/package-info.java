/// The downcall holders for `libgoldberry-webview`, the one library this module
/// binds that is built beside `libgoldberry` rather than into it, and opened on
/// demand. None of its functions is `webview/webview`'s own.
///
/// **Not exported**, like every `…calls` package. The wrappers in `…natives.webview`
/// are the callers.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.webview.calls;

import org.jspecify.annotations.NullMarked;
