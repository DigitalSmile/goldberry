# 466. A slider marks spans of its range

Date: 2026-09-23

## Status

Accepted. Adds one component to `slider` in `:widgets` (`docs/core-widgets.md`
§3), asked for by `goldberry-media`'s seek bar, which shows what a network
source has buffered (`docs/goldberry-media.md` §4, S3; ADR-0465).

## Context

A media seek bar shows two things along one groove: where playback is (the
fill and the thumb) and what is already fetched, so that a seek there is
instant. `PlayerStatus.bufferedRanges` has carried the second since phase 6, and
`slider` had nowhere to draw it. The fill cannot carry it: a buffered stretch
can start after the thumb and there can be several.

The groove places its thumb by flex ratio (fill, thumb, rest), so that nothing
in Java learns the groove's width (ADR-0079). Whatever marks a stretch must not
disturb that ratio.

## Decision

`Slider` gains `spans`, a thirteenth component: a list of `Slider.Span(from,
to)` in the slider's own units, with a wither (`slider.spans(...)`). The
twelve-component constructor stays, so no existing slider changes, and markup
has no attribute for it, since spans are live data rather than a document's.

Each span is a `slider-span` part, the first children of `slider-groove`,
positioned **absolutely** and inset by percentages of the groove: its fractions
along the axis, zero across it. It is painted under the fill and the thumb, and
takes no part in the ratio. Spans go through the slider's `Scale`, are clamped
to its range, and one that is empty or wholly outside it is not drawn. A
vertical slider insets from the bottom.

The theme colours them with `--gb-slider-span-bg`: an alpha over the groove,
the `--gb-border-strong` technique, a step from the groove and well short of the
accent.

## Alternatives considered

- **Flex spacers, as the thumb is placed.** A second row of grow factors laid
  over the groove would need an absolute layer anyway, and a spacer per gap.
  Percentage insets say the same with one box per span.
- **A `progress` under the slider.** Two widgets for one groove, aligned by
  hand, and only one stretch.
- **Spans along the thumb's travel rather than the groove.** Exact where the
  thumb's centre is, but it needs the travel's width, which only layout knows.
  Along the groove the two agree in the middle and differ by at most half a
  thumb at the ends, where a buffered edge is not something anyone reads to
  the pixel.

## Consequences

- `media-controls`, `audio-player` and `media-player` show a network source's
  buffered stretches, and look again every quarter second while the fetch goes
  on, paused or not. A local file reports none, so nothing it draws changed.
- Two new goldens, `slider-spans` and `slider-spans-light`; every other slider
  golden is unchanged.
