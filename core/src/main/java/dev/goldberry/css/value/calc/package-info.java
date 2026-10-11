/// CSS's math functions: `calc()`, `min()`, `max()`, `clamp()`, `round()`,
/// `mod()`, `rem()`, `abs()` and `sign()`, read into a tree and folded to one
/// value when a style is computed.
///
/// The tree is a sealed [dev.goldberry.css.value.calc.CalcNode], so the fold is a
/// `switch` with no default arm. Folding asks the caller what each unit is
/// worth, so this package knows nothing about fonts or windows: the length
/// reader answers for `em`, `vw` and `ch`, and everything comes back as a
/// [dev.goldberry.css.value.calc.Quantity], a number of pixels, degrees or
/// milliseconds plus a percentage still waiting for its basis.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
@NullMarked
package dev.goldberry.css.value.calc;

import org.jspecify.annotations.NullMarked;
