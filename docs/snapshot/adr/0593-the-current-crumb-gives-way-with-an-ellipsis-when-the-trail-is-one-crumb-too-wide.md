# ADR-0593: The current crumb gives way with an ellipsis when the trail is one crumb too wide

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** docs/gaps.md (G52),
  [ADR-0306](0306-the-last-crumb-is-where-you-are.md)

## Context

`controls.css` said the trail neither wraps nor shrinks its crumbs: a wrapped
path puts "where you are" under "where you started", and the answer to a
narrow window is the overflow menu (ADR-0306). That menu counts crumbs. It
collapses the middle of a path longer than `collapseAfter`, and it cannot
answer a trail of two whose last name is wider than the row. Tessera's
`Chat › #platform` in a 900px Header with a Room open is that trail.
`crumb { flex-shrink: 0 }` then guaranteed the overrun `OverflowLog` reported.
`text-overflow: ellipsis` on a crumb did nothing, because the label is an
anonymous child box the crumb's style never reaches.

The entry offered two answers: `Breadcrumbs.eliding(true)`, or making
`crumb { min-width: 0; text-overflow: ellipsis }` take effect.

## Decision

**No API. The current crumb gives way, by a rule in the stylesheet.**

- `crumb` declares `white-space: nowrap` and `text-overflow: ellipsis`, and
  `Crumb.render` passes the crumb's `textFlow()` down to its label. That is
  `item`'s and `option`'s arrangement for the same anonymous-child problem.
  `nowrap` keeps the label at its natural width whatever width it is offered.
  `ellipsis` marks where it was cut.
- `crumb:checked` (the current crumb, always the last) declares
  `flex-shrink: 1; min-width: 0`. It is the one crumb that may be narrower
  than its label, so a title too long for the row ends in `…` before the
  trail would paint past its edge.
- Every crumb before it keeps `flex-shrink: 0` and its whole name. The menu
  still collapses the middle of a long path, by count, as before.
- A crumb's icon keeps its size (`shrink(0)` in `Crumb.render`). A crumb that
  gives way spends its missing pixels on the label, which has an ellipsis to
  say so.

**Why not `eliding(true)`.** It would be a flag for a behaviour every trail
wants: no application wants a trail that paints over the controls beside it.
It would also be a second switch for something the stylesheet already
expresses. With the rule in `controls.css`, an application that prefers a
different answer says so in CSS: shrink an earlier crumb too, or put
`flex-shrink: 0` back on the current one and clip. Making `ellipsis` reach the
label was needed on either route, and with that done the API added nothing.

**Why the current crumb, when ADR-0306 says a truncated name is worse than a
hidden one.** That sentence is about the crumbs the menu can hold: a folder
name cut short looks like a name, and the menu shows it whole. The current
crumb is never in the menu, so a trail too narrow for it has three choices:
paint past its own edge, be clipped with no mark (Tessera's stopgap, an
`overflow: hidden` wrapper), or end in `…`. The page under the trail carries
its name in full in its heading. `BreadcrumbsTest.labelsAreNeverTruncated`
still holds, since it is about the labels a trail is built with.

## Where reality differed from the entry

The entry said the ellipsis "belongs to the text inside the crumb rather than
to the crumb itself", and that is what was fixed. `min-width: 0` turned out to
be documentation rather than mechanism: Yoga has no `min-width: auto`, so the
crumb could shrink once `flex-shrink` allowed it. It is declared anyway, for
a reader who knows CSS's rule.

## Consequences

- `BreadcrumbsWidthTest`: a two-crumb trail in 200px reports no `Overrun`.
  The current crumb's label carries the ellipsising flow and is laid out
  narrower than its natural width, which is a line that ends in `…`. The crumb
  before it keeps at least its natural width, and at 600px nothing is cut.
- The wide-window pictures are unchanged: `BreadcrumbsGoldenTest`
  (`breadcrumbs-dark`, `breadcrumbs-light`) passes without re-blessing, and so
  do the book's `breadcrumbs` and `crumb` pictures. A retake does differ from
  them by a few pixels, inside the tolerance, so they were not retaken.
- The `breadcrumbs` comment in `controls.css` and the class note on
  `Breadcrumbs` say which crumb gives way and why.
