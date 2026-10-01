/// `docs/core-widgets.md` §3's `checkbox` — a binary or tri-state choice whose value
/// is bound from outside and whose label is part of the click target.
///
/// [dev.goldberry.widgets.controls.checkbox.Checkbox] is the
/// widget.
/// [dev.goldberry.widgets.controls.checkbox.CheckIndicator], the
/// 16px glyph, is a part that is public only so that a `tree` row can draw the same
/// box; it is CSS-selectable as `check-indicator` and has no markup node.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.controls.checkbox;

import org.jspecify.annotations.NullMarked;
