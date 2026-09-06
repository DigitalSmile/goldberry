# 276. A plane is HSV, and the hex is the value

Date: 2026-09-06

## Status

Accepted. Builds `docs/core-widgets.md` §4's `color-picker`, which completes §4.

## Context

§4's entry is unusually specific about the *model*:

> Value is the toolkit's `CssColor`, so a picked colour is directly usable in a
> stylesheet; **the model carries OKLCH internally** because that is what §1.7
> interpolates in and what the ramp utility uses, and round-trips to hex without
> drift.

and, one clause earlier, equally specific about the *shape*:

> a swatch button opening a `popover` with a **saturation/value plane**, a hue
> slider, an optional alpha slider, a hex `text-input`, and an
> application-supplied palette of preset swatches.

Those two sentences want different things, and building the control is what makes
that visible.

## The model is HSV, which is a departure

A saturation/value plane **is** HSV. The axes are S and V; that is what the words
mean and what the picture is.

OKLCH's axes are lightness, chroma and hue, and its chroma has a gamut boundary
that varies with both of the others — the most saturated blue an sRGB screen can
show is a long way from the most saturated yellow. A rectangular plane over OKLCH
therefore has **large unreachable regions**: corners that clamp to something else,
a cursor that cannot be put where it was clicked, and a colour that changes when
the hue slider moves under a stationary cursor. A picker whose plane lies about
where its colours are is worse than one that interpolates in the wrong space —
and this control **interpolates nothing**. Dragging is not a transition.

The sentence's stated reason survives intact somewhere else, which is the test
that it was a good reason for the wrong thing: "that is what §1.7 interpolates
in" is about **transitions**, and every colour transition in the toolkit still
goes through `Oklch`. A colour picked here is an ordinary `0xAARRGGBB` and fades
like any other.

"Round-trips to hex without drift" is kept and asserted: 4,096 colours across the
cube, plus every 24-bit corner and six alphas, all exact through sRGB → HSV →
sRGB.

## Two pieces of state, and the second one is the point

§4 makes the hex field the source of truth — "for the same reason the date field
is" — and it is: `ColorPickerState` holds **text**, and the plane, the ramps and
the presets write hex into it exactly as a user would, so a value takes one path
and is parsed in one place.

But the plane's cursor and the hue ramp **cannot be derived from a colour**,
because the conversion is lossy in exactly the place a user drags to: every colour
with `s == 0` is a grey and has no hue, and black has neither hue nor saturation.
A picker that re-derived HSV each frame would swing the hue slider to red the
moment somebody dragged to the left edge, and lose the hue altogether at the
bottom.

So `dragging` is the editing state — `TextEdit`'s arrangement one level up — and
`HsvColor.withArgb` is the rule that keeps the two honest: a colour with no hue
of its own keeps the one being dragged. That is CSS Color 4's powerless-hue rule,
which `Oklch` already applies for the same reason.

## The closed control is a swatch, not a field

Which is the one place this picker's chrome departs from the other two. §4 says
"a swatch **button**", so it is one: focusable, `Role.BUTTON`, `Space` or `Enter`
opens the popover, and its accessible name is the hex — which is the one half of
"the hex as its value text" there is anywhere to put.

The presets are **not** focusable, and that is a decision rather than an
omission: a palette of twelve colours would be twelve Tab stops inside a popover,
and §4 gives them no roving mechanism to be one stop with. The keyboard's route to
any colour is the hex field, which is the source of truth anyway.

## Painted, not styled

Everything this control draws is a picture of what a value means, and §8's subset
cannot express any of it: the plane is a hue with two gradients over it, the hue
ramp is six stops round the wheel, the alpha ramp is a chequerboard under a fade.
`BlendGradient` is a fill style the painter has and not something a stylesheet can
ask for (ADR-0207), so these are `canvas` boxes with drags on them.

- **Three fills and no per-pixel loop.** A 200×160 plane is 32,000 pixels, and
  computing each of them in Java once a frame is a colour picker that makes the
  frame budget its problem.
- **White then black, and not the other way.** Saturation is a wash towards white
  and value a wash towards black; black over a half-washed white is the colour at
  that corner, where white over black is grey. This is the kind of wrong that a
  picture catches and no assertion does, which is why there are five goldens.
- **The plane's cursor picks black or white** from the colour under it, because it
  is the one mark on this control that has to be visible over everything it can
  sit on. A ramp's thumb crosses colours it cannot choose between, so it is white
  with a black edge instead.
- **The alpha ramp is drawn over a chequerboard**, or its transparent end is the
  popover's own surface and the slider says nothing about what transparent looks
  like.

## `alpha=#false` refuses in both directions

§4: "hides the alpha slider **and refuses translucent values**". The second half
matters as much as the first: a picker with no way to change alpha must not report
one, or a `bind=` carrying `#88c0d080` leaves the control showing a colour it
cannot express and a form holding one nobody chose. `ColorPicker.gate` is the one
place that is asked — by the field that parsed a colour, by the ramps before they
move it, and by a preset before it is offered.

## Consequences

- **§4 is complete.** `text-input`, `text-area`, `field`, `form`, the validation
  model, autocomplete, `code-input` and all three pickers.
- **`SemanticsSweepTest` asked what a plane is**, and the honest answer is
  `Role.SLIDER` — a control whose value you move continuously — with a note that
  it has **two** axes and neither this enum nor ARIA has a word for that.
  `GROUP` says "a boundary with content in it" and this has none; `GRID` promises
  cells addressed by row and column, which is the one thing a continuous plane is
  not.
- **A fourth widget joins the M5 semantics entry.** A plane cannot say which
  colour its cursor is on, for the same reason a `code-input` cannot say what it
  holds.
- **`#88c0` is a colour**, which a test found by asserting it was rubbish: it is
  CSS's four-digit `#rgba` form. The picker takes whatever `CssColor.parse` takes,
  because one that second-guessed the engine would refuse text a stylesheet
  accepts.
- **The affordance's placement is scoped now.** `picker-toggle` was absolutely
  positioned over its field's right padding, which is right for the two pickers
  that have a field and lands on top of a 24-point swatch for the one that does
  not. Found in the first picture of the closed control.

## Alternatives considered

- **An OKLCH plane.** Priced above: unreachable corners and a cursor that cannot
  be put where it was clicked.
- **Deriving HSV from the hex each frame.** The hue vanishes at the left edge and
  at the bottom, which is where people drag.
- **A per-pixel plane.** 32,000 pixels a frame in Java.
- **Focusable presets.** Twelve Tab stops in a popover, with no roving mechanism
  specified to make them one.
- **`Role.GRID` for the plane.** It promises cells addressed by row and column.
