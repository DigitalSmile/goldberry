/// The structural primitives of `docs/core-widgets.md` §1 — `row`, `column`, `stack`
/// and `spacer` — that every layout is built from and that paint nothing of their
/// own.
///
/// They lived in `:core` until ADR-0092 moved them here as ordinary widgets.
/// [dev.goldberry.widgets.core.Primitives] lists the structural
/// node names a document may write, kept apart from the control catalog so that an
/// application wanting a layout and no controls can register these alone. The
/// subpackages hold the other §1 primitives, one per package: `affix`, `canvas`,
/// `image`, `qr-code`, `scroll` and the embedded `web-view`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.core;

import org.jspecify.annotations.NullMarked;
