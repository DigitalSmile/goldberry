# 377. An underline travels by being let go of

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A `tabs` indicator still cannot travel,
though `segmented`'s does", and the half of ADR-0097 it left open.

## Context

`docs/design-system.md` §3.1 gives `tabs` and `segmented` one selection
indicator with one effect. `segmented`'s pill travels: its cells are a grid, so
the distance to cell *k* is *k* times the pill's own width and a percentage in a
`transform` says it with no measurement at all (ADR-0099). `tabs` cannot do
that, because its headers are as wide as their labels — and ADR-0097 deferred
the travelling version for want of "a fact about two boxes' laid-out geometry",
which no widget could reach.

It can now. Every header reports where it was painted through `Located`, which
is how a drag knows which headers it was dropped between (ADR-0372), and the
strip keeps the rectangles.

The obvious design — one indicator for the strip, positioned where the selected
header is — was built and taken back out. It makes the underline a function of
geometry that only arrives through the router, so any paint without one draws no
underline at all; half the golden images in the catalog are a `BoxPainter.paint`
of a rendered tree, and every one of them lost it.

## Decision

**The underline stays inside its own tab and travels by being displaced onto the
old one and then released.**

- `Tab.Travel(dx, scale, id, arrived)` is a **displacement**, not a position:
  how far back and how much wider the underline still is, relative to where it
  belongs. The strip computes it from the two headers' painted rectangles.
- The frame a selection changes, the newly selected tab's indicator carries the
  whole displacement — `translateX` and `scaleX` in one `transform` about the
  top-left corner — and is therefore drawn exactly over the header being left.
  The frame after, the strip hands over zero and the `transition` in
  `controls.css` slides it home on `--gb-motion-base`, which is the clock
  `segmented`'s pill is on.
- `id` is the indicator's **key**. A displaced underline is a newly built
  element, whose first frame starts nothing (ADR-0065) — otherwise the
  displacement would itself animate and the underline would glide *backwards*
  before gliding forwards. Letting go of the displacement keeps the same id, so
  that half is a change to the same element and therefore a transition.
- `arrived` is called from the indicator's `render`, which is where the frame
  that drew the displacement exists. It marks the strip for a rebuild, exactly as
  a finished departure does (ADR-0052, ADR-0109).
- A strip whose headers nothing has measured hands over null, and the underline
  appears where it belongs with no journey — which is what every golden image of
  a tab strip photographs, and why they are all unchanged.

## Consequences

- The underline is still each tab's own box and still pinned across its own
  header, so the resting picture, the colour a tab carries into it, and the rule
  it sits on are all exactly as they were.
- The travel is a transform and nothing else. `transition` takes the
  compositor-cheap set only: a `left` or a `width` that animated would run Yoga
  on every frame of the journey.
- `TabTravelTest` drives a real router so the rectangles exist, and asserts the
  painted left edge rather than a matrix entry — the transform is applied about
  the box's own place, so the matrix alone reads as an offset of something else.
- The *other* tabs still jump to their new places when the application reorders
  its list. That was ADR-0372's consequence and it stays one: a reorder moves
  several boxes at once, and this displaces one.
