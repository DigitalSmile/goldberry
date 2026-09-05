# 258. The edge a measurement chose

Date: 2026-09-05

## Status

Accepted. Takes fifteen of the sixteen pairs off `ContrastTest`'s recorded debt
and leaves the sixteenth with an arithmetic reason rather than an undecided one.

## Context

[ADR-0239](0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md) built the
non-text half of §1.2's sweep and found nineteen pairs below the 3:1 floor.
[ADR-0240](0240-the-ring-follows-the-accent.md) fixed three of them — §2.2's
focus ring — because its cause was a ramp left behind rather than a colour anyone
chose. The other sixteen were recorded as debt, with the reason stated plainly in
the test:

> Every one of these is a **theme colour**, and sliding a ramp to clear 3:1
> changes what the toolkit looks like … That is a design decision with a
> golden-image tail, and it is not one a test may take on its own authority.

The `TODO.md` entry inherited that framing and filed all sixteen together, as one
thing waiting for one decision.

**They are not one thing.** Measured against the arithmetic rather than against
the sentence, they are three:

- **Twelve control boundaries** where the palette has a gap and nothing had been
  put in it.
- **Three marks that miss by 0.02**, which is what a ramp slide is for.
- **One mark that no solid colour can fix**, and which is therefore not a ramp
  question at all.

`design-system.md` §1.2 already says "**Every non-text pair meets 3:1**". So
fifteen of these are the code being out of compliance with a decision the design
system has already made, and closing them is obeying it rather than taking it.

## Decision

### `--gb-checkbox-border`, and a gap in Nord

The twelve boundaries are one fact: `--gb-checkbox-bg` **is** `--gb-surface-2` on
the dark theme, so an unchecked box on a `group-box` differs from its backdrop by
nothing at all — 1.00:1 — and the whole control was held up by its edge at
1.17:1. The edge was `--gb-border`, and `controls.css` argued for it in as many
words: *"the border makes an unchecked box visible on a surface it would
otherwise match; `--gb-border` is the token for exactly that and is why this is
not an invented colour."*

The measurement refutes that sentence. A divider colour is chosen to be subtle,
and a control's edge is the one thing §1.2 does not allow to be.

**Nord has nothing to put there.** Between `--nord3` (#4c566a) and `--nord4`
(#d8dee9) the palette simply stops, and against `--gb-surface-2` those two
measure 1.17:1 and 6.39:1 — invisible, or a bright white edge around a dark
control. The light theme is the same gap read from the other end: 1.11:1 and
6.06:1.

So the colour is invented, and ADR-0088's method says how far: slide until it
clears and no further, and write the measurement beside it. `#959dad` on dark and
`#79818f` on light are the midpoints of that gap, at **3.17:1** and **3.22:1**
against the tightest surface each faces.

Inventing a value is not new here — `--gb-accent-bg-hover`, `--gb-accent-fill`
and `--gb-border-strong` are all derived rather than palette colours, each with a
comment saying why. What is new is that this one's justification is a number.

`--gb-radio-border` is `--gb-checkbox-border`, for the reason every other radio
token is a checkbox token: §2.1 gives the two controls one drawing.

### The light accent slides by 0.02

Three of §3's marks are `--gb-accent` on `--gb-border` — a slider's fill, a
progress bar's, a knob's arc — which is one pair wearing three names, and it
measured 2.98:1. `--nord10` moved to `#5c7ea8`, which is 3.11:1.

Every other pair the accent appears in moves the **same** way, because a darker
accent on a light theme is further from every surface it is drawn on: the focus
ring gains, the checked fills gain, and `--gb-checkbox-mark-checked` on the
checked fill gains. There is no pair this trades against, which is what made it
safe to do without a second sweep to referee it.

### The sixteenth is arithmetic, and it is now written down as such

The light theme's slider track sits between a **white thumb** and a **dark accent
fill**, and §1.2 asks it to clear 3:1 against both. A white thumb needs the
track's relative luminance at **0.300 or below**; the accent fill needs it at
**0.688 or above**. No solid colour is both, so there is no value of
`--gb-slider-track-bg` that fixes this and no amount of deliberation that will
find one.

What has to change is what a light-theme thumb *is* — a border around it, or a
fill that is not white — and that is a sentence `docs/design-system.md` §3 does
not contain. It stays on `MARKS_BELOW_FLOOR`, alone, with the impossibility
recorded beside it so nobody spends an afternoon sliding the track.

## Alternatives considered

- **A palette colour for the edge.** `--nord9` clears on dark at 3.21:1 and is a
  **Frost** blue: an unchecked control with a blue edge reads as selected, beside
  a checked one whose whole fill is the accent. `--nord4` clears at 6.39 and is a
  near-white ring around a dark box. The gap is why this is derived.
- **Darkening `--gb-checkbox-bg` instead of lighting the edge.** It fixes the
  fill measurement and makes the control *recede* — an unchecked box would become
  a hole rather than a component, which is the opposite of what "can be
  identified" asks for.
- **Lightening `--gb-border` itself.** It is a decorative divider in every other
  use, and ADR-0239 already established that measuring it as one was the first
  version of this sweep and was wrong. Making the divider loud to fix the control
  would put the error back the other way round.
- **Sliding the light track to fix the thumb.** Ruled out by the arithmetic above,
  and recorded rather than attempted.
- **Leaving all sixteen.** What the entry proposed. It treats an impossibility, a
  0.02 miss and a palette gap as one decision, and the effect is that the two
  solvable ones wait on the unsolvable one.

## Consequences

- **`BOUNDARIES_BELOW_FLOOR` is empty**, and asserted empty on
  [ADR-0088](0088-a-fill-that-carries-text-moves-away-from-it.md)'s exact-set
  terms: a boundary that newly breaks has somewhere it must be written down, and
  writing it there fails a test whose name says what happened.
- **Twenty-two goldens moved**, which is the tail the entry predicted and the
  reason it had not been paid. `controls-on-surface-dark` and
  `controls-on-surface-light` are the two to look at: they exist because the
  glyph used to disappear on a panel, and they are now the images that show it
  does not.
- **The accent change is invisible** and the edge change is not, which is the
  right way round: 0.02 of ratio is a colour nobody can see moving, and an edge
  that was doing no work now does some.
- **`controls.css`'s comment is inverted rather than deleted.** It argued for
  `--gb-border` and the argument was wrong; the new comment says so and says what
  measured it, because the next person will otherwise re-derive the same
  reasonable-sounding mistake.
- **Two design-system claims are now true that were not.** §1.2's "every non-text
  pair meets 3:1" had fifteen exceptions and has one, and the one is annotated.
- **Nothing about the *text* sweep changed.** `KNOWN_FAILURES` was empty before
  this and is empty after, which is the check that the accent move did not buy
  the marks at the labels' expense.
