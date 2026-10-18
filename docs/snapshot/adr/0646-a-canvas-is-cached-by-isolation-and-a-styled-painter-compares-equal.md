# ADR-0646: A canvas is cached by `isolation`, and a styled painter compares equal

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0071](0071-a-layer-is-a-subtrees-raster.md)

## Context

`TODO.md` carried *A painter is called on every paint of its box*. That is right
for a drawing whose data changed and wasteful for a still one that is expensive.
The entry pointed at the seam, a repaint boundary with a `Layer`, and said nothing
had measured a case that needed it.

The seam was already reachable from CSS. `isolation: isolate` promotes any box,
a leaf included, to a layer that is kept while the box is unchanged. Counting
painter calls over five frames of an unchanged tree:

| | not isolated | `isolation: isolate` |
|---|---|---|
| `Painter` | 5 | 1 |
| `StyledPainter` | 5 | **5** |

A styled painter was never cached. `Canvas.render` bound it with
`StyledPainter.bound`, which answers a new lambda every time, so the box was
equal to nothing and the layer was invalidated on every frame.

## Decision

**A canvas is cached by its stylesheet, `isolation: isolate`, and no new API
is added for it.** The canvas binds a styled painter as `BoundPainter`, a
record that is equal to another when it holds the same painter (compared by
identity) and the same style. For a still canvas the style's time is left out
of the comparison: such a canvas is painted once and left there, so the time it
was painted at is history and not a change. For an animating one the time is
part of what it draws, so every frame is a new drawing, isolated or not.

Rejected:

- **A `cached` flag on `Canvas`.** It would be a second spelling of something
  CSS already says, and the render tree's layer is the cache either way.
- **Leaving styled painters out.** Every chart in the catalogue is a styled
  painter, and charts are the drawings most likely to be expensive.

## Consequences

- A still styled canvas that is not isolated is also equal from frame to frame
  now, as a plain painter already was. Its painter runs when the box is
  painted, as before. A painter that reads mutable state is redrawn when its
  canvas is rebuilt, as a plain one already had to be.
- `CanvasCacheTest` counts the calls in each case and holds the equality.
