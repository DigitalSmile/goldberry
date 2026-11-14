# ADR-0691: A frame is heard whole, and its spread is percentiles

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0681 (a composited canvas damages nothing), and the frame
  ring a `hud` reads

## Context

A game logging `FrameStats` every five seconds could not see a hitch: a
window read 64.6 fps with a median frame of 10.8 ms, and nothing said that
one frame in it took 50 (Lantern's G-3, here L-3). The ring kept sixty frames
privately and answered means and min/mean/max spans only. The time a
`Canvas3dRenderer` spent recording its passes was folded into the
compositor's `submit`, so a heavier scene read as a slower toolkit. The game
kept its own per-frame bookkeeping beside Goldberry's to get percentiles and
the longest frame.

## Decision

Three additions, none of which allocates on the frame path.

- **A layer stage.** `PresentTimings.layerNanos` times the GPU layers that
  rendered again (`LayerTextures.renderAll`) apart from the composite, and
  `FrameStats.layers()` is its span over the composited frames. A `hud`
  shows it as the `layers` reading; it is not in the `present` preset, so no
  existing HUD changes.
- **Percentiles.** `FrameStats.percentile(FrameStage, percent)` over the
  retained frames, by nearest rank, so the answer is always a frame that
  happened. `FrameStage` names the interval between frames, the paint and
  its four stages, and the four parts of a composited present (taken over
  composited frames alone, as their spans are). The ring sorts a copy of one
  column in an array it keeps for that, so asking inside a frame allocates
  nothing. A source with no window answers 0.
- **A frame listener.** `Window.onFrame(Consumer<FrameSample>)` returns a
  `Subscription` and is called once per presented frame, after the present.
  The listener is handed **one reused view** over the ring's newest slot,
  valid while it runs; `FrameSample.copy()` makes a `FrameRecord` to keep.
  Listeners are held in an immutable list replaced on (un)subscription, so
  the frame path walks it by index and a listener may close itself from
  inside its call. A frame the platform refused is not presented and not
  heard; the next frame's `lateRefreshes` counts it.

The ring's length stays sixty: a listener sees every frame, so a five-second
window is the application's to keep.

## Consequences

- `PresentTimings` has a sixth component; the five-argument constructor
  stays for presents that render no layer.
- `FrameStats` gains two default methods, so other implementations compile
  unchanged.
- Tested by `FrameRingTest` (layers, percentiles over a hitch and over a
  wrapped ring, the newest view), `WindowFrameListenerTest` (one call a
  frame through one view, unsubscribing from inside a call) and
  `HudTest.layerReading`.
