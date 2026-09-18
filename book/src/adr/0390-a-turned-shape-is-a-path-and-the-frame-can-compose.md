# 390. A turned shape is a path, and the frame can compose

Date: 2026-09-18

## Status

Accepted. Closes `docs/gaps.md` G46. Promotes `example.motion.Rotated`
([ADR-0354](0354-a-choreography-is-a-function-of-time-and-a-timer-wakes-it.md))
into the toolkit and adds the composing transform
[ADR-0068](0068-the-transform-stack-is-java-side.md) left out, without touching
the native boundary.

## Context

An application's landing page turns its tiles as they settle: each starts a few
degrees over and lands flat. It is drawn on a `canvas`, and inside a canvas a
painter has no way to turn anything.

`Frame.transform(a, b, c, d, e, f)` **replaces** the matrix, and the matrix
already holds the translation that put the canvas on screen. A painter cannot
read that translation back — `bl_context_get_transform` is not on the export
list — so setting a rotation draws the tile at the window's corner instead of at
its own. ADR-0197 fixed the same bug once already, for the charts, by handing
the ambient matrix down to `paintCanvas`; an application's painter is handed no
such thing.

That left rewriting the path's own coordinates, which is what the showcase does:
`example.motion.Rotated`, forty lines of `switch` over every `Path.Segment`
kind. It is path geometry, it is not the showcase's to own, and a second copy of
it in every application that animates a canvas is a second toolkit growing
beside this one. **It is also wrong in the interesting case.** `Rotated` adds
the turn to an arc's `rotation` and leaves the radii and the sweep flag alone,
which is right for a rotation and right for nothing else: a scale stretches the
ellipse, and a mirror makes the arc run the other way round.

## Decision

**The transform is a path's, in `paint.geom`, over the matrix type the toolkit
already has — and `Frame` gains a composing `concat` that costs no native
change.**

- **`paint.geom.Transformer`**, beside `Flattener` and `Dasher`, maps a `Path`
  through an affine. `Path.transformed(Affine)` is the entry point, with
  `rotated(radians, cx, cy)`, `translated(dx, dy)` and `scaled(sx, sy)` for the
  three cases that would otherwise be a matrix spelled out at every call site.
  The identity gives back the same path rather than a copy, so a tile at rest
  costs nothing.
- **`css.value.Affine` is that matrix, unmoved.** It already has `rotate`,
  `translate`, `scale`, `then` and `about`, its `about` is `transform-origin`,
  and its arithmetic is the arithmetic hit testing inverts. A second matrix type
  in `paint.geom` would be two implementations that must agree exactly, which is
  the failure its own javadoc was written to prevent. It stays in `css.value`
  because moving it would churn the cascade for a package name: `Path` already
  imports `css.Corners` for the same reason.
- **The arc is decomposed rather than adjusted.** An arc carries the *shape of
  its ellipse* — two radii and a rotation — and the transformed ellipse is the
  unit circle under the caller's linear part times the arc's own basis.
  Recovering radii and an angle from that product is the singular value
  decomposition of a 2×2 matrix, which has a closed form: the singular values
  are the semi-axes, the left rotation is the angle they sit at, and the right
  rotation is discarded because it spins the unit circle onto itself. The
  large-arc flag is unchanged. **The sweep flag flips when the determinant is
  negative**, because a mirror reverses the direction the arc is travelled and
  the endpoints do not say which side of the chord the ink is on. The rotation
  comes back in `[0, π)`, since an ellipse is unchanged by a half turn and a
  shape turned a degree at a time for an hour should not accumulate an angle.
- **`Frame.concat(a, b, c, d, e, f)`** multiplies rather than replaces: the
  caller's matrix applies first, in the coordinates it draws in, and whatever
  was in force applies to the result. Same display-scale semantics as
  `Frame.transform` — a concatenated `translate(10, 0)` moves ten logical pixels
  at any scale — and the same absence of a push, since `save()` and `restore()`
  are already the state stack.
- **The composition is Java's, and no native symbol or constant was added.**
  This is the part G46 asked to be checked. `bl_context_apply_transform_op` **is**
  exported — it is how the display scale reaches the rasterizer — but the compose
  operation is an *enumerator*, `BL_TRANSFORM_OP_TRANSFORM`, and the enumerators
  the bindings hard-code are checked against the compiled library by
  `LayoutVerifier`. Only `RESET`, `ASSIGN`, `TRANSLATE` and `SCALE` are in
  `goldberry_shim.c`, so naming a fifth means a new `GB_CONSTANT` row and a
  rebuilt native library — a native change for six multiplies, and six multiplies
  that must agree with what hit testing inverts. So `Frame` mirrors its own
  logical matrix in an `Affine` field, composes there, and assigns the answer
  through the `ASSIGN` op that was already bound. The mirror is exact because
  every change to the matrix goes through `transform`, `concat` or
  `resetTransform`, and `save`/`restore` push and pop it alongside the
  rasterizer's own stack.
- **`example.motion.Rotated` is deleted.** `TileFloor` composes the turn and the
  drop into one `Affine` and calls `Path.transformed`. No golden image moved,
  which is the evidence that the geometry it had was the geometry it kept.

## Consequences

- A canvas painter has **two** ways to turn what it draws, and they are for
  different things. A transformed path is a value: it can be measured,
  hit-tested and drawn many times in the coordinates it will appear in, and it
  needs nothing on the frame to be balanced. `concat` is for a painter drawing
  many shapes under one matrix — text included, which a path transform cannot
  reach.
- `Transformer` keeps curves as curves and arcs as arcs. Nothing is flattened,
  so a transformed path costs one pass over the segments and the rasterizer
  still sees the shape the author wrote.
- `Frame` holds state it did not hold before — six doubles and a stack of them.
  It is the frame's own matrix, mirrored, and it is what makes a composing
  transform possible without either a native change or a second answer to "where
  am I".
- ADR-0068 is unchanged where it matters: the painter still assigns an absolute
  matrix per box, hit testing still inverts that same matrix, and a run of
  untransformed boxes still costs no native call. `concat` is a convenience over
  the assignment, not a second mechanism.
- The arc arithmetic has no other caller today. It is tested on the four cases
  that break it separately — a turn, a stretch, a mirror and a large arc — and
  on one that cannot be faked: the transformed arc is flattened a thousand times
  finer than a frame ever is and asserted to pass through the transformed points
  of the original.
