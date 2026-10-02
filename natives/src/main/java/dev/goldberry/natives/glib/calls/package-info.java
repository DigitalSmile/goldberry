/// The downcall holders for GLib's two logging hooks — the only holders in this
/// module bound against a system library found by soname, rather than against
/// `libgoldberry` or `libgoldberry-webview`.
///
/// **Not exported**, like every `…calls` package. The log bridge in `…natives.glib`
/// is the one caller.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.glib.calls;

import org.jspecify.annotations.NullMarked;
