/// The CSS engine's front door: a parsed [dev.goldberry.css.Stylesheet], the
/// [dev.goldberry.css.Theme]s that ship with the toolkit, and the
/// [dev.goldberry.css.ComputedStyle] every render object is styled by.
///
/// An application loads its stylesheets here and reads nothing else; the stages
/// in between (tokens, selectors, the cascade, value types) are the sub-packages.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html).
@NullMarked
package dev.goldberry.css;

import org.jspecify.annotations.NullMarked;
