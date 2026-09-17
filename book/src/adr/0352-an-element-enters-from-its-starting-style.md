# 352. An element enters from its starting style

Date: 2026-09-17

## Status

Accepted. Extends [ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md).
Builds `design-system.md` §3.1's `button[float]` entrance, which
[ADR-0347](0347-an-icon-only-button-is-a-circle-and-float-is-a-place.md) left
unbuilt.

## Context

ADR-0067 decided that an element's first frame starts no transition. There is no
previous style to move from, and a window that faded every control in from black
as it opened would be wrong. That rule is still right for nearly everything.

It also left no way to say "this particular element arrives". §3.1 specifies
`button[float]` as "in: `opacity` + `scale` 0.9→1, base", and ADR-0347 could not
build it because the overlay layer mounts a widget already at rest. The same gap
kept `check-mark` and `radio-dot` mounted and invisible in every unchecked
control. A mark that came into existence checked would have snapped instead of
growing in.

CSS has an answer to this exact question, and it is not a new lifecycle API:
`@starting-style`. Rules inside it describe the style an element has *before* its
first style, so the transitions the element already declares run from there.

## Decision

**`@starting-style { rules }` is in the subset. On an element's first styled
frame, if a starting rule matches it, the renderer observes the starting style
first and the element's real style second, so its declared transitions run from
the one to the other.**

- **Parser.** The block form at the top level of a sheet, with no prelude. Its
  rules are ordinary `StyleRule`s with `starting = true`, numbered in source
  order among the other rules. A nested at-rule or a prelude is refused.
- **Cascade.** `StyleResolver` puts starting rules in buckets of their own. They
  are never part of `resolve`, so a starting rule can never leak into the style an
  element has. `resolveStarting(element)` returns null unless a starting rule
  matches. Otherwise it runs the ordinary cascade with the matching starting rules
  added in their source positions. That is CSS's "before-change style": a starting
  rule that says only `opacity: 0` leaves every other property where the element's
  rules put it. `var()` is substituted against the element's own custom
  properties, and not against any a starting rule declares, which would otherwise
  be cached as the element's for every later frame.
- **Renderer.** `Element.firstStyled()` answers true exactly once per element.
  On that frame, if the element's style declares transitions and a starting style
  resolves, `Animations.observe(starting)` runs before `observe(style)`. The widget's
  `restyle` is applied to the starting style too, so an inline value such as a
  segmented indicator's position does not appear to move.
- **No transition, no entrance**, as in CSS. A starting style only chooses where
  transitions start from.
- **Reduced motion** reduces the starting style's transitions as it reduces every
  other style's, so the element arrives at once.
- **Once.** A rebuild keeps the element, so the element does not enter again. A
  keyed element that moves keeps its element. An element that is unmounted and
  mounted again is a new element, and it enters again.

## Consequences

- `button.float` enters from `opacity: 0; transform: scale(0.9)` over
  `--gb-motion-base`. Its press still snaps because of a `button.float:active` rule,
  since the float rule would otherwise out-rank `button:active` by source order.
  `FloatEntranceTest` pins the frames.
- A sheet with no starting rules pays one boolean per element per frame.
  `hasStartingStyles()` keeps the second cascade off the first frame entirely.
- The way *out* is still not built. An overlay that is removed has no frame left
  to transition in, and that is §1.7's `closing` phase rather than a stylesheet's.
- `check-mark` and `radio-dot` could now be built only while checked and enter
  from a starting style. They are left as they are, because nothing about them is
  wrong and a golden would move for no visible gain.
