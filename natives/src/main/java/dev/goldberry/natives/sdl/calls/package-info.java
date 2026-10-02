/// The downcall holders for SDL3: one record per subject — windows, events,
/// displays, cursors, the clipboard, the tray, file dialogs, audio streams, logging
/// and the GPU API — with one static final handle per function.
///
/// **Not exported**, like every `…calls` package. The callers are the wrappers in
/// `…natives.sdl` and its subpackages.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.sdl.calls;

import org.jspecify.annotations.NullMarked;
