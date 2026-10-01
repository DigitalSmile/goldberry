/// `docs/core-widgets.md` §5's `collapse` — a header and a body that folds away.
///
/// [dev.goldberry.widgets.panel.collapse.Collapse] unmounts its
/// body while closed rather than hiding it, so a closed section keeps no
/// subscriptions alive. It can keep its own open state or be controlled by the
/// application; several in an accordion column become exclusive. The header, the
/// chevron and the body are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.panel.collapse;

import org.jspecify.annotations.NullMarked;
