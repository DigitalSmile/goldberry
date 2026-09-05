# 267. A text scale scales the text, and not the layout

Date: 2026-09-05

## Status

Accepted. Implements §1.4's global text-scale token, which
`docs/ARCHITECTURE.md` §17 has recorded as "neither implemented nor
gallery-enforced" since the accessibility baseline was written. The enforcement
half is now buildable and is not built here.

## Context

Two documents ask for this and one entry complains about the consequence.

> Global **text-scale token 90–150%**; every component must survive 150% without
> clipping (gallery-enforced). — `design-system.md` §1.4

> text scale to 150% without clipping — §13's accessibility baseline

> **The gallery goldens cannot see typography at all.** … what is still missing is
> any image that would show a *layout* wrong because of a font size — text that
> clips at 150% scale is §1.4's explicit gallery-enforced requirement and nothing
> enforces it. — `TODO.md`

The entry reads as a gap in the **tests**. It is not: nothing enforces the 150%
case because nothing *implements* it. There was no way to ask for 150% text, so
there was nothing for an image to be of.

## Decision

**`renderer.textScale(double)`**, and the factor is applied where a
`ComputedStyle` becomes a `Font` — nowhere in the cascade.

### A switch on the renderer

Which is where §13's other accessibility switches are. `reducedMotion` is the
same shape for the same reason: it is a *user* preference applied to a whole
window, and there is no selector that could express one. The `TODO.md` entries
about reduced motion, density and the scrollbar gutter all name the same missing
"settings mechanism"; this is a fourth switch on the same object rather than an
invention.

### It scales the text and not the layout, which is the whole point

The factor multiplies the `Typography` at the one seam where the renderer turns
a style into a font. So a paragraph is shaped larger and a **measured leaf grows
around it**, while a `height: 32px` stays 32.

That is exactly the condition §1.4 asks components to survive: *"every component
must survive 150% without clipping"*. A control whose box grew with its text
could not fail that test, and the requirement would be vacuous.

**Scaling inside the cascade was the other design and is wrong twice over.**
`font-size: 1.2em` resolves against a parent that would already have been scaled,
so an `em` chain takes the factor once per level and a nested label ends up at
1.5² or worse. And a `padding: 0.5em` would grow with it — so the boxes get
bigger too, and the clipping this exists to reveal is hidden by the mechanism
meant to reveal it.

### The line height scales and a line-height *ratio* does not

`Typography.lineHeight` is a length or, when negative, a **ratio**. The length
scales, because a line box that did not grow with its text is a paragraph whose
lines overlap. The ratio does not: a multiple of the size already scales by the
size scaling, and multiplying it too would square the factor. A test asserts the
resolved line height grows exactly once.

### The range is a clamp, not a refusal

§1.4 says 90–150%. `textScale` clamps rather than throwing, because a text scale
is a **user setting**: a window that failed to open because someone's accessibility
preference was 200% is worse than a window whose text is as large as the design
system allows. A non-finite factor is still refused, since there is no reading of
`NaN` that draws anything.

### One is the default, and no golden moved

`Typography.scaled(1)` returns `this`, so the default path allocates nothing and
draws exactly what it drew. That is what made it safe to add the mechanism before
anything enforces the 150% case — the whole corpus is the evidence that the
default is inert.

## What is deliberately not built

**The gallery enforcement.** §1.4 says "gallery-enforced", and an image at 150%
is now takeable. It is not taken here because *what to assert* is a real
question: with `text-overflow: ellipsis` shipping
([ADR-0255](0255-a-label-that-does-not-fit-is-cut-not-wrapped.md)) some cutting
is now correct, so "no text is clipped" is no longer the assertion — and a golden
image of eleven screens at 150% would pin every one of those decisions at once,
in a picture, before anybody had decided them.

The mechanism is the part that was missing. The entry can now say what it is
waiting for.

**A `--gb-text-scale` custom property.** §1.4 calls it a token, and the tokens are
read by the cascade — which is the half that must *not* see this factor, for the
`em`-compounding reason above. An application sets it on the renderer, exactly as
it sets reduced motion.

## Alternatives considered

- **Scaling `CssLength.Context.fontSize`.** The obvious place, and it only reaches
  `em` and `rem`. Every font size in the design system is `px`, so it would scale
  nothing that matters.
- **Scaling in `ComputedStyle.of` after resolution.** Compounds through `em`
  chains, as above, and cannot tell an inherited size from a declared one without
  threading a flag.
- **A zoom — scaling the display scale instead.** That is a different feature and
  the toolkit already has it (`DisplayScale`). Zoom scales everything including
  the boxes, so it can never clip; §1.4 asks for the one that can.
- **Waiting until the gallery enforcement is designed.** The enforcement needs
  this to exist. Doing them together would mean deciding what a clipped label
  means in the same change that makes 150% expressible.

## Consequences

- **§13's accessibility baseline gains an item.** `ARCHITECTURE.md` §17's "text
  scale to 150% is neither implemented nor gallery-enforced" is half true now,
  and the half that is left is the half §1.4 spells "gallery-enforced".
- **Seven tests**, in two classes: the unit half on `Typography.scaled` — including
  the ratio that must not be scaled twice — and the end-to-end half that says the
  factor reaches a laid-out frame at all. A scale applied to a `Typography`
  nothing shapes with would pass every unit assertion and draw the same picture.
- **The end-to-end test measures a text box in a `row`**, and says why: a flex
  child is stretched on its cross axis, so a text box in a `column` is as wide as
  the window and in a `row` is as tall as the row. The first version asserted a
  width that was 400 at both scales.
- **A `button`'s height is asserted *not* to change**, which is the condition being
  created rather than a side effect. It is also the assertion that would have
  caught the cascade-scaling design, since that one grows the padding.
