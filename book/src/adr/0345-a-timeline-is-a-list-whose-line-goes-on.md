# 345. A timeline is a list whose line goes on

Date: 2026-09-17

## Status

Accepted. Builds `docs/core-widgets.md` §10's `timeline`. A `badge` as the
marker, left unbuilt here, is ADR-0356's `marker` slot.

## Context

§10 specifies `timeline` in three sentences and one of them is the whole
widget: "`pending=#true` renders a trailing unfilled marker for 'and then what
happens next', which is what distinguishes a timeline from a list with dots."
The rest — a marker, a label, a timestamp, a body, `direction`, `align` — is
what a list row would carry too. The axis is the widget.

Two things had to be worked out on the way. A line between markers has to run
the full height of whatever is beside it, which a `gap` on the list would
break; and an alternating timeline has to keep its axis in one place whatever
is on either side of it, which a two-part row cannot do and §8's subset has no
`flex-basis` to do it with.

## Decision

**A timeline is a column of entries, each a rail beside a side; the rail
stretches, the line after the last marker is drawn only when the story goes
on, and an alternating timeline gives every entry both sides at half width.**

- **`Timeline`** is `Widget.Stateless`, building a `TimelineList` (ADR-0109)
  that carries `horizontal` and `alternate` as classes. On every build it
  writes a `Placement` onto each `Entry`: its index, the direction, which side
  it sits on, whether the list is two-sided, and whether the line continues
  past it — true for every entry but the last, and for the last too when
  `pending`. A pending timeline gets one more entry with no words, whose
  marker is a ring.
- **An entry is a row of the rail and a side**, and the gap between entries
  is the body's bottom padding rather than a gap on the list, so the rail's
  line runs from marker to marker unbroken. The rail is a column of the marker
  and, when the line continues, a `TimelineLine` that grows to the entry's
  height.
- **Alternating is three columns.** Every entry gets a side, the rail and a
  side, with the words in one and the other empty; both sides are `width:
  50%` with an equal shrink, which is the arithmetic `flex-basis: 0; flex-grow:
  1` would have done. The first cut gave only the crossed-over entries a
  second side, and the axis wandered.
- **The marker sits in a cell one line tall.** `timeline-marker-cell` is
  `min-height: var(--gb-line-body)` with the dot centred, so the dot is on the
  head's centre whatever the token is; the first cut padded the rail by four
  pixels for a line-height of twenty that was eighteen.
- **The marker is a dot or an icon.** A dot takes the entry's `colour` or the
  stylesheet's, which is `chip`'s rule for a dot (ADR-0328); an icon sits in a
  larger disc. A `badge` as the marker is not built: a marker that is a widget
  needs a slot markup can name, and nothing else in the catalog has one yet.
- **The line is a drawing.** Each entry answers `ROW` and is named by its
  label and its time; the rail, the marker and the line carry no semantics,
  which is §10's "the connecting line is a drawing and is not announced".

## Consequences

- `timeline` and `entry` are markup; `timeline-rail`, `timeline-marker`,
  `timeline-line`, `timeline-side`, `timeline-body`, `timeline-head`,
  `timeline-label`, `timeline-time` and `timeline-content` are parts.
- A record with a `children` component and a `Widget.Leaf` override of
  `children()` hands the parts out through the accessor the component was
  meant to have. `Entry` calls its content `body`; the parts call theirs
  `content`. Worth knowing before the next widget does it.
- The showcase's Collections screen has a timeline with a pending marker and a
  coloured dot.
