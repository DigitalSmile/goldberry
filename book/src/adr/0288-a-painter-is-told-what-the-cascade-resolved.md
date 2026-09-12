# 288. A painter is told what the cascade resolved

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G11.

## Context

G11: *"A painter is given a `Frame` and a size. It is not given the
`ComputedStyle` of the box it is painting, so canvas text has to name a font
(`BundledFont.UI`) rather than inherit the one the cascade resolved. …
`Painter.paint(Frame, LogicalSize, ComputedStyle)`, or a `Frame.style()`."*

The showcase had the symptom in one line, and it is worth quoting because it is
what every application would have written:

```java
try (var font = Font.bundled(BundledFont.UI, 12)) {
    Paragraph.of(font, note).paint(frame, 8, size.height() - 22, width - 16, ACCENT);
}
```

A named face, a named size, a named colour, and a **parse of the font file on
every frame** — because `Font.bundled` opens its own face. None of it moves when
the theme does. The renderer knew all three answers and was not passing them on.

The same paragraph of the toolkit's documentation says a chart is a `canvas`
"which is what lets a chart inherit the theme" — and that is true of the widgets
in the catalog, because they implement `Paints` and read the style in `render`.
It was not true of an application's canvas, which has no `render` to read it in.

## Decision

### A `StyledPainter` beside `Painter`, not instead of it

```java
new Canvas((frame, size) -> …);                // unchanged
new Canvas((frame, size, style) -> …);         // a StyledPainter
```

`StyledPainter extends Painter` and adds `paint(Frame, LogicalSize, CanvasStyle)`.
Three things fall out of the subtyping and each was the reason for it:

- **The compiler picks by arity.** A two-parameter implicit lambda is not
  potentially applicable to a three-parameter function type, and vice versa, so
  `new Canvas(…)` takes either form with no cast and neither is second class.
- **`new Canvas(null)` still resolves.** Two unrelated interfaces would have made
  it ambiguous; a subtype makes the `StyledPainter` overload *more specific*, and
  the existing call sites that pass `null` compile untouched.
- **Nothing in `paint` changed.** `Box.painting` still holds a `Painter`,
  `BoxPainter` still calls two arguments, `Offscreen.paint(Painter)` is
  unchanged, and the eight painters in the catalog that never wanted a style did
  not grow an unused parameter.

The alternative — making the three-parameter form *the* signature — was rejected
for that last point. Ten call sites would have gained a parameter they ignore,
and a signature that most of its callers do not use is a signature that is wrong
for most of its callers.

### `CanvasStyle` is a record, and it is a snapshot

```java
public record CanvasStyle(Font font, int ink, double nowMillis, boolean reducedMotion)
```

Not `ComputedStyle`, as G11 proposed, and the difference matters twice.

**It carries a `Font`, not a `Typography`.** The thing a painter needs is the
opened face at the resolved size, and only the renderer can produce one — it owns
the `Fonts` book that makes "Inter 600 at 13px" a map lookup instead of a parse.
`ComputedStyle` would have handed over the *description* of a font and left the
caller to open it, which is the line the showcase already wrote.

**It is read when the box is built, not when it is painted.** This is not an
optimisation; it is the only correct shape. `Paints.Context` answers per node by
knowing which node is currently rendering — `color("--gb-chart-1", …)` resolves
against a field the renderer sets just before each `render` call. A context held
past that point answers for whichever node rendered last. So `Canvas.render`
binds the painter to a snapshot:

```java
var painting = painter instanceof StyledPainter styled ? styled.bound(context.canvasStyle(style)) : painter;
```

The same reasoning says what is **not** on `CanvasStyle`: the custom-property
accessors. A painter cannot name in advance the tokens it will want, so they
cannot be snapshotted, and a widget that needs them is a `Paints` and reads them
in `render` — which is what every chart in the catalog does (ADR-0195).

### Two things beyond the font, because a painter could not reach them either

`nowMillis` and `reducedMotion` are in the record. A canvas painter had no clock:
`Paints.Context.nowMillis()` is the frame time read **once per frame and shared**,
so two spinners tick together (ADR-0081), and an application animating a canvas
had nothing but `System.nanoTime()` — which is a second clock, off by a frame,
and immune to the virtual one every test drives. `reducedMotion` is the §1.7
question that has no declaration behind it and therefore has to be asked.

### `Frame.style()` was the other option in the entry

Rejected. A `Frame` is the rasterizer's surface and is one object for the whole
window; a per-box CSS answer on it would be mutable state set and unset around
each canvas, readable at the wrong moments by anything holding the frame, and a
`paint` type with an opinion about the cascade. The parameter says the same thing
and cannot be read where it does not apply.

## Consequences

- **Nothing moved in any golden image.** The catalog's painters are unchanged and
  the showcase's converted line draws text that is empty until a key is pressed.
- **The showcase stopped parsing a font file per frame.** One line, and it is the
  line G11 was written about.
- **`Canvas` gained four constructors** — the three it had, once more each for a
  `StyledPainter`, plus the canonical three-argument one, without which a method
  reference to a three-parameter painter has nothing to match at the three-
  argument call site.
- **An unbound `StyledPainter` paints with `CanvasStyle.none()`** rather than
  failing. Handed straight to a `Box` or to `Offscreen.paint`, there is no node
  and therefore no cascade; the bundled UI face at the body size, opaque black
  and time zero are **defaults and the javadoc says so**, not the cascade's
  answers. Failing mid-frame instead would turn a mislaid binding into a crash.
- **`CanvasStyle.none()` is one lazily made instance.** `Font.bundled` parses the
  face out of the jar, which is not work to do in a class initializer or twice.
- **`CanvasStyle` is a record and will grow by gaining components**, which is a
  source-breaking change for anyone constructing one directly. That is the trade
  for it being a snapshot: an interface could have grown silently and would have
  invited an implementation that answers live.

## Alternatives considered

- **`Painter.paint(Frame, LogicalSize, ComputedStyle)` as the single method**, as
  the entry proposed. Ten call sites gain an unused parameter, and the style it
  hands over still cannot open a font.
- **`Frame.style()`** — see above.
- **A `ScopedValue<CanvasStyle>` bound around the painter call.** Structurally
  correct and genuinely tidy, and still ambient: the value would be reachable
  from any code the painter calls, including code that is not a painter, and the
  binding would be invisible at the call site. A parameter is the same guarantee
  written down.
- **Putting the style on `Box` as a 26th component.** It would have pushed the
  resolution into `BoxPainter` and made every `new Box(…)` in the toolkit carry a
  field that means something for one box in a hundred.
