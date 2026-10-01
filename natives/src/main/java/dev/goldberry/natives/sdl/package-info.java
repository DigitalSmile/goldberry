/// SDL3, the platform backend: its lifecycle, video, event, log and file dialog
/// calls, and the wrappers that carry a window across the module boundary without a
/// `MemorySegment` (ADR-0003).
///
/// Exported to every module, unlike Blend2D's, HarfBuzz's and Yoga's wrappers,
/// because an application legitimately reaches a window, a tray or a cursor —
/// which is why a window's pointer stays package-private here. SDL's failures are
/// turned into exceptions at the boundary rather than left as a `false` to check.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.sdl;

import org.jspecify.annotations.NullMarked;
