# ADR-0607: A rule may hold rules, and the parser flattens them

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-01, CSS-23), ADR-0606,
  `docs/css-support-2026-10-10.md` §1

## Context

CSS nesting is how a sheet written today is written. Until CSS-01 a nested
rule made the whole sheet malformed; CSS-01 made it a refused rule, so a
lenient sheet lost the rule that held it and kept the rest. The support
analysis put nesting among the first things a sheet from elsewhere trips on.

The rest of the engine has no idea what nesting is, and has no need to: a
nested rule means exactly what one flat rule with a longer selector means,
once `&` is resolved. CSS defines it that way, with `&` read as `:is()` of the
outer rule's selectors, which ADR-0606 made available.

## Decision

**Nesting is in the subset, and is gone by the time the parser returns: every
nested rule is an ordinary `StyleRule`.**

- A rule's block may hold declarations, style rules and `@media` blocks.
  `@media` inside a rule holds declarations for that rule's selector and rules
  of its own, under the enclosing condition joined with its own. Any other
  at-rule inside a rule is outside the subset.
- `&` is `:is(outer selectors)`, which gives CSS's specificity. A nested
  selector without `&` is relative: `b` is `& b`, and `> b` is `& > b`.
- When the outer rule has one selector, `&` is written into the nested
  selector rather than kept as `:is()`: its parts take `&`'s place, and what
  the nested compound adds to `&` (`&.open`, `&:hover`, `div&`) joins the outer
  rule's rightmost compound. It matches and weighs the same, and keeps the
  resolver's type buckets exact. A compound that would name two types or two
  ids keeps the `:is()`.
- Order follows CSS: the declarations before the first nested rule are the
  rule, each nested rule follows, and declarations after a nested rule are a
  rule of their own with the outer selectors, placed after it.
- A nested rule outside the subset, `& + b` say, is dropped by itself in a
  lenient sheet: the outer rule and its other nested rules are kept. A strict
  sheet refuses it, as before.

## Consequences

- No stage after the parser changed. A flattened sheet is indistinguishable
  from one written flat, so the cascade, the indexes and the lint all see what
  they always saw.
- A keyframe's block still holds declarations only.
- The toolkit's own sheets stay flat; nothing about them had to change.

## Status

Done 2026-10-11: `CssParser.block`, `nestedRule`, `nestedSelectorList`,
`spliced` and `mediaCondition`; tests (`NestingTest`, `LenientParseTest`, the
conformance table).
