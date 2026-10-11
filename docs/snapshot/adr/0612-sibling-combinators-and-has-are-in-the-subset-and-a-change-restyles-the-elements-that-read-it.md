# ADR-0612: Sibling combinators and `:has()` are in the subset, and a change restyles the elements that read it

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-43), `docs/css-support-2026-10-10.md`
  §2; withdraws the part of ADR-0606 that kept `:has()` out; the guide's
  "Not in the subset" paragraph

## Context

The selector subset stopped at what a walk up the ancestor chain can answer.
`StyleElement` said where an element sits among its siblings, as a number,
and handed out nothing else, and the guide gave that as the reason `+`, `~`
and `:has()` could not be written: each makes one element's match depend on
another element's content, and the tree only invalidated elements whose own
position changed.

CSS-21 (ADR-0606) changed the first half. `:nth-child(An+B of S)` needed the
siblings themselves, so `StyleElement.siblings()` exists, and a sheet with
`of S` already restyles every child of a parent that rebuilds and the
siblings of a child whose state changes. What was left was `:has()`, which
needs a change to restyle the elements *above* it.

The support analysis lists all three among the selectors a sheet written
elsewhere uses most: `label + input`, `h2 ~ p`, and `:has()` for a container
that reacts to what it holds (`field:has(:invalid)`,
`card:has(:checked)`). It also lists attribute selectors. Those would expose a
widget's markup properties to the cascade, which the guide refuses on
purpose: a state a stylesheet should see is a class or a pseudo-class the
widget chooses to publish.

The decision was the user's, on 2026-10-11: `+`, `~` and `:has()` in;
attribute selectors stay out.

## Decision

**`+`, `~` and `:has()` are matched as CSS Selectors 4 says. A sheet that
writes one makes the element tree restyle the elements that can read a
change, and a sheet that writes none costs nothing.**

- `Selector.Combinator` gains `NEXT_SIBLING` and `SUBSEQUENT_SIBLING`. The
  matcher reads them off `StyleElement.siblings()`, by identity, with the
  element's own index tried first; `~` backtracks across earlier siblings as
  the descendant combinator does across ancestors.
- `Functional.Has` holds **relative** selectors. The leftmost part's
  combinator, which is `NONE` in an ordinary selector, says where the argument
  starts: a descendant when nothing is written, or `>`, `+` or `~`. The
  matcher searches down from the element, or along and below its later
  siblings for an argument that starts at one. `StyleElement.children()` is
  new for it, empty by default. Specificity is the most specific argument, as
  for `:is()`. A `:has()` inside another is refused, as CSS refuses it.
- The parser reads the two combinators anywhere a selector is read, including
  a nested rule's leading `+ b` and `~ b`. Attribute selectors and namespaces
  are still refused.
- **Invalidation.** A sibling combinator makes
  `StyleResolver.siblingsMatter()` true, which already restyles every child of
  a rebuilt parent and the siblings of a changed child. A compound joined by
  one to its left counts as reading a position, so in an ancestor position
  (`a + b text`) its subtree goes with it. A `:has()` builds an index of the
  types its compounds name, whether any names none, which of them stand in an
  ancestor position, and whether an argument starts at a sibling.
  `StyleResolver.hasReach(type)` answers `NONE`, `OWN` or `SUBTREE` from it.
  `Element` walks up from every change that alters what matches it (a
  pseudo-class, a frame class, a re-description with new classes, a child
  list that gained, lost or reordered elements) and restyles each ancestor as
  the index says, and the siblings before each one when an argument starts at
  a sibling. A `:has()` compound in an ancestor position followed by `+` or
  `~` also restyles the siblings after it.
- **No frame of lag.** A widget's own states (`:checked`, `:disabled`,
  `:invalid`, …) were mirrored onto its element as the styling walk reached
  it, after its ancestors and earlier siblings were already styled. Under a
  sheet that reads siblings or `:has()`, the renderer mirrors every state in a
  pass before the walk. Mirroring is idempotent, so the walk doing it again
  costs a comparison.
- `StyleLint` builds a sibling combinator's probes as siblings under one
  parent rather than as a parent and a child.

## Consequences

- The toolkit's own sheets write neither, so `siblingsMatter()` and
  `hasMatters()` are false for them and every new path is two boolean reads.
- A `:has()` match is a subtree search. A rule like `*:has(x)` on a large tree
  is as expensive as it looks; the index narrows the restyle by type, not the
  search. That is the cost CSS has too, and nothing here hides it.
- The router's `:hover` chain now restyles `:has()` anchors on the way up
  when a sheet writes `:has(:hover)`. That is a walk to the root per change,
  narrowed by type.
- `:empty` and the other unplanned pseudo-classes that read children could
  now be written over `children()`. They stay unplanned rows until asked for.

## Status

Done 2026-10-11: `Selector.Combinator`, `Functional.Has`,
`StyleElement.children` (and `Element`'s), `SelectorMatcher`, the parser's
`explicitCombinator` and `relativeSelectors`, `StyleResolver.hasReach`,
`hasMatters`, `hasLooksAtSiblings` and `css.cascade.HasReach`,
`Element.restyleReaders` and `restyleHasAnchors`, `WidgetRenderer.mirrorAhead`,
`StyleLint`'s probe; `SiblingCombinatorTest`, `HasTest`, `HasReachTest`,
`SiblingAndHasRestyleTest`, the conformance rows.
