/// SDL3, the platform backend: its lifecycle, video, event, log and file dialog
/// calls, and the wrappers that carry a window across the module boundary without a
/// `MemorySegment`.
///
/// Exported to every module, unlike Blend2D's, HarfBuzz's and Yoga's wrappers,
/// because an application legitimately reaches a window, a tray or a cursor —
/// which is why a window's pointer stays package-private here. SDL's failures are
/// turned into exceptions at the boundary rather than left as a `false` to check.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.sdl;

import org.jspecify.annotations.NullMarked;
