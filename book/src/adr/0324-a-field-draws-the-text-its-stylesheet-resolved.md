# 324. A field draws the text its stylesheet resolved

Date: 2026-09-15

## Status

Accepted. Finishes what
[ADR-0318](0318-a-line-starts-where-the-paint-says-it-does.md) and
[ADR-0321](0321-a-rule-under-text-belongs-to-the-face.md) left in `text-input` and
`text-area`, and closes the caveat both of them recorded.

## Context

ADR-0318 gave `TextGeometry` the alignment and said plainly what it had not
touched:

> The `text-area` and `text-input` controls are unchanged and still ignore
> `text-align` altogether: both measure their own carets against their own origin,
> and both draw their value through `Box.text(paragraph, argb)` with the default
> flow, so neither indents its glyphs either. They are consistent today and they
> are ready.

Consistent, and useless in both directions. `slider-value` has been `text-align:
end` since ADR-0256 because a column of numbers lines up on its units column — and
a **field** a user types numbers into could not do the same thing. Neither could a
centred note in a `text-area`, and neither could underline a value.

The reason it was deferred rather than done is worth keeping: these two controls
hold the most delicate geometry in the catalog. A caret, a highlight, a
composition's underline and a hit test are four readings of one number, and the
glyphs are drawn by a *different* object — `Value`, a part whose paragraph the
painter indents. Wiring the paint alone would have been strictly worse than leaving
both alone: the glyphs would move and the caret would not.

## Decision

**One part passes the flow, and each control places its own geometry from the same
alignment.**

`Value.render` hands the box `style.textFlow()` rather than only `style.color()`,
so the glyphs get `text-align` **and** `text-decoration` — and both properties
inherit, so a rule on the field reaches the anonymous label inside it.

The two controls then have to agree with that paint, and they agree in two
different ways because their value boxes are shaped differently:

### `text-input`: the box hugs its text, so the box moves

A single-line field's value is an absolutely positioned child sized by its content,
so `Paragraph.paint` sees no slack and indents by nothing. What moves is the child:
`laidOut` computes `align.indentOf(textWidth, room)` and returns **one** number —
the scroll, less the indent — which every part is inset by and which the hit test
adds back.

One number is safe because the two can never both be non-zero: `indentOf` clamps
the slack at zero, so a value too long to fit scrolls and is never indented, and
one that fits does not scroll. That invariant is asserted rather than assumed.

### `text-area`: the box has a definite width, so the paint indents each line

A multi-line field gives its value box `contentWidth()`, so the painter does the
per-line indent itself — and the control adds the **same line's** indent to the
caret, the highlight, the composition's rule, the hit test and the column a run of
`Up`/`Down` keeps. Per line, because two lines of different lengths do not start in
the same place, and a single "where does the text start" number cannot describe a
centred paragraph.

Both go through `TextAlign.indentOf`, which is the rule ADR-0318 moved to the
property precisely so that a fourth and fifth reader could not disagree with the
first three.

The alignment travels **down with the paragraph**, through `laidOut`, for the
reason the wrap width already did: `render` is the only place a widget is handed
anything that can measure text, and the resolved style for the frame being
described is what the paint will use.

## Consequences

`text-input { text-align: end }` and `text-area { text-align: center }` work, and a
field can be underlined. A numeric column in a form lines up on its units column
the way `slider-value` does.

**The round trip is what the tests assert**, in both controls: ask where the caret
is drawn, press exactly there, get the same offset back. Under `center` that fails
by half the line's slack if either half of the change is missing, and on a
`text-area`'s second line it fails by a *different* amount — which is why the
per-line case has a test of its own.

`caretArea` for a `text-area` now starts at the line's indent, so an input method's
candidate window lands under the glyphs rather than under the paragraph's origin.
`caretOffset` stays relative to that rectangle, which is what the seam already
promised.

`white-space` and `text-overflow` ride along with the flow and mean what they say.
No shipped rule sets either on a field: a wrapped `text-area` is the `text-area`'s
own business and it passes a definite width down, so nothing in the catalog moved
and no golden image changed.

## Alternatives considered

**Leave the controls alone, as ADR-0318 did.** Defensible while nothing asked;
`docs/gaps.md` G30 asked, and the property has been in the subset since ADR-0256
with two controls unable to use it.

**Pass the alignment to `Value` and let the paint do everything.** It is half the
change and it is the bug: the glyphs would be centred and the caret would not.

**Give the controls a `TextFlow` rather than a `TextAlign`.** They read one of its
four fields. `white-space` is the `text-area`'s own decision (it passes a width
down), `text-overflow` belongs to a single-line label, and a decoration is drawn by
the paint with no geometry for the control to place — so taking the whole value
would be taking three things nothing here reads.

**Use `TextGeometry`'s new aligned forms in both controls.** The honest long-term
answer, and a bigger change than this one: both controls do their own line walking
for reasons that predate `TextGeometry` (a masked display, a preedit splice, a
scrolled window, a row cap). Replacing that is a refactor with no new behaviour,
and this ADR is the behaviour. The indent they add is the same method
`TextGeometry` calls, so the duplication is one call and not one rule.
