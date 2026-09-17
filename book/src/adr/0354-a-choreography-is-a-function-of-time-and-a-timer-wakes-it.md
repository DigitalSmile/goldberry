# 354. A choreography is a function of time, and a timer wakes it

Date: 2026-09-17

## Status

Accepted. Uses [ADR-0348](0348-a-canvas-asks-for-its-next-frame-with-what-it-was-painted-with.md)
(G41) and sits beside [ADR-0352](0352-an-element-enters-from-its-starting-style.md)
and [ADR-0353](0353-a-stylesheet-may-name-keyframes.md).

## Context

The application that filed G41 described a floor of glazed tiles that
**settles**. Each tile drops in from 20px above, turned a few degrees, staggered
by its distance from a focus. After that, one tile every 1.3 seconds takes the
glaze of a neighbouring band. G41 gave a canvas the means. What remained was to
show a choreography built the right way, because there are two wrong ways that
both work:

- **Ask for frames for ever.** `animating(style -> true)` keeps the settle and
  the fades smooth. It also repaints a still floor sixty times a second for the
  900ms between swaps, which is most of the time.
- **Keep state per frame.** Advance each tile's position a little on every paint.
  The floor then runs at a different speed on a 144Hz panel, and a dropped frame
  becomes a slower settle, which §1.7's frame clock exists to prevent.

## Decision

**The showcase's Motion screen (`example.ui.MotionScreen`) draws the floor as a
function of the frame time. The canvas asks for frames only while something
moves, and a host timer starts each swap.**

- **`example.motion.Settle`** is the arithmetic: a pose (offset, turn, opacity)
  as a function of time since the floor started, a tile's delay and its turn. It
  uses §1.7's `ease-enter`, and opacity reaches 1 a third of the way down, so a
  tile is solid by the time it lands. 850ms, 20px, 4°, 45ms per place of distance.
- **`example.motion.TileFloor`** holds what is state: the glazes, the focus, the
  swap counter, and the two start times. **Both start times are set by the paint**
  that first sees them unset. A replay or a swap has no frame time of its own
  (a press arrives between frames, and a timer fires on the wall clock), so each
  leaves the time unset and the next frame fills it in.
- **Who asks for frames.** `Canvas.animating(style -> floor.isMoving(now, reduced))`
  is true while a tile has not landed or a glaze is within its 400ms fade, and it
  is also true while a start time is unset, because a frame is what sets it.
  **Between swaps it is false, and the loop idles.** `host.after(1.3s)` calls
  `swap()` and `setState`. The rebuild brings one frame, the frame sets the fade's
  start, and the predicate keeps frames coming for 400ms.
- **Turned tiles are rotated paths** (`example.motion.Rotated`), not a frame
  transform. `Frame.transform` states the whole matrix, and inside a canvas that
  would discard the translation that puts the canvas on screen.
- **Reduced motion.** The floor is drawn at rest, a swap is a cut, and
  `isMoving` is always false. The glaze still changes, because the change carries
  meaning (§1.7 rule 5) and only the movement is dropped.
- **Deterministic.** Which tile turns which way, and which tile each swap visits,
  come from the tile index and the swap counter, so the sequence is the same on
  every run and in every test.

The same screen has a card for each of the other two mechanisms: five swatches
breathing on a stagger with `@keyframes`, and entries added by a button that
enter from an `@starting-style`.

## Consequences

- A canvas choreography in this toolkit has three parts: a pure function of time,
  a predicate over it, and a timer for wake-ups. `TileFloorTest` checks that the
  floor asks for frames exactly until the last tile lands and exactly for the
  length of a fade.
- An offscreen render paints once, so the floor card is empty in one: the first
  paint is the settle's start. That is why the screen has no golden. The tests
  assert the moments that matter as numbers.
- `Screen.GALLERY` gains `motion`, last, so no digit shortcut moves. Every gallery
  golden changed by the one new tab in the strip.
