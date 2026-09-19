# 436. A column count is a width the window does

Date: 2026-09-19

## Status

Accepted. Builds `min-column-width` on `masonry`, which
[ADR-0196](0196-a-masonry-is-a-layout-that-reads-last-frame.md) left out and
[ADR-0117](0117-a-widget-may-be-told-what-it-measured.md) is the rule for.
Corrects one sentence of `core-widgets.md` §1 that this went to check and found
false. Its fixed-point evidence is
[ADR-0420](0420-the-measured-rule-is-checked-by-a-fixed-point.md)'s harness.

## Context

`TODO.md` carried this for a long time as a thing that could not be built:

> A `masonry`'s column count is a number and not a breakpoint. Two columns at
> 1200px are two columns at 720px — half as wide and twice as tall — because the
> count is a constructor argument and no selector can count columns.

The entry's first draft said the reason was the layout: *"as many columns as fit
at a minimum width" is a layout pass that reads its own width, which is the loop
ADR-0196 built the last-frame read to avoid.* That reads the record backwards.
ADR-0196 **is** the last-frame read. A `masonry` already banks every card's
height through `Measured` and re-deals its columns on the strength of it;
reading its own *width* is the same door one step over, and `Measured`'s third
rule — *what it triggers must not change what it reports* — has the same answer
for the width that it has for the heights: a column count changes the wall's
height and not its width.

What actually blocked it was a document. `masonry` had **no row in
`core-widgets.md` §1 at all** — it was named once, in passing, as what the
showcase's screens are made of. §5's spec-then-metrics-then-gallery gate had
nothing to have passed, so there was no specification to build against and no
way to tell an addition from a drift. `Masonry`'s own class comment said so, in
the first line under the code fence: *"Not in `core-widgets.md`."*

The row exists now, and so does the `design-system.md` §3 metrics row. This
builds them.

## Decision

**A `masonry` says how many columns it wants or how narrow a column may get, and
never both. The width it counts against is its own, from last frame.**

### Two modes, one of them the default, and no third

`columns=N` is a fixed count. `min-column-width=N` is *as many columns as fit at
this width, at least one*. Both at once throws at construction, and that is the
choice worth defending: the alternative is a precedence rule, and a precedence
rule is a thing an author reads once and then guesses at for ever. A document
that says both meant one of them, and the machine cannot tell which.

Neither gets `min-column-width: 320`, from §3's row. **The default moved from a
count to a width**, and that is the whole of this ADR in one line. A count
cannot be right at two window sizes; a default is exactly the value nobody
thought about; so the value that ships with no thought behind it must be the one
that survives a resize. `DEFAULT_COLUMNS = 3` is gone.

The absent one is `Masonry.UNSET`, which is `-1` and not `0`. Zero is a number an
author can type and a spreadsheet can produce, and reading it as *"I said
nothing"* would turn `columns=0` — which has thrown since ADR-0196 — into a
silent change of layout mode. `inflate` clamps neither property now, for the
same reason the two together throw: a document that disagrees with its layout is
found by looking at a picture, and a document that throws is found by running
anything.

### The width comes from the node the stylesheet already selects

`MasonryBox` carries the CSS type, the `id` and the classes — the arrangement
every stateful widget in the catalog uses — which makes it the one node in the
subtree whose rectangle *is* the masonry's. So it implements `Measured` and
reports its width, and no wrapper had to be invented to hold the question.

One had been invented already. `IconsScreen` wanted as many tiles as fit and
built `icon-sheet` to measure the room it had (ADR-0309), with a class comment
arguing rule 3 from first principles and reaching the same conclusion this does.
That wrapper turns out to have been the widget's job all along; it stays where
it is, because that screen is a virtualized `list` now (ADR-0316) and no longer
has a masonry in it to hand the job back to.

### The gap is read in `render`, and it is the fence-post

`n` columns need `n` minimums **and `n − 1` gaps**, so the count is
`⌊(width + gap) / (minColumnWidth + gap)⌋` — add one gap to both sides and the
fence-post goes away. Counting without the gaps over-counts by one at every
boundary, and an over-counted wall is one whose columns are each a few pixels
under the minimum that was the point of asking.

Which means the widget needs the sheet's `gap`, and only `render` is handed the
style the cascade resolved. That is `toaster`'s shape exactly (ADR-0178) and is
taken for the same reason: a stylesheet that changed `masonry { gap }` and
nothing else would otherwise leave every wall counting against the wrong pitch.
It is banked **without** `setState`, and not as an optimization — `render` is
inside the frame a rebuild would dirty. It does not need one either: the gap
arrives on the first `render`, which is strictly before the first width the
router can deliver, so the very first count is already counted against the real
pitch.

A percentage gap reads as none rather than as a guess. A percentage gap on a row
is a fraction of the row's own width, which is the number being counted against,
so honouring one would make the count a function of itself.

### A width does not rebuild the wall; a *count* does

`MasonryState.width` banks every reading and calls `setState` only when the
derived count changes. A wall told it is one pixel wider has not changed shape,
and a wall that rebuilt on every pixel of a window drag would re-deal a hundred
cards per frame to put them all back exactly where they were. §1.7's idle frame
loop is the reason the heights have had the same guard since ADR-0196.

## What this went to check, and got wrong

§1's row hedges the safety argument: *"This holds only for a `masonry` whose
width comes from its parent […] a `masonry` inside a shrink-to-fit box would
oscillate, and that combination is refused for the same reason `columns` and
`min-column-width` together are."*

It cannot be refused where the other one is. Shrink-to-fit is a property of the
box a wall was **put in**, and a widget cannot see its parent; nothing at
construction knows. So the plan was to make it a named red case in the
fixed-point harness instead — and the harness says it is green, in one layout,
which is the number a tree with no feedback in it at all returns.

**It does not oscillate, and the reason it does not is one line of
`controls.css`.** The feared loop is real arithmetic: a wall sized to its own
content would be `n` columns each as wide as the widest card in it, so a bigger
`n` makes a wider wall, which asks for a bigger `n`. It needs the columns to be
as wide as their cards — and since ADR-0373 they are not. A `masonry-column` is
`flex-basis: 0` with `flex-grow: 1`, so it contributes **nothing** to its
parent's content width, and a masonry with no definite width of its own is
measured at zero however many columns it has. The count is independent of itself
by construction, which is precisely what rule 3 asks for, and the construction is
a stylesheet rule rather than a promise about somebody's parent.

So there is nothing to refuse, and what is left is worse documentation rather
than a worse layout: a `masonry` in a shrink-to-fit box is zero pixels wide with
its cards hanging out of it, and has been since ADR-0373, with a fixed `columns`
exactly as much as with this. Blaming that on `min-column-width` would have
attached a real defect to the wrong change and left the older one unnamed.
`MasonrySettleTest.ShrinkToFit` holds both halves so the claim is a number and
not a paragraph.

`core-widgets.md` is not this ADR's to edit. The sentence to strike is the
"would oscillate" clause; what belongs there instead is that a masonry needs a
box that gives it a width, in either mode, and that the toolkit cannot produce
the loop the sentence describes.

## Consequences

- **Three distinct layouts, and `Offscreen` affords exactly three.** A
  responsive wall of prose is measured (1), re-columns — which puts every card
  at a different width and makes every banked height stale in the same instant
  (2) — and re-deals on the new heights (3). A fixed wall takes two; a wall whose
  cards have written-down heights takes two, because re-columning cannot move a
  height that is written down; a wall too narrow to divide takes one.
  `Offscreen.render` runs two measuring passes and paints the third
  (ADR-0424), so a responsive wall is photographed **settled with nothing to
  spare**. A fourth layout would not be a red test, it would be a gallery of
  walls caught mid-reflow — which is why `MasonrySettleTest` asserts the number
  rather than bounding it.
- **The first frame of a responsive wall is one column.** Nothing has measured
  it, and a wall that guessed would be photographed mid-guess by anything that
  renders a fixed number of passes. It is the round-robin first frame ADR-0196
  already accepts, one step wider.
- **`Wall` carries two numbers now.** A showcase screen hands its document's
  mode on rather than its count, because a screen that came back as a count when
  the document said a width would quietly stop following the window and nothing
  would say so. `ShowcaseDocumentsTest` asserts the two legal shapes and no
  third.
- **One golden moved, and it is the one the entry is about.** `basic.kdl` says
  `min-column-width=560`; that wall is 1168 wide in a 1200 window and 688 in a
  720 one, with a 12 gap, so it is the two columns it always was at 1200 and
  **one** at 720. `gallery-basic` and `gallery-basic-light` are unchanged, and
  `gallery-basic-narrow` is now a picture of a wall reflowing instead of a
  picture of a wall surviving. The assertion it makes is stronger: the old one
  could only tell you that nothing burst.
- **560 and not §3's 320, on that screen, and the other eight walls keep their
  counts.** At 320 the default would give *three* columns at 1200 — a third
  narrower than these cards were built for, on a screen where §10's `wrap` is
  not built to catch what overflows — and still **two** at 720, so the picture
  this change exists to fix would not have moved. Converting the rest would
  re-column six screens at the width the gallery is photographed at, which is a
  redesign of the gallery rather than a demonstration of the attribute. The
  default is right for a wall of unknown cards; a showcase knows its cards.
- **Every `masonry` is told its width, including the fixed ones**, which throw
  the number away. One `MasonryBox` is cheaper than two, and the router only
  notifies on a change, so a still window notifies nothing.
- `design-system.md` §3 says `gap 16 (12)` and `controls.css` says `12` flat.
  That disagreement predates this and is not touched here: the gap is read from
  the sheet, so the count follows whichever number the sheet ends up with.
