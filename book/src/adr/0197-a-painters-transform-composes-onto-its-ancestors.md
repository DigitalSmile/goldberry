# 197. A painter's transform composes onto its ancestors'

Date: 2026-08-24

## Status

Accepted. Corrects the second half of
[ADR-0193](0193-a-canvas-is-a-second-clip-depth.md), which got the clip right and
was silent about the matrix.

## Context

Scroll the Charts screen and the charts do not move. The cards slide, the
headings slide, the axis labels slide — and the five plots stay exactly where
they were laid out, clipped by a window travelling over them.

Everything about that is one line. `paintCanvas` moves the painter's origin to
the box's content corner, which is what makes a painter draw from `(0, 0)`:

```java
frame.transform(1, 0, 0, 1, x + left, y + top);
```

`Frame.transform` **assigns**. It is
`bl_context_apply_transform_op(ctx, BL_TRANSFORM_OP_ASSIGN, m)` — six numbers
replacing whatever the context was carrying — and
[ADR-0068](0068-the-transform-stack-is-java-side.md) chose that deliberately:
Blend2D offers no transform stack the toolkit wants to depend on, the accumulated
matrix is composed in Java as the walk descends, and each box assigns the answer.
`RenderTree.Painting.current` exists precisely so that a run of untransformed
boxes costs no native call at all.

Every other thing `paintOne` draws is in the context's current user space, so the
ambient matrix applies to all of them and none of them has to know it exists. A
canvas is the only content that sets a matrix of its own — and by assigning, it
discarded its ancestors'.

A `scroll` moves its content with a `translate`
(`ScrollContent.restyle`), for §1.7's reason: movement stays off layout
properties. So "a canvas under a transform" is not an exotic case. It is every
chart in a scrolling panel, which is every chart the showcase has.

The clip in the same method was already right, and for a reason worth writing
down beside the bug: `clipTo` lands in the context's *current* user space, which
is the space the box's own rectangle is written in, and Blend2D intersects rather
than replaces. That is why the symptom was a chart standing still inside a
moving window rather than a chart that vanished — the two halves of the same
method disagreed about which space they were in.

Neither the unit tests nor the goldens could see it. Every canvas test paints at
the root, and a golden is captured at scroll offset zero — where `ScrollContent`
skips the transform entirely, because an unscrolled viewport should put nothing
on the context.

## Decision

**A painter is handed the matrix the frame already carries, and composes onto
it.** `BoxPainter.paintOne` takes an `ambient` `Affine` — `Affine.IDENTITY` for
the overwhelming majority of boxes, and for the four-argument overload every
widget in the catalog calls — and the canvas branch spells its own translation as

```java
var painting = Affine.translate(x + left, y + top).then(ambient);
```

The three callers that know the accumulated matrix pass it: `RenderTree.paint`
and `RenderTree.paintIntoLayer`, which have it as the `transform` they just
assigned, and `BoxPainter.paintPlaced`, which has it on the `Placed`.

## Alternatives considered

- **Make `Frame.transform` compose.** It is the obvious reading of the name and
  it would fix this at the source. It also reverses ADR-0068 for every caller:
  the walk composes the matrix in Java *because* the painter assigns, so a
  composing `transform` would double-apply every box's matrix and every call site
  would need a reset before it. The one method that wants composition is the one
  handing the context to somebody else.
- **A `Frame.translate(dx, dy)` over `BL_TRANSFORM_OP_TRANSLATE`.** Blend2D will
  compose a translation onto the current matrix natively, and the op is already
  bound. But the frame would then have state the toolkit cannot read back — the
  painter path tracks `current` in Java and would no longer know what the context
  holds — and the *scale* pre-multiply in `BlendContext.transform` exists because
  the caller's matrix is assigned in logical pixels. Two mechanisms for one
  matrix is how the walk and the context start disagreeing.
- **Undo the canvas's translation in the painter's coordinates instead** — pass
  the painter its box origin and let it draw at `(x, y)`. That gives up the
  primitive's first guarantee (ADR-0193: a painter draws from its own origin) to
  fix an implementation detail, and hands every application arithmetic it would
  get wrong exactly where the toolkit just did.

## Consequences

- **A chart scrolls.** So does a canvas in a `carousel`, in a `split-pane` being
  dragged, inside anything animating a `transform`, and inside a promoted layer —
  which composites at identity, so it was already right and stays right.
- **`paintOne` has a five-argument form**, and the four-argument one it had is
  kept and documented as "a box drawn where it was laid out". Nothing outside
  `core` changes: the eight widgets that call `paintOne` directly paint into
  their own untransformed frames.
- **The rule generalizes to the next caller.** `goldberry-html`'s native
  `document_container` (ADR-0190) draws through these same exported symbols one
  nesting level deeper, and will set transforms of its own. Whatever hands it
  the context owes it the same composition.
- **It is asserted in pixels.** `CanvasPaintTest` puts a canvas under a
  `translate` and reads the moved rectangle, which is the assertion the four
  existing guarantees were missing — each of them paints at the root, where
  identity hides this entirely.
- **A non-translating ambient matrix is still only as good as `clipTo`.** A
  canvas inside a *rotated* subtree composes correctly and is clipped by an
  axis-aligned rectangle, because that is what a Blend2D clip is. No widget in
  the catalog rotates a scroll viewport; when one does, it is a clip question and
  not a transform one.
