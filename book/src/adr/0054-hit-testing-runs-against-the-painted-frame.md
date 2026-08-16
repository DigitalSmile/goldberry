# ADR-0054: Hit testing runs against the painted frame

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §7, §8; [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md), [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md), [ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md), [ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md)

## Context

§7 wants pointer events hit-tested against the render tree, dispatched
capture → target → bubble with `consume()`, and `:hover` derived from pointer
flow. §8 already has the pseudo-classes; §7.2 wants focus, and `:focus`
distinct from `:focus-visible`.

The awkward part is that [ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md)
materializes the render tree as a `Box` tree per frame, and a `Box` had no way to
say which node produced it. Without that, a rectangle on screen leads nowhere.

## Decision

**A `Box` carries an opaque `owner` tag.** Typed `Object`, not `Element`, so the
`layout` package keeps knowing nothing about widgets — it is set by the renderer
and read by hit testing, and nothing between the two looks at it. One extra
component on a record whose construction is entirely internal, which is what made
it cheap.

**Hit testing runs against a snapshot taken while painting, not a fresh layout
pass.** This is not an optimization; it is the only correct answer. A pointer
event is about what the user can see, and what they can see is the last frame
that was painted. Laying out again to answer would test against a frame that does
not exist yet, and every drag would be one frame ahead of the thing being
dragged.

**The topmost box wins, and untagged boxes are skipped.** `capture` records
parents before children — paint order — so scanning backwards finds the box the
user can actually see. A box nobody tagged is scenery: an event delivered to it
would have nowhere to go, so hit testing passes through to whatever is behind.

**Regions are logical, not physical.** The window scale is applied when the frame
is rasterized ([ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)), and an
application's coordinates are logical everywhere else. There is a test at 200%,
because a hit test out by the display scale is the kind of bug that works
perfectly on the machine it was written on.

**`:hover` applies to the whole ancestor chain, and only the difference changes.**
`.card:hover .title` has to work, so hover is not just the deepest node. The
router compares the old chain with the new one and marks only what differs —
which is what stops a pointer moving one pixel inside a widget from invalidating
its ancestors. §8's invalidation is coarse (a pseudo-class change recomputes the
subtree), so *which* nodes change matters more than it looks.

**Focus walks up to the nearest focusable ancestor.** Clicking the text inside a
button focuses the button, not the text. `isFocusable()` defaults to false,
because most nodes are scenery and a Tab traversal that stopped on each would be
unusable.

**`:focus` and `:focus-visible` are set separately**, per §7.2: a pointer press
sets `:focus` only, and the focus ring is a stylesheet's reaction to
`:focus-visible`. Input therefore never learns what a ring is.

**State lives on the router and the element, never on the widget.** Widgets are
rebuilt constantly and could not remember who is hovered. This is the same
argument [ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)
made for state, arriving at the same place from a different direction.

## Alternatives considered

- **Re-run layout to hit-test.** Rejected above: it answers about a frame that has
  not been shown.
- **A parallel array of elements in paint order, instead of a field on `Box`.**
  It works and it is fragile: the array's order and `forEachBox`'s traversal have
  to agree forever, with nothing checking that they do. A field cannot drift.
- **Type `owner` as `Element`.** Rejected: `layout` would then depend on
  `widget`, and the box tree is deliberately usable without one — every golden
  image builds boxes directly.
- **Dispatch to every node and let widgets filter.** Rejected: `Handles` is
  opt-in, so dispatch costs the number of interested nodes rather than the depth
  of the tree.

## Consequences

- **`button` is now buildable.** Press, release, hover, active and focus all
  reach a widget, which is what §11's controls were waiting on.
- **The backend does not send pointer events yet.** `BackendEvent` has no pointer
  cases and the sdl3 backend translates none, so nothing calls `PointerRouter`
  from a real window. The router is tested by driving it directly. That plumbing
  is the next piece and it is mechanical: SDL's motion and button events into the
  sealed event type, which will break `GoldberryRuntime`'s exhaustive switch until
  it handles them — by design.
- **Keyboard, text input, wheel and cursor are not here.** §7.1's `KeyEvent` /
  `TextEvent` split, libxkbcommon's `xkb_compose` for dead keys, pixel-precise
  wheel deltas, and §7.3's cursor shapes are all still to come. The split matters
  for IME later and should be built when there is a text input to receive it.
- **Pointer capture on drag is not implemented.** §7.1 asks for it. Without it a
  drag that leaves a widget's bounds stops being delivered to it, which is wrong
  for a slider — and a slider is the first widget that will need it.
- **`takeStylesDirty()` clears on read.** So the question it answers is exactly
  "did a pseudo-class change since the last frame", which is what a frame loop
  wants to ask before restyling.
