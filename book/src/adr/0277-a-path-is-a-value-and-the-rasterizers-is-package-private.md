# 277. A path is a value, and the rasterizer's is package-private

Date: 2026-09-12

## Status

Accepted. Opens the work that closes `docs/gaps.md` G1, G2 and G10, and the first
of four steps toward `:natives` exporting to `:core` and to nobody else.

## Context

`docs/ARCHITECTURE.md` §3.1 states one boundary rule and `ExportedSurfaceTest`
enforces it: a raw `MemorySegment` never leaves `:natives`. That rule is kept.
There is a second rule nobody wrote down — **no `:natives` type appears in an
application-facing signature** — and it is broken in two families.

The one that was noticed is drawing. `Frame` could fill a rectangle and nothing
else without a `BlendPath`:

```java
public void fillPath(double x, double y, BlendPath path, int argb);
public void fillPath(double x, double y, BlendPath path, BlendGradient gradient);
public void strokePath(double x, double y, BlendPath path, double width,
                       BlendStrokeCap cap, BlendStrokeJoin join, int argb);
```

So an ellipse, a polyline or an arrowhead forced a caller into `:natives`. Five
widgets in this repository did it — `Sparkline`, `DonutSurface`, `ChartSurface`,
`ColorRamp`, `ColorPlane` — and so did the first application built on the toolkit,
which filed it as G1. `paint.Arc` and `paint.RoundRect` were already in `:core`,
typed on `BlendPath`, because `:core` had no path type of its own to hang them on.

The one that was not noticed is layout, and it is larger: `paint.Box` and
`css.ComputedStyle` each carry thirteen Yoga-typed components, and `Box` is what
every custom widget returns from `render()`. That is ADR-0279's, and it is named
here because it is why this ADR is the first of four rather than the whole answer.

## Decision

A `Path` is an immutable value in `io.github.digitalsmile.goldberry.paint`, built
from two parallel arrays — a verb per segment, and the coordinates those verbs
consume. Beside it: `Stroke` (width, `Cap`, `Join`, miter limit, `Dash`) and a
sealed `Gradient`. None of them mentions a `:natives` type in public.

The seam is one package-private method:

```java
void replayInto(BlendPath path);
```

`Path` and `Frame` are in the same package, so the one place the two models meet is
invisible from outside it. An application holds a `Path`; nothing it can name holds
a `BlendPath`.

## Replaying is cheaper than what it replaces, which is the argument for it

The obvious objection is that a `Path` is a second geometry model over the same
rasterizer, and that building one and then replaying it must cost more than
building the native path directly. It costs less, because of what the two modules
were already doing.

A `BlendPath` is a confined `Arena` and a `bl_path_init`. `:core` knew this and
worked around it: `BoxPainter` pools one path per paint walk and `reset()`s it
between shapes, threading it through its own public signature —

```java
public static void paintOne(Frame frame, BlendPath path, Box box, ComputedLayout layout);
```

— with a comment explaining that forty rounded controls would otherwise make eighty
arenas a frame. `:widgets` did not know it. Every path site there is a fresh
`try (var path = BlendPath.create())` inside the paint call: four in `ChartSurface`,
two each in `ColorRamp`, `ColorPlane` and `Sparkline`, one in `DonutSurface`.

So `Frame` owns the scratch path now. Building a `Path` allocates two Java arrays
and no native memory at all, the pooling nobody remembered to do happens once in
the place that can see every drawing call, and `paintOne` loses a parameter that
was only ever there to carry the workaround.

## What it measured

`./gradlew :core:benchmark`, same machine, before and after the whole of phase 1
(the value types, the retyped `Frame`, and the five migrated widgets). The
showcase frame at 960x640:

| | before | after |
|---|---|---|
| frame, 0 threads (median) | 0.957 ms | 0.966 ms |
| frame, 4 threads (median) | 0.760 ms | 0.751 ms |
| three stroked icons add | 0.206 ms | 0.106 ms |

The frame is unchanged inside the run-to-run spread, which is what was wanted:
`BoxPainter` already pooled its path and still does, through the frame instead of
through its own signature.

**The icons halved**, and that is the pooling arriving somewhere it had not been.
An `Icon` used to hold a `BlendPath` and stroke it directly; it holds a `Path` now
and is replayed into the frame's pooled one — so the three icons in that scene no
longer each carry a native allocation through the frame. The same change is
waiting for every chart: `ChartSurface` opened four confined `Arena`s per paint
and now opens none.

No number here is a test. `PaintBenchmark` asserts no timing, deliberately —
"a timing assertion on shared CI hardware fails for reasons that have nothing to
do with the code" — so these are evidence recorded where they can be argued with.

## Two arrays rather than a list of records

`Path.Segment` is a sealed interface of six records, and `segments()` returns a
list of them — but that is the *readable* view, built on demand, and drawing does
not use it. Replay is a loop over primitives with nothing boxed, which matters
because a smoothed chart line is hundreds of segments and replay happens once per
shape per frame.

The list is not decoration. It is what a test asserts on, and it is what an
application writing a board out as SVG needs: the `d` attribute is a `switch` over
exactly those six records, and a path it cannot read is a path it cannot serialize.
Sealing means that `switch` needs no default branch.

An arc's two boolean flags live in the **verb byte** rather than as two doubles
holding 0 or 1 — five coordinates, not seven.

## `Dash` is a type because `Stroke` is a record

A `double[]` component would give a record whose `equals` compares array identity,
so two strokes written the same way would be unequal — and a widget caching "have I
already drawn this?" would cache nothing, silently, with no symptom but a frame
time. `Dash` holds a `List<Double>`, which boxes two to four numbers per stroke and
makes the enclosing record behave like a value.

SVG's odd-length rule is applied in the constructor rather than left to the
rasterizer, so `pattern()` reads back what will actually be drawn: `Dash.of(5)` is
`[5, 5]`.

## What is deliberately not here

- **The colour.** It stays an argument to the drawing call, like every other fill
  in the toolkit, so that one style and eight series colours is one `Stroke` and
  eight `int`s.
- **Radial gradients.** `Gradient` is sealed with one implementation because
  `BlendGradient` binds one. Sealing is what makes adding `Radial` later a record
  and an exhaustive `switch` that stops compiling, rather than a default branch
  that quietly draws the wrong thing.
- **An origin, which `docs/gaps.md` G1 did not propose.** `fillPath(x, y, Path, …)`
  and `strokePath(x, y, Path, …)` are kept beside the plain forms: the origin moves
  the shape without transforming the frame, which is what lets one 24x24 icon path
  be drawn at several places without being rebuilt or bracketed in `save`/`restore`.
  `Icon.draw` and a chart's readout are both that case.

## Consequences

- **`Join` has a `MITER` the rasterizer's enum does not.** Blend2D has three miter
  variants differing only in what happens past the limit; SVG and CSS have one
  `miter` and a separate number. The toolkit takes SVG's shape, and
  `Stroke.miterLimit` is where the number goes — which means `Join` cannot be
  translated by ordinal and needs a real `switch`. That is the general cost of
  owning a vocabulary rather than re-exporting one, and it is the point.
- **`Cap` and `Join` carry no wire values.** The C numbering is not alphabetical —
  round is 2 for a cap and 4 for a join — and that ordering stays in `:natives`,
  checked against the compiled library where it belongs.
- **`paint.Path` collides with `java.nio.file.Path` by simple name.** A
  single-type import shadows a same-package type, so a class in `paint` that needs
  the file one can still import it; everywhere else it is an import like any other.
  G4's proposed `Image.decode(Path file)` will have to be written with that in mind.
- **A non-finite coordinate throws where it is written.** Blend2D accepts a NaN and
  fills nothing, which is indistinguishable from arithmetic that went wrong three
  methods earlier. This moves that failure to the line that caused it.
- **`Arc` and `RoundRect` have one implementation between them and `Path`.** The
  KAPPA arithmetic moved rather than being copied, so ADR-0050's tolerance argument
  and ADR-0064's no-new-symbols argument both still hold, unchanged.
- **No golden image may move.** The point sequences are the ones `RoundRect` and
  `Arc` emitted before, asserted in `PathTest` rather than assumed. A diff out of
  `blessGoldens` during this phase is a bug in the value type, not a picture to
  re-record.

## Alternatives considered

- **Keep the `BlendPath` overloads public as an escape hatch.** Then the module can
  never be sealed, and the rule stays a convention that a test cannot check. The
  overloads are package-private instead; `:core`'s own painters keep using them.
- **Memoize a `BlendPath` inside each `Path`.** It removes the replay, and it gives
  an immutable value a native resource with a thread affinity and no `close()` —
  a lifetime problem in a type whose whole appeal is not having one.
- **`List<Segment>` as the storage.** One object per segment, hundreds per chart
  line per frame. The list is the view, not the model.
- **A `float`-based path.** brd is a board with a viewport transform over it, and
  `Frame`'s coordinates are already `double`. Narrowing in the middle would be a
  precision cliff nobody could see coming.
- **Deprecate rather than remove.** 0.1 is an M5 item and the only consumers are
  `:example` and brd, both in hand. A deprecation cycle here would buy nothing and
  postpone the seal.
