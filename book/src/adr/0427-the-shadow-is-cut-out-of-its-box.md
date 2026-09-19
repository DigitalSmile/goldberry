# 427. The shadow is cut out of its box

Date: 2026-09-19

## Status

Accepted. Closes the deviation [ADR-0310](0310-a-shadow-is-a-stack-of-rectangles.md)
recorded and could not fix, and retires the occlusion flag that decision
introduced to soften it.

## Context

CSS paints an outer `box-shadow` **only outside the border box**. The box's own
rectangle is knocked out of the shadow, so a translucent background does not
have its own shadow showing through from underneath.

ADR-0310 could not do that, and said so in as many words — in the decision, in
`ShadowGeometry`'s class comment, and in a test written to fail the day it
became possible. The reason was that the Blend2D binding exported neither a path
clip nor a fill rule, and the obvious trick without either is worse than the
problem it fixes: a **reversed sub-path** under the default non-zero winding
*fills* the parts of itself the outer shape does not cover, so the inner half of
a blur would paint a dark ring exactly where it was supposed to erase one.

So the toolkit painted the whole shape and relied on the box being drawn on top
of it. That is invisible under an opaque background — which is every shadowed
surface the design system has — and shows under a box mid-`opacity` transition,
which fades its shadow by the same factor and therefore darkens itself slightly
as it fades.

### The choice the entry offered does not exist

`book/src/TODO.md` named the fix as "`BLContextSetFillRule` or a path-clip call
on the export list". Only one of those is real. **Blend2D clips to a rectangle
and to nothing else.** Its whole clipping surface in `core/context.h` is:

```c
BL_API BLResult bl_context_clip_to_rect_i(BLContextCore*, const BLRectI*);
BL_API BLResult bl_context_clip_to_rect_d(BLContextCore*, const BLRect*);
```

Both were already exported, for damage-driven painting (ADR-0072) and for
`scroll`'s viewport (ADR-0114). There is no `clip_to_path` to add. A rectangular
clip cannot cut a rounded hole, and every shadowed surface in this toolkit is
rounded — so the alternative the entry held open is not a cheaper way to do this,
it is not a way to do this.

That leaves one call, and the decision is really about how to *use* it.

## Decision

**Export `bl_context_set_fill_rule`, and fill every shadow band together with
the border box under the even-odd rule.**

The painter builds two sub-paths into the pooled rasterizer path — the band, then
`ShadowGeometry.borderBox` — and fills the pair even-odd:

```java
path.reset();
outline.replayInto(path);
hole.replayInto(path);
frame.fillPathEvenOdd(x, y, path, band.argb());
```

A point inside both sub-paths is crossed twice, which is even, which is outside.
The hole costs one more sub-path per band and **no extra fill**.

**Even-odd and not a reversed sub-path under non-zero.** This is the whole point
of binding a rule rather than being cleverer with geometry. Under non-zero the
answer depends on winding direction, so it depends on `Path.roundRect` emitting
its corners in a particular order and on that order surviving every future edit
— and when it is wrong, it is wrong by painting a dark ring rather than by
failing. Under even-odd the direction is not an input. The band and the hole can
be wound identically, which they are, because both come out of the same
`Path.roundRect`.

**The hole does not move with the shadow.** The band sits at
`(offsetX - grow, offsetY - grow)`; the hole sits at `(0, 0)` with the box's own
radii, ungrown. That asymmetry is the whole of why an offset shadow is still
visible: a hole that travelled with its band would land exactly on top of it and
the even-odd fill would paint nothing at all.

**It is unconditional.** A painter that asked whether the background was opaque
before cutting the hole would be making the deviation conditional rather than
removing it, and would carry two paint paths to do it.

**The rule is set and put back in the same call.** `BlendContext.fillPathEvenOdd`
sets `BL_FILL_RULE_EVEN_ODD`, fills, and restores `BL_FILL_RULE_NON_ZERO` in a
`finally`. The fill rule is context state and Blend2D offers no stack for it, so
a caller that set it and forgot would hand the rule to whatever drew next — and
the symptom is a hole in an unrelated shape three boxes later, with no error
anywhere. A bare setter on `BlendContext` would have been the honest binding and
the wrong surface; there is exactly one drawing in the toolkit that wants this,
and it does not get to leak.

**Nothing is added to `Path` or to the public `Frame.fillPath`.** A `Path` is a
value describing a shape and a fill rule is a statement about how to read one;
attaching a rule to the value would mean every path in the toolkit carries an
answer to a question one drawing asks. `Frame.fillPathEvenOdd` is
package-private, and grows a public form the day something outside `paint` needs
a hole.

### The flag ADR-0310 introduced is deleted

ADR-0310 gave `ShadowRamp.bands` a second argument — whether the box would paint
an **opaque** fill over its own rectangle — and dropped the bands hidden under it
when it would. That was two things at once: an optimisation, and an admission,
because a translucent box got every band precisely *because* its shadow showed
through it.

With the hole cut, a band entirely inside the border box paints nothing whatever
the background's alpha is, so the question has one answer and the parameter is
gone. What replaces it is `ShadowGeometry.coveredAt(shadow)`, which is geometry
and says so:

```text
grow <= -max(|offsetX|, |offsetY|)
```

and the painter stops there, because `grow` only decreases. The bands are still
never filled; they are now skipped by the painter rather than never built by the
ramp. That costs at most twenty-four `Band` records per shadowed box — a
`double` and an `int` each — and buys the thing the split was actually for: the
alpha solver can be tested across the **whole** curve rather than across whatever
a culling rule left of it, which is where `ShadowRampTest`'s sharpest assertions
live.

## Consequences

- `ShadowPaintTest.throughATranslucentBox` was renamed and inverted. It used to
  assert `luminance(pixel(60, 50)) < 128` — the shadow visibly darkening the
  middle of a 50%-red box — as a deliberate pin on known-wrong behaviour. It now
  asserts that the pixel is **identical** to the same box painted with
  `Shadow.NONE`, and that a 50% red over white is a light pixel.
  `blurredUnderATranslucentBox` joins it, because a blur reaches inside the
  border box by half its radius and a knock-out that only handled the hard case
  would pass the first test and still darken every fading card.
- **Twelve golden images move, and none is re-blessed here.** All twelve move in
  the same place and for the same reason: the anti-aliased arc of a rounded
  corner. Along a straight edge on an integral coordinate the box covers the
  pixel completely and nothing changes; along a corner arc it covers a fraction,
  and the shadow underneath that fraction is now cut away instead of painted.
  Every one is far inside the 2.00% pixel tolerance and fails on the per-channel
  ceiling of 2:

  | golden | differing | worst channel |
  | --- | --- | --- |
  | `elevation-light` | 91 / 19800 (0.46%) | 10 |
  | `elevation-dark` | 94 / 19800 (0.47%) | 8 |
  | `dialog-light` | 74 / 138000 (0.05%) | 8 |
  | `dialog-dark` | 66 / 138000 (0.05%) | 5 |
  | `toast-light` | 123 / 96000 (0.13%) | 9 |
  | `toast-dark` | 129 / 96000 (0.13%) | 7 |
  | `toast-top-start` | 129 / 96000 (0.13%) | 7 |
  | `toast-arriving` | 127 / 96000 (0.13%) | 3 |
  | `toast-reflowing` | 86 / 96000 (0.09%) | 7 |
  | `card-hover` | 79 / 30600 (0.26%) | 4 |
  | `card-light` | 48 / 33000 (0.15%) | 7 |
  | `card-dark` | 50 / 33000 (0.15%) | 4 |

  This is the seam a browser has too — it composites a clipped shadow and then
  the box, in that order, and gets the same fractional coverage twice. The new
  pixels are the faithful ones and the goldens encode the old behaviour, so
  re-blessing them is the correct next step and is deliberately somebody else's:
  ten of the twelve are under `widgets/`, which this change does not own.
- The ABI is **14**, in `goldberry_shim.c` and `GoldberryShim.SUPPORTED_ABI_VERSION`
  together. `BL_FILL_RULE_NON_ZERO` and `BL_FILL_RULE_EVEN_ODD` are rows on the
  layout table through `BlendFillRule`, because an enumerator this narrow is
  exactly as silent when wrong as a struct offset: the wrong value paints the
  hole solid and reports success.
- `BlendFillRuleTest` proves the three claims separately — that even-odd leaves
  the hole, that the *same path* under the default rule does not, and that the
  rule does not leak into the next fill. The second is what says the new call is
  doing the work rather than the geometry having changed underneath it.
- An inner `box-shadow` is still not implemented and this does not bring it
  closer in kind, but it does bring it closer in parts: `inset` is the same
  band stack with the hole and the shape swapped over, and both halves now exist.

## What to write instead

A drawing that needs a hole builds both sub-paths into one path and calls
`Frame.fillPathEvenOdd`. It does not reverse the inner sub-path and hope: under
the rule Blend2D starts with, a reversed sub-path inside an outer one is still
filled, and the mistake looks like a shading bug rather than a winding bug.
