/// Selectors: what a rule matches, how strongly, and the matcher that decides
/// whether one applies to an element.
///
/// The subset is type, `.class`, `#id`, the descendant and child combinators, and
/// a closed set of pseudo-classes for widget state. Matching walks up the
/// ancestor chain and never asks about order, which is what keeps restyling
/// cheap.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#selectors).
@NullMarked
package dev.goldberry.css.select;

import org.jspecify.annotations.NullMarked;
