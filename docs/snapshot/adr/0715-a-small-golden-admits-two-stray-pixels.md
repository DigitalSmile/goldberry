# ADR-0715: A small golden admits two stray pixels

- **Status:** Accepted
- **Date:** 2026-11-21
- **Amends:** ADR-0630 (a few stroke pixels in a hundred thousand)

## Context

ADR-0630 let one pixel in 100,000 stray beyond `Tolerance.RASTER`'s two
levels, because arm64 and x86-64 builds of Blend2D outline a stroke a hair
apart. As a share, that admits about ten pixels in a 1200x900 frame and none
at all in a widget-sized golden under 100,000 pixels.

Snapshot #83 was the first macOS run over the rich-text format bar. Its two
goldens (300x44, 13,200 pixels) each came out of Apple Silicon with one icon
pixel 3 levels from the x86-64 golden. Every other pixel was within the
tolerance. It is the drift ADR-0630 describes, in a picture too small for the
share to admit it.

## Decision

`Tolerance` gains a fourth number, `strayPixels`: a count of pixels that may
stray beyond the channel tolerance whatever the image's size. A rendering is
admitted when its strays are within the share or within the count. `RASTER`
admits two; `GPU` and every three-number tolerance admit none beyond their
share.

Two rather than one, so that a stroke drifting at two vertices of an icon
does not fail either. Not more, because a missing line, caret or focus ring
moves many more than two pixels by tens of levels.

Rejected: a looser tolerance on the format bar's test alone. The drift
belongs to every stroked icon in every small golden, and the next one would
fail the next macOS run in the same way.

## Consequences

- The format bar's goldens pass on macOS without being blessed again.
- A golden can now hide two wrong pixels anywhere. A change that moves fewer
  than three pixels beyond two levels (one dot of a glyph, one corner of a
  one-pixel rule) is no longer caught by a golden, only by a test that reads
  the pixels themselves.
- The failure message names both limits: the share and the count.
