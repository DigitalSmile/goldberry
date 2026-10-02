/// What a widget is and what it is called, to something that cannot see it: roles,
/// names and live regions.
///
/// The role vocabulary is the catalogue's own rather than ARIA's: a role nothing
/// implements is a promise nobody keeps. Every focusable widget in the catalogue
/// implements it. Exported because a second catalogue would have to as well, and
/// because an accessibility bridge would read it from outside `:core`.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more:
/// [Semantics: a role and a name](https://goldberry.dev/docs/guide/writing-a-widget.html#semantics-a-role-and-a-name).
@NullMarked
package dev.goldberry.widget.semantics;

import org.jspecify.annotations.NullMarked;
