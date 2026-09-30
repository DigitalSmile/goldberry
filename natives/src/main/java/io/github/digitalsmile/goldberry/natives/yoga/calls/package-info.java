/// The downcall holders for Yoga: its config, its nodes, their style setters and
/// layout results, and the measure probe — one static final handle per function.
///
/// **Not exported** (ADR-0173). The package-private binding class in
/// `…natives.yoga` is their only caller.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.yoga.calls;

import org.jspecify.annotations.NullMarked;
