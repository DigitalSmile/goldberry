# 281. A canvas hears what it draws on

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G3, which was the last **now** on that list and
the one it called "the single biggest blocker: brd's client is a viewer until it
lands".

## Context

G3 asks for "pointer, wheel, keys, capture, cursor" on a `canvas` and says of the
toolkit: *"There is no wheel, no drag, no key, no pointer capture, no per-widget
cursor."*

That is not true, and finding out it is not true is most of this decision.

`Handles` already declares `onPointer`, `onPointerCapture`, `onKey`, `onText`,
`isFocusable` and the focus notifications. `PointerEvent` already carries the
button, the click count, the modifier keys, the wheel's fraction *and* its
detents, and where the gesture started. `PointerRouter` already captures the
pointer implicitly on press and releases it on the matching release, so a drag
that leaves a widget keeps arriving — `WheelAndCaptureTest` has asserted that
since the scrollbars were built. `Box` already carries a `Cursor`, and the
hit-test snapshot already records it per region.

What was missing is that **`Canvas` implemented none of it.** It was
`Widget.Leaf, Styled, Paints, Attributed` and not `Handles`, so a painter was
handed a frame and no events. The gap was one widget's declaration, not a
subsystem.

## Decision

`Canvas` implements `Handles` and takes an `Input` beside its `Painter`:

```java
new Canvas(painter, event -> …)          // or an Input with onKey, onText
```

`Input` is `Painter`'s sibling and hands over the toolkit's **own**
`PointerEvent` and `KeyEvent` unchanged. A `CanvasPointerEvent` re-expressing the
same facts would be a second vocabulary to keep in step, and the existing one
already carries everything a board tool needs.

A canvas is focusable exactly when it has an `Input` that wants to be. A chart
that took a Tab stop and did nothing with it would be a keyboard trap with no
exit, which is what §2.2's "everything reachable" is least served by.

## The one thing that had to be built is the coordinate space

ADR-0193 made a canvas painter draw **inside the padding** and clipped it there,
so that `canvas { padding: 8px }` is a framed drawing surface rather than a
surprise. The hit-test snapshot recorded only the border box.

So a canvas reading `PointerEvent.local()` would have had every event offset from
its own ink by exactly the padding — a press eight pixels from where it was
drawn, silently, on the one widget whose entire job is to be drawn on. The
painting decision and the input decision would have disagreed, and the disagreement
is invisible in both signatures.

`HitTest.Region` therefore records a **content rectangle** beside its own, and
`PointerEvent.content()` reports the pointer inside it. For a box with no padding
— which is most boxes — the two are the same rectangle and this costs nothing.

**There is one implementation of the arithmetic.** `Length.resolve(length, base)`
moved onto the layout vocabulary precisely so that the painter and the snapshot
cannot drift: `BoxPainter.paintCanvas` and `HitTest.collect` now call the same
method rather than keeping two copies of a `switch` that must agree.

A consequence worth stating: percentage padding resolves **per axis** — left
against the width, top against the height. CSS resolves all four against the
containing block's inline size. That divergence is `BoxPainter`'s and predates
this; what matters here is that input and paint agree, and they cannot fail to.

## What did not need building

- **Capture.** The router captures on press for every widget. G3's sketched
  `.capturePointer()` is already the default, and a marquee dragged off the edge
  needs to ask for nothing.
- **The wheel.** A `PointerEvent` of kind `WHEEL`, with `deltaY()` for a
  touchpad's fraction and `ticksY()` for a mouse's detents.
- **The cursor.** `canvas { cursor: crosshair }` works like it does on any box,
  and the snapshot records it because the style that decided it is gone by the
  next frame.
- **Consuming.** `event.consume()` is how a zoomable board stops the wheel
  scrolling the pane it sits in.

## Consequences

- **A canvas is a `Role.FIGURE`** — "a picture of data, which a reader reaches
  with the keyboard". `SemanticsSweepTest` caught the omission the moment the
  widget became focusable, which is the invariant working: every focusable widget
  must say what it is. The *name* is the application's, through
  `Input.accessibleName()`, because the toolkit knows only that something was
  drawn.
- **`Canvas` gained a record component**, so its canonical constructor changed.
  The two existing shapes — `new Canvas(painter)` and
  `new Canvas(painter, attributes)` — are kept.
- **`PointerEvent` gained `content()`**, which every widget now carries and
  almost none reads. It is four floats set per handler beside `local()`, which the
  router was already computing.
- **Markup still names neither a painter nor an input.** Both are Java, and the
  registry indirection a document would need is filed rather than guessed at
  (ADR-0043).
- **In-canvas text *editing* is still G6.** `Input.onText` delivers committed
  text; a caret, a selection and preedit are a different piece of work.

## Alternatives considered

- **Fluent `.onPointerDown(…)` per kind**, as G3 sketched. A canvas tool is a
  state machine over a *sequence* of events — press, drag, release — and five
  callbacks that have to share state between them is five closures over the same
  mutable object. One method and a `switch` on `kind()` is what a tool actually
  writes.
- **A `CanvasPointerEvent` in canvas coordinates.** A second vocabulary over the
  same facts, and it would have had to grow a field every time `PointerEvent`
  did.
- **Report positions in border-box coordinates and document the offset.** The
  exact surprise ADR-0193 refused for painting, reintroduced for input.
- **Make the canvas's hit region its content box.** Then a click on the padding
  would miss the canvas entirely — but the canvas's *background* is painted on the
  border box, so the pointer would fall through something the user can see.
- **A sibling `input-canvas` widget.** Two widgets that must be styled alike and
  kept alike, so that one of them can be the one that listens.
