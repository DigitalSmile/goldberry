# ADR-0696: A list has sections, and their headers are not rows

- **Status:** Accepted
- **Date:** 2026-11-14
- **Relates to:** the measured rows of `ListView.virtualized(RowHeights)`, and
  `affix`

## Context

A chat timeline wants its day headers to stay pinned at the top while the
day's messages scroll under them (Tessera's G-04, here T-04). `ListView`
already measured variable rows and kept the reader's place, but an `affix`
could only be put inside a row: it pinned within that row, and the row was
virtualized away as soon as it scrolled out.

The open question in the plan was whether headers are rows (skipped by
`Home`, `End` and the typeahead) or decoration.

## Decision

Headers are **not rows**. `ListView.sections(sectionOf, header)` names each
item's section key and the widget that heads a section; a section is a run of
consecutive items with one key. A header is a `list-section-header` node: it
takes no focus, no selection and no typeahead, and every keyboard path still
walks rows only, so none of them had to learn to skip anything.

- **In a measured list** a header is counted into the block of its section's
  first row: `MeasuredWindow` keeps a header height per section-start item
  beside the row heights, the block begins at the header's top, and the
  spacers, the window, the reader's line and the width reflow all work in
  blocks as before. A header not yet measured counts at the last header
  measured. A header that changes height above the reader's line moves the
  viewport by the change, as a row does.
- **One pinned copy.** While the top section's own header is above the
  viewport's edge, a copy classed `pinned` is laid over the top of the list,
  out of the flow and painted over the rows. It sits at the viewport's edge,
  never lower than the next section's header less its own height, so the next
  header pushes it out as it arrives. It is placed from the geometry the frame
  reports, as `affix` is.
- **A list virtualized at one height** measures once it has sections, with
  that height as its estimate, because a header makes each section's first
  block a different height.
- **A list that builds every row** puts each section in a `list-section`
  with its header in an `affix`, which pins within the section box and leaves
  with it.

`list-section-header` is opaque (`--gb-surface`) and muted by default, so the
pinned copy is readable over the rows.

## Consequences

- `ListView` has a thirteenth component, `sections`; the twelve-argument
  constructor stays and means none.
- Tested by `ListSectionsTest`: headers above each section's first row, every
  header in the list's height, the pinned copy at the viewport's edge, pushed
  by the next header and replaced by it, `End` reaching the last row, a list
  at one height measuring, and a fully built list in `list-section` boxes
  with an `affix` each.
