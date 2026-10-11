# ADR-0606: A selector may take a selector list, and `:is()`, `:not()` and `:where()` follow CSS

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-21), `docs/css-support-2026-10-10.md`
  §2; withdraws the guide's argument against `:not(:disabled)`

## Context

The selector subset had no pseudo-class that takes a selector, and the guide
argued for that. The router refuses `:hover` and `:active` to a disabled
widget, so "a disabled control stays dull without `:not(:disabled):hover`",
and the absence of `:not()` kept the resolver's two indexes simple: a rule's
subject type is its rightmost compound's, and a pseudo-class in an ancestor
position is on a compound with one type.

The support analysis counted `:not()`, `:is()` and `:where()` among the gaps
a sheet written elsewhere hits first. Frameworks write `:where()` for every
default they ship, so that any rule overrides it, and `:not(.selected)` is
the ordinary way to say "the others". `:nth-child(An+B of S)` was refused by
CSS-01 because there was nowhere to keep `S`.

The router's refusal of `:hover` on a disabled widget is still the right
default; it is not what stood in the way.

## Decision

**A compound may hold functional pseudo-classes that take a selector list,
matched and weighed as CSS Selectors 4 says, and the resolver's indexes walk
into them.**

- `Selector.Compound` gains `functional`, a list of the sealed
  `dev.goldberry.css.select.Functional`: `Is`, `Where`, `Not` and `NthOf`.
  Every argument is a complex selector, combinators included, matched against
  the element the compound is on.
- Specificity: `:is()` and `:not()` add their most specific argument,
  `:where()` adds nothing, `:nth-child(An+B of S)` adds a pseudo-class and the
  most specific of `S`.
- `:nth-child(An+B of S)` and `:nth-last-child(An+B of S)` count the siblings
  that match `S`, through a new `StyleElement.siblings()`. `of S` on
  `:nth-of-type()` is malformed, as in CSS.
- The argument list is **unforgiving**. An argument outside the subset, such
  as `:is(a + b)`, drops the whole rule in a lenient sheet and refuses a strict
  one, as anywhere else in the rule. CSS's `:is()` and `:where()` would ignore
  that one argument; the subset says what it refused instead.
- The resolver walks into arguments for each index. A rule whose subject is
  `:is(a, b)` or `:where(a, b)` and names no type of its own is bucketed under
  every type its arguments name, or as untyped when one names none. A
  pseudo-class inside an argument in an ancestor position (`:is(row:hover)
  text`) reaches descendants exactly as one outside it does, and so does a
  structural pseudo-class.
- A sheet with an `of S` count makes `StyleResolver.siblingsMatter()` true.
  Then a parent that rebuilds restyles every child, whether or not its index
  moved, and a child whose state changes restyles its siblings: either can
  change which siblings match `S`. A sheet without one pays nothing.

## Consequences

- The toolkit's own sheets do not change: the router's rule about `:hover` on
  a disabled widget stays, and `controls.css` writes no functional
  pseudo-class.
- `:has()` stays refused (CSS-43): it needs invalidation upward, which none of
  these do.
- Nesting (CSS-23) can now flatten a parent selector list into `:is(…)`.

## Status

Done 2026-10-11: `Functional`, `Compound.functional`, `StyleElement.siblings`
(and `Element`'s), the matcher, the parser's `argumentSelectors`, the
resolver's `eachCompound` walk, `subjectTypes` and `siblingsMatter`, and tests
(`FunctionalPseudoClassTest`, `FilteredSiblingsTest`, `LenientParseTest`, the
conformance table).
