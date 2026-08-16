# ADR-0043: Icons are stroked paths, and SVG is the format

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §6; [ADR-0033](0033-assets-are-fetched-and-compiled-not-committed.md), [ADR-0034](0034-one-size-and-the-design-unit-crossing.md)

## Context

[ADR-0033](0033-assets-are-fetched-and-compiled-not-committed.md) put Lucide's
1544 icons in `goldberry-core`: fetched at build time, pinned by checksum,
compiled by `:assets` into a table of SVG path data in a 24×24 box.
[ADR-0034](0034-one-size-and-the-design-unit-crossing.md) then drew a glyph and
closed with the note that *"the icon half is untouched: the Lucide table holds
SVG path data and Blend2D's path API — `bl_path_*` and `bl_context_fill_path_*` —
is not bound. It was scoped out on purpose, because it shares nothing with the
font chain except the context."*

That is still true, and it is what makes this a decision rather than a chore.
Two things had to be settled.

**An icon is a stroke, not a fill.** Lucide is drawn as 2px round-capped,
round-joined strokes on a 24×24 grid, with no fill at all. Most of its shapes are
not closed — `check` is three points and two line segments. Filling that path
produces a triangle. So the stroke options are as much a part of an icon as its
geometry is, and `bl_context_fill_path_*` alone would have been the wrong half of
the API to bind.

**Something has to read SVG path data.** Blend2D has no path-data parser; it has
commands. The table is `d` attributes, and their grammar is not
whitespace-separated numbers — `1.5.5` is two numbers, `1-2` is two numbers, and
`A5 5 0 011 1` packs two flags and a coordinate into `011` because SVG defines
the arc flags as *single characters*. A parser that split on whitespace and
commas parses most icons and quietly mangles the rest.

## Decision

**Bind the path commands SVG has, and no more.** Seventeen symbols: the path
lifecycle, `move_to`, `line_to`, `quad_to`, `cubic_to`, `smooth_quad_to`,
`smooth_cubic_to`, `elliptic_arc_to`, `close`, the two stroke-and-fill calls, and
the three stroke options. Every SVG command maps onto one of them:

| SVG | Blend2D |
|---|---|
| `M` `L` `H` `V` | `bl_path_move_to`, `bl_path_line_to` |
| `C` `Q` | `bl_path_cubic_to`, `bl_path_quad_to` |
| `S` `T` | `bl_path_smooth_cubic_to`, `bl_path_smooth_quad_to` |
| `A` | `bl_path_elliptic_arc_to` |
| `Z` | `bl_path_close` |

The two rows worth arguing about are the last three:

- **`A` maps directly.** Converting an elliptic arc to cubics is a page of
  arithmetic with four degenerate cases — a zero radius, an out-of-range radius,
  coincident endpoints, a rotation. Blend2D has that code and is tested on it.
  Writing it again in Java to avoid one binding would be trading a symbol for a
  class of bugs that only show on the icons that use arcs, which is most of the
  rounded ones.
- **`S` and `T` map directly.** Blend2D reflects the previous control point
  itself, against the command it recorded. A caller tracking "the last control
  point" in Java agrees with it until a `Z` or a bare `M` intervenes, and then
  silently does not.

**`SvgPath` in `:core` is a reader, not a geometry library.** It scans a command
letter, scans its arguments with SVG's number grammar, and calls the
corresponding method. Malformed data is refused with the index and a bounded
excerpt rather than half-drawn: the icon set is a checksummed archive compiled by
our own `:assets`, so a parse failure means the compiler emitted something the
reader cannot read, which is a build problem worth hearing about.

**An icon belongs to a size**, the way a `Font` does and for the reason ADR-0034
gives. `Icon.bundled(name, size)` parses the 24×24 data *pre-scaled*, so the
coordinates handed to Blend2D are the ones it rasterizes and there is no
transform at draw time. The stroke width scales with it — Lucide's 2px in a 24×24
box, so a 48px icon strokes at 4. Drawing the same symbol at two sizes is two
`Icon`s.

**The stroke style travels with the call.** `Frame.strokePath` takes the width,
cap and join per call rather than holding them as frame state. Blend2D's *are*
context state, and a frame that set them once would leak the last icon's weight
into whatever drew next — a bug that manifests as a hairline somewhere else.

## Alternatives considered

- **Normalize the path data at build time**, so `:assets` emits an absolute,
  arc-free, `M`/`L`/`C`/`Z` stream and `:core` needs four calls and a trivial
  reader. Genuinely attractive: it moves the grammar to build time, where a
  failure is a build failure. Rejected because arc-to-cubic conversion has to
  happen *somewhere*, and doing it in `:assets` means writing exactly the code
  the `A` binding avoids — and then owning it, without Blend2D's tests.
- **Fill the paths instead of stroking them.** Rejected by the icon set: most
  Lucide shapes are open outlines. It is mentioned because `fill` is bound too —
  for shapes that are closed and want it — and because filling is the obvious
  first thing to try and produces a recognisable-looking blob.
- **One `Icon` for all sizes, scaled by a context transform at draw time.**
  Rejected on ADR-0034's grounds: it puts the size in two places. It would also
  mean saving and restoring the context transform around every icon, when the
  context's transform is currently *only* the display scale and is safer that way.
- **A general SVG renderer.** Rejected, as `IconCompiler` already rejected it:
  transforms, groups, gradients and fills are not what an icon set needs, and
  supporting them badly is worse than refusing them. `SvgShapes` handles the
  seven basic shapes and this handles path data; between them that is all of
  Lucide.

## Consequences

- **Icons draw.** The showcase puts three down its sidebar, and the last
  ADR-0034 loose end is closed.
- **The layout table grew a struct and six constants.** `BLPathCore` is checked
  to be `BLObjectDetail`-shaped, and the three stroke caps and three joins are
  checked against C. That matters more than it looks: both enums number
  positionally and neither is intuitive — `BL_STROKE_JOIN_ROUND` is 4 while
  `BL_STROKE_CAP_ROUND` is 2, with a *reversed* round at 3 — and a drifted
  constant would draw every icon in the set with the wrong corners, on every
  platform at once, returning `BL_SUCCESS`.
- **`BlendPath.close()` is not SVG's `Z`.** A path has two closes — finish this
  figure, give the memory back — and they are `closeSubPath()` and `close()`
  respectively. Naming them alike would make `try-with-resources` draw a segment.
- **A drawing command after `Z` issues an implicit move.** SVG says a new
  sub-path starts at the closed one's start point; Blend2D says it more firmly,
  by refusing a `line_to` with no figure to extend. This was found by a test and
  not by reading either specification, which is the argument for the test.
- **Every bundled icon is asserted to parse.** `SvgPathTest` walks all 1544 and
  requires geometry from each. It costs about a second and it is the only thing
  standing between an unhandled command form and one checkbox in a future
  showcase being mysteriously empty.
- **An icon is not a `Box` yet.** The showcase draws them over the sidebar rather
  than laying them out in it, because nothing decides an icon's intrinsic size
  until the widget model does ([ADR-0004](0004-three-tree-retained-declarative-model.md)).
  That is the next thing this needs.
- **Nothing caches a parsed icon.** Building an `Icon` parses path data and
  allocates a Blend2D path, so it belongs outside the frame loop — as the
  showcase does it. A cache keyed by `(name, size)` is the obvious follow-up and
  is deliberately not built until something rebuilds a widget tree, which is the
  same reason `ParagraphCache` has no consumer
  ([ADR-0037](0037-what-the-text-path-costs.md)).
