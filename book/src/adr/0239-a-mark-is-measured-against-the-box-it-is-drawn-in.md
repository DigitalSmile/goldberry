# 239. A mark is measured against the box it is drawn in

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0088](0088-a-fill-that-carries-text-moves-away-from-it.md), and opens one in
its place — the nineteen pairs the measurement found.

## Context

`ContrastTest` has enforced §1.2's **4.5:1** for text since ADR-0087, and the
entry recorded what it did not enforce:

> Non-text contrast is not checked at all. `ContrastTest` measures text against
> the fill under it. §1.2's other half — 3:1 for anything that is not text —
> reaches a checked checkbox's mark, a slider's thumb against its groove, a
> spinner's ring, a border against the surface it separates, and the focus ring
> against whatever is behind it. None of them is measured, and ADR-0088's
> argument that the accent ramp did not need to move rests on exactly that
> unenforced number. The arithmetic is already written; what is missing is
> deciding what counts as the "background" of a mark drawn *onto* its own box.

The last sentence is the whole of the design problem, and it turns out to have a
simpler answer than it sounds.

## Decision

### What counts as the background of a mark drawn onto its own box

**Its own box**, and both halves come out of one `ComputedStyle`.

A mark is coloured by the `color` of the element it is drawn in, and that element
supplies its own `background`. A checked checkbox's tick is
`--gb-checkbox-mark-checked` on `--gb-checkbox-bg-checked`, and *both are set by
the same `check-indicator:checked` rule*. A slider's thumb sits on its groove; a
knob's arc on its track. So the pair is one element's two properties rather than
a composite of anything, and nothing here needs the painted frame — which is what
keeps this a cascade test like the two sweeps beside it.

### Three sweeps, because there are three shapes of question

- **A mark against the box it is drawn in** (`everyMarkIsVisible`) — nine pairs:
  the checkbox tick, the radio dot, the toggle thumb in both states, the slider
  thumb and fill, the progress fill, the knob arc and pointer.
- **A ring against the surface behind it** (`everyRingIsVisible`) — the focus
  ring and the spinner, each on all three surfaces a window paints, because
  neither has a plate of its own and a control may sit on the page, in a `card`
  or inside a `group-box`.
- **A control against that surface** (`everyControlIsDistinguishable`) — and this
  one is a **maximum of fill and edge**, not two independent measurements.

### The maximum is the part that took thinking

§1.2's non-text floor exists so a component can be *identified*, and a control
offers two means at once: a fill that differs from the surface, and an edge drawn
around it. WCAG asks that some means clears the floor, not that every one does —
a filled button with no border is not a failure for having no border.

Measuring the two separately was the first version, and it is wrong in a way that
matters: it reported `--gb-border` failing on every surface in both themes, which
is a *decorative divider* doing exactly what a 1px separator is meant to do. The
same token is also a control's edge, and only in that role is it held to 3:1. The
maximum is what tells the two roles apart without needing two tokens.

### What the sweeps find is recorded, not fixed

Nineteen pairs are below the floor, held in three exact-set lists on
`KNOWN_FAILURES`' terms — a pair that newly breaks cannot be parked quietly, and
one that gets fixed fails the test until it is taken out. Each carries its
measurement.

They are **not fixed here**, and the distinction is deliberate. `KNOWN_FAILURES`
is empty because ADR-0088 *fixed* the seven text pairs it found; the same move is
not available, because every one of these is a **theme colour** and sliding a ramp
to clear 3:1 changes what the toolkit looks like. That is a design decision with a
golden-image tail rather than something a test may take on its own authority.

What this change owes is the measurement, and the entry said so itself:
"ADR-0088's argument that the accent ramp did not need to move rests on exactly
that unenforced number". It is enforced now, and it says the ramp does need to
move.

## Alternatives considered

- **Walk the real element tree and compare every box against its ancestors.** It
  generalises and it is unusable: a `card` on the page is a deliberate, subtle
  surface change and would be reported as a failure, because nothing in the
  cascade says which colour differences carry meaning. The curated list is what
  encodes that judgement, and it is the same shape `pairs()` already uses.
- **Composite translucent fills over a stated surface**, so `button.ghost` and
  `--gb-selection` could join the sweep. That is the backdrop-aware check the
  `button.ghost` entry already says would need the painted frame; adding half of
  it here would let a ratio look measured when what it composites over is still a
  guess.
- **Fix the themes in this change.** Nineteen pairs across two themes is a
  redesign of the neutral and accent ramps, and every golden in the repository
  would move with it. Shipping the measurement first is what makes that a
  reviewable diff rather than an unreviewable one.
- **Lower the floor for the pairs that nearly clear it.** Three of the four mark
  failures are one token pair at 2.98:1 — `--gb-accent` on `--gb-border` in the
  light theme, missing by 0.02. A floor that moves to admit what fails it is not
  a floor.
- **Leave the unchecked checkbox out**, on the grounds that a checkbox is usually
  on a form's own surface. It is 1.00:1 against `--gb-surface-2` in the dark
  theme — the fill *is* the token — and a `group-box` paints exactly that.

## Consequences

- **Three new sweeps and nineteen recorded failures**, the worst of them §2.2's
  **focus ring, below 3:1 on all three surfaces of the light theme** (1.74, 2.00,
  1.64). A focus ring is the one mark in the system with no second means of being
  seen, so it is the first thing the follow-up entry names.
- **`--gb-checkbox-bg` is `--gb-surface-2` in the dark theme**, so on a
  `group-box` an unchecked box differs from its backdrop by nothing at all and is
  held up entirely by a 1.17:1 edge. That is the shape of all twelve boundary
  failures.
- **The class comment no longer says "the exemption list is empty".** It was true
  of the text sweep and is now only true of the text sweep, and a comment that
  overstates a guarantee is the kind ADR-0237 had just finished correcting
  elsewhere.
- **`ratio(theme, background, color)` is factored out** of the three sweeps that
  had been building the same forced rule inline.
- **Nothing in the toolkit changed.** This is a test-only change, which is why it
  can state the problem without also being the thing that solves it.
