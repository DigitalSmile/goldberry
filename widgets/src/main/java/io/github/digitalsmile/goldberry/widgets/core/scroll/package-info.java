/// `docs/core-widgets.md` §1's `scroll` — a viewport that shows part of something
/// larger than itself, on one or both axes, with overlay scrollbars.
///
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll] is the widget,
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis] and
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAnchor] say which way
/// it moves and where it rests as its content changes.
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController] is the
/// handle an owner holds to scroll a child into view, and
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollScope] finds the
/// viewport around a widget from the widget itself.
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.Fitted] puts popup content
/// that is taller than the screen into a viewport, which is how `menu` and `select`
/// stay whole. The viewport, content and thumb are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.core.scroll;

import org.jspecify.annotations.NullMarked;
