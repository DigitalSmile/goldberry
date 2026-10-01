/// `docs/core-widgets.md` §1's `affix` — a child pinned to an edge of the nearest
/// `scroll` once it would have scrolled past it, such as a section header.
///
/// [dev.goldberry.widgets.core.affix.Affix] is the widget and
/// [dev.goldberry.widgets.core.affix.Edge] the side it pins to. It
/// leaves a same-sized hole in layout when it detaches, so nothing below it jumps;
/// the slot and the pinned content are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.core.affix;

import org.jspecify.annotations.NullMarked;
