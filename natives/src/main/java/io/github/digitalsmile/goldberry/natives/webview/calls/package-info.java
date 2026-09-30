/// The downcall holders for `libgoldberry-webview`, the one library this module
/// binds that is built beside `libgoldberry` rather than into it, and opened on
/// demand. None of its functions is `webview/webview`'s own.
///
/// **Not exported** (ADR-0173). The wrappers in `…natives.webview` are the callers.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.webview.calls;

import org.jspecify.annotations.NullMarked;
