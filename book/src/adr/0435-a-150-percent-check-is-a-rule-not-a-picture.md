# 435. A 150% check is a rule, not a picture

Date: 2026-09-19

## Status

Accepted. Finishes what [ADR-0267](0267-a-text-scale-is-a-renderer-switch.md)
started — it built `renderer.textScale` and said in as many words that nothing
enforced §1.4's 150% yet — and answers the half of `TODO.md`'s typography entry
that was waiting on a decision. Sits beside
[ADR-0118](0118-the-gallery-is-photographed-screen-by-screen.md), whose
single-font gallery turns out to be load-bearing here in a way nobody had
noticed.

## Context

§1.4 asks that "every component must survive 150% without clipping". ADR-0267
built the switch: a factor applied where a `ComputedStyle` becomes a `Font`, so
the text grows and a `height: 32px` does not. It recorded that nothing enforced
the condition, because nothing *could* — the mechanism had only just arrived.

The obvious enforcement is a golden: the eleven gallery screens, photographed
again at `textScale(1.5)`. `TODO.md` warned against it, and the warning is the
whole context:

> since `text-overflow: ellipsis` shipped, some cutting is correct, so "no text
> is clipped" is no longer the sentence, and a golden of eleven screens at 150%
> would pin every one of those decisions at once in a picture before anybody had
> taken them.

That is exactly right, and it is worth being precise about why, because "a golden
is expensive" is the weaker half of the argument. A golden asserts **this is what
it drew**, which a wrong picture satisfies as well as a right one. Before
`text-overflow` existed the gap was survivable: the rule was "no text is
clipped", a reviewer could check eleven images by eye once, and the images then
held the answer. It stopped being survivable when a cut became a legitimate
outcome. At 150% some labels are *supposed* to end in `…`, some are supposed to
wrap, and which is which is a decision per label — three hundred of them across
the gallery. A photograph would freeze all three hundred in one commit, in a form
nobody reviews, before a single one had been made.

**And there is a second thing the entry could not have known.** The gallery's
goldens are taken with `WidgetRenderer`'s single-font constructor, whose paint
context is:

```java
this.paintContext = context(style -> font);
```

The style is discarded. The text scale is applied *to a style* — that is ADR-0267's
central design choice, and the reason an `em` chain does not take the factor once
per level. So **`textScale` is a no-op with the one-font renderer.** A 150% golden
of the gallery, taken the way the gallery's goldens are taken, would have rendered
the 100% tree, matched the 100% image, and passed for ever. The clipping half was
not only waiting on a decision; it was waiting on one more mechanism nobody had
looked for, hidden behind the same single-font constructor ADR-0118 recorded as
the reason the gallery cannot see typography at all.

## Decision

**The 150% check is a rule evaluated against the laid-out tree, and the rule is
differential.** `TextScaleAudit` in `core/src/testFixtures` lays a widget tree out
twice — at 100% and at 150%, through a **font book**, at display scale 1.0 — and
asserts:

> Growing the text to 150% introduces no cut nobody asked for, and pushes no box
> past its container.

Two arms, and four candidates were weighed to get there.

### Adopted: no line is cut without something asking for the cut

For every laid-out box with text, the audit mirrors `BoxPainter` exactly — inside
the padding, wrapped at the content width under `normal` and laid out
unconstrained under `nowrap` ([ADR-0255]) — and compares the widest line against
the content width. Over it is a **cut**; `TextFlow.ellipsises()` says whether the
stylesheet asked for one.

That is the first candidate, "no text is clipped *without* an ellipsis", made
exact. Mirroring the painter rather than re-deriving it is deliberate: a second
opinion about where the text goes is a check that passes while the picture is
wrong, and [ADR-0111]'s padding bug is what that looks like.

### Adopted: no box overruns its container

`OverflowWatch` already exists, already runs inside every `RenderTree.update`,
and already deduplicates through `OverflowLog` — so the audit gets this arm for
the price of reading a static list. It is **half** of the answer and could not be
all of it, which is worth writing down because the question "is `OverflowWatch`
the answer" is the obvious one to ask:

- **Its noise is a fact.** It caught five rows of buttons running off a
  720-point window at 150%, and the navigation wall growing 556 points taller
  than its 900-point screen.
- **Its silence is not evidence.** The walk is gated on the **root** node's
  `hadOverflow` ([ADR-0375]). A button that overruns its row while the window
  still has room reports nothing. Measured: at 150% the Emoji sheet has 119 tile
  captions taller than their tiles and the Icons sheet has 36, and `OverflowWatch`
  said nothing about any of them, because the window was not full.

### Rejected: every ellipsis at 150% was also reachable at 100%

Backwards. Making the text half again as wide is exactly what makes a new
ellipsis appear; a check that forbade it would forbid the feature. It is also
false on the corpus today: at 150% the HTML screen gains one marked cut (2 → 3)
and the Markdown screen gains two (1 → 3), and all three are `text-overflow`
working as designed. This candidate would have failed three correct ellipses and
called §1.4 broken.

### Rejected: nothing overlaps

Siblings overlap on purpose throughout the catalog — an absolutely positioned
child, a popover over its anchor, a tab's underline across its header, anything
`elevated`, every `transform`. The exception list would be longer than the rule,
and each entry in it would be a place the check had been told to stop looking.

### Measured but not asserted: a paragraph taller than its box

The audit also compares the paragraph's height against the content height, and
does **not** fail on it. This is the one call in the file worth arguing over, so:
nothing in the painter clips a paragraph vertically. The ellipsis is applied per
line; a fourth line past the bottom of a three-line box is *drawn*, over whatever
is beneath it, unless an ancestor's `overflow` cuts it. So a spill on its own is
not lost text — it is a box that overran, wearing a paragraph's clothes, and the
overrun arm is where that question belongs.

It is counted because it is the best evidence in the repository about where 150%
actually hurts: **Emoji 45 → 119 spilled captions, Icons 0 → 36.** Those are real
defects, in two sheets whose tile captions are given a height a 13.5-point line
does not fit, and they are invisible to every other check here.

### Differential, and a ratchet

An absolute "nothing is ever cut" would be a claim about the stylesheets as they
stand, and it makes the 150% question hostage to an unrelated backlog: the
Collections screen has one silent cut at 100% **and** at 150%, and the narrow
Basic screen already overruns twice at 100% before the text grows at all. What
§1.4 asks is narrower and answerable — *growing the text must not break what was
not already broken* — so the audit compares the two runs and fails only on what
150% added.

The overruns 150% adds today are six, and they are not fixed here: they are
listed in `GalleryTextScaleTest`, one line each with its reason, as **accepted**.
The list is a ratchet in both directions. A seventh fails. So does an accepted one
that stops happening, because a line left behind after the defect is fixed is a
line that will silently welcome it back.

They are recorded rather than repaired because `OverflowLog`'s own warning is
right that nothing here can pick the answer: "a `scroll` around it, an ellipsis on
it, or a `min-width` it may not go below are the three answers; nothing here picks
one." Choosing is a change to the showcase's widgets and its stylesheet. Choosing
it *inside* the commit that installs the check would be the golden's mistake made
in Java.

### And it opens a book

There is no single-`Font` form on `TextScaleAudit`, for the reason in Context: a
renderer built over one font applies the scale to nothing. So the audit opens a
book — which makes it the **first check in the repository to lay the gallery out
with `font-family`, `font-size` and `font-weight` resolved per node**, the
blindness ADR-0118 recorded and ADR-0386 chipped one screen off. That was not the
goal. It is a consequence of the mechanism, and it means the audit and the golden
beside it are looking at two different trees: a heading is 20px SemiBold in one
and 13px Regular in the other. Only one of them is looking at what the application
draws.

## Consequences

- `GalleryTextScaleTest` lays out fourteen scenes — the eleven screens, the two
  an optional module draws, and the narrow window — twice each, and inspects
  **2,602 paragraphs per scale**. It costs **1.3 s** of `:example:test`, against
  `GalleryGoldenTest`'s 12.0 s for 21 images. That ratio is the decision paying
  for itself: no pixel is rasterized anywhere in the audit, because the question
  is about rectangles and the rectangles come out of the layout pass. It runs
  `Offscreen`'s settling sequence with the paint removed — the two measuring
  passes are still there, or every `masonry` and `text-area` would be audited on
  its first guess.
- **Nothing in the gallery loses a line horizontally at 150%.** Across 2,602
  paragraphs the silent-cut count is 1 at 100% and 1 at 150% — the same one, on
  the Collections screen. That is a real result and it is the weakest part of this
  ADR: an arm that has never fired on the corpus is an arm this corpus cannot
  vouch for, which is why `TextScaleAuditTest` proves the rule on boxes whose
  numbers are in the test file rather than leaving the eleven screens to speak
  for it.
- **The gallery does not survive 150% today**, and now says so out loud: six
  accepted overruns and 155 spilled tile captions. That is the check's first and
  largest finding, and none of it was visible before.
- Combined with [ADR-0434], `check` now pays a three-multiplier display-scale
  sweep over 245 goldens **and** this two-scale text-scale audit over 28 layouts.
  The sweep is about four seconds; the audit is 1.3. They **do not multiply**: the
  audit runs at display scale 1.0 only, on purpose. Crossing the two axes would
  cost the whole gallery again — 1.3 s becomes 5.2 — to ask whether a label fits
  its box on a Retina display, which is a question already settled in logical
  units before a device is chosen. A text scale is not a zoom.
- `-Dgoldberry.golden.scales.report=true` prints what every screen measured, at
  both text scales, alongside what the display-scale sweep measured. One switch,
  reused rather than added, because `example/build.gradle` forwards a fixed list
  of properties into the test JVM and a new name would have needed a line there.
- The vertical spill is carried in `Result.spills()` and asserted by nothing. If
  the Emoji and Icons captions are ever given a height that fits, the honest way
  to lock that in is to promote the spill to an assertion — which is a one-line
  change here and a conversation about 155 tile captions first.

## What this does not do

It does not take a picture. If the 150% layout of a screen is ever worth pinning
as an image, the order is the one `TODO.md` gave: decide what the rule is, then
photograph the screen that obeys it. Not the other way round.
