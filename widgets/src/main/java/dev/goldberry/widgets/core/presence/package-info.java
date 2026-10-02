/// How an overlay or a panel arrives and leaves: the
/// [dev.goldberry.widgets.core.presence.Phase] it is in, and
/// the [dev.goldberry.widgets.core.presence.Departure] that
/// keeps it painted until its exit has played.
///
/// Nine packages share it. It is a lifecycle, not a structural primitive like
/// `column` or `row`, which is why it has a package of its own.
///
/// Null-marked: every reference is non-null unless annotated otherwise.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html#motion).
@NullMarked
package dev.goldberry.widgets.core.presence;

import org.jspecify.annotations.NullMarked;
