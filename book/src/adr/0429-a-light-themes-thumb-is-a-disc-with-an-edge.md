# 429. A light theme's thumb is a disc with an edge

Date: 2026-09-19

## Status

Accepted. Closes the last entry on `ContrastTest.MARKS_BELOW_FLOOR`, which
[ADR-0239](0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md) opened with sixteen and
[ADR-0258](0258-the-edge-a-measurement-chose.md) left
holding one.

## Context

`docs/design-system.md` §1.2 states the problem and declines to solve it:

> **One exception is left and it is arithmetic**: the light theme's slider track
> sits between a white thumb and a dark accent fill, and clearing 3:1 against
> both needs its luminance at once ≤ 0.300 and ≥ 0.688. What that asks for is a
> sentence §3 does not contain about what a light-theme thumb is — a border, or a
> fill that is not white — and until it does, `MARKS_BELOW_FLOOR` carries it
> alone.

The arithmetic is done and it is right. `--gb-slider-thumb-bg` is `#ffffff` on
`--gb-slider-track-bg`, which is `--nord4`: **1.35:1**, the worst mark
measurement in either theme. Nothing about the track can fix it, because the
track is squeezed from both sides.

What §1.2 leaves open is the choice between the two fixes, and it is not a
toss-up. **The second one does not work.**

### Why a fill that is not white fails

Run the same arithmetic on the thumb instead of the track. The groove is
`--nord4` at relative luminance 0.727, so a thumb that clears 3:1 against it
needs luminance **≤ 0.209**.

Now look at what else the thumb is lying on. A slider's thumb is centred on the
value, which means it straddles the boundary between the fill and the rest: half
of it is over `--gb-slider-fill-bg` — the light accent, `#5c7ea8`, luminance
**0.200** — and half is over the bare groove. A thumb at 0.209 and a fill at
0.200 are the same colour to a tenth of a percent. The disc would clear the
groove and **vanish into its own filled half**, which is not an improvement; it
is the same failure moved to the other side of the thumb's centre.

Clearing both wants luminance ≤ 0.083, which is `#525252` or darker: a near-black
disc on a light theme. That is a shape the theme file has already rejected twice,
in its own words, about the switch:

> a thumb the colour of the window reads as a hole rather than as a disc

> the result read as a hole punched through the switch rather than a disc sitting
> in it

Both of those were ADR-0075, paid for twice. A third instance was available for
free and is declined here.

### The thumb is the one shape in the system with two backdrops

That is the whole of it. Every other entry in `MARKS` is a mark on **one** box —
a tick on a checkbox's fill, a dot on a radio's, an arc on a knob's track — so
one colour answers one pair and a ramp slide is always available. A slider's
thumb answers two pairs at once, and there is no colour that answers both,
because the two backdrops are 3.6:1 apart from each other by design.

A shape with two backdrops needs two means of being seen. §1.2 already allows
exactly that, and `ContrastTest.everyControlIsDistinguishable` has been written
on it since it was written:

> a control offers two means of it at once: a fill that differs from the surface,
> and an edge drawn around it. WCAG asks that *some* means clears the floor

## Decision

**The thumb keeps its white fill and gains a 1px edge, `--gb-slider-thumb-border`
— and the marks sweep credits the better of fill and edge, as the boundaries
sweep already does.**

```css
slider-thumb {
  width: var(--gb-slider-thumb-size);
  height: var(--gb-slider-thumb-size);
  border-radius: 8px;
  border: 1px solid var(--gb-slider-thumb-border);
  background: var(--gb-slider-thumb-bg);
}
```

```css
/* nord-light */  --gb-slider-thumb-border: var(--nord3);   /* 5.46:1 */
/* nord-dark  */  --gb-slider-thumb-border: transparent;
```

Four things about the shape.

**The fill is what carries it against the accent and the edge is what carries it
against the groove.** This is not a belt-and-braces argument; it is the only
division of labour that works, and it falls straight out of the two-backdrop
analysis above. White is the *best* available answer to the accent fill and the
worst available answer to the groove, so the thing to add is an answer to the
groove and the thing to keep is white.

**One token, not three.** The light theme has three thumb fills —
`--gb-slider-thumb-bg`, `-hover` and `-active` — and re-choosing the resting one
would have left the other two to be re-chosen after it. Worse: `-active` is
literally `var(--nord4)`, which **is** that theme's groove, so a pressed thumb has
measured **1.00:1** against the track it is being dragged along for the whole of
this control's life. An edge covers all three states without a ramp moving at
all.

That measurement is new, and it is the second thing this change buys. The marks
sweep looked only at resting fills, so the worst pair in the file was the one
nobody had asked about. All three states are swept now.

**`transparent` on the dark theme, and the token exists anyway.** nord6 on nord3
is 6.40:1 and needs no help. The token is declared in both files for
`--gb-toggle-thumb-bg-checked`'s reason — "two tokens because a theme may need
two, not because this one does" — and because a rule in `controls.css` cannot ask
one theme for a border and not the other. A transparent border composites to
nothing: `BoxPainter` fills the whole border box *before* it strokes, so the dark
theme's disc is the same 16px of nord6 it always was. The golden proves it, by
not moving.

**Drawn inside the border box.** `BoxPainter` insets the stroke path by half the
border width, which is what `border-box` sizing means. The thumb is still 16
wide, so `slider-ticks`' half-a-thumb inset still lands on the thumb's centre and
`SliderGeometryTest` still passes untouched. An edge drawn *outside* would have
been 18px of thumb and a scale pointing two pixels wrong.

## Consequences

- `MARKS_BELOW_FLOOR` is empty, and is asserted to be. §1.2's non-text sentence
  has no exceptions left. `ContrastTest.MARKS` gained three rows —
  `slider thumb`, `:hover` and `:active` — and an optional `edge`, measured as
  `max(fill, edge)` with `NaN` meaning "this shape has no edge to credit". A
  `transparent` edge is an **absent** edge, not a failing one: alpha is ignored
  by `Contrast.ratio`, so crediting it would score black and hand the thumb a 3:1
  it has not got.
- **Two goldens moved**, both light-theme, and both show one thing: white discs
  gaining a slate ring. `slider-light` (520 of 60000 pixels, 0.87%) and
  `controls-on-surface-light` (104 of 120600, 0.09%). The entry that asked for
  this expected twenty-two, which is what a *ramp slide* would have cost — the
  accent and the groove appear in every image with a range control in it. An edge
  touches only the thumb, and only where a thumb is drawn.
- The light theme's slider looks slightly more drawn and slightly less soft. That
  is the price and it is visible in `slider-light`: at 0% the thumb used to be a
  white disc on a pale groove that a reader had to look for, and is now a disc.
- Nothing in `docs/design-system.md` §3 says this yet. The sentence it needs is in
  "What to write instead" below; §3's `slider` row should gain the edge beside the
  two metrics it already pins.

## What to write instead

§3's `slider` row today is a size and a radius and nothing else:

```
| `slider` | track 4; thumb 16 (`full` radius); hit ≥32 cross-axis |
```

A thumb is not only a size. It is the one shape in the system drawn across two
backdrops at once, and the row has to say what carries it against each:

> A thumb is a disc with a **1px edge**, and it needs both: it is centred on the
> value, so it lies half over the accent fill and half over the bare groove, and
> no single colour clears §1.2's 3:1 against both — the light theme's white fill
> is 1.35:1 on its groove, and every fill dark enough to clear that is within a
> hair of the accent fill's own luminance. The fill answers the accent, the edge
> (`--gb-slider-thumb-border`) answers the groove, and a theme whose fill already
> clears its groove sets the edge to `transparent` rather than inventing a
> decorative one.
