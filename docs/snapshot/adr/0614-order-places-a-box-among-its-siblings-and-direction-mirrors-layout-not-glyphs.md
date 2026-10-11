# ADR-0614: `order` places a box among its siblings, and `direction` mirrors layout, not glyphs

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-48), `docs/css-support-2026-10-10.md`
  §5; ADR-0598 (`text-align` reads left and right as sides of the box); the
  logical properties of CSS-39

## Context

`order` and `direction` were unknown properties. Yoga has both: a node's
children are laid out in the order they were inserted, and `YGNodeStyleSetDirection`
is bound already (`YogaNode.setDirection`), so neither needs a native change.

`direction` reaches further than layout. CSS-39 mapped every logical property
to one physical one on the assumption of a single left-to-right writing mode,
and `TextAlign`'s `start` and `end` were written to part company with `left`
and `right` "once a right-to-left line is placed from the right". And text:
`Paragraph` has no bidi run splitting. A string with right-to-left characters
is shaped with the direction forced to left to right and drawn in logical
order (`isBidiApproximate`), which keeps every width, caret and hit test
consistent and draws Arabic and Hebrew mirrored.

The user chose on 2026-10-11 to build `order` and `direction: rtl`, with
vertical `writing-mode` staying out.

## Decision

**`order` sorts a box's children before layout. `direction` is an inherited
property that sets Yoga's direction on every node, turns the logical
properties and `text-align: start`/`end` to the sides it names, and is the
base direction a line is aligned in. Glyph reordering is not part of it.**

- Both live on `layout.Presence`, beside `z-index`: `order` (not inherited)
  and `direction` (a new `layout.Direction`, inherited with `visibility` and
  the others). `ComputedStyle.inheritsSameAs` compares it.
- `order` is a whole number, negative allowed. `RenderObject.inOrder` sorts a
  box's children by it, stably, before they are reconciled into the render
  tree, and hands the list back untouched when every child has one order.
  Layout, paint and the hit test all read the render tree's children, so all
  three follow it. Focus moves through the element tree and keeps document
  order, as CSS has it.
- `direction` is read first, with the font, in `ComputedStyle.of` and
  `applied`, so a logical property written before it in the same rule still
  takes its side from it. `LogicalProperties.physical(name, value, direction)`
  turns the inline start to the right in a right-to-left box. A global keyword
  on a logical property goes through the same mapping rather than the property
  table, whose aliases are written left to right.
- `RenderObject.apply` sets the node's Yoga direction from the box's, on every
  node rather than relying on Yoga's inheritance, since the cascade has
  already inherited it. A right-to-left row lays out from the right, and
  `flex-start` is its right edge.
- `TextAlign.in(direction)` turns `start` into `right` and `end` into `left` in
  a right-to-left box. `ComputedStyle.lineAlign()` is the placed value, which
  `textFlow()` and the two editors (`TextField`, `TextAreaBox`) read; the
  keyword is what inherits, so a left-to-right child of a right-to-left parent
  starts at the left.
- `writing-mode` stays unknown (a `by-design` row), and `unicode-bidi` is not
  read.

## Consequences

- A right-to-left box is mirrored as a box: rows, alignment, logical margins,
  padding and insets. Its text is still shaped and drawn as before, so
  right-to-left script reads mirrored, as it did. Splitting a paragraph into
  directional runs, with carets and selection that follow them, is its own
  item (CSS-50 in the plan), waiting on a decision.
- `revert` and `revert-layer` on a logical property still find the longhands
  through the left-to-right table. That is wrong in a right-to-left box, and
  rare enough to stay a note here rather than a mechanism.
- Physical properties stay physical: `margin-left` is the left margin in
  either direction, and so is `text-align: left`, as ADR-0598 says.

## Status

Done 2026-10-11: `layout.Direction`, `Presence.order`/`direction`, the
`order` and `direction` arms, `READ_FIRST`, `LogicalProperties.physical(…,
direction)`, the keyword mapping in `ComputedStyle.declare`, `TextAlign.in`,
`ComputedStyle.lineAlign`, `paint.tree.Yoga.writing`, `RenderObject.apply`
and `inOrder`, the longhand table; `OrderAndDirectionTest`,
`OrderAndDirectionLayoutTest`, `PropertiesTest`, the conformance rows.
