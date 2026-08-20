# 157. A layer is blitted into its own size

Date: 2026-08-20

## Status

Accepted. Fixes a bug in
[ADR-0071](0071-a-layer-is-a-subtrees-raster.md)'s composite step that
was invisible at a 1:1 display scale.

## Context

Reported from a Mac: **every disabled control was about twice the size it should
be.** Disabled is the only thing in the toolkit's stylesheets that sets
`opacity` (ADR-0077), and `opacity` is what promotes a subtree to a layer — so
"disabled controls are huge" was really "promoted subtrees are huge", and only on
a display whose scale is not 1.

Two coordinate spaces meet at the composite, and each is right on its own:

- **A layer is a raster**, so `Layer.of` allocates it in *physical* pixels —
  `ceil(logical × scale)`. Allocating at logical size would throw the display's
  detail away, which is the whole point of promoting to a raster on a HiDPI
  screen.
- **A frame is in logical coordinates**, because its Blend2D context is scaled
  once when the frame begins. Every other call on it — a rectangle, a path, a
  glyph run — is stated in logical units.

`Frame.drawLayer` used `bl_context_blit_image_d`, which draws an image at an
origin, **one image pixel per context unit**. A 60-point square on a 2× display
is a 120×120 raster drawn across 120 logical units: 240 physical pixels, twice
the size it laid out at.

At 1× the two spaces coincide and the arithmetic is correct by accident. Every
golden image, every test in `LayerTest`, and every Linux and Windows run of the
showcase is at 1×.

## Decision

**The blit states the destination size, in the context's units.**

`bl_context_blit_scaled_image_d` takes a `BLRect` rather than a `BLPoint`, so the
raster is drawn to a stated size and the two spaces are reconciled in one place.
`BlendContext` gains `blitScaled`, `blit` stays for an image that really is one
pixel per unit, and its javadoc now says which is which.

The destination size is derived from **the raster**, not from the bounds the
caller laid out:

```java
context.blitScaled(x, y, size.width() / factor, size.height() / factor, image);
```

`Layer.of` rounds the physical size *up*, so at a fractional scale the raster is
a fraction of a pixel larger than the box. Dividing that back is what maps it
one-for-one onto the device; passing the box's logical size instead would squash
the raster by that fraction and leave a resampling seam along two edges of every
promoted subtree.

## Alternatives considered

**Allocate the layer at logical size.** The two spaces would then agree and no
new symbol would be needed — at the cost of rasterizing every promoted subtree at
1× and scaling it up, which is exactly the blurring that promoting to a raster on
a HiDPI display is meant to avoid.

**Neutralise the context transform around the blit.** Push `scale(1/factor)`,
blit at `x × factor`, pop. It works and needs no new native symbol, but it makes
the composite depend on the transform stack being in a known state at that
moment — and the promoted node's own affine is applied immediately before. A
destination rectangle composes with whatever transform is in force and needs to
know nothing about it.

## Consequences

**A 185th exported symbol.** The list in `exports/goldberry.symbols` is explicit
and this adds one line to it, which is the intended way to add a call and the
reason the list exists.

**The goldens did not move.** Blend2D's scaled blit at exactly 1:1 is
pixel-identical to the point blit, so every existing golden passes unchanged —
which is the evidence that this is a fix and not a rendering change.

**Two blits, and the wrong one is still callable.** `blit` remains, because an
image rasterized at the context's own scale genuinely wants it. The distinction
is now in the javadoc of both rather than in nobody's head.

**The tests are at 2× and 1.5×, and that is the lasting change.** `LayerTest`
grew a `Scaled` nest, and the fractional case is there deliberately: it is the
one a "just divide by two" fix gets wrong. **The broader gap is not closed** —
almost every pixel assertion in this repository is at 1×, and this class of bug
is invisible there. A golden corpus at 2× is the obvious next step and is not
built.
