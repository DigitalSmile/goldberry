# ADR-0068: The transform stack is accumulated in Java, and hit testing inverts the matrix the painter used

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/design-system.md` §1.7; `docs/ARCHITECTURE.md` §8, §11;
  closes the `transform` gap left open by
  [ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md); extends
  [ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md) and
  [ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md)

## Context

`transform` is on §1.7's whitelist of properties that may animate, and it is the
only one of the five that was not implemented. ADR-0067 named it as a gap and
said why it was being deferred rather than shipped inside that change:

> `Box` carries no transform, and adding one means the painter **and** hit
> testing, which needs the inverse to map a pointer back through it and silently
> mis-routes clicks if it does not. A correctness trap worth arriving on its own.

That is the whole of the problem. A transform is not hard to draw — Blend2D has
had the machinery since the display scale was first applied to a context. It is
hard to draw *and route input through consistently*, because the two run in
different places at different times: painting happens inside the frame callback
with a live rendering context, and hit testing happens on the input path against
a snapshot of the last frame, with no context anywhere near it
([ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md)).

The failure mode is what makes it worth its own record. A transform applied by
the painter and ignored by hit testing produces **no error and no wrong pixel**.
The control is drawn exactly where the stylesheet asked. It simply does not
respond where it looks like it should, and it does respond somewhere invisible.
Nothing in a screenshot, a log or a test of either half on its own would show it.

Two further things were unresolved and had to be decided here, because both are
consequences of the same question:

- **What a computed `transform` *is*.** Every other property in `ComputedStyle`
  is finished when the cascade produces it. This one cannot be: `translate(50%)`
  and the `transform-origin` default of `50% 50%` are proportions of the
  element's own border box, and the box has no size until Yoga has run.
- **How a transform interpolates.** §1.7's one named use is the check mark's
  `scale 0.6→1`, and the obvious implementation — interpolate the six matrix
  entries — is wrong in a way that only shows on rotation.

## Decision

### 1. Blend2D gets an absolute matrix, and no new symbol crosses the boundary

Blend2D's `bl_context_save` and `bl_context_restore` are **not on the export
list**. The natural implementation of a transform stack — push on the way into a
subtree, pop on the way out — is therefore not available without adding two
symbols to a boundary that has caught the same class of local-symbol bug three
times (`--exclude-libs,ALL`, then `BL_STATIC` making `BL_API` expand to nothing,
then HarfBuzz's bare `HB_EXTERN`), each answered only by a CI run across four
targets ([ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md)).

It turns out not to be needed. `bl_context_apply_transform_op` is **already
exported** — it is how the display scale reaches the rasterizer — and its
operation set includes `BL_TRANSFORM_OP_ASSIGN`, which *replaces* the context's
transform with a `BLMatrix2D` rather than composing onto it. So the stack is
accumulated in Java and each box states its whole matrix. Zero new symbols; one
new enumerator on a call that already crosses.

This is the same trade ADR-0064 made for the rounded rectangle, and it is being
recorded as a pattern rather than as a coincidence: **before adding to the export
list, check whether an already-exported call has an operand that does the job.**

The one cost is real and is stated: `BL_TRANSFORM_OP_ASSIGN`'s operand crosses as
`const void*`, so nothing on either side checks that six doubles are what it
reads. `BLMatrix2D` is therefore in the layout registry, and the probe compares
its six offsets against what the C compiler computed for the library actually
loaded. That check is not ceremonial — the named members live in an anonymous
struct inside a union with a `double[6]`, and the upstream header carries a
`TODO` to remove the union.

### 2. The matrix type is Java's, and hit testing inverts *that instance*

`Affine` is a record of six doubles with compose, invert, map and decompose. It
lives in `:core`, not in `:natives`, and Blend2D never sees it — what crosses is
the six numbers, the same way a `BLRect` is four numbers rather than a type.

The load-bearing part is what happens to the inverse. `HitTest.Region` carries
it, and it is computed **once, while painting, from the very matrix that was
handed to Blend2D**. It is not recomputed on the input path from the same inputs
by different code. That is the whole point: two derivations of an inverse that
must agree exactly is precisely how the silent failure above gets in, and the
second derivation is the one nobody tests because the first one looks right.

`Region.contains` then maps the **pointer** backward rather than mapping the
box's corners forward. That is not only less arithmetic — it is the only version
that is correct, because a box under nested transforms is a general quadrilateral
on screen and a bounding-box test would claim its corners while a corner-mapping
test would need a point-in-polygon routine that is a second implementation of
geometry the painter already did.

A box whose transform has no inverse is **dropped from the snapshot**.
`scale(0)` collapses the plane onto a point: there is nothing on screen for a
pointer to be inside of, and the alternative — every point in the window mapping
into it — would route every click in the application to an invisible control.

### 3. A computed `transform` is a function list, not a matrix

`ComputedStyle.transform()` carries the functions an author wrote plus the
origin, and `Transform.matrix(width, height)` resolves them when the rectangle is
known, inside the paint walk. Two independent reasons:

- **Percentages need a box.** Above.
- **Interpolation needs the parts.** Halfway between `rotate(0)` and
  `rotate(180deg)`, interpolated entry by entry, is a matrix of zeroes — a box
  collapsed to a point. Keeping the functions means the common case interpolates
  the numbers an author wrote, which is both correct and predictable:
  `scale(0.6)` → `scale(1)` is `scale(0.8)` at the midpoint.

Where the two lists have different shapes, the shorter is padded with **the
identity of the other side's function** — so `none` → `scale(1.1)` grows from
`scale(1)` rather than from a zero matrix. That is not a detail: it is the
transition every `:hover` rule will write, because the resting state has no
transform at all.

Where two functions at the same position are *different kinds*, the value swaps
at the halfway point. CSS resolves that case by multiplying both sides out and
decomposing, which needs a box to resolve percentages against, and interpolation
runs in the animation overlay — before layout. Nothing in the design system asks
for it. A stylesheet that does gets a jump, and this sentence, rather than a
shape that is in neither end state.

### 4. Layout runs first, and the transform moves the result

Yoga never sees a transform. The walk accumulates positions from the layout pass
and applies matrices to what comes out, which is CSS's rule and is the reason
`transform` can be on the animation whitelist at all: a control that scales on
hover moves no sibling and costs no layout pass. It is the same argument that
made `transition: width` a **dropped declaration with a warning** in
ADR-0067 — and §1.7's answer to "but I want to move something" has always been
"use a transform". Now there is one.

## Consequences

- `transform` and `transform-origin` parse, cascade, inherit their effect down
  the box subtree exactly as `opacity` does, animate through the overlay, and
  route input correctly. `Transitions.Animatable` has its fifth and last member.
- **No new native symbol.** The export list is unchanged; one enumerator and one
  struct layout were added, and both are checked against the compiled library by
  the probe.
- `Animations.Running` now holds an `Object` rather than a `double`. Four of the
  five animatable properties are numbers — a colour is a number, because a
  `double` holds every 32-bit integer exactly — and `transform` is the first that
  is not. A second map keyed by the same enum was the alternative, and would have
  given `observe`, `apply`, `settle` and `currentOr` a second half to keep in
  step with the first.
- The 2D subset only: `translate`, `scale`, `rotate`, `skew`, `matrix` and the
  axis variants. The 3D functions need a projection the painter has no concept
  of, and `perspective` on a CPU rasterizer is a different feature wearing this
  one's name.
- `em` and `rem` in a transform resolve against `CssLength.Context`'s fixed
  numbers rather than the node's own `font-size` — the same known gap the rest of
  the cascade has ([ADR-0066](0066-a-weight-is-a-face-and-color-inherits.md)),
  inherited rather than newly introduced.

### What this does *not* do

- **The check mark still does not scale.** §1.7 specifies the checkbox tick's
  animation as "scale 0.6→1 + opacity", and the opacity half has shipped since
  ADR-0067. The scale needs the mark to be a cascade node of its own — a second
  **part** beside `check-indicator` — because the mark is drawn *onto* the
  indicator's box and scaling that box would scale the 16px square with it.
  Adding a part is a decision ADR-0065 took carefully once, and it should be
  taken carefully again rather than as a side effect of this change.
- **Layer promotion does not exist**, so a frame with an animating transform
  repaints the window. §1.7 promotes an animating node to a repaint-boundary
  layer so the per-frame cost is compositing only. That mechanism is the same one
  CSS group opacity needs ([ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md))
  and the same one damage tracking wants, and — unlike everything in this record
  — it **does** need new exports: `bl_context_blit_image_d` at minimum, and
  `bl_context_set_global_alpha` to composite the layer at anything but full
  opacity. That is the reason it is not in here: it is a change to the native
  boundary, answered by a CI run across four targets, and it deserves the record
  that goes with one.
- **None of this has been rasterized off `linux-x64`.** `transforms.png` is the
  eleventh golden image resting on arcs and matrices that AVX-512, Apple
  Silicon's NEON path and MSVC have never drawn. Blend2D JITs its pipelines per
  CPU; the next CI run is what answers it, which is what the goldens'
  per-channel *and* area tolerance is for
  ([ADR-0050](0050-golden-images-have-a-tolerance.md)).
