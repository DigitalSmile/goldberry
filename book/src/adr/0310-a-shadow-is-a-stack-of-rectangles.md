# 310. A shadow is a stack of rectangles

Date: 2026-09-14

## Status

Accepted. Reverses the "no `box-shadow`" half of
[ADR-0164](0164-elevation-is-an-edge-and-a-closed-section-is-absent.md) and
[ADR-0166](0166-a-raised-thing-is-told-apart-by-its-edge.md), both of which named
adding it as an alternative and turned it down. The edge those records built stays
— `--gb-border-strong` is still what tells a card apart — and it is now an edge
*and* a shadow rather than an edge standing in for one.

## Context

`box-shadow` has been in §8's property list since the beginning and has been
turned down three times. The reasons given were, in order:

1. **`Box` has no field for it** (ADR-0164, ADR-0166).
2. **Nothing in the toolkit paints outside a box's own rectangle**, so a shadow
   would need a damage rectangle nobody had (ADR-0166).
3. **The rasterizer has no blur** — ADR-0256's line, and the only one of the three
   that was still true this week.

The first two stopped being true while other work went past them. The focus ring
is drawn outside the border box, and
[RenderTree](0072-a-partial-repaint-needs-a-promise.md)'s damage rectangle has
grown by `outline-offset + outline-width` ever since. So "nothing paints outside
the box" describes a toolkit two hundred ADRs ago.

The third is still true and is the interesting one. Blend2D's image filters are
not on the export list, and putting them there would not help much: a blur is an
offscreen pass, and a box that wanted one every frame would pay for a buffer, two
passes over it and a composite. The frost material's 3-pass box blur
(`docs/design-system.md` §1.5) is not the same thing — it runs over a *backdrop*,
on a downsampled copy, cached while static, which is exactly the arrangement a
shadow cast by a box in the middle of a paint walk cannot have.

What the rasterizer does have is a very fast rounded-rectangle fill.

Meanwhile the cost of not having the property kept showing up. A card is told
apart by a 16%-alpha rim; a menu, a popover and a dialog float over the window
with nothing under them; §1.5 pins two shadow recipes that nothing could draw;
§1.7 describes `affix` animating "opacity on the elevation shadow" for a shadow
that did not exist. Every one of those is the design system asking for a property
and getting an apology.

## Decision

**`box-shadow: <x> <y> <blur> [<spread>] <color>`, drawn as a stack of nested
rounded-rectangle fills.**

One shadow per box, not a comma list. Three pieces:

- **`css.value.Shadow`** — the value: four lengths, a colour, a parser, `fade`,
  `mix`, and the four *asymmetric* outsets that say how far past each edge it
  reaches. It rides on `Decoration` rather than as `Box`'s twenty-eighth
  component, on the sentence that class opens with: a drop shadow is drawn
  **around** a box and not in it, it is geometry derived from the corner radii,
  and nothing reads it without also reading them.
- **`paint.shadow`** — a package of two halves, neither of which needs a `Frame`:
  `ShadowRamp` says how opaque each band is, `ShadowGeometry` says what shape it
  is.
- **`paint.ShadowPainter`** — twelve lines, in `paint` because it is the only part
  that touches the pooled rasterizer path.

And **`--gb-elevation-1` / `-2` / `-3` in each theme**, as whole `box-shadow`
values rather than colours.

### The alphas are solved for, not read off

This is the part that is easy to get wrong and invisible when it is. Nested fills
composite `over` one another, so a point covered by the outer five bands does not
end up at the fifth band's alpha — it ends up at `1 - Π(1 - aᵢ)`. Bands whose
alphas are read straight off the fade curve give a shadow far too heavy in the
middle, with visible rings in it. So the curve is treated as the **accumulated**
alpha and each band's own alpha is solved:

```text
Aₖ = T · C(uₖ)                 the alpha the fade wants after k bands
aₖ = 1 − (1 − Aₖ)/(1 − Aₖ₋₁)   the alpha this band must be painted at
```

`C` is smoothstep across the blur, which is not a Gaussian and has the two
properties that matter: it is exactly `0.5` on the shape's own edge, which is what
a blur does, and flat at both ends, so the fade meets "nothing" and "solid"
without a seam. Against a true Gaussian of σ = blur/2 it is a few percent light in
the shoulders; at the alphas a shadow is painted at, a few percent of a few
percent is under a bit of one channel.

One band per logical pixel of blur, between four and forty-eight. Per **logical**
pixel, so a window at 150% paints the same bands at 1.5× the size rather than half
again as many of them — which is what keeps a shadow inside `ScaleInvariance`.

### The bands under an opaque box are never built

The painter tells the ramp whether the background will cover the box's own
rectangle. When it will, the bands lying entirely inside it are dropped — they are
always a suffix, because the bands only shrink, so nothing earlier changes and the
visible picture is identical. For `0 0 <blur>`, which is what a glow is, that is
half the fills. For the design system's `0 2px 8px` it is most of the inner half.

### The alpha belongs to the theme, and the geometry does not

A shadow is black cast onto whatever is underneath, so what it costs in contrast
depends entirely on how light that is. `rgba(0, 0, 0, 0.16)` is a clear soft edge
on nord-light's `#eceff4` and very nearly nothing on nord-0. An application
picking the number would pick one number and be wrong on one theme — which is
precisely the mistake `--gb-surface-2` cost three widgets
([ADR-0245](0245-the-second-surface-stays-and-says-so.md)).

So the **alpha** differs between the two files, by roughly two and a half times.
The **geometry** does not: an object 8px off the page throws the same shape
whatever colour the page is, and §1.5's `0 2px 8px` and `0 8px 32px` are what it
is. `ThemeTest` asserts both halves — dark is heavier at every level, and the four
numbers are identical.

Three levels where §1.5's ladder names two shadowed ones: `-1` is its *raised*,
`-2` its *overlay*, and `-3` is above both, for a thing the pointer is dragging.

### `transition: box-shadow`

Added to `Transitions.Animatable`, because a `transition` naming a property the
engine resolves and cannot animate is the silent-nothing that enum's own class
note refuses. Every component interpolates, so a card lifting from `-1` to `-2`
grows its blur and its offset as well as its alpha, which is what an object rising
off a page does. Interpolating from `none` fades the arriving shape in at full
size rather than ramping its geometry up from zero — CSS's rule, and the reason
for it is that the other way a card appears to inflate.

## What this does not do

**It does not knock the border box out of the shadow.** CSS paints an outer shadow
only *outside* the box that casts it; this paints the whole shape and relies on
the box being drawn on top of it.

That is a deliberate limit and not an oversight. Cutting the hole needs either a
path clip or a fill rule, and the binding has neither — and the obvious trick does
not work: a reversed sub-path under Blend2D's default non-zero winding *fills*
the parts of itself the outer shape does not cover, so a band inside the border
box (which every band of the inner half is) would paint a dark ring where it was
supposed to erase one. Worse than the thing it fixes.

The difference is invisible under an opaque background, which is every shadowed
surface the design system has. It shows under a **fading** one: a box mid-`opacity`
transition fades its shadow by the same factor, so what is under it darkens it
slightly rather than being hidden. `ShadowPaintTest` pins that, so the day a fill
rule lands there is a test that says the deviation is gone. `TODO.md` carries it.

**It does not put a shadow on any widget.** The tokens exist and nothing in
`controls.css` reads one yet. Elevating `card`, `menu`, `popover` and `dialog` is a
visual change to the whole catalog and belongs in its own change, with its own
goldens.

## Alternatives considered

**Export Blend2D's blur and render each shadow offscreen.** The faithful answer,
and rejected on cost: a buffer, two passes and a composite per shadowed box per
frame, for a picture the eye cannot tell from a ramp of fills. It is also the
wrong shape — the blur would have to run at physical resolution while everything
around it is described in logical pixels, which is where a shadow picks up a
half-pixel seam at fractional scale.

**A comma-separated list, as CSS takes.** Rejected, and the list is read as its
first entry with the rest logged. Every shadow §1.5 pins is one shadow and every
one a theme ships is a single token, so a list has no author here. The idiom
mostly exists to *fake* a blur profile out of two hard-ish shadows, and this is a
ramp already. The first entry rather than a refusal for the reason
`border: 1px dashed red` draws a solid line: drawing something is the more useful
of the two wrong answers.

**Make the token a colour — `--gb-elevation-1: rgba(0,0,0,.44)` — and let the
widget write the geometry.** Rejected, and it is the version this nearly was. It
gets the theme-owns-the-alpha half right and leaves every widget restating
`0 2px 8px`, which is four numbers in eleven stylesheets that have to agree for
the elevation ladder to mean anything. A whole-value token is the one where a rule
writes `box-shadow: var(--gb-elevation-2)` and chooses *nothing*.

**Keep the edge and add nothing.** What ADR-0164 and ADR-0166 decided, twice, and
it was right both times: the edge works, it needs no drawing outside the box, and
it is still what a card wears. What changed is that the reason for it — "nothing
paints outside a box" — stopped being a fact about the toolkit, so it was no longer
a principle, only a limit.

**Default a colourless shadow to black.** Rejected. CSS's default is
`currentColor` and §8's subset has no such thing; guessing black paints a hard
black halo where an author meant a tinted one. The declaration is dropped and
logged, which is what §8 does with a value it cannot honour.

**`inset` shadows.** Refused at the parser. An inner shadow is clipped *to* the
border box rather than cast outside it — a different drawing that needs the clip
this one was careful not to need — and no rule in the canon asks for one.

## Consequences

**§8's list is two short.** `backdrop-filter` and `letter-spacing` are what is
left; `box-shadow` was the example in four different comments about properties the
engine drops, and those comments now name `backdrop-filter`.

**A shadowed box costs a run of fills.** Eight for `--gb-elevation-1`'s
`0 2px 8px`, five of them once the bands under the box are dropped; thirty-one
for `-2`'s `0 8px 32px`, twenty-two after. Each is a rounded-rectangle fill,
which is the primitive the rasterizer is fastest at, into one pooled native
path. An unshadowed box costs one
branch on `hasShadow()` and nothing else, which matters because that is every box
in an ordinary window.

**The damage rectangle is asymmetric now.** It was one outset on four sides and it
is `max(ring, shadow.outsetX)` per side, because `0 8px 32px` reaches 24px below a
box and 8px above it. Taking the larger for all four would repaint bands nothing
drew in — and, the day a shadow is offset further than it is blurred, *miss* one,
which leaves a smear that survives until something else repaints over it.

**`Decoration` has a seventh component.** Every wither in it grew an argument.
That is the cost of not putting it on `Box`, and it is much the smaller of the two
bills.

**Three new test files and two goldens.** The ramp arithmetic and the band
geometry are tested without a rasterizer, because a golden can say the picture
changed and cannot say that the alphas do not add. The goldens are one per theme,
because the alpha is the half of an elevation token that belongs to the theme and
one image could not say that.
