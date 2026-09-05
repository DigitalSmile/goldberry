# 265. Yoga measures an inset from the border box

Date: 2026-09-05

## Status

Accepted as a **finding**. Answers the question the `text-input` entry left open
and prices the fix; no behaviour changes.

## Context

The entry asked one question and did not answer it:

> **An absolutely positioned child is placed against the border box, and the clip
> is the padding box.** `text-input` allows for it by adding its own padding to
> every child's `left`, which works and is a workaround: the next widget that
> places a child absolutely inside a padded box will hit the same thing and will
> not know to. **Whether Yoga or the painter is the one disagreeing with CSS has
> not been established.**

CSS is unambiguous. An absolutely positioned box's containing block is the
**padding box** of its nearest positioned ancestor, and `overflow: hidden` clips
to that same padding box. The two agree by construction, which is why placing
against one and clipping to the other is a *disagreement* rather than a pair of
choices.

## The finding

**Yoga.** And it contradicts itself.

A 40×20 absolute child in a root with `padding: 12px`, laid out at 200×100, with
`errata` at its default of `None` — Yoga's spec-compliant mode, so this is not a
setting anybody turned off:

| child | Yoga | CSS |
|---|---|---|
| `left: 0; top: 0` | **(0, 0)** | (12, 12) |
| no insets at all | **(12, 12)** | (12, 12) |

So it is not that Yoga does not implement CSS absolute positioning. It is that
**one path of two** does. Given no insets it uses the padding box and is right;
given an inset it measures from the border box and is wrong.

The painter is exonerated. It clips to the padding box, which is what CSS says,
and it is doing so against positions Yoga computed against a different box.

Two permanent tests in `YogaLayoutTest` record both halves, against the compiled
library rather than against this record.

## Why this is not fixed here

The fix is one line of arithmetic in the wrong place. `RenderObject` applies an
inset to its **own** Yoga node and has no reference to its parent's resolved
padding at that point, so making the toolkit CSS-correct means the parent pushing
its padding down to each absolutely positioned child — a change to how the render
tree is applied, not a change to a number.

And the widgets that use this **already compensate, differently**:

- `text-input` and `text-area` add their own padding to every child's `left`,
  which is the workaround the entry names.
- `SegmentedIndicator`, `TourVeil`, `TourStop`, `WindowRoot` and `scrollbar` place
  absolutely inside parents that have no padding, so they are correct by
  accident of their surroundings.

Fixing the placement centrally therefore means **removing** `text-input`'s
compensation in the same commit, or it double-counts — and it moves the layout of
anything in the second group whose parent later grows padding. That is a
correctness change with a golden tail across `text-input`, `text-area`,
`segmented`, `tour` and `scroll`, and it is worth doing deliberately rather than
as a rider on an investigation.

What has changed is that the next person does not have to find this out. The
entry said "has not been established"; it is established, with numbers, in a test
that runs against the library on every platform.

## Alternatives considered

- **A Yoga `errata` flag.** There is one for the *other* case —
  `YGErrataAbsolutePositionWithoutInsetsExcludesPadding` — and none for this one.
  The flags exist to opt *into* legacy wrongness, and the default is already the
  spec-compliant setting, so there is nothing to turn on.
- **Clipping to the border box instead**, to make the pair consistent the other
  way. It would contradict CSS twice rather than once, and `overflow: hidden` is
  read by hit testing as well as the painter — a clip that included the padding
  would take presses in a control's margin.
- **Fixing it now.** Priced above. The reason not to is that it must be paired
  with removing a compensation in two widgets, and doing that inside an entry
  about establishing a fact is how a golden moves without anybody looking at it.
- **Leaving the question open.** The entry had been open for two milestones with a
  workaround in the tree and a comment saying the next widget would not know. An
  hour of Yoga answered it.

## Consequences

- **The entry keeps its subject and gains its answer**, and the answer names the
  path rather than the library: Yoga's inset path, not Yoga.
- **Two tests in `:natives`**, which is where a claim about the compiled library
  belongs — and they are the first tests in that file that assert Yoga is *wrong*
  about something, so both say what CSS would have said instead.
- **The fix is priced and located**: parent pushes padding to absolutely
  positioned children in `RenderObject`, minus `text-input`'s and `text-area`'s
  compensation, plus whatever goldens move. Nothing about that is discovery any
  more.
- **`text-input`'s workaround is now a *documented* workaround** rather than a
  local oddity, which is the difference between the next widget hitting this and
  the next widget reading about it.
