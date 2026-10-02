# ADR-0530: Five white-space keywords are two behaviours, and a long word may be cut

- **Status:** Accepted. Amends [ADR-0255](0255-a-label-that-does-not-fit-is-cut-not-wrapped.md),
  which named two `white-space` values and refused the rest.
- **Date:** 2026-10-02
- **Relates to:** [ADR-0036](0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md),
  [ADR-0529](0529-an-applications-stylesheet-is-lenient-and-loud.md),
  `docs/goldberry-gaps.md` #25

## Context

`WhiteSpace` had `NORMAL` and `NOWRAP`, and its documentation explained why
there were no more: a paragraph draws the string it was handed and never
collapses a space or a newline, so CSS's `pre-wrap` is what `normal` already
does here and `pre` is what `nowrap` already does. "Naming them would be four
spellings of two behaviours." The cascade then refused `pre`, `pre-wrap` and
`pre-line` as values it did not know, so a stylesheet that said what it meant
lost the declaration.

The other half of the report was real missing behaviour. `Paragraph.wrap`
breaks only at line-break opportunities, and "a word longer than the whole
width is not broken": it overflows on a line of its own. A log line holding a
digest or a path cannot be kept inside its box.

## Decision

**All five `white-space` keywords are accepted and read onto the two
behaviours, and `overflow-wrap` and `word-break` cut a word between grapheme
clusters.**

- `WhiteSpace.parse`: `normal`, `pre-wrap` and `pre-line` are `NORMAL`; `nowrap`
  and `pre` are `NOWRAP`. Mapped honestly rather than implemented: nothing
  collapses, so `pre-line`, whose difference from `pre-wrap` is that it folds
  runs of spaces, keeps them. Folding would make the paragraph's text differ
  from the string it was given, and every caret and selection offset is an
  index into that string.
- `OverflowWrap` (`normal`, `anywhere`, and `break-word` read as `anywhere`;
  `word-wrap` is accepted as its old name) and `WordBreak` (`normal`,
  `break-all`) in `text.flow`. Both inherit, as in CSS, and both travel on
  `TextFlow`, so the measure function and the painter read the same answer.
- `Paragraph.layout(width, flow)` honours them. Under `overflow-wrap: anywhere`
  a chunk wider than the whole line is cut at the last grapheme boundary that
  fits, using the same grapheme iterator truncation uses, and the remainder
  stays on the line being built so the next word can join it. Under
  `word-break: break-all` every grapheme boundary is a break opportunity, so
  lines fill to the edge. A line always takes at least one grapheme, so a box
  narrower than one character still finishes. The memo is keyed on the width
  and the breaking mode, because one cached paragraph may be laid out under
  two flows.
- `keep-all` and `break-spaces` are not in the subset.

## Consequences

- `.log { white-space: pre-wrap; overflow-wrap: anywhere }` keeps indentation
  and keeps a digest inside the box.
- `layout(width)` without a flow is unchanged, and so is every caller of it,
  which is everything except the measure function and the painter. A `text`
  styled with `overflow-wrap: anywhere` is measured and drawn cut; a caret or
  selection walk over the same paragraph through `layout(width)` would see the
  uncut lines, which no catalog widget does today.
- CSS also lets `overflow-wrap: anywhere` lower the box's smallest width. A
  paragraph here reports its widest line at the width it was offered, so
  `anywhere` and `break-word` cannot differ, and do not.
