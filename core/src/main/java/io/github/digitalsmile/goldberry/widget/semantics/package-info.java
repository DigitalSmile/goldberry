/// What a widget is and what it is called, to something that cannot see it — roles,
/// names, and the live regions of `docs/core-widgets.md` §7.
///
/// The role vocabulary is the catalog's own rather than ARIA's: a role nothing
/// implements is a promise nobody keeps. Every focusable widget in the catalog
/// implements it. Exported because a second catalog would have to as well, and
/// because an accessibility bridge would read it from outside `:core`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widget.semantics;

import org.jspecify.annotations.NullMarked;
