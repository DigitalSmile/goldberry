/// The plain values a window is created and described with: creation flags, the
/// pixel formats the CPU present path accepts, icon images, and the platform's own
/// handle for a window — the escape hatch for embedding foreign content
/// (`docs/ARCHITECTURE.md` §12).
///
/// Exported to every module.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.sdl.window;

import org.jspecify.annotations.NullMarked;
