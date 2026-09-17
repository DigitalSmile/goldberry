# 380. The tooltip row is what ships

Date: 2026-09-17

## Status

Accepted. Closes `docs/ARCHITECTURE.md` §17.1's "A `tooltip`'s radius and its
type rank".

## Context

`design-system.md` §3's row for `tooltip` asks for radius 4 and `caption`. The
stylesheet wrote 8 and `body`, each with an argument beside it:

- **The radius.** §1.5 groups radii as 4 (inputs, small controls), 8 (buttons,
  cards) and 12 (dialogs, popovers, frost panels), and names no tooltip in any
  of them. The nearest named thing is a popover at 12, and a tooltip is a small
  one — so 8 is a reading and 4 is a reading, and neither *follows*.
- **The type rank.** §1.4 gives `caption` to secondary text *under* a control,
  where the reader has the control itself for context. A tooltip is the only
  text on screen at the moment it is read, and 11px of it at arm's length is a
  squint.

ADR-0263 found three of that row's four numbers had departed and pinned what
ships in `TooltipMetricsTest`, so a fourth departure is a failing test. §17.1
recorded the two as decisions nobody had taken.

## Decision

**The code follows the design system: radius 4 and `caption`.**

The arguments for 8 and `body` are good and are not the point. The design
documents are the authority — `ARCHITECTURE.md` §17 says so, and says departures
are recorded rather than quietly made — and a toolkit whose own stylesheet
departs from its own specification in two places has a specification nobody can
read a screen against. Where the reading is genuinely open, as §1.5 leaves the
radius, the tie goes to the row that wrote a number down.

The counter-argument for `body` stays in `controls.css` beside the declaration,
because it is an argument about §1.4 and §1.4 is where it has to be answered.

## Consequences

- Five tooltip goldens are re-blessed: the plate is tighter and the text is
  11px. That is the change being made, and the images are the review of it.
- `TooltipMetricsTest` now pins agreement rather than a departure, and the row
  it guards is four numbers with no exceptions in it.
- §3's row loses its "not what ships" caveat, and `design-system.md` says where
  the `body` argument went.
