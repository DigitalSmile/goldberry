# ADR-0080 — A value is measured along a part

*Accepted, 2026-08-17. Supersedes nothing; extends
[ADR-0079](0079-a-continuous-value-is-placed-by-ratio.md).*

## Context

`slider` shipped with three things `docs/core-widgets.md` §3 asks for and it did
not have: "optional **tick marks** and **value label**", and, for `fader`,
"optional **dB scale** mapping". They look like three small additions to one
control. They are not, and the reason is that each of them breaks a different
thing the control was resting on.

ADR-0079 put the thumb at a fraction of the track by flex ratio, and read the
pointer back with `PointerEvent.local().fractionX()` — *where inside the widget
currently handling this event did it land*. Both halves assume the same
sentence: **the control is the track**. It was true, because a slider had
nothing in it but a groove.

A value label is what makes it false. `[ track ──────── ] 40` is one control and
two boxes, and the one the value lives along is the shorter one. The status log
predicted this when the label was deferred:

> The label is the awkward one: it would sit inside the control's own box, so the
> pointer-to-value mapping would stop being "along the control" and would need the
> *track's* rectangle rather than the slider's.

Tick marks break something else. A mark names a position the thumb can sit on, so
a scale is a claim about where **another part** ends up — and the thumb's centre
does not travel the full width of the track. It travels the width less its own
16px, because it is a box in a flex row rather than a point.

And the dB scale breaks the arithmetic in the middle: `min + f × (max − min)` is
written twice, once each way, in two methods that must stay inverses of each
other.

## Decision

### A widget may name the part its pointer position is measured against

`Handles.localPart()` returns a CSS type name, or null. The router resolves it to
the first descendant element with that type and reports `local()` against **that**
rectangle. `Slider` returns `"slider-track"`.

Named as a **CSS type** because that is the vocabulary a part already has
([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)) — the same string
the stylesheet writes, resolved against the same tree. Resolved by the **router**
because the widget cannot see its own elements: the identical argument to
`dragX()` ([ADR-0075](0075-a-gestures-origin-is-the-routers.md)) and to Tab
([ADR-0073](0073-a-composite-is-one-tab-stop.md)) — the router owns what the
widget cannot see.

The fallback is on the **rectangle**, not on the element, and that distinction is
the whole of the fallback being useful. A part is in the element tree from the
first build and has no region until the first paint, so an element-level check
finds it and then hands back `Local.UNKNOWN` — a zero-sized box, whose every
fraction is 0, which for a slider means *the user asked for the minimum*. A
control missing its label for one frame would jump to zero. Falling back to the
control's own box is wrong by the label's width; the other answer is wrong by the
whole range.

### The control is not the box the value lives on, and the anatomy says so

`slider-track` was the 4px groove. It is now the **full-height box the value is
measured along**, and the groove is a part inside it called `slider-groove`:

```
slider
└── slider-track          grow 1, the hit target, and what localPart names
    ├── slider-groove     4px, and the flex ratio ADR-0079 describes
    │   ├── slider-fill   grow f
    │   ├── slider-thumb  16
    │   └── slider-rest   grow 1-f
    └── slider-ticks      the scale, if any
└── slider-value          the readout, if any
```

The rename is the point rather than a side effect. Two boxes were doing one job
under one name, and the day a third thing joined the control they stopped being
the same box — so the names now say which is which: you drag along the *track*,
and the *groove* is the channel you can see.

**Every existing golden image is byte-identical after this restructure**, which is
what says it was a refactor and not a redraw.

### The scale hangs out of a zero-height row, moved by a transform

Two things had to be true at once, and each rules out the obvious implementation
of the other:

- **The marks must clear the thumb.** A scale drawn under a 16px disc is a scale
  you cannot read where it matters most.
- **Adding a scale must not move the groove.** The track centres its column, so
  anything the scale contributes to that column pushes the groove up — and two
  sliders in one settings list, one with a scale and one without, would sit at
  different heights for a reason no reader could see.

So `slider-ticks` is `height: 0` — it takes no part in the centring — and each
mark is moved clear by `transform: translate(0, 10px)`. A transform costs no
layout ([ADR-0068](0068-the-transform-stack-is-java-side.md)), which is exactly
the property needed: the mark moves and the line it hangs from does not exist.
Ten is half the thumb's 16 plus the two a mark straddles its own line by, and it
buys two pixels of air.

### A mark is centred by a cell with no width

The marks are spread by `justify-content: space-between`, and each one sits in a
synthesized **0×0 box** that it overflows out of, centred.

That wrapper is the whole of why the scale lines up. Spread five 2px marks across
the free space directly and their centres land at `i × (C − 2)/4 + 1` rather than
at `i × C/4`: the first a pixel right of where it belongs, the last a pixel left,
every one of them a pixel off the thumb centre it is supposed to name. A mark's
own width has no business being in the spacing arithmetic, and at zero it is not.

The cell is zero on **both** axes rather than on the main one, and that is what
keeps the widget from knowing which axis it is on: a fader flips the row to a
column in the stylesheet, and a 0×0 cell is already correct in either. The widget
names the semantics and the stylesheet names the axis — ADR-0079's rule, applied
to the one part that would otherwise have needed a `vertical` flag of its own.

The cells are **boxes and not widgets**. They carry no style, match no selector
and mean nothing to an author; a part is what an author can restyle, and there is
nothing here to restyle.

The tick row's `padding: 0 8px` is **half of §3's thumb 16**, and it is one
arithmetic statement with the thumb the way the toggle's `2 + 16 + 16 + 2 = 36`
is one with its travel: a mark names a position the thumb's *centre* reaches, and
that centre stops half a thumb short of each end.

### Marks are counted along the travel, not along the value

`ticks=5` is five marks, both ends included, evenly spaced along the **travel**.

Two alternatives were rejected. One mark per `step` is what most toolkits do and
it puts twenty-one marks on a 0–100 slider stepping by 5, which is a wall rather
than a scale. Marks at even *values* are the same list as even positions on a
linear slider and a useless one on a scaled fader — five marks at gains 0, 0.25,
0.5, 0.75 and 1 land at 0%, 80%, 90%, 96% and 100% of a decibel travel, which is
four marks huddled at the top and one at the bottom.

One mark is refused at construction. A scale is its two ends and what is between
them.

### The value label is a format string, and it is fixed-width

`format` is a `java.util.Formatter` pattern held on the record, **not** a
`DoubleFunction<String>`. §11's parity invariant asserts that the Java-built and
KDL-built forms of a control are `equals`, and two lambdas doing the same
arithmetic never are. A pattern is a value, so `format="%.0f%%"` in markup and in
Java produce the same slider.

It is validated when the slider is built — by formatting `min` with it — so a
`%d` against a double fails at inflation with the pattern quoted, rather than
throwing an `IllegalFormatConversionException` out of a paint on whichever frame
first has a value to draw. That is ADR-0062's rule applied to a format string.

Formatted in `Locale.ROOT`. Not tidiness: the default locale would draw `0,5` on
a machine set to `de_DE` where CI drew `0.5`, and the golden that failed would be
a pixel diff nobody could reproduce anywhere else. A locale-aware readout is the
application's to pass in already formatted.

`slider-value` has a **fixed width in the stylesheet**, and that is the decision
rather than a default. A label that sized itself to its content would take three
pixels off the track between 9 and 10 — which moves the value under the finger
that is setting it, at the moment it is being set.

### A scale is a value, and the dB one is a taper

`Scale` is a sealed interface with two methods that are inverses — `toFraction`
and `toValue` — and two implementations: `Linear` and `Decibels(floorDb)`. Every
place the slider converted between a value and a position now goes through it,
which is three places rather than the two that were obvious (the thumb, the
pointer, and the arrow keys).

Records rather than lambdas, for the parity reason above: `scale="db"` and
`Scale.decibels()` are the same value.

`Decibels` places a **linear gain** at a position that is linear in dB, which is
what a mixing desk's fader does and what §3 means by "dB scale mapping". A gain
of 0.5 is 6 dB down, which is 90% of the way up a 60 dB travel and half way up a
linear slider. That difference *is* the feature: placed linearly, everything a
fader is used for happens in its top inch.

The bottom of the travel is `min` **exactly** rather than `max × 10^(floor/20)`,
because the thing a fader must be able to do is go silent. It is a discontinuity
of 0.001 of full scale at one end of the control — the difference between −60 dB
and nothing, which is not audible. A fader that bottomed out at "very quiet" is a
fader with a bug.

`scale="dB"` is **refused** rather than resolved quietly to linear, like every
other name a document can write ([ADR-0062](0062-bind-is-a-path-and-nothing-else.md)):
the alternative is a fader that works and is wrong.

### A continuous slider steps along the travel; a stepped one keeps its grid

An arrow moves a hundredth of the **travel** and a page a tenth, when `step` is 0.
On a linear scale those are the range's hundredth and tenth and nothing changes.
On a fader they are not: a hundredth of the *gain* is a hair at the top of the
travel and a third of it at the bottom, so a fader would step unevenly under a key
held down.

A slider that *does* have a `step` keeps stepping in value space, because a grid
is what the author asked for and the values on it are theirs rather than the
screen's.

## Consequences

Three new parts (`slider-ticks`, `slider-tick`, `slider-value`), one renamed
(`slider-track` → `slider-groove`) and one repurposed (`slider-track`). One new
method on `Handles`, defaulting to null, which every other widget ignores.

`SliderGeometryTest` is a new kind of test in this repository and the change is
what needed it. The claims the marks rest on are **geometric relations between two
parts** — a mark under the thumb's centre at both ends and at any width, a scale
that clears the thumb, a groove that does not move when a scale is added — and
each of them is a number that no stylesheet states and no value assertion can
reach. They come out of the flexbox algorithm, and every wrong version of them
lays out perfectly and draws a plausible picture. It lays a tree out through the
real `RenderTree` and asserts against the captured rectangles, which is the same
route hit testing takes.

Two of its six assertions failed on the first run, and both were real: the tick
row's `padding-top` was pushing the groove up by five pixels, because Yoga adds
padding to a box with an explicit `height: 0`; and the label's width was not
coming off the track at all, because a slider in a *row* collapses to its content
width and the test's own scene was the thing that was wrong. The transform came
out of the first of those.

**What this does not do**, and each is named rather than left to be discovered:

- A slider still maps the pointer over the track's **full width**, so at the
  extremes the thumb's centre is up to 8px from the finger. Closing it means a
  widget being told a resolved metric — the thumb's width — which is a bigger door
  than this is worth. The *marks* do not have this problem, because their inset is
  the stylesheet's own and sits beside the thumb's width in the same file.
- The readout is **left-aligned in its box**, because §8's subset has no
  `text-align` (`ARCHITECTURE.md` §8.1 says so deliberately). This is the one thing
  here that is a gap rather than a decision.
- A slider with a readout is **wider in its row** and one with a scale is no
  taller, which is the trade the zero-height tick row makes: the marks are drawn
  inside the control's 32px, in the space below the groove that the hit target was
  already claiming.
- `knob`'s taper is what `Scale` was built general for, and there is no `knob`.
