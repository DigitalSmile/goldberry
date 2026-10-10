# ADR-0598: `text-align` reads `left` and `right` as sides of the box

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** the Gwent clone's issue list (GB-034),
  [ADR-0256](0256-a-line-is-placed-by-the-paint-not-by-the-box.md)

## Context

`TextAlign` had `START`, `CENTER` and `END`. A stylesheet that wrote
`text-align: right` or `left` lost the declaration, and the log said
`dropping "text-align": right is not a valid value`. The refusal was on
purpose. `TextAlign`'s documentation, the comment in `ComputedStyle` and a test
all said why: `right` is `end` under left-to-right text and `start` under
right-to-left, so an alias would be right today and wrong once a line is placed
from the right.

The downstream set its name plate to `text-align: right`. The declaration was
dropped, and nothing showed it apart from that one log line. `left` and
`right` are the keywords most stylesheets use, and anything written for the
web uses them. `justify` is refused for a different reason: a paragraph is
shaped once and sliced into lines, and a placement cannot widen the spaces in
a line.

## Decision

**`left` and `right` are read, as constants of their own.** `TextAlign` gains
`LEFT` and `RIGHT` after the three it had, so the existing ordinals stay the
same. They are not aliases of `START` and `END`. The objection to an alias was
that it records the wrong meaning. A constant records the right one: `RIGHT`
is the box's right side whichever way the text runs. Every line is set left to
right today, so `fractionOfSlack` gives `LEFT` the same placement as `START`
and `RIGHT` the same as `END`, through the switch every caller already goes
through (`indentOf`). The painter, the caret, the hit test and the selection
all ask that one method, so a `right` field and a `right` label agree with no
further change. When right-to-left lines are placed from the right, `START`
and `END` will follow the line's direction and `LEFT` and `RIGHT` will not.
That difference is the reason they are separate constants.

**`justify` stays refused, and the warning says why**:
`dropping "text-align": justify is not supported: a line is placed, never
respaced, so it keeps the alignment it inherited`. The generic "not a valid value" would
send an author looking for a typo in a keyword CSS does define.
`ComputedStyle.dropped` takes the reason as an overload, deduplicated like
every other drop. A dropped declaration leaves the inherited value in force,
as it always has.

## Consequences

- `text-align: left | right` work in an application stylesheet. Nothing the
  toolkit ships changes: its own sheets say `start`, `center` and `end`.
- A `switch` over `TextAlign` outside the toolkit now has two more cases to
  cover. The toolkit had only one such switch, `fractionOfSlack`.
- The guide's *Text flow* paragraph says `left` and `right` are refused. Its
  replacement is parked in `docs/snapshot/guide-styling-text-align.md` until
  the release.
- Not done: justification. It needs extra advance at each space of a line
  except the last, and the caret, the hit test and the selection would have to
  share it. That is a respacing of the shaped run per line, not a placement,
  and it waits until something asks for it.

## Status

Done 2026-10-10: `TextAlign.LEFT`/`RIGHT`, the `justify` warning, and tests
(`ComputedStyleTest`, `TextFlowTest`, `ParagraphFlowTest`).
