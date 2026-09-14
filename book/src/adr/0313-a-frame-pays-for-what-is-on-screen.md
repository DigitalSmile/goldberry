# 313. A frame pays for what is on screen

Date: 2026-09-14

## Status

Accepted. Builds on [ADR-0114](0114-a-clip-is-a-rectangle-the-painter-carries.md), which put the clip
stack in Java, and on [ADR-0069](0069-the-render-tree-is-retained.md),
which made the render tree survive a frame.

## Context

ADR-0114 gave the painter a clip stack and said, of the one place it stops a
walk:

> Scrolled entirely out of sight. Nothing under here can produce a pixel, so the
> walk stops — which is the one place a clip saves the *traversal* as well as the
> rasterization.

That sentence is about a **clipping box** scrolled out of its parent. It is not
about the ordinary case, which is a viewport with a thousand rows in it and forty
of them on screen: each of those thousand rows is a box in a subtree whose clip is
perfectly non-empty, and every one of them was handed to Blend2D to be clipped
away. A `fillRect` outside the clip is cheap. A stroked icon path and a shaped
glyph run are not, and neither is doing it 1504 times.

The screen that made this unignorable is the icon sheet
([ADR-0309](0309-a-sheet-of-icons-reflows-and-pays-for-it.md)), which is 1544
tiles and 4709 elements because a masonry cannot virtualize. That record measured
what the trade costs and published the numbers — build, style, layout — and
measured **no raster at all**, which is where the cost actually was:

| icon sheet, 1280×900, one Blend2D thread | before |
|---|---|
| style | 3.5 ms |
| layout | 0.7 ms |
| **raster** | **18.0 ms** |

Eighteen milliseconds is the whole of a 60 Hz frame on a screen where forty tiles
are visible. The user-facing report was "the icon view is really slow", and it was
right.

## Decision

**The painter skips a subtree whose ink cannot land inside the clip in force.**

Three pieces, and the first two are arithmetic over values with no `Frame` in
sight — a new `paint.cull` package, for `paint.geom`'s reason:

- **`Ink`** — the rectangle a box, or a whole subtree of them, actually puts ink
  in. Four edges, like `Clip`, and its mirror image: a clip says what *may* be
  drawn and this says what *would* be. `union`, `shiftedBy`, `mappedBy` and one
  question, `overlaps(Clip)`.
- **`BoxInk`** — what **one** box draws, in its own coordinates. Its border box,
  grown by the three things that are outside it *by design*: the focus ring, the
  drop shadow's four asymmetric outsets
  ([ADR-0310](0310-a-shadow-is-a-stack-of-rectangles.md)), and an icon larger
  than the slot the painter centres it in
  ([ADR-0143](0143-a-strip-keeps-its-height-and-an-icon-its-centre.md)).
- **`RenderObject.settle()`** — reads where Yoga put this subtree and unions its
  ink, bottom-up, once per layout pass.

The painter's test is one line, at the top of the walk:

```java
if (!parentClip.isNone()
        && !object.ink().shiftedBy(left, top).mappedBy(transform).overlaps(parentClip)) {
    boxesCulled++;
    return;
}
```

### The subtree's ink, not the box's own

Culling on a box's own rectangle would be wrong, and wrong in the way that loses
content rather than the way that wastes time. A child may be drawn outside its
parent: flexbox lets a box overflow, a `transform` moves one out from under its
parent, and a focus ring is outside the border box by definition. So `Ink` is the
union over the subtree, and a parent with nothing in its own rectangle stays alive
because a grandchild is on screen.

### What is deliberately not covered

Content that overflows **its own** box — a paragraph in a box a stylesheet gave a
height too small for it. A measured leaf is sized by the text in it, so a text box
fits its text by construction; a box whose own content spills past a pinned height
is drawing over its siblings already, and the culler treats it the way the painter
does.

### Settled once, not measured twice

`settle()` also reads Yoga. That is not an extra cost, it is a cost moved and then
removed: four separate walks want to know where every node is — the paint pass,
the damage pass, the hit-test snapshot and the ink pass itself — and each of them
was making four native downcalls per node for the same answer. Nothing between one
`update` and the next can move a node, so `layout()` is read once and handed back
afterwards.

And the pass caches. A subtree is re-measured only when something in it changed
**or** its own rectangle moved; otherwise last frame's ink still stands and the
walk stops there. That is what makes **scrolling** free: a viewport moves by a
`transform` on one box, so exactly one node is changed and the thousand under it
are skipped at the first rectangle that held.

### One rule about what is outside a box

`RenderTree.bounds` — the rectangle a promoted layer is allocated at — asked the
same question for the opposite reason and answered it with its own copy of the
ring-and-shadow arithmetic. It now calls `BoxInk`. Too small a rectangle there
clips a focus ring off a promoted node; too small a one here drops a row that was
on screen. One rule, one place.

## The predicate had a hole, and a test found it

The cache reuses last frame's ink when `changed` is false and the rectangle held.
`changed` is the box diff over a subtree — and `sameAppearance`, which computes
it, did not compare `flex-wrap`, `align-self`, the min/max `limits`, `overflow` or
`elevated`.

`RenderTreeTest.flexWrap` failed immediately: a row that starts wrapping puts its
third child on a second line without changing one field of that child's box, so a
subtree this comparison called unchanged had every rectangle in it move. The five
properties are compared now. That makes damage and layer invalidation slightly
more conservative and fixes two latent bugs of their own — a box that starts
clipping, or starts painting over its siblings, produced no self-damage at all.

## Alternatives considered

**Virtualize the icon sheet.** It is the direct fix for the screen that prompted
this, and ADR-0309 already explained why it cannot be done: a masonry places each
card under the shortest column, which is a decision about *every* card. It also
fixes exactly one screen. Culling is the same win for `list`, `table`, `tree`,
`menu`, the tab strip and every application viewport nobody has written yet.

**Let Blend2D do it.** It already does — that is what the 18 ms *was*. The
rasterizer clips correctly and cheaply; what it cannot skip is the transform of a
path, the shaping lookup for a glyph run and the downcall that submits them.

**Cull each box against the clip and keep walking.** Safer, and it saves the
drawing but not the traversal. It also needs no subtree ink, which is the whole of
the risk here. Rejected because the traversal is where the per-node `layout()`
calls were, and because a subtree test costs the same arithmetic as a box test
once the pass exists.

**Compute ink lazily, during the paint walk.** Appealing — nothing above a
viewport would ever be measured. It does not work: deciding whether to descend
into a node needs that node's *subtree* ink, so the first container asked about
walks everything under it anyway, and the memoization has to be invalidated by
hand rather than by a layout pass that has just run.

## Consequences

**The measurement**, same machine, same frame, one Blend2D thread:

| icon sheet, 1280×900 | before | after |
|---|---|---|
| style | 3.5 ms | 3.6 ms |
| layout (now including `settle`) | 0.7 ms | 1.0 ms |
| raster | 18.0 ms | **4.4 ms** |
| **a settled frame** | **22.2 ms** | **9.0 ms** |

The wall of cards — the screen most of this application is — pays 0.03 ms for the
ink pass and gets a slightly cheaper raster back. Nothing regressed anywhere.

**A culler that stops working draws the same frame.** No assertion on pixels can
tell a correct cull from no cull at all, which is
[ADR-0081](0081-a-perpetual-loop-has-no-state.md)'s argument for
`layersRepainted` arriving at a second optimization. So `RenderTree` counts
`boxesPainted` and `boxesCulled`, and `CullingTest` asserts on both — the counts
say the walk stopped, the pixels say it stopped in the right place.

**Ten times the rows in the same viewport now costs the same to paint.** That is
the property an application can rely on, and it is asserted directly.

**What is now expensive to get wrong.** `BoxInk` is the list of things drawn
outside a border box. A property added to `Box` that draws outside one — an
`outline` on a second edge, a glow, a second shadow — and not added there is a
subtree that disappears at a viewport's edge and nowhere else. The same is true
of `sameAppearance`: a new *layout* property not compared there is a stale ink
rectangle. Both are one-line additions and neither announces itself.

**The ink pass is O(nodes) on the frame something changes**, and `settle` does not
know how to be cheaper than that when a whole screen is rebuilt. On the icon sheet
that is about 1 ms, against the 13 ms it takes off the raster. The obvious next
step is to cache across the box-diff rather than re-walk, which is what the
`changed` guard already does for the common case and what a finer flag could do
for the rest.
