/// The downcall holders for SDL3: one record per subject — windows, events,
/// displays, cursors, the clipboard, the tray, file dialogs, audio streams, logging
/// and the GPU API — with one static final handle per function.
///
/// **Not exported** (ADR-0173). The callers are the wrappers in `…natives.sdl` and
/// its subpackages.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.sdl.calls;

import org.jspecify.annotations.NullMarked;
