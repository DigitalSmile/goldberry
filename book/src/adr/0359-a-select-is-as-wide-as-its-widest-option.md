# 359. A select is as wide as its widest option

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A `select`'s *field* is as wide as its
current value", and records the items `docs/widgets-finishing.md` answers
rather than builds.

## Context

`docs/core-widgets.md` §3 asks for a select whose closed field does not move
when its value changes. The field tracked its value: "Dark" made a narrow
control and "High contrast" a wide one. The TODO entry had already found that
its own reason had expired: `Paints.Context.paragraph(style, text)` shapes
against the node's resolved style during `render`, and a shaped paragraph's
width is one call. What remained was a decision about a shipped drawing, since
every `select` golden changes.

## Decision

**The value cell's preferred width is the natural width of the widest label
it could show: every option's and the placeholder's.**

- `SelectState` hands `SelectField` the labels, and `SelectValue` shapes each
  against its own style and sets `width` to the widest, rounded up to a point.
- **Preferred, not minimum.** The cell keeps `flex-shrink: 1`, so a stylesheet
  that sizes the select (`select { width: 90px }`) still wins and the value
  ellipsizes, as before.
- Not for `tree=`, whose labels are nodes that may not be loaded, and not for
  `multiple` or `autocomplete`, which draw chips or an editor instead of a
  value.
- It is not the `Measured` trap: what is measured is text, a function of the
  model and the style, not last frame's geometry.

## Consequences

- `select-dark`, `select-light`, `select-disabled`, `select-on-surface` and
  `select-placeholder` are re-blessed. Each field is now as wide as "Choose a
  theme".
- Shaping every label is paid on every render of a closed select. It is a
  cache hit after the first frame (ADR-0037), and a select with thousands of
  options is `autocomplete`'s case, which is excluded.
