# ADR-0693: A strip measures what a frame costs

- **Status:** Accepted
- **Date:** 2026-11-14
- **Relates to:** ADR-0691 (a frame is heard whole, and its spread is
  percentiles)

## Context

`Offscreen.strip(…).frame()` rasterizes the whole buffer on every call, even
when nothing changed: Lantern measured a 2560 × 1440 HUD at a 9 ms median with
nothing changed and 7.6 ms with its weapons bar rebuilding every frame
(Lantern's G-4, here L-4). A widget tree's frame cost could therefore not be
measured without a window, and every cost question needed a run on somebody's
screen.

The full paint is on purpose. An `Image` from `Filmstrip.frame()` is a view
over the buffer it was painted into, so each frame needs a buffer of its own,
and a new buffer has no last frame for a damaged repaint to build on.

## Decision

A fifth terminal on the builder, `Offscreen.measure(Widget)`, returns a
`MeasuringStrip`. It sits over a `Filmstrip` (the same mounted tree, renderer
and virtual clock) and differs in what it paints into and what it says:

- **One buffer for its lifetime.** `frame()` runs the frame sequence, asks the
  render tree for its damage and repaints only that, as a window does. The
  first frame is the whole buffer; a frame with nothing changed paints
  nothing.
- **The damage is cleared first.** `RenderTree.repaint(frame, damage,
  background)` replaces the region with the strip's background before
  painting it. A window's root paints an opaque background over its damage; a
  HUD measured on its own has nothing under it, and its antialiased edges
  would otherwise blend onto last frame's pixels.
- **Each frame reports.** `frame()` returns a `MeasuredFrame`: the
  `FrameRecord` of W2.1 (stages, paint, number) and the damaged rectangles,
  with `damagedPixels()` and `isQuiet()`. The raster stage runs until the
  frame has ended, so a threaded rasterizer's work is inside it.
- **The window's statistics.** `stats()` is a `FrameRing` fed the way a
  window feeds one, so means, spans and `percentile(FrameStage, p)` read the
  same. Nothing is presented, so the present readings are zero.
- **Pictures are copies.** `picture()` copies the buffer into an `Image`,
  because the next frame repaints part of it.

`Filmstrip.frame()` keeps its documented buffer per frame.

## Consequences

- A frame's cost can be measured in a test and in a headless bench. The
  measurement is of the toolkit's work only: no upload, no present.
- `RenderTree` gains `repaint`, a public method beside `paint(frame, damage)`.
- The private measuring pass in `Offscreen` is renamed `measurePass`, so it
  does not overload the public terminal.
- Tested by `MeasuringStripTest`: the first frame is whole, a still frame
  paints 0 px and leaves the buffer as it was, each damaged frame of a
  translucent dot sliding over a transparent field equals the full paint
  of a `Filmstrip` at the same clock (it goes red if the clear is left out),
  the damage is smaller than the buffer, pictures stay as they were, and the
  stages and percentiles are reported.
