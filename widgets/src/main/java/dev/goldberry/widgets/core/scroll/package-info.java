/// The `scroll` widget — a viewport that shows part of something larger than
/// itself, on one or both axes, with overlay scrollbars.
///
/// [dev.goldberry.widgets.core.scroll.Scroll] is the widget,
/// [dev.goldberry.widgets.core.scroll.ScrollAxis] and
/// [dev.goldberry.widgets.core.scroll.ScrollAnchor] say which way
/// it moves and where it rests as its content changes.
/// [dev.goldberry.widgets.core.scroll.ScrollController] is the
/// handle an owner holds to scroll a child into view, and
/// [dev.goldberry.widgets.core.scroll.ScrollScope] finds the
/// viewport around a widget from the widget itself.
/// [dev.goldberry.widgets.core.scroll.EdgeScroll] carries a
/// viewport on while a drag is held at its edge, which is how a selection keeps
/// going past the bottom of a pane.
/// [dev.goldberry.widgets.core.scroll.Fitted] puts popup content
/// that is taller than the screen into a viewport, which is how `menu` and `select`
/// stay whole. The viewport, content and thumb are parts.
///
/// Null-marked: every reference is non-null unless annotated otherwise.
///
/// Read more: [Scroll](https://goldberry.dev/docs/layout/scroll.html#scroll).
@NullMarked
package dev.goldberry.widgets.core.scroll;

import org.jspecify.annotations.NullMarked;
