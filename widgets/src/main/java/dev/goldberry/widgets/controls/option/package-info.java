/// `docs/core-widgets.md` §3's `option` — one choice, the child node that `segmented`
/// and `select` share, in a package of its own because it has two callers (ADR-0141).
///
/// [dev.goldberry.widgets.controls.option.Option] is a value, a
/// label and an icon.
/// [dev.goldberry.widgets.controls.option.Suggested] lets a
/// document name the value a field's suggestions arrive in, for `text-input
/// suggestions=` and `select options=` (ADR-0367).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.controls.option;

import org.jspecify.annotations.NullMarked;
