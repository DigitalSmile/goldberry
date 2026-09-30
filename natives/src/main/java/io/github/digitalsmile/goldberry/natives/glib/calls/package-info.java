/// The downcall holders for GLib's two logging hooks — the only holders in this
/// module bound against a system library found by soname, rather than against
/// `libgoldberry` or `libgoldberry-webview`.
///
/// **Not exported** (ADR-0173). The log bridge in `…natives.glib` is the one caller.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.glib.calls;

import org.jspecify.annotations.NullMarked;
