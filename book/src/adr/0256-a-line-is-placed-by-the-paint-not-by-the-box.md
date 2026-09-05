# 256. A line is placed by the paint, not by the box

Date: 2026-09-05

## Status

Accepted. Closes the `slider` value-label entry in `TODO.md` and removes
`text-align` from `docs/ARCHITECTURE.md` §8's list of properties that resolve
into nothing. Extends
[ADR-0255](0255-a-label-that-does-not-fit-is-cut-not-wrapped.md), whose value
this adds a third component to.

## Context

§8 has listed `text-align` in its paint half from the beginning, and §8's own
note explained why it did not resolve:

> `box-shadow`, `backdrop-filter`, `letter-spacing` and `text-align` are absent,
> because `Box` cannot express them and a property that resolves into nothing is
> a property with no test that means anything.

Three of those four are true. `box-shadow` needs a drawing `Box` has no field
for; `backdrop-filter` needs a second pass over what is underneath;
`letter-spacing` needs the shaper to be told something before it shapes. Each is
a real absence in a real place.

`text-align` is not like them, and the entry that has been open longest says so
without meaning to:

> **A slider's value label is left-aligned in its box**, because §8's subset has
> no `text-align` — `docs/ARCHITECTURE.md` §8.1 lists it among the properties
> `Box` cannot express, so it resolves into nothing and has no test that could
> mean anything. A right-aligned readout is what the column of numbers beside a
> row of faders wants. It arrives with whatever else needs `Box` to place text
> inside a box rather than at its origin.

**Nothing has to be added to `Box`.** `Paragraph.paint` is already handed the
box's width — it has to be, or the text could not wrap to it — and every
`TextLine` has already measured itself. The two numbers the alignment needs have
been in the same method the whole time; what was missing was a keyword saying
what to do with them. ADR-0255 then put the last piece in place by giving that
method a style value to read.

`slider-value` is the consumer that has been waiting: it is `width: 40px` by
declaration, because a label that sized itself to its digits would resize the
track under the finger setting it (ADR-0080). A fixed box is exactly the
condition under which alignment means something — and left-aligned, `9%` and
`100%` start in the same column and end four pixels apart, which is the opposite
of what a column of numbers is for.

## Decision

**`text-align: start | center | end`**, resolved by the cascade and applied by
`Paragraph.paint`. `Box` is untouched.

### It is a third component of `TextFlow`, not a fourth thing to thread

`TextFlow` was already the value the paragraph reads about its box. `white-space`
and `text-overflow` answer the **too-wide** question and `text-align` answers the
**too-narrow** one, and all three are answered in the same place for the same
reason: the paint is the only code that holds both the line's width and the
box's.

It inherits, like `white-space` and unlike `text-overflow`, which is CSS's own
split and the reason `ComputedStyle` carries three components rather than one
record. It also has to inherit to be usable: `text-align` is written on a
*container* far more often than on the node that draws the text.

### Per line, and never negative

The offset is `max(0, boxWidth - lineWidth) × fraction`, computed per line.

**Per line** is what `text-align` means — a centred paragraph centres each of its
lines rather than centring the block they make up, and the difference is exactly
the short last line.

**Clamped at zero** for two separate reasons, both reachable. A `nowrap` line
wider than its box would otherwise be pulled *left* by `text-align: end`, hiding
the beginning of the text to show an end the reader can already guess. And
`maxWidth` is `UNCONSTRAINED` wherever a caller is measuring rather than placing,
which without the clamp is an infinite offset and a blank frame.

### An ellipsised line is not moved

A truncated line fills its box by construction, so there is no slack to share
out. Falling out of the arithmetic rather than being special-cased would also
have been correct; it is written as a branch because the reader should not have
to derive it.

## What is deliberately not accepted

- **`left` and `right`.**
  [ADR-0247](0247-start-is-css-and-flex-start-is-yoga.md) settled the same
  question for `align-items` in the other direction: `start` and `end` are what
  CSS Box Alignment defines and what Yoga lacked, while `left` and `right` name
  sides of the screen. They coincide under LTR and part company under RTL, so
  accepting `right` as a synonym for `end` writes down an answer that is right
  today and silently wrong the day bidi run splitting lands. A stylesheet that
  writes one gets the ordinary dropped-value warning, and `SupportedPropertyTest`
  makes that a build failure for the toolkit's own sheets.
- **`justify`.** Not a placement but a respacing. A paragraph here is shaped once
  and sliced into lines, so there is nowhere to put the extra advance without
  re-shaping — which is the one thing ADR-0036's design is built to avoid.
- **Vertical alignment.** `align-items` on the box already does it, because a
  paragraph is the whole of a measured leaf's content.

## Alternatives considered

- **A field on `Box` and an offset in `BoxPainter`.** It is where §8's note said
  the work was, and it puts a text decision in the box painter — which would then
  need the line widths, which only the paragraph has. The box painter would have
  to ask the paragraph and then tell it, one call apart.
- **Leaving the spacer in a menu row.** It stays, and is not an alternative to
  this: `text-align` places a line inside **one** box, and a menu row shares its
  room between five. A growing box is flexbox's own answer to that and the only
  one that keeps the chevron after the accelerator rather than under it. The
  comment in `Item.render` now says which of the two each is for.
- **Accepting `right` and mapping it to `end` with a warning.** A warning nobody
  reads on a rule that works is a rule that works, and the day it stops working
  is the day the warning was needed.

## Consequences

- **`slider-value` is `text-align: end`**, and `slider-value.png` moved: `9%`,
  `50%` and `100%` now line up on their trailing edge, which is what a fixed
  width was always for. **Four goldens changed and all four are the same
  readout** — `slider-value` plus the showcase's Basic screen in its three
  variants, where the diff is 274 pixels and every one of them is the `40%` on
  the gain slider. Nothing else in the corpus draws a `slider-value`, which is
  why the count is four rather than the number of images with text in them.
- **`ComputedStyle` grew a fourth wither and a third text component**, and
  `inheritsSameAs` grew a third field. That is one more way for the style cache
  to miss and is the cost of the property inheriting, which it must.
- **`TextFlow` gained a two-argument constructor** so that every caller written
  before this keeps drawing exactly what it drew. `TextFlow.NORMAL` and
  `TextFlow.ELLIPSIS` both say `START`.
- **§8's "properties `Box` cannot express" is down to three**, and the three that
  are left are there for reasons that are actually about `Box`.
- **Nothing centres anything yet.** `CENTER` is built and has no consumer in the
  catalog, which is one more property than the "grow against a named need" rule
  strictly allows — it is one enum constant on a property that had to be parsed
  anyway, and refusing the middle value of three would be a stranger thing to
  explain than shipping it.
