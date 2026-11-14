# ADR-0692: A pinch is a pointer event with a scale

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0687 (`Path.contains`, the other half of the same
  entry), ADR-0680 (`AUX_CLICKED`, the last kind added)

## Context

A whiteboard zooms with two fingers on a trackpad (Tessera's G-12, here
T-12). SDL 3.4 reports `SDL_EVENT_PINCH_BEGIN`, `_UPDATE` and `_END` on
macOS, Wayland and X11 (XInput2), with a scale change since the last update
and the window under the fingers. Goldberry mapped none of them, so the only
zoom was `Ctrl`+wheel.

`SDL_PinchFingerEvent` is the one input arm whose `windowID` is not at the
common offset: the scale comes first. Reading it through the accessor every
other arm uses would read the scale's bits as a window id.

## Decision

**`PointerEvent.Kind.PINCH`**, with `scale()` (the factor since the last
change; 1 for every other kind and for a pinch's start and end) and
`pinchPhase()` (`BEGAN`, `CHANGED`, `ENDED`).

- The natives map the three event types (checked against SDL's own
  constants) and bind `SDL_PinchFingerEvent` as a layout of its own,
  `Layouts.SDL_PINCH_FINGER_EVENT`, checked by the layout probe.
  `SdlEventBuffer.pinchScale()` and `pinchWindowId()` read its fields. The
  shim's table gains the struct and the constants and no function, so the
  native ABI stays 26.
- `Sdl3Backend` translates them into `BackendEvent.PointerPinch` from the
  pinch's own window field, drops one under no window of ours, and turns a
  scale that is no positive number into 1.
- The platform gives a pinch no position, so `PointerRouter.pointerPinch`
  aims it where the pointer last was, or at the captor while a button is
  held, and dispatches it like a wheel: capture, then bubble, and past a
  disabled control, because a pinch is aimed at whatever zooms.
- `HeadlessWindow.pinchPointer` queues one for a test.

## Consequences

- Windows has no pinch in SDL; a precision touchpad's zoom arrives there as
  `Ctrl`+wheel, which a zooming canvas already handles.
- A `switch` over `PointerEvent.Kind` without a default must handle `PINCH`;
  none in the toolkit had to change.
- A modal window swallows a pinch over its owner as it swallows a wheel.
- Tested by `PinchTest` (the router and the headless backend),
  `Sdl3EventPathTest.pinchReachesTheSink` (real SDL, events pushed through
  its queue) and `SdlEventBufferTest.pinch`; `LayoutVerificationTest` checks
  the layout and constants against the compiled shim.
