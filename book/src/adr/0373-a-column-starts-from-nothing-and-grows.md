# 373. A column starts from nothing and grows

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "`flex-basis` is one of two layout
properties §8 names and nothing resolves".

## Context

§8 has listed `flex-grow`, `flex-shrink` and `flex-basis` from the beginning.
The first two are resolved; the third was implemented for `segmented` and taken
back out, because `flex-basis: 0` on a track inside an unconstrained row makes
Yoga compute that row's content size as zero and the bar collapsed.

Two widgets have wanted it since and written around it:

- `masonry` needs `1/n` of a row where `n` is a count no selector can make. It
  wrote an inline `width: 100/n %` instead — which is `1/n` of the row **before**
  its gaps, so three columns and two 12px gaps overflowed by 24px.
- `timeline.alternate` wants half a row per side and writes `width: 50%` with an
  equal shrink, which is the same arithmetic and the same overflow.

The thing that collapsed was never the property: it was `flex-basis: 0` on a box
whose parent had no definite main size, which is CSS's behaviour too.

## Decision

**`flex-basis` is resolved, as a `Length` on `ComputedStyle` and on `Box`, and
`masonry` is its first consumer.**

- `auto` is the initial value — Yoga's and CSS's — so a box that never mentions
  it lays out exactly as before. It is a value rather than a missing one: it
  undoes a more general rule, which is `align-self: auto`'s argument (ADR-0244).
- `Box.basis(Length)` is the builder, and `RenderObject` sets it on the node the
  same way `width` is set: only when it differs from the previous frame.
- `masonry-column` is `flex-basis: 0; flex-grow: 1` in `controls.css`, and
  `MasonryColumn` no longer takes a count or writes a `restyle`. The columns are
  now a share of what the gaps left rather than of the whole row.

## Consequences

- The collapse that took this out is still real and is now the author's to avoid:
  `flex-basis: 0` in a row with no definite main size sizes that row to nothing.
  The `segmented` bar keeps the grid ADR-0099 gave it.
- `RecordWitherTest` covers the new component on both records for free.
- `timeline.alternate` is left as it is: its percentage is of a row it does not
  share with a gap, so the arithmetic is the same and the change would only move
  goldens.
