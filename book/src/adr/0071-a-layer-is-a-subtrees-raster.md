# ADR-0071: A layer is a subtree's raster, and damage is where it moved

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/design-system.md` §1.7; `docs/ARCHITECTURE.md` §5;
  answers the group-opacity question left open by
  [ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md); needs the render-object
  identity from [ADR-0069](0069-the-render-tree-is-retained.md); the first
  additions to the export list since
  [ADR-0018](0018-sdl-conventions-stop-at-the-boundary.md) and
  [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md) caught its third
  local-symbol bug

## Context

Three separate features had been deferred with the same sentence for three
records running: **they all want a layer.**

- **Group opacity.** [ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md)
  shipped `opacity` as an alpha multiplied into each box's colours, and said
  plainly that this is not CSS: the specification renders the element and its
  descendants into a buffer and composites *that* once, which differs exactly
  where two children overlap. It predicted `stack` would make the difference
  visible.
- **Layer promotion.** `docs/design-system.md` §1.7 promotes a node animating
  `opacity` or `transform` to a repaint boundary so a frame of the animation
  costs a composite rather than a repaint.
- **Damage tracking.** §5 has always described "a frame re-rasterizes only dirty
  layers and blits the rest; damage rects flow to the backend".

[ADR-0068](0068-the-transform-stack-is-java-side.md) named the blocker as the
missing exports; [ADR-0069](0069-the-render-tree-is-retained.md) supplied the
other half, the render-object identity a cached raster can hang off. And after
ADR-0069 and [ADR-0070](0070-the-cascade-resolves-invalidated-nodes.md) took the
CPU before rasterization from 354 µs to 3.5 µs, **rasterization is the frame** —
about 320 µs at 960×640 on one thread — so this is the only remaining term of
consequence.

## Decision

### 1. Two new exports, and only two

`bl_context_blit_image_d` and `bl_context_set_global_alpha`. That list has caught
the same class of bug three times — `--exclude-libs,ALL`, then `BL_STATIC` making
`BL_API` expand to nothing, then HarfBuzz's bare `HB_EXTERN` — each of which
linked a symbol in and left it **local**, visible to `nm` and absent from
`nm -D`, and each answered only by a run against a real library. So `BlendLayerTest`
exists: seven pixel assertions that cannot pass unless both symbols really
exported.

What is **not** exported is an image constructor. Blend2D's `bl_image_init_as`
would allocate the pixels itself; a `PixelBuffer` allocated in Java and wrapped
with the already-exported `bl_image_init_as_from_data` costs nothing and keeps the
principle the export list states in its own comment — *Goldberry never asks
Blend2D to allocate pixels*. `img_area` crosses as NULL, which Blend2D reads as
the whole image, so no `BLRectI` crosses either and no layout row is needed for
one.

### 2. Promotion is `opacity < 1` **and having children**

Stated in one place, `RenderObject.isPromoted`, because a promotion policy spread
across a renderer is a policy nobody can check.

A node with children is exactly where CSS's group opacity and a per-box multiply
disagree. A translucent **leaf** is deliberately not promoted: its own
background, border and text can overlap each other, so a layer would differ there
too — by a fraction of a level along an antialiased edge — and paying an
allocation and a blit for every faded label to fix that is a poor trade.

The three goldens with a `:disabled` control at 45% (§2.1) changed when this
landed, and the change is the correction. The diff is confined to the disabled
control and to the region where its own shapes overlap; everything else in every
image is untouched. **That is the review step the golden workflow exists for**,
and it was reviewed rather than accepted.

### 3. The subtree is drawn at full strength, untransformed

Two decisions that look like details and are the whole feature:

- **Full strength.** The layer holds what the subtree looks like, not what it
  looks like *at this alpha*. The alpha is applied once to the finished raster.
  That is what makes it a group, and it is also what makes the raster reusable
  while the alpha moves.
- **Untransformed.** The transform is applied to the **blit**, not inside the
  layer. So a node animating a transform re-blits a raster it already has rather
  than re-rasterizing itself through a new matrix — which is the §1.7 promise.

### 4. The layer's bounds are the subtree's, not the border box

Three things reach outside a node's own rectangle, and a layer sized to the
border box would **clip each of them away** — a visible bug rather than a
rounding difference:

- a focus ring, which CSS draws outside the border box by design;
- a child transformed out from under its parent;
- a child that simply overflows, which flexbox allows.

So the bounds walk maps **all four corners** of every descendant through the
accumulated transform, because a rotation turns a rectangle into one that is not
axis-aligned and taking two corners would miss half of it.

### 5. Damage is the union of where a node was and where it is

Both, because a node that moved leaves a hole behind it, and damaging only its
new position is the classic partial-repaint artefact where the old drawing stays
on screen. `RenderObject` remembers its last rectangle for exactly this.

The flag it reads is `selfChanged`, **not** the subtree's `changed`. They have to
be separate: a parent whose child moved is "changed" for a layer's purposes and
has not itself moved a pixel, so damaging its whole rectangle would report the
entire window dirty every time anything in it did anything.

An **empty** damage list is a real answer — "nothing changed, upload nothing" —
and `Window` distinguishes it from *null*, which is a caller that has not said
anything and must get the whole frame. Conflating the two is how damage tracking
ends up either useless or wrong, in opposite directions.

## The bug a resize found, which the tests did not

Dragging a window's edge ended the event loop:

```
java.lang.IllegalArgumentException:
    damage 2066x1103+0+0 falls outside the 2065x1102 px frame
```

A node's remembered rectangle was measured against the **previous** frame. Every
rectangle *computed* this frame is clamped to it — but `union(before, now)` puts
the old one back, and a window dragged one pixel narrower produces a union that
fits neither frame. The backend refused it, correctly, and took the loop with it.

The fix is to clamp on the way **out**, where it holds regardless of which frame
a rectangle came from, rather than only where each is computed. Nothing is lost
by clipping: the part of `before` outside the new frame is not on screen any
more, so there is nothing there to repaint.

Worth recording for what it says about the test suite rather than the fix. Every
damage test used one frame size, because that is the natural thing to write —
and a resize is the one moment the remembered rectangle and the current frame
disagree, which is the entire premise of remembering it. `DamageTest` now resizes
by **one pixel** between frames, because that is what a drag actually produces
and a test that only jumped by fifty would have passed against a fix that only
handled large changes.

It is also the second time in this pair of records that keeping state produced a
failure keeping nothing could not — after ADR-0069's measure function. That is
the standing cost of a retained tree, and it is being counted.

## The bug this introduced, caught by its own tests

The first version of `collectDamage` returned early on the first frame, when a
node had no remembered rectangle to compare against. It therefore never recorded
its **children's** rectangles — so the next frame found them null too, reported
the whole window, and did it again forever. Damage tracking that was fully
implemented, fully wired, and did nothing.

`DamageTest`'s "a frame that changed nothing damages nothing" is what caught it,
and it is the reason that test asserts on an *area* rather than on "some damage
was reported": a version that always answers "everything" is correct by every
loose assertion anyone would write.

## Consequences

- **`opacity` is CSS's.** `group-opacity.png` is two overlapping squares under a
  parent at 50%: the overlap is the upper square and the lower one does not show
  through it. `LayerTest` asserts the overlapping pixel *equals* the
  non-overlapping one, which is true for a layer and false for a multiply.
  ADR-0064's open question is closed, and `stack` no longer has to wait for it.
- **A promoted subtree that did not change is a blit.** `RenderTree.rootChanged`
  is what decides, and it is exposed because that is the only thing a caching
  test can honestly assert on — inferring it from pixels would pass whether the
  raster was reused or redrawn.
- **Damage rects reach the backend.** `Window.damaged(...)` is how an application
  reports them and the showcase does. Where the platform lets it, the upload
  shrinks to what moved.
- **The frame is still painted in full.** This is the honest limit: damage says
  what an *upload* has to carry, not what the rasterizer may skip. Painting less
  needs the context clipped to the damage — a third export — **and** a promise
  from the backend SPI that the buffer it lends back holds last frame's pixels.
  SDL's window surface does; the SPI does not say so, and a partial repaint
  against a backend that hands over a fresh buffer would draw one control on a
  field of uninitialised memory. Stating that contract is the next step and it is
  a change to the SPI, not to this.
- **A fading group still re-rasterizes.** `opacity` lives on the promoted node's
  own box, so changing it counts as a change to that box and invalidates the
  raster — which is precisely the case §1.7 wanted promotion for. The fix is to
  exclude `opacity` from the comparison for a promoted node, and it is small; it
  is written down rather than done because it wants a benchmark showing the
  animation is actually cheaper, and this record has enough unmeasured claims in
  it already. `LayerTest` asserts the current behaviour so that changing it is
  deliberate.
- **A layer is a full-size allocation.** Bounds-sized rather than frame-sized, so
  a disabled button costs a few tens of kilobytes rather than 2.4 MB — but a
  window of many translucent groups allocates one each, and nothing bounds the
  total. A pool belongs here when something makes it matter.
- **None of it has been rasterized off `linux-x64`.** Two new symbols across
  three export mechanisms — the ELF version script, the MSVC `.def` and the
  Mach-O `-exported_symbols_list` — plus a twelfth golden resting on a blit path
  that AVX-512, NEON and MSVC have never run. This is exactly the class of change
  the export list has caught three times, and the next CI run is what answers it.
