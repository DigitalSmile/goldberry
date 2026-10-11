# ADR-0627: A masonry that is a frame stale wraps rather than squeezes

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** ADR-0624 (the showcase's shell); `docs/showcase-redesign.md`

## Context

A responsive `masonry` counts its columns from the width the **last** frame
gave it, through `Measured`. That is one frame late by design. When the window
gets narrower, by un-maximizing or by a sidebar opening beside the wall, the
next frame is laid out with the column count of the wider window. The columns
are `flex-basis: 0; flex-grow: 1`, so they squeezed into the narrower row,
each below its minimum. Every card's fixed-width content then ran past its
card. A segmented bar, a 240px text sample or a 180px container-query panel
each overran, and the overflow log said so once per card, for a frame nobody
saw. The user reported seven of these lines from the Styling screen after
un-maximizing the showcase to 1280×800.

## Decision

**A responsive masonry holds each column at its minimum width and lets its row
wrap.** `MasonryBox` sets `flex-wrap: wrap` on its own box and
`min-width: <min-column-width>` on each column's, in `render`. A wall given a
fixed count is unchanged.

In a settled frame the count already fits — `n` minimums and `n − 1` gaps —
so nothing wraps and nothing moves. In the stale frame, the columns that do
not fit move to a second line. A wrapped column overruns nothing, and the next
frame recounts and deals the cards again.

## Consequences

- No overflow line for a frame the wall was already going to correct.
- The stale frame shows a column on a second line for one frame, where it
  used to show squeezed cards. At 60 Hz neither is seen.
- `MasonryTest` asserts the wrap and the minimum on a responsive wall, and
  their absence on a fixed one.
