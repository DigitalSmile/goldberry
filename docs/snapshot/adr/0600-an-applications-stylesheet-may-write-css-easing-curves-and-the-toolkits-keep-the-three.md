# ADR-0600: An application's stylesheet may write CSS's easing curves, and the toolkit's keep the three

- **Status:** Accepted. Amends [ADR-0527](0527-css-easing-keywords-run-on-the-system-curves.md)
  for application and theme sheets; the toolkit's own sheets keep its rule.
- **Date:** 2026-10-11
- **Relates to:** [ADR-0529](0529-an-applications-stylesheet-is-lenient-and-loud.md),
  [ADR-0353](0353-a-stylesheet-may-name-keyframes.md), `docs/css-plan.md`
  (CSS-03, CSS-15), `docs/css-support-2026-10-10.md` finding 4

## Context

`Easing` was an enum of the design system's three curves, and its
documentation gave the reason: "a design system where every screen can invent
its own curve has no motion language, only motion". ADR-0527 kept that rule
and softened its edges. CSS's four keywords run on the nearest system curve,
and `cubic-bezier(…)`, `steps(…)`, `linear(…)`, `step-start` and `step-end`
drop themselves with a warning, leaving the entry on `ease-enter`.

The CSS support analysis of 2026-10-10 measured what that costs a sheet
written for the web. `ease-in-out` runs as a decelerating curve, so an
alternating pulse eases one way and snaps the other, and a curve copied from a
design tool runs as `ease-enter`. Nothing fails loudly. The motion is only
different from what the author wrote. Finding 4 also noted that the three
curves were already stored as cubic Bézier control points, so a general curve
needs no new maths.

The rule guards the toolkit's motion language, the curves its own controls
move on. An application's sheet is a different case: it is the application's
motion, written by its author, and is already read leniently on the same
reasoning (ADR-0529).

## Decision

**The sheets an application writes may use CSS's own curves. The toolkit's
own sheets keep the three.**

- `Easing` is a sealed interface over three kinds of curve, one record each in
  `dev.goldberry.motion.easing`: `CubicBezier`, `Steps` and `PiecewiseLinear`.
  The design system's curves are constants on the interface (`EASE_ENTER`,
  `EASE_EXIT`, `LINEAR`) and `Easing.BUILT_IN` lists them. `LINEAR` is the
  identity `PiecewiseLinear`, so it returns `t` exactly, with no solver in the
  way. Equality is the curve: `cubic-bezier(0.2, 0, 0, 1)` equals `EASE_ENTER`.
- A sheet read leniently, the application and theme layers, may write
  `cubic-bezier()`, `steps()`, `step-start`, `step-end` and `linear()`. In
  those sheets `ease`, `ease-in`, `ease-out` and `ease-in-out` run as CSS's
  own curves, not the nearest system curve.
- A sheet read strictly, the toolkit's own, may name only `ease-enter`,
  `ease-exit` and `linear`. The strict parse refuses anything else as outside
  its subset, so a control cannot drift from the motion language by accident.
- A curve an application writes may overshoot, as CSS allows. The three
  built-in curves never do.

## Consequences

- `Easing` is no longer an enum. Code that named the three constants compiles
  unchanged. `Easing.values()` is gone (`BUILT_IN` replaces it), and so is a
  `switch` over the constants. A `switch` over `Easing` now has one case per
  kind of curve.
- An application sheet that writes `ease-in-out` moves differently once the
  parser change lands. That is the point, and it is the curve the author
  wrote. The info line that named each mapping goes with it.
- Each value an `Animatable` interpolates has to accept progress outside
  `0..1` once an application's curve can overshoot. That work belongs with the
  parser change.
- The guide's *Transition and animation* section says the opposite until the
  release. Its replacement is parked with the parser change.

## Status

Done 2026-10-11, in two parts.

- The type (CSS-03): `Easing` sealed, the three records, and
  `EasingCurvesTest` against CSS's definitions.
- The parser (CSS-15): `CssEasing` reads every keyword and function in a
  lenient sheet, `Easing.parse` gives CSS's keywords their own curves, and the
  strict parse refuses anything but the three names in `transition`,
  `animation` and their timing longhands. An animated `opacity` is clamped to
  `0..1`, the one animatable value an overshoot could put out of range;
  colours clamp their channels and shadows their blur already.

Two choices were made with the parser. `transition: all` names every
animatable property, and the four `transition-*` longhands are read, kept as
lists beside the timings (`Transitions.Lists`) because they pair up by
position and arrive from different rules. And **`transition-property` starts
out empty, where CSS's is `all`.** A duration written alone animates nothing
until a property is named, so a control still declares what it animates,
and no node is put through the transition machinery by a rule that only
meant to change a speed.
