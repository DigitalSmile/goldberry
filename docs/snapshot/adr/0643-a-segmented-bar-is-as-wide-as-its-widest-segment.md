# ADR-0643: A segmented bar is as wide as its widest segment

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0099](0099-an-indicator-travels-on-a-grid.md)

## Context

ADR-0099 made every segment exactly `1/n` of the track, so the pill travels by
its own width. The cells were a percentage of an indefinite width, so a bar that
nothing sized filled its parent. `TODO.md` recorded it as a real loss: in a
toolbar beside other widgets the bar took the whole row until an author wrote
`width`. It also said there was no third option under flexbox, since
content-sized cells cannot be travelled between and a zero basis collapses the
bar.

There is a third option, and `select` already uses its reasoning (ADR-0359):
**the natural width of the widest label is known at render time.** A segment's
box carries its shaped paragraph, its icon's size, its padding and its gap.

## Decision

**Every cell starts from the natural width of the widest segment and grows and
shrinks by the same amount as the others.** `SegmentedTrack` reads each
segment's rendered box (`NaturalWidth`: border, padding, content side by side,
gaps) and gives every cell that basis, rounded up to a point, with
`flex-grow: 1` and `flex-shrink: 1`. Equal bases with equal factors stay equal,
so:

- **Where nothing sizes the bar** it is `n` × the widest segment, plus its
  border and padding, and the rest of a row is left to the widgets beside it.
- **Where a column stretches it** it fills the row and the cells divide it
  equally, as before. No golden of the stretched form moved.
- **Where a stylesheet narrows it** the cells shrink equally and the labels are
  cut with an ellipsis rather than overflowing. `segmented-track` gained
  `flex-shrink: 1` for this.

The indicator is unchanged: its width is still `1/n` of the track and its travel
`k` times its own width, because the cells are still a grid.

It is not the `Measured` trap: what is measured is text and icons, a function of
the model and the style, not last frame's geometry, so the first frame is
already right.

Rejected:

- **`Measured` on each segment, then sizing the cells a frame later.** Every
  bar would show one frame at the wrong width, for a number available on the
  first frame.
- **Leaving it, and telling authors to write `width`.** A toolbar is where a
  segmented control belongs (`docs/core-widgets.md` §3), and there it was
  always wrong without help.

## Consequences

- A segmented control in a row no longer fills it. A layout that relied on the
  fill writes `flex-grow: 1` or a `width` on the bar.
- Eleven `segmented-*` goldens are re-blessed, each frame trimmed to its new
  content width. `segmented-stretched` did not move.
- `SegmentedWidthTest` holds the three cases and the natural-width arithmetic.
- Shaping every label is paid on each render of the track. It is a cache hit
  after the first frame (ADR-0037), and a bar has a handful of segments.
