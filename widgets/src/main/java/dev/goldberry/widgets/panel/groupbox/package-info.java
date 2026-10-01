/// `docs/core-widgets.md` §5's `group-box` — a titled frame around a cluster of
/// settings.
///
/// [dev.goldberry.widgets.panel.groupbox.GroupBox] draws the frame
/// round both the title and the body rather than through the border as a `fieldset`
/// does (ADR-0166). The title and the body are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.panel.groupbox;

import org.jspecify.annotations.NullMarked;
