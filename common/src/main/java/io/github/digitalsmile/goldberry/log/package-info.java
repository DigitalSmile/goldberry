/// Logging and the start-up timeline — what both halves of the toolkit need and
/// neither owns (ADR-0174).
///
/// **`@NullMarked`**, which is what puts this package under NullAway. Inside a
/// marked package every type is non-null unless it says `@Nullable`, and the
/// checker fails the build on a violation — a field that can be null and is
/// dereferenced, a `@Nullable` value returned where a caller may not expect one.
///
/// It is marked one package at a time on purpose. `docs/testing.md` §2 asks for
/// JSpecify on all public API, and a codebase this size cannot be annotated in
/// one commit; NullAway runs in `OnlyNullMarked` mode so an unmarked package is
/// simply invisible to it, and each one is checked from the moment it opts in.
/// This is the first, being the smallest and the one everything else depends on.
@NullMarked
package io.github.digitalsmile.goldberry.log;

import org.jspecify.annotations.NullMarked;
