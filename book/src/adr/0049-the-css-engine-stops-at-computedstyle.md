# ADR-0049: The CSS engine stops at ComputedStyle

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §8, §10; [ADR-0004](0004-three-tree-retained-declarative-model.md), [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)

## Context

M2 opens with "CSS engine". That is not one decision, and the interesting ones
are not about parsing — a tokenizer follows a specification. They are about where
the engine *ends*, what it refuses, and what it hands to the rest of the toolkit.

The engine also had to be buildable before the thing it styles exists.
[ADR-0004](0004-three-tree-retained-declarative-model.md) is accepted but its
element tree is not written, and waiting for it would have meant designing the
cascade against an imagined API.

## Decision

**The engine ends at [ComputedStyle], and produces no pixels and no `YGNode`.**
Tokens in, typed values out. §8 already called the layout/paint property split a
design invariant; making it the shape of one record is what stops it being a
convention that erodes. `Box.style(ComputedStyle)` is the whole join: the layout
half lands on the fields Yoga reads, the paint half on the ones Blend2D reads,
and nothing in between is a string.

**The subset is enforced, not approximated.** `[attr]`, `+`, `~`, `::before`,
`@supports` and unknown pseudo-classes are hard errors with a line and column.
The CSS specification says to discard what you do not understand, because a
browser must render pages written for browsers that did not exist yet. A toolkit
reads a stylesheet its own application shipped, and there a dropped rule is a
widget that is the wrong colour with nothing in the log. `:hovered` should not be
a rule that silently never matches.

**Strictness stops at the frame loop.** Parsing throws; *resolving* does not. An
unresolvable `var()` drops one declaration and warns; a value that will not parse
into its property drops that declaration and warns; an unknown property is
ignored at debug level. Those three run per node per restyle, inside the frame
loop, and taking a window down over one typo in a stylesheet is worse than
painting one thing wrong. Parse errors happen once, on load, where a stack trace
is useful.

**Matching is right to left, and backtracks.** [Selector] stores its parts
rightmost-first because that is the order matching reads them: the key compound
is the only one that must match the element itself, and the rest is a walk up the
ancestor chain. The walk backtracks, which is not optional — for `.a > .b .c`
against `.a > .b > .b > .c`, a greedy walk finds the inner `.b`, fails to find
`.a` as its parent, and wrongly reports no match.

**[StyleElement] is the seam, and its smallness is the design.** Four questions:
type, id, classes, parent, state. There is no `nextSibling()` and no
`indexInParent()`, so `+`, `~` and `:nth-child` *cannot* be expressed — which is
why §8's subset stops where it does. Each of them forces the matcher to know
about ordering, and ordering is what makes invalidation expensive.

**Layers sit after specificity.** The cascade key is
`(important, specificity, layer, order)`. §8 says "later layer wins **at equal
specificity**", which makes a layer an extension of source order rather than the
override `@layer` gives: a sharper toolkit rule still beats a vaguer application
one, exactly as two rules in one stylesheet would.

**A theme is a stylesheet and nothing else.** `nord-light.css` and
`nord-dark.css` contain no selector but `:root` and no property a widget rule
reads directly. Two tiers: the raw palette is theme-*invariant* — `--nord8` is a
fact about Nord — and the semantic tokens are what differ and what widgets
consume. A widget asks for `--gb-bg` and never learns which theme answered.

## Alternatives considered

- **A property table driving reflection into `ComputedStyle`.** Less code than the
  switch, and it would make every property a row rather than a case. Rejected for
  now: the switch is where the *type* of each property lives, and a table would
  have to encode that anyway in a form the compiler could not check.
- **Keyword-to-Yoga-enum mapping by hand.** Rejected: the two vocabularies
  already agree — Yoga's enum constants *are* the CSS keywords — so `space-between`
  to `SPACE_BETWEEN` is a name transform, and a table would be a second place for
  them to drift.
- **Resolving `em`/`rem` inside Yoga.** Yoga has no concept of a font size, so
  they are resolved at compute time against a [CssLength.Context]. The cost is
  that the caller must supply one; the alternative is a unit Yoga would have to
  be taught.
- **Shipping the full 148 CSS colour names.** Goldberry's palette comes from Nord
  through custom properties. A stylesheet reaching for `papayawhip` is not using
  the theming mechanism, and 148 names is 148 chances for `grey`/`gray` to look
  like a toolkit bug. The 16 Level 1 names are there, with both spellings of grey.

## Consequences

- **`ComputedStyle` is deliberately shorter than §8's property list.** It carries
  what `Box` can express and no more. A property that resolves into nothing is a
  property whose tests assert nothing, so each arrives with the thing that paints
  it: `flex-wrap`, `margin`, `min`/`max`, `position`, borders, shadows,
  transitions and the font properties are all still to come.
- **`opacity` resolves but is dropped by `Box.style`.** The group opacity CSS
  specifies needs a layer to composite through, not a colour to multiply into.
  Resolving it now means the parser and the tests are already right when that
  layer exists.
- **`Token.cssText()` exists because `text()` is not a round trip.** A hash holds
  `ff0000` without its `#` and a dimension holds `16` without its unit. Anything
  reassembling a value — a serialized style, a hot-reload diff, an error naming
  the value that failed — needs the spelling back.
- **`:root` is in the pseudo-class set although §8 does not list it.** §10 makes a
  theme a custom-property layer, and the only place to hang properties that
  everything inherits is the root. Without it the engine could not express its own
  themes. It is structural rather than a state, so unlike the other six it can
  never invalidate a subtree.
- **Nothing is cached yet.** No rule index by class or id, no memoised
  `ComputedStyle`, no invalidation beyond §8's "recompute the subtree". The
  cascade currently walks every rule in every stylesheet for every element. That
  is fine at the scale anything is tested at and will not be at widget scale; it
  is deliberately left until there is a tree big enough to measure against.
