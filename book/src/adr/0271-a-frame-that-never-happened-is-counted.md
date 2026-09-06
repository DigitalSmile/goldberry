# 271. A frame that never happened is counted by the pacer

Date: 2026-09-05

## Status

Accepted. Answers the "nothing reports a dropped frame" entry, and adds the
reading [ADR-0146](0146-a-hud-shows-where-the-frame-went.md) and
[ADR-0150](0150-a-hud-reads-itself-against-a-budget.md) had no source for.

## Context

The entry, in full:

> **Nothing reports a dropped frame.** The ring behind `hud` records frames that
> were painted, so a frame the platform refused *after* it was painted is in the
> mean and a frame the loop never reached is not. "3 late" needs the pacer's view
> as well as the painter's, and the pacer belongs to the `sdl3` backend.

Everything a `hud` shows is a mean over the frames in `FrameRing`, and a frame
gets into that ring by being painted. So the two ways a frame can fail to reach
the user are both invisible, in opposite directions:

- A frame the loop **never reached** leaves no record at all. Sixty frames at 30
  fps and sixty frames at 60 fps are both "sixty frames"; only the interval is
  different, and an interval also gets longer when the user stops touching the
  window, which §1.7 makes the ordinary idle case. So the number that would tell
  them apart is the number that cannot be read off the ring.
- A frame the platform **refused after it was painted** is in the ring as though
  somebody had seen it. `Window.paint` already catches this — the window became a
  different size while the frame was being drawn for the old one, which during a
  resize drag is ordinary rather than exotic — and logged it at `debug`.

## Decision

**One number, `FrameStats.lateFrames()`, fed by both.**

### The pacer's half

`FramePacer.missedRefreshes(pendingSince, now)` is the whole of the new
arithmetic, and it is a pure function of two stamps like everything else on that
class:

```
due  = max(pendingSince, lastFrameAt + interval)
late = (now - due) / interval        // whole intervals, floored
```

**`pendingSince` is what makes it honest.** The naive number — the gap since the
last frame, divided by the interval — says a window nobody touched for a minute
dropped three and a half thousand frames. It drew every frame it was asked for.
What counts is the span between the moment a frame *could* have been handed over
and the moment one was: the request has to already exist for a refresh to be a
refresh anybody missed.

So `Sdl3Window` stamps `framePendingSince` when a request arrives and the backend
reads it in `emitDueFrames`, before the request is consumed and before the pacer
is stamped — both of which are what the lateness is measured against.

The counter on the window is **monotonic**, like a frame count, because the
window above it keeps a ring of sixty frames and a backend has no business
knowing that.

### The painter's half

`Window.paint` increments `refusedFrames` where it already caught the refusal,
and banks it with the next frame. A frame that was refused asks for a repaint on
the line below, so there is always a next frame to bank it on.

### Banked with the frame that follows the gap

`FrameRing.late(n)` is set before `record`, in the same pending-then-consumed
shape the four stage timings already use. A gap has to be attached to *some*
frame in the ring or it ages out on a different schedule from the frames it sits
between — and then a HUD would show frames dropped during a resize that finished
long enough ago for the resize itself to have left the window.

**A window rather than a total**, therefore, like every other number on
`FrameStats` and unlike `count()`. A total since start-up only ever goes up, so a
loop that dropped four frames a minute ago would still be reporting them, and
somebody watching the HUD while they work would never see it come back to zero.

### `late` is a reading

`Reading.LATE` prints whole frames with no unit — `late 3` — and is in
`Hud.STAGES`, which is what the showcase turns on. It is not a stage and it is in
the breakdown anyway: the frames that did not happen are the one thing a
breakdown of the frames that did can never account for.

Its level is **stated rather than derived**. Every other reading is a share of a
display frame ([ADR-0153](0153-a-rate-is-counted-a-refresh-is-asked-for.md)) and a count of
frames is not a duration. Zero is fine; one is `near`, because a resize refuses a
frame that was painted for the size the window has just stopped being and that is
ordinary; more than three of the ring's sixty is `over`, which is one in twenty
and is stutter somebody can see. A threshold derived from `capacity()` was the
first draft and is wrong for the reason that method documents: a source that
keeps no window reports zero, so one dropped frame would be an alarm for every
fixed source there is.

## Alternatives considered

- **Counting the gap between painted frames.** Free, already in the ring, and it
  reports an idle window as a catastrophe. §1.7's idle loop is the common case,
  not the exception.
- **A monotonic total.** Simpler, and it never goes back down — which for a
  diagnostic somebody watches while they work is the difference between "this is
  happening now" and "this happened".
- **Two readings, one per source.** They are two ways for the same thing to
  happen, and a reader wants the one number. The javadoc on `lateFrames()` says
  which two, for whoever needs to know which half moved.
- **Leaving the refusal at `debug`.** It is the half that was already known and
  already invisible: a log line is not something anybody is watching during a
  resize drag.

## Consequences

- **`hud readings="stages"` reports the frames nobody saw**, and the over-budget
  golden now photographs `late 7` in red beside the paint time that caused it —
  which is the pairing the image is for: 22 fps on a 60 Hz display *is* seven
  frames in sixty going missing, and nothing else on the plate could say so.
- **Two golden images moved**, both HUD plates, both by one row.
- **`BackendWindow.lateFrames()` defaults to zero**, so a backend with no display
  under it reports "nothing measured" in the same voice `refreshRate` does. The
  headless backend does not pace and never claims a dropped frame.
- **The pacer is the only thing that can answer this**, which is why the number
  comes up through the SPI rather than being computed in `Window`: the request's
  arrival and the display's interval are both the backend's, and neither is
  visible from above it.
