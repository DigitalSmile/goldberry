# ADR-0679: A length divided by a length is a number

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** the CSS batch's math functions (ADR-0600 to ADR-0622), ADR-0626 (the showcase's reading size)

## Context

A downstream game scales its HUD by `min(width / 2560, height / 1440)` of the
window. `vw`, `vh`, `min()` and `calc()` all existed, but the folder refused
any division by something that was not a number, so the ratio could not be
written. The game shipped a ladder of 35 `@media` rules instead, one per 0.05
step, and its UI-scale setting would have had to multiply a ladder.

Two ways out were weighed: setting a custom property on a node from Java (an
inline layer), or the typed arithmetic CSS Values 4 already specifies, where
`<length> / <length>` is a `<number>`. The owner chose the second on
2026-11-08: it is standard CSS, and it keeps the rule that a widget writes only
what a stylesheet could not.

## Decision

**`Folding.product` does typed arithmetic for division.**

- A divisor may be a length, an angle or a time, not only a number.
- A dimension divided by one of the same kind cancels into a number:
  `calc(100vw / 2560px)`, `calc(90deg / 180deg)`, `calc(1s / 500ms)`.
- What is left after cancelling must be a number or one dimension in the
  numerator. `2px * 3px`, `2 / 1px` and `1px / 1s` still do not fold.
- A percentage may not divide or be divided, because its pixels are not known
  until layout. It may still be scaled by a number.
- Division by zero still does not fold.

So `scale: min(100vw / 2560px, 100vh / 1440px)` is a declaration. A sheet that
writes a viewport unit already gets a new resolver on every resize, so the
scale follows the window.

## Consequences

- `FoldingTest`'s `calc(4px / 2px)` refusal became `calc(2 / 4px)`: the old
  case is valid CSS now.
- Every reader that folds math gains it: a `line-height` of `calc(24px / 16px)`
  is a ratio, and an `opacity` can be a quotient of lengths.
- Tested by `TypedDivisionTest` (the ratio, the HUD's `min()`, angles and
  times, the refusals, `scale` taking the ratio while `scale(calc(1px))` stays
  refused).
