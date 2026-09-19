# 431. A translucent fill is measured on the frame

Date: 2026-09-19

## Status

Accepted. Fills the four holes
[ADR-0087](0087-a-semantic-fill-brings-its-own-foreground.md) opened and
[ADR-0239](0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md) and
[ADR-0241](0241-a-theme-can-be-audited-by-whoever-wrote-it.md) widened without
being able to close.

## Context

`ContrastTest` resolves a `background` and a `color` through the real cascade and
divides. That is the right check and it has found real failures — seven button
pairs, five semantic hues, twelve control boundaries — and it has, in four
places, written down a pair it refused to measure:

- `button.ghost`, whose fill is `transparent` and whose hover is a wash;
- `--gb-selection`, which is `#5e81ac66` and `#88c0d04d`;
- a segment's hover and press, which are the same overlay tokens;
- `button.link`, measured as ink alone because the variant itself cannot be swept.

Every one of those exclusions is correct. `Contrast.ratio` ignores alpha, so a
`transparent` fill measures as **black** — `button.ghost` would have scored a
comfortable pass on every surface in both themes while guaranteeing nothing at
all. A check that pretends to a guarantee it cannot make is worse than the
absence of one, and the file says so.

But the exclusions are a hole, and the entry named its shape exactly:

> A backdrop-aware check would need the painted frame rather than the cascade,
> which is a different kind of test.

It is a different kind of test, and the toolkit already has every piece of it.
`TestFrames` hands out a real frame, `BoxPainter` paints a real box tree into it,
and `TestFrames.Target.pixel` reads a pixel back out. Two suites already read
pixels — `ChartFrame` and `TextAreaGutterStripTest`. Nothing was missing except
somebody pointing them at §1.2.

### The other option, and why not

The alternative is to composite in Java: `over(argb, backdrop)`, nine lines, no
renderer, no native library, runs everywhere.
`PlaceholderContrastTest` already has exactly that helper, privately.

It is a **second opinion about blending**, and the first opinion is the one that
ships. A test that agrees with its own arithmetic and disagrees with Blend2D
about premultiplication, gamma or rounding is a test that reports a number no
user will ever see. Rendering costs a native library and buys the actual answer.

## Decision

**`BackdropContrastTest` renders a real widget tree over each surface the toolkit
paints, reads the painted pixels, and measures §1.2 against those.**

Three assertions, and the third is the one that keeps the other two honest.

**The label is measured against the pixel behind it.** Five surfaces × four
probes × two themes = forty pairs, every one of which is a pair no check in the
repo could previously express. The backdrop comes off the frame; the **ink comes
off the cascade**, and deliberately. Ink is opaque everywhere in the toolkit —
`--gb-text` and the ranks around it are palette entries, never washes — so there
is nothing about it a paint would decide that a resolution would not, and reading
it out of the frame would mean sampling the inside of a 13px glyph, where every
pixel is partly the backdrop.

**A translucent state has to change the pixel.** Not 3:1 — §2.1 asks hover for
"one surface step" and designs it to be subtle, and §1.2's 3:1 is about telling a
*component* from its background rather than a hover from a rest. Asserting 3:1
here would invent a rule the design system does not hold itself to and fail every
state in the catalog on the strength of it. What is asserted is the claim the
system does make: that the surface *steps*. The exact ratios are printed, so the
size of each step is on the record — the smallest is 1.151:1, a light-theme
selection on `--gb-surface-2`.

That assertion has a concrete failure in view. `--gb-overlay-hover` is a white
wash on the dark theme and a **black** one on the light theme, and the light
theme's `--gb-surface` and `--gb-surface-raised` are both literally `#ffffff`.
Had the light theme taken the dark one's wash — which is what a single shared
"overlay" token would have forced — hovering a ghost button on a panel, a card, a
dialog or a toast would have changed not one pixel, and nothing in the cascade
could have reported it, because the declaration would have been present and
correct on both.

**The surface list is the themes', and is checked against them.** "A ghost button
on any surface" is what `controls.css` claims, and a check over *any* surface is
a check nobody can satisfy: an application may paint a photograph behind a
toolbar. What the toolkit can be held to is the surfaces it paints itself, and
there are five — `--gb-bg`, `--gb-surface`, `--gb-surface-2`,
`--gb-surface-raised`, `--gb-surface-sunken`. That list is pinned, and
`theSurfacesAreTheOnesTheThemesDeclare` reads both theme files for their
`--gb-surface*` declarations and asserts the set, so a sixth surface fails a test
until the sweep covers it. This is `noBareHueDrawsInk`'s technique, in the same
file's register and for its reason: a claim about what a stylesheet contains has
to be checked against the stylesheet.

Each backdrop is a **stack** rather than a token, which is the second thing the
cascade could not do. `--gb-surface-sunken` is `rgba(0, 0, 0, 0.22)` and
`rgba(0, 0, 0, 0.07)`: a text field's fill is *itself* a wash over whatever holds
the field. Resolving it gives a colour with an alpha channel and no answer;
painting it gives `#2e3440` and `#ededed`, numbers written down nowhere.

## Consequences

- **Nothing failed.** Forty pairs, all above 4.5:1. The four exclusions
  `ContrastTest` carries were correct to make *and* correct to leave: the colours
  behind them were fine, and there was no way to say so. That is the result, and
  it is worth having — an unmeasured pass and a measured one are different
  states, and only one of them survives the next theme edit.
- Two margins are now on the record. `button.ghost:active` on the dark theme's
  `--gb-surface-2` and `--gb-surface-raised` is **4.79:1**, the tightest pair
  here and 0.29 above the floor; both surfaces are `--nord2`, so this is a
  pressed ghost button in a card, a dialog or a toast. `--gb-selection` on the
  same two is **5.41:1** — `controls.css` claims of it that "the label under it
  does not need a foreground of its own the way a segment's does on its opaque
  pill", which had never been measured and is now true by 0.91.
- **The first draft found three failures and they were its own.** `INSET` sampled
  three pixels into an unpadded row and took a bite out of the `A`, which reads
  as ink-over-fill and moved `--gb-selection` on `--gb-surface` from 5.92:1 to
  4.14:1 — a plausible number, in the right region, pointing at a real token. A
  pixel test that samples the wrong pixel is more confident and more wrong than
  the cascade test it replaces. The guard is two lines: the sampled pixel must
  equal its right-hand neighbour, because ink is never flat and two neighbours
  that agree are two pixels of surface.
- It costs a render per pair — 70 renders across the three tests, about eight
  seconds — and it **skips** rather than fails where libgoldberry is not loadable,
  through `RendererRequirement.enforce()`. `ContrastTest` needs no renderer and
  still covers every opaque pair, so the cheap check stays the broad one and this
  is the narrow one.
- Two frames per measurement, not one: the rectangles come from a layout pass and
  the colours from a paint. Running both into the same frame would composite a
  translucent wash over itself, which is precisely the quantity being measured,
  doubled.
- `button.link` is still not swept as a variant. Its fill is `transparent` and it
  could now be measured the way `button.ghost` is; it is left alone because its
  ink is already swept on all three surfaces and the variant adds no second pair.
  A later entry can take it.
