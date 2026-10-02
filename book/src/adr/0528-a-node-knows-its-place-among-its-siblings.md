# ADR-0528: A node knows its place among its siblings

- **Status:** Accepted. Amends [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)
  ("there is no `indexInParent()`, so `:nth-child` cannot be expressed").
- **Date:** 2026-10-02
- **Relates to:** [ADR-0065](0065-a-part-is-styleable-and-not-constructible.md),
  [ADR-0352](0352-an-element-enters-from-its-starting-style.md),
  [ADR-0529](0529-an-applications-stylesheet-is-lenient-and-loud.md),
  `docs/goldberry-gaps.md` #15

## Context

ADR-0049 kept `StyleElement` to type, id, classes, parent and state, and drew
the line on purpose: "there is no `nextSibling()` and no `indexInParent()`, so
`+`, `~` and `:nth-child` cannot be expressed. Each of them forces the matcher
to know about ordering, and ordering is what makes invalidation expensive."

Deploy Orc reported what that costs an application. A lane banner and the
connectors between track steps (`::before`, `:first-child`) became extra
widgets; `:has(input:checked)` and `[aria-current]` became classes computed in
Java; `.acct-step > :last-child` stopped the application starting (ADR-0529).

The two halves of ADR-0049's sentence are not the same size. A sibling
combinator or `:has()` makes one node's match depend on **another node's
content**, so a change anywhere in a list can restyle anything after it. A
position is different: it is a number the element tree already computes,
because reconciling a parent's children is a walk over them in order. The cost
ADR-0049 feared is invalidating too much when the list changes, and that can be
bounded.

## Decision

**`:first-child`, `:last-child`, `:only-child`, `:nth-child(An+B)` and
`:nth-last-child(An+B)` are in the subset. Pseudo-elements, attribute
selectors, sibling combinators and `:has()` are not.**

- `StyleElement` gains `indexInParent()` and `siblingCount()`, default methods
  answering 0 of 1, so a root and a test's hand-written element need nothing.
- `Selector.Compound` gains `structural`, a list of the sealed `Structural`:
  `Nth(step, offset, fromEnd)` for the four `-child` forms, with `:first-child`
  being `Nth(0, 1, false)`, and `OnlyChild`. Each counts as a pseudo-class in
  specificity and prints as written. `odd`, `even`, `3`, `2n+1`, `-n+3`, with
  any spacing, are read by the parser; a formula that is none of them is a
  mistake rather than a missing feature.
- **The siblings are the children of `parent()`**, every child, composition
  nodes included. That is what the child combinator already reads, and it is
  how an unstyled `<div>` behaves in HTML: a card a stateless wrapper builds is
  the only child of that wrapper.
- **Invalidation is per child and only when the answer changes.** When an
  element reconciles its children, it tells each its new index and count. A
  child whose pair is unchanged is untouched. For one that moved,
  `StyleResolver.positionMatters(old, new)` asks every distinct structural
  pseudo-class in the sheets, an index built once per sheet set, whether its
  answer changed; only then is the child's style dropped, and its subtree only
  when a structural pseudo-class sits in an ancestor compound
  (`positionReachesDescendants`). Appending to a list under `:first-child`
  rules restyles nobody; under `:last-child`, the old last child alone.
- **Cost, measured.** Under sheets with no structural pseudo-class, which is
  all of the toolkit's, `positionMatters` is a loop over an empty list and a
  rebuild pays one integer comparison per child; the matcher pays one
  `isEmpty()` per compound, and the cascade one identity comparison per rule
  for `@media` (ADR-0526). `:widgets:benchmark` on this machine, the release
  tree against this change, medians:

  | | before | after |
  |---|---|---|
  | `DeepTreeStyleBenchmark`, depth 200, first frame `render()` | 4756 µs | 4738 µs |
  | the same, hover on the middle panel | 3065 µs | 3029 µs |
  | the same, no change | 179 µs | 176 µs |
  | `FrameBenchmark`, showcase frame, render (cascade + boxes) | 10.5 µs | 11.2 µs |
  | `FrameBenchmark`, whole frame | 808 µs | 794 µs |

  Every row is inside the run-to-run noise. `FrameBudgetTest` would have been
  the better witness and does not run on this tree: its showcase fixture stops
  before the first frame, where `AudioPlayer.inflate` is handed no player,
  which is outside anything this record touches.

### Not built, and what each would take

- **`::before` and `::after`.** A generated box is a node the cascade creates,
  with content from a property, and no element behind it. The render walk
  would have to synthesize a box per matching element, the hit test and the
  semantics tree would have to agree on whether it exists, and `content:`
  would be the first property that makes text. That is its own decision, after
  this batch; until then a decoration is a widget, which is what Deploy Orc
  built.
- **Attribute selectors.** The widgets carry no attribute map. Every
  attribute a stylesheet would select on is a field of a record, and state is
  already a pseudo-class or a class. `[aria-current]` is a class the widget
  sets, or a pseudo-class if it is state the toolkit owns.
- **`+`, `~` and `:has()`.** Each makes a match depend on another node's
  content, so one change can restyle every later sibling or every ancestor.
  `:has()` in particular inverts the matcher's direction: it walks down from a
  candidate rather than up. Both would need a dependency index from each node
  to the rules that read it. Not built; not planned.

In an application's sheet each of these drops its rule with a warning
(ADR-0529), so a sheet that uses them still loads.

## Consequences

- `.acct-step > :last-child`, `list-row:nth-child(even)` and
  `track-step:first-child` work as written.
- `html.css` and `markdown.css` mark a table's first row and cell from the
  builder "because the subset has no `:first-child`". The comments now say the
  builder's mark came first; the builders keep it, since moving it fixes
  nothing.
- A stateless wrapper around each list item makes every item an only child.
  The guide says so beside the table of pseudo-classes, because it is the one
  surprise this reading has.
