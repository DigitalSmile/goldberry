# ADR-0616: `display: grid` waits for Yoga's own grid

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-42), `docs/css-support-2026-10-10.md`
  §5; ADR-0603 (every box stays a flex box)

## Context

Every box here is a Yoga flex box, and Yoga 3.2 has no grid. CSS-42 asked
which of three ways to answer `display: grid`: write a grid layout in Java
beside Yoga, with a grid container's items still Yoga nodes sized through
their measure; wait for upstream Yoga, whose grid work is unreleased; or
answer it with the `table` widget and refuse the value for good.

A Java grid would be the largest item in the CSS plan, and a second layout
engine whose answers Yoga's would have to agree with at every boundary:
intrinsic sizes, `min-content` and `max-content` contributions, absolute
children, and the pixel rounding Yoga does. Upstream would replace it.

The user chose on 2026-10-11 to wait for upstream Yoga.

## Decision

**`display: grid` and the grid properties stay refused until the vendored
Yoga lays out a grid. Then they are bound the way the flex properties are.**

- `display: grid` and `inline-grid` keep being dropped with a warning that
  says the subset has flex layout only. `grid-template-*`, `grid-area`,
  `grid-column`, `grid-row` and the rest stay unknown properties.
- The conformance rows keep `CSS-42` in their plan column, since they change
  when Yoga does.
- When Yoga's grid ships, its API is bound like the rest of Yoga's surface:
  the style setters exported from `libgoldberry`, an ABI bump, and a
  `display` value beside `flex`, `none` and `contents`.

## Consequences

- A sheet written for a browser that lays out with grid falls back to flex.
  The two-dimensional layouts the toolkit draws today are `table`, wrapping
  rows (`flex-wrap`) and nested rows and columns.
- Nothing is built twice: there is no Java grid to retire.

## Status

Answered 2026-10-11. Nothing built. Revisit when the pinned Yoga reference
moves to a release with grid layout.
