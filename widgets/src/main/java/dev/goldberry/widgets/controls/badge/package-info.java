/// `docs/core-widgets.md` §3's `badge` — a count or a status, typically composed
/// inside a `stack`.
///
/// [dev.goldberry.widgets.controls.badge.Badge] is the whole
/// package, and the one entry in §3 that is not a control: it is not focusable, holds
/// no value and has no parts. Its variants are classes, such as `badge.danger`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.controls.badge;

import org.jspecify.annotations.NullMarked;
