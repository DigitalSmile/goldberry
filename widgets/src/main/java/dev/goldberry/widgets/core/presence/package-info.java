/// How an overlay or a panel arrives and leaves: the
/// [dev.goldberry.widgets.core.presence.Phase] it is in, and
/// the [dev.goldberry.widgets.core.presence.Departure] that
/// keeps it painted until its exit has played.
///
/// Nine packages share it. It is a lifecycle, not a structural primitive like
/// `column` or `row`, which is why it left `…widgets.core` (ADR-0496).
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package dev.goldberry.widgets.core.presence;

import org.jspecify.annotations.NullMarked;
