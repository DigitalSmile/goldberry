/// `docs/core-widgets.md` §5's `split-pane` — two children and a divider you can
/// drag, or move from the keyboard.
///
/// [io.github.digitalsmile.goldberry.widgets.panel.split.SplitPane] keeps its
/// position as a fraction and the minimums as pixels, either retaining the position
/// itself or taking it from the application.
/// [io.github.digitalsmile.goldberry.widgets.panel.split.SplitAxis] names the
/// arrangement of the two panes, not the divider. The divider and the sides are
/// parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.panel.split;

import org.jspecify.annotations.NullMarked;
