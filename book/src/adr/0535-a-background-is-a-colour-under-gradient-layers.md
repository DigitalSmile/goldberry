# ADR-0535: A background is a colour under gradient layers

- **Status:** Accepted.
- **Date:** 2026-10-02
- **Relates to:** [ADR-0310](0310-a-shadow-is-a-stack-of-rectangles.md),
  [ADR-0505](0505-a-border-has-four-sides-and-takes-no-room.md),
  [ADR-0529](0529-an-applications-stylesheet-is-lenient-and-loud.md),
  `docs/goldberry-gaps.md` #12

## Context

Deploy Orc's prototype draws radial glows behind its panels and a marching
dashed segment on a running step, which is a `repeating-linear-gradient`
with an animated `background-position`. Goldberry's `background` was a
colour or `none`: `ComputedStyle` and `Box` each held an `int`, and the
shorthand dropped anything else. The app left the glows out and built its
failed card's tint out of two nested boxes.

The rasterizer was never the obstacle. The Blend2D binding already builds
linear, radial and conic gradients with pad, repeat and reflect, for the
COLRv1 emoji painter. What was missing was a value for the cascade to carry
and a painter that used it. Around 110 call sites read `background()` as an
`int`, and around 770 build boxes with one.

## Decision

**A background is a sealed `Background`: a colour, and gradient layers
painted over it, top first. The `int` stays the way everything reads the
colour.**

- `css.background.Background` has two shapes. `Colour` is every box the
  toolkit draws. `Layers` is a colour, a list of `GradientLayer`s and a
  `BackgroundPosition`. `ComputedStyle` and `Box` carry it as the `fill`
  component. `background()` still returns the colour as an `int`,
  `background(int)` still sets it (keeping any layers), and `Box` keeps its
  `int` constructor. No caller moved.
- `GradientLayer` is the gradient as written: `Linear` with an angle or a
  corner, and `Radial` with a shape, an extent or explicit radii, and a
  centre, each with colour stops and a `repeating` flag. It is in
  proportions of a box, so the painter asks it to `resolve` once the box has
  a rectangle. Resolving places the stops by CSS's rules, builds CSS's
  gradient line (or the ellipse for an extent), and gives a `paint.Gradient`
  whose ramp runs from the first stop to the last.
- `paint.Gradient` gains `Radial`, an ellipse with a `start` fraction, and an
  `Extend` of `PAD`, `REPEAT` or `REFLECT`. A `repeating-` gradient is
  `REPEAT`. `Frame` draws an ellipse as Blend2D's circle squeezed about its
  centre, and a radial ramp that starts out from the centre as the two-circle
  form with a focal radius. A repeating radial ramp with stops inside the
  centre is moved out by whole periods. A plain one is cut at the centre at
  the colour it had there.
- `BackgroundParser` reads the `background` shorthand (comma-separated
  layers, the colour only in the last), `background-image` and
  `background-position`. `background-color` keeps the layers. The shorthand
  resets the colour and the position, so `background: none` is still no fill.
- `BoxPainter` fills the colour as before. It then fills each layer, last
  first, over the box's rounded outline, with the ramp in frame coordinates.
- `background-position` joins `Transitions.Animatable`. That makes the
  marching stripe a keyframe animation of one length.

## Consequences

- The prototype's glows, tints and running stripe can be written as CSS.
- A box with no layers costs one `layers().isEmpty()` per paint. The colour
  path is unchanged, so no golden moved.
- `opacity` fades every stop with the rest of the box.

## What is not done

- No `url()`, `background-size`, `background-repeat`, `background-clip` or
  `background-origin`. A layer covers the whole border box once and is never
  tiled. CSS tiles a shifted layer, and this pads or repeats the ramp
  instead. For a repeating gradient that is the same picture without the
  tile's seam.
- A percentage or a keyword in `background-position` resolves to zero. With
  a layer the size of its box that is CSS's own answer, and the parser
  carries only lengths.
- No `conic-gradient`, transition hints or CSS Images 4 interpolation
  methods. Each drops the declaration with the usual warning.
- Gradients do not interpolate. `background-image` is not animatable in CSS
  either.
- A layer is filled over the border box, under the border. CSS's default
  `background-clip: border-box` puts it there too.
