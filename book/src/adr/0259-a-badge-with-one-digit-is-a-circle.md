# 259. A badge with one digit is a circle

Date: 2026-09-05

## Status

Accepted. Amends `docs/design-system.md` §3's `badge` row, and spends the last of
[ADR-0181](0181-a-box-may-say-how-small-and-how-large.md)'s four bounds.

## Context

`badge`'s `TODO.md` entry has been open since the widget shipped, and its stated
reason expired two records ago:

> §8's subset has no `min-width` at all, so a one-digit chip is a stadium rather
> than the circle a badge usually is. `badge-digits.png` is the record of it.

ADR-0181 added `min-width`, `max-width`, `min-height` and `max-height`. Three of
the four found consumers immediately — `dialog`, `toast` and `tooltip` had each
written a *width* where they meant a maximum. `min-width` had none, and this is
it.

So the entry stopped being about the style engine and became about the design
system: §3's row says *"height 20; padding-x 8; radius `full`; `caption`"* and
does not say a minimum width, and §5 wants a metrics row before code.

## Decision

**§3's `badge` row gains `min-width 20` and its padding-x drops from 8 to 4.**

### The minimum is the height, and is not really a second number

Equal width and height inside a `full` radius is what a circle *is*. So the row
is not gaining an independent metric so much as saying its first one twice, and
the test asserts `minWidth == height` rather than `minWidth == 20` — because the
two drifting apart is the failure worth naming, and a test on the literal would
pass while they did.

### The padding had to move, and the ramp says where to

`min-width` alone does nothing here. A caption digit is about 7px, so `8 + 7 + 8`
is 23 in a 20-tall box: **a badge with the default padding can never be round**,
whatever its minimum says. The minimum only bites once the content plus padding
is under 20.

6 would have been the comfortable answer and is **off §1.3's ramp**, which lists
`2, 4, 8, 12, 16, 20, …` and says "no off-ramp values" in as many words. 4 is the
legal step below 8, it makes `4 + 7 + 4 = 15` so the minimum takes over, and it
is what a count chip wants anyway.

§1.3 calls 8 the component padding **default**, not a floor, so this is a
departure from a default rather than a breach of a rule — and `badge` is the
widget in the catalog that most obviously is not a component with content in it.

### `select-chip` keeps the default, and stops sharing the rule

The two used to share one block. A chip holds a label and a ×, is never one
character wide, and has nothing round about it, so it keeps `padding-x 8` and
gains no minimum. The split is three lines rather than a duplicated block:
`badge` follows the shared rule and overrides two declarations.

## Alternatives considered

- **`min-width` with the padding left at 8.** The obvious reading of the entry,
  and it changes nothing: every badge is already wider than 20, so the minimum
  never applies. This is the version that would have looked done and not been.
- **Padding-x 6.** Off the ramp. §1.3's "no off-ramp values" is the kind of rule
  that only means anything when it is inconvenient.
- **A conditional padding — 4 for one character, 8 otherwise.** CSS cannot say it,
  the widget would have to count characters, and a badge whose padding changed as
  its number crossed 9 would be a chip that jumps.
- **Leaving it.** The entry's own position for two milestones, and reasonable
  while `min-width` did not exist. It does.

## Consequences

- **`badge-digits.png` moved and now shows the thing it was named for.** It has
  always been the record of the defect; it is the record of the fix in the same
  four numbers — `3` round, `12`, `128` and `1024` growing sideways.
- **Every badge is 8px narrower.** `badge-variants`, `badge-on-surface` and the
  showcase screens that carry one all moved, which is the visible cost of taking
  4 off each end.
- **§1.3's ramp did the deciding**, which is what a ramp is for: the number was
  not chosen for how it looked and then justified.
- **`select-chip` is on its own rule now**, and a change to one no longer reaches
  the other by accident. That is a small loss of the "one drawing" property the
  shared block expressed, and it is the honest shape: they were never the same
  control.
