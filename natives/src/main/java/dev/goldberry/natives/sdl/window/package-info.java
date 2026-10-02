/// The plain values a window is created and described with: creation flags, the
/// pixel formats the CPU present path accepts, icon images, and the platform's own
/// handle for a window — the escape hatch for embedding foreign content.
///
/// Exported to every module.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.sdl.window;

import org.jspecify.annotations.NullMarked;
