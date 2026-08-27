# 207. A fill may be a ramp

Date: 2026-08-27

## Status

Accepted. The last row of `charts.md` §3.1, and the first widening of the export
list that is not for a widget.

## Context

ADR-0206 finished §3.1 "except for the gradient fill", and the exception was not
a chart problem. Every drawing call on the export list takes its colour as an
`rgba32` argument — `bl_context_fill_rect_d_rgba32`, `bl_context_fill_path_d_rgba32`,
`bl_context_fill_glyph_run_d_rgba32` — because that is what the toolkit's own
painter has ever needed. A gradient is not a colour: it is an object with a
geometry and a list of stops, it has to exist while the fill happens, and it
reaches the context as *state* rather than as an argument. So there was nothing
on the list a chart could call, and the entry sat in `TODO.md` with the reason
recorded.

The way out that does not work was recorded there too. Faking a fade with a stack
of translucent strips is forty rectangles per band per frame, it bands visibly on
any gradient shallower than one strip, and it puts a `Layer`'s worth of overdraw
on a chart that is otherwise two paths.

And it is **shared work**, which is what makes the width worth taking:
`goldberry-html` needs the same symbols for CSS gradients and `goldberry-vector`
for SVG's (ADR-0190). Both would otherwise open by adding these five lines.

## Decision

**Six symbols, and the sixth is the interesting one.** `bl_gradient_init_as`,
`bl_gradient_destroy` and `bl_gradient_add_stop_rgba32` build one;
`bl_context_set_fill_style` puts it on the context and
`bl_context_set_fill_style_rgba32` takes it off again. The sixth is
`bl_context_fill_path_d` — the plain fill, with no `_rgba32` suffix, which draws
with whatever style is set. It is the only styleless drawing call bound and the
only way a gradient reaches a path.

**A `BlendGradient` is a resource, like a `BlendPath`.** Confined to its thread,
closed by its owner, and closable the moment the fill has been issued — Blend2D
retains its own reference when the style is set. Only the *linear* form is
constructed: `bl_gradient_init_as` takes its geometry as a `const void*` whose
shape the type implies, and a method that took any `BLGradientType` would let a
radial gradient read six doubles out of a four-double allocation and report
`BL_SUCCESS`. A second shape is a second method with its own layout row beside
it.

**The fill style goes back to opaque black after every gradient fill.** Every
other call on `BlendContext` states its own colour, which is what keeps a frame
free of style state nobody set back; a gradient left set would be drawn by
whatever reached for the styleless fill next, somewhere else in the frame
entirely. This is `globalAlpha`'s rule and the opposite of its mechanism: an
alpha has a neutral value to go back to and a fill style does not, so restoring
one means choosing one.

**A gradient is not moved by the origin its path is filled at.** The path
translates and the ramp does not, because the two answer different questions — a
path is a shape drawn somewhere and a gradient is a statement about a region of
the surface. One placed from the top of a plot to its baseline is the same ramp
for every band drawn through it.

**A fade repeats its own colour at zero alpha.** `0x00000000` is transparent
*black*, and a ramp from a green to it goes through grey on the way out. That is
the classic wrong gradient and it is not a thing each caller should have to
remember, so `BlendGradient.fade` is the two-stop constructor and the far stop is
`argb & 0x00FFFFFF`.

**On the chart side there is a `Fill` with three values and not an opacity
number.** `NONE` is the default, `SOLID` is a flat wash and `GRADIENT` is a fade.
An opacity would be a number a caller could want any value of; a fill is a choice
between two conventions, and a caller who wants a third writes a `canvas`, which
is the escape hatch `charts.md` §4 names for exactly this.

**`NONE` is the default so that nothing changed.** A line chart draws no fill,
which is what a line chart already was; an area chart reads `NONE` as `SOLID`,
because a band with no fill is not a band. Every existing golden is untouched and
`GRADIENT` is a thing an application asks for.

**A ramp is anchored to the data, not to the plot.** Under a line it runs from
the furthest point of that run from the baseline back to the baseline; in a band
it runs across that band's own extent. Anchored to the plot instead, two series
of different magnitudes would be drawn at different strengths, and a stack's
lower bands would be half gone before they started.

## Alternatives considered

- **A `Gradient` value type in `:core`'s `paint` package**, converted to a
  Blend2D object per fill. It would keep `Frame`'s surface free of a native
  resource — but `Frame` already takes `BlendPath`, `BlendFont` and
  `BlendGlyphBuffer`, so the boundary being protected is not there, and the
  conversion would allocate the same native object at the same rate with a Java
  object in front of it.
- **Caching one gradient per series across frames.** A gradient's geometry
  depends on the plot's height and on the data's extent, both of which change; a
  cache keyed on those is a cache that misses whenever anything moves, plus a
  lifetime to own.
- **Interpolating the ramp in OKLCH**, which is the word the deferred entry used.
  It turns out to be vacuous for the thing being built: a fade between two alphas
  of *one* hue is the same curve in every perceptual space. What makes it correct
  is premultiplied interpolation, which Blend2D does, and repeating the colour at
  the far stop, which the caller must. OKLCH would start to matter for a ramp
  between two different hues, and nothing draws one.
- **An opacity number rather than three values** — `fill(0.3)`. It makes the
  gradient inexpressible without a second argument, and it invites the two
  degenerate values (`0` and `1`) that `NONE` and `SOLID` say better.
- **`SOLID` as the default for a line chart**, which is what most dashboard
  libraries do. It would put an area under every line chart in every application
  that upgraded, which is a change to a picture nobody asked to change.
- **Binding `bl_context_fill_rect_d` as well**, so a rectangle could be filled
  with a ramp. Nothing needs it: a chart's ramps are on paths, and the export
  list holds what a painter needs rather than what one might.

## Consequences

- **The export list is six symbols wider and has its first style object.** The
  layout registry gains two rows — `BLGradientCore`, which is `BLObjectDetail`
  again, and `BLLinearGradientValues`, which earns its row the way `BLMatrix2D`
  does: it crosses as a `const void*` and nothing on either side checks its
  shape. Six enumerators joined the constant probe, three gradient types and
  three extend modes, although only one of each is used.
- **`goldberry-html` and `goldberry-vector` start one commit further along.**
  Both entries in `TODO.md` named these symbols as their own first step.
- **A gradient is constructed inside the paint pass**, which is the first Blend2D
  object that is. One per band per run per frame — three native calls beside a
  path that is already hundreds. Measured against the alternative rather than
  against zero: the strips it replaces were forty fills.
- **`charts.md` §3.1 is complete.** Every row is built.
- **A degenerate ramp is filled flat.** A perfectly flat series has a zero-length
  gradient, which Blend2D resolves as the last stop — that is, invisibly. Under a
  pixel of span it fills with the near colour instead, so a flat line keeps a
  fill rather than losing one.
- **The half-pixel is now written down.** A gradient is sampled at each pixel's
  centre, so the pixel on the start point is already half a pixel along the ramp.
  The native tests assert *near* the stop colour rather than equal to it, because
  the exact form would be an assertion about Blend2D's sampling grid.
