# 402. A sheet has a position in its layer

Date: 2026-09-18

## Status

Accepted. Answers C4 and C5 of the whole-tree review recorded in
`docs/review-2026-09-18.md`.

## Context

CSS resolves a property by asking, in order: is it `!important`; which cascade
layer; how specific is the selector; and, when all of those tie, **which
declaration came later in source order**. The last of those was wrong here.

`Match.order` was `StyleRule.order`, which `CssParser` assigns as the rule's index
**within its own sheet**. Once the type buckets flatten several sheets into one
matching pass, that number is all the cascade has, and it means nothing across
sheets: an earlier sheet's rule 3 beat a later sheet's rule 0 at equal
specificity. Three of the toolkit's own sheets sit in `TOOLKIT_BASE` together —
`controls.css`, `MarkdownStyles` and `HtmlStyles` — so this is not a
hypothetical about applications.

The lint had the mirror of the same confusion. `StyleLint.checkRule` resolved the
element and then looked each declaration's **property** up in the result, which
is the cascade's *winner* for that property and not this declaration's value. A
declaration that lost was therefore checked against somebody else's value: a bad
loser produced no finding at all, and a finding that did fire printed the
winner's value at the loser's line and column. The comment claiming that
`resolve` returns `null` for an overridden rule was false — the property is in
the result either way, which is exactly the problem.

## Decision

**A sheet carries its index, and the cascade compares it between layer and rule
order.**

`Candidate` already existed to carry the layer back after the buckets flatten the
sheets away; it carries the sheet index too. `CASCADE` therefore sorts by
`!important`, then layer, then specificity, then **sheet**, then the rule's index
within that sheet.

Placing it between layer and rule order is what makes the change provably narrow:
the new key is only ever consulted when two matches already agree on
`!important`, specificity and layer, so no layer comparison and no specificity
comparison can change. It separates exactly the pairs that were previously
separated by the wrong number.

**The lint asks for a written value rather than a resolved one.** `StyleResolver`
gained `substitutedFor(element, value)`, which substitutes a declaration's
`var()`s against an element **without cascading**, and `checkRule` checks that.
A losing declaration is now checked on its own value, at its own line.

## Alternatives considered

- **A composite order, `sheetIndex * K + order`.** It needs a bound on the number
  of rules in a sheet, and past that bound it silently transposes — the failure
  mode is the bug being fixed, back again and harder to find. Rejected for being
  a smaller change that is not actually correct.
- **A global counter assigned at registration.** The only place to put it is
  `StyleRule.order`, a public record component documented as "the rule's position
  in its stylesheet" and produced by `CssParser`, which does not know it is inside
  a sheet, let alone which one. Renumbering at `StyleResolver` construction would
  rewrite every rule and make `order` mean different things in a `Stylesheet` a
  test built and one a resolver had seen. The sheet index is also strictly more
  information: it is still possible to ask which sheet a declaration came from,
  which a flattened counter throws away.

## Consequences

- Two sheets in one layer cascade by the order they were added.
  `StyleResolverTest.Cascade.sheetOrderWithinALayer` holds it, with
  `sheetOrderReversed`, `layerBeatsSheetOrder` and `specificityBeatsSheetOrder`
  pinning the two orderings the new key must not disturb.
- `resolveStarting` is fixed for free: `@starting-style` rules now sort into their
  own sheet's position instead of being interleaved with ordinary rules by bare
  index.
- The lint is stricter and found nothing new in the toolkit's own sheets —
  `SupportedPropertyTest` holds them to zero findings under three theme and
  density combinations, and still does. What changed is that a *future* bad
  declaration under a good one will now be reported, at its own line, which it
  would not have been.
- Nothing shipped renders differently: no golden image moved. That is worth
  stating rather than assuming, because a cascade change is exactly the kind that
  should have moved one — and the reason it did not is that the three
  `TOOLKIT_BASE` sheets do not currently collide at equal specificity.
