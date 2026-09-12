# 278. A dash is Goldberry's arithmetic, and not the rasterizer's

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G2's arithmetic half, and follows
[ADR-0277](0277-a-path-is-a-value-and-the-rasterizers-is-package-private.md),
whose `Stroke` could describe a dash and not draw one.

## Context

ADR-0277 gave `Stroke` a `Dash` and a miter limit, and wired neither: the plan
was that this commit would widen the native surface, `bl_context_set_stroke_dash_array`
and its neighbours would be bound, and `Frame` would then take a `Stroke` with
every field honoured. Six symbols, one CMake change, a probe constant, a
four-target CI run — the shape `tray-icon` paid for eleven symbols in ADR-0191.

That was written, and it does not work.

**Blend2D does not implement dashing.** It has the API — `bl_context_set_stroke_dash_array`,
`bl_context_set_stroke_dash_offset`, `BLStrokeOptions::dash_array` — and the
raster context stores what it is given, validates the array's element type,
retains it, releases it, copies it on `save` and restores it on `restore`. Then
nothing reads it. `core/pathstroke.cpp` is 988 lines and the word "dash" does
not appear in it. Of the eight files in the library that mention dashes, not one
is the stroker.

So every call returned `BL_SUCCESS` and the line came out solid, which is the
worst way for a missing feature to present itself: no error, no warning, no
signature to inspect, and six tests failing on pixel comparisons that looked like
arithmetic mistakes of our own.

## Decision

Dashing happens in `:core`, over `paint.Path`, before the path reaches the
rasterizer. **A dashed stroke is a solid stroke of a different path.**

Two public classes in a new `io.github.digitalsmile.goldberry.paint.geom`:

```java
public static Path Flattener.flatten(Path path, double tolerance);
public static Path Dasher.dash(Path path, Dash dash);
```

`Dasher` flattens, then walks the result accumulating arc length, emitting a
sub-path per on run. A solid pattern returns the input path itself — not a copy —
so the drawing path stays free for the overwhelming majority of strokes that are
not dashed.

The native surface gains **one** symbol rather than six:
`bl_context_set_stroke_miter_limit`, which *is* implemented and which closes a
gap `BlendStrokeJoin` had admitted to in its own javadoc for as long as it has
existed — it binds three of Blend2D's five joins, and the two omitted are the
miter-with-a-fallback variants "whose behaviour depends on the miter limit, and
nothing binds that yet".

## Flattening is not an optimisation, it is the only way

A dash is a statement about **arc length**, and the arc length of a cubic Bézier
has no closed form. There is no walking four pixels along a curve; there is only
walking four pixels along a polyline that approximates it.

The segment count comes from a bound on the curve's second derivative rather than
from its control-polygon length: subdividing into `n` pieces leaves an error of at
most `max|B''| / (8n²)`, so `n` is that inequality solved. This spends segments
where a curve actually bends and gives a nearly-straight cubic the single segment
it deserves — which the tests pin by asserting that a cubic with collinear
controls comes back as one line.

The tolerance is a tenth of a logical pixel and is not a parameter on the drawing
path. Below the rasterizer's own antialiasing at 1× and a quarter of that at 2×,
a caller choosing it would be choosing between two invisible options and one slow
one.

Elliptic arcs go through SVG's endpoint-to-centre conversion (implementation notes
F.6.5 and F.6.6), including F.6.6's rule that radii too small to span the
endpoints are **scaled up until they exactly do** rather than refused.

## The two zeros are not the same zero

A zero in a dash pattern means two different things depending on which side of
the alternation it falls, and the code has to tell them apart:

- A zero-length **gap** does not break the run. `4 0 4` is a solid eight, and
  stopping there would put two caps in the middle of a dash — visible with a round
  one. So a zero gap is stepped over and the pen stays down.
- A zero-length **dash** is a dot, and is kept. `stroke-dasharray="0 4"` with a
  round cap is how a dotted line is written; it is an idiom, not a degenerate
  case, and skipping it the way the gap is skipped leaves the line blank. What a
  dot *looks* like is then the cap's business, exactly as SVG says.

The first implementation skipped both, and the dotted-line test is what found it.

### …and the rasterizer does not draw it

**Correction, from the showcase.** `Dasher` emits the zero-length sub-path
correctly — `DasherTest` asserts the coincident points — and **Blend2D drops it**.
A zero-length sub-path contributes no outline, so a round cap on nothing is
nothing: `Dash.of(0, 8)` at width 4 inks **zero pixels**, measured in
`DashRenderingTest`. The canvas screen in the showcase was drawn with two dotted
rings that simply were not there.

So the geometry follows SVG and the picture does not. A dotted line is written
with a **short** dash — `Dash.of(1, 7)` round-capped is a dot to every eye and is
drawn by every rasterizer — and the zero-length case is left as SVG-faithful
geometry that this backend declines to ink. Recorded as a test rather than as a
sentence, because the failure mode is a line that silently does not appear.

## Consequences

- **The export list is five symbols shorter than planned, and the CI risk goes
  with it.** Dashing is the same Java on all four targets, so there is no
  platform on which it can behave differently — a stronger guarantee than binding
  would have given, arrived at by accident.
- **`:natives` grows a public `strokeMiterLimit`** and `Join.MITER` now means
  something. The limit is refused below 1, where a miter is shorter than the bevel
  it would fall back to.
- **A dashed path is flattened, so its curves are gone.** A dashed circle is a
  many-segment polygon. At the tolerance above this is invisible, but it is true,
  and a caller that dashed a path and then measured its segment count will find a
  different number than it put in.
- **Closed sub-paths come back open.** A dashed ring is arcs with gaps between
  them; there is nothing left to close, and the join at the start point goes with
  it — so a dashed rectangle's corner is drawn by the cap. SVG does the same.
- **The walk stops at the path.** A run that would begin exactly where the path
  ends is not drawn, and a run still open when the path ends is cut there. Stated
  as a rule because two tests depend on it and it is otherwise the sort of thing
  each caller rediscovers.
- **The pattern is not reset per sub-path.** SVG's rule: a rectangle drawn as four
  sides has one dash pattern around it, not four, or every corner is a seam.
- **Dashing costs a path allocation and a walk.** It is not free the way setting a
  rasterizer flag would have been — but the rasterizer flag did not draw anything,
  so the comparison is with not having the feature.

## Alternatives considered

- **Bind the dash API anyway and file the gap upstream.** Five exported symbols
  that provably do nothing, on a list whose whole discipline is that "a symbol
  here that nothing binds is dead weight". It would also have shipped a `Stroke`
  whose `dash` field was silently ignored, which ADR-0277 explicitly set out to
  avoid.
- **Patch Blend2D.** The ref is a pinned commit SHA (ADR-0030) precisely so that
  upstream is a known quantity; carrying a local patch to a statically linked
  C++ renderer is a different kind of commitment, and the Java is 300 lines.
- **Dash after stroking, by intersecting the outline.** A boolean operation
  against a stroke outline, which needs a path intersector the toolkit does not
  have and would get the caps wrong at every dash end.
- **Flatten with a fixed segment count.** Cheap, and wrong in both directions at
  once: sixteen segments is wasteful for a rounded corner and visibly polygonal
  for a full-screen curve.
- **Adaptive subdivision by flatness test.** The usual recursive answer, and it
  allocates per level and recurses to a depth that depends on the input. The
  second-derivative bound gives the segment count in closed form up front.
