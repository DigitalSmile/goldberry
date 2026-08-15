# ADR-0024: A repaint must wake the loop

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §5, [ADR-0019](0019-the-backend-spis-first-cut.md), [ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md), [ADR-0022](0022-window-is-the-front-door.md)

## Context

Resizing the showcase window was visibly poor: the contents lagged the pointer,
and freshly uncovered areas showed black until the window caught up.

Four causes, all in the frame path, and the first one dominated.

**A frame request did not wake the loop.** `requestFrame()` set a flag. The loop
was parked in `SDL_WaitEventTimeout` with a one-second idle timeout, and nothing
told it the flag had changed — so a repaint asked for by an event handler was
drawn when the next platform event happened to arrive, or a second later. The
showcase's own log showed it plainly: frames 1→2 took 96 ms, frame 3 took 1004.

**The pump collected frame requests before dispatching events.** Every repaint
that matters is asked for by a handler — a resize, an expose and a scale change
all end in `repaint()` — so those requests were always collected on the *next*
pass, one pump late even once the loop was awake.

**Every resize event threw away the frame buffer.** `handleResize` set the cached
buffer to null, so a multi-megabyte allocation happened per resize event, which
during a drag is per pointer motion.

**Filling the frame was per-pixel.** `Frame.fill` wrote one `putInt` per pixel:
over two million calls per frame at 1080p, on the UI thread, before anything
could be presented.

And one hazard that only appears under fast resizing: the window can change size
between the frame's dimensions being read and the frame reaching SDL. The backend
refuses a frame that no longer matches its surface — correctly, since a
mismatched blit is corruption — but that exception ended the event loop.

## Decision

`requestFrame()` wakes the loop, once per outstanding request. On `sdl3` that is
`SDL_PushEvent` of a user event, which is the mechanism `wakeup()` already uses
and the one thing SDL's queue permits from any thread. The guard matters: without
it, ten `repaint()` calls in a batch put ten events on the queue.

The pump dispatches platform events **first**, then collects frame requests and
dispatches those. A resize handled in a pump produces its frame in the same pump.
Requests made while handling a `FrameDue` are deliberately left for the next one —
draining until empty here would let a self-scheduling animation hold the loop and
starve input.

The frame buffer is not dropped on resize. `paint()` already reallocates when the
size no longer matches, which is the same test one step later and once per
*actual* size change rather than once per event.

`Frame` fills by building one row and copying it down the rectangle, at memory
speed rather than per pixel.

A frame refused because the window changed size underneath it is a **dropped
frame**: `Window.paint` checks whether the size really did move, logs it at debug,
and asks for another. Anything else is rethrown.

## Alternatives considered

**Shorten the idle timeout.** A 16 ms heartbeat would have hidden the latency and
made every idle application wake sixty times a second to do nothing — the exact
cost the request-driven design exists to avoid. It also would not have fixed the
ordering problem, only made it less visible.

**Draw on a timer during resize.** Some toolkits run a repaint clock while a
resize gesture is in progress. It is simpler than getting the event path right and
it paints frames nobody asked for; with the wakeup in place the event path is
already prompt.

**Let `present()` accept a mismatched frame and scale or clip it.** It would
remove the dropped-frame case entirely. It would also mean the toolkit silently
presenting a frame at the wrong size, which is a worse thing to be able to do than
to skip a frame during a drag.

**Keep the per-pixel fill and wait for Blend2D.** `Frame` is a placeholder and
Blend2D replaces it in M1, so optimising it is work with a short life. It is
twenty lines, it removed a visible stall today, and the row-copy shape is what the
Blend2D path will want anyway.

## Addendum, same day: the copy nobody needed

The latency fixes above made the frame *rate* right and the resize still felt
poor, so the next step was measurement rather than more reasoning. With
`-Dgoldberry.log.level=TRACE` the frame path reports its own stages, and at 1080p
they were:

| stage | cost |
|---|---|
| paint (fill 8.3 MB) | 2–4 ms |
| copy (our buffer → SDL's surface) | 2–5 ms |
| update (SDL → compositor) | 3–8 ms |

Three full-frame copies per frame, and the middle one existed only because the SPI
said the toolkit owns the buffer and the backend copies it.

So `BackendWindow.acquireFrame()` was added: the backend lends its own buffer when
it has one, the toolkit paints directly into it, and passing that same buffer back
to `present` skips the copy. `headless` returns empty and nothing changes for it;
`sdl3` returns `SDL_GetWindowSurface`'s memory as a `ByteBuffer`, which keeps the
`MemorySegment` inside `:natives` where §3.1 wants it.

Measured after: present 2.6–5 ms, total frame 4.3–9.6 ms against 10–14 ms before.
Two things did **not** help and are recorded so nobody tries them again: making
the frame buffer direct rather than heap, and collapsing the row-by-row copy into
one bulk copy when the strides match. Both are correct and neither moved the
number, because the cost was never the memcpy — it was doing it at all.

What remains is SDL's own upload to the compositor, which the surface API does not
let us avoid.

## Consequences

Repaints are prompt: frame-to-frame is 2–3 ms in the showcase where it was up to
a second, and a resize keeps up.

A pending frame request now guarantees the next pump returns promptly, whatever
the backend's mechanism. `HeadlessBackendTest` asserts exactly that, phrased as
the observable rule rather than as either implementation, so `sdl3`'s wakeup and
`headless`'s queued event are both covered by it.

`requestFrame` now has a side effect on the event queue. It is idempotent while a
request is outstanding, but it is no longer a pure flag set, and a backend
implementing the SPI has to know that prompt delivery is part of the contract.

Live resize is still not smooth everywhere. On Windows and macOS the platform runs
a modal loop during a resize gesture and SDL does not return from event pumping
until it ends, so frames stop until the drag does. Fixing that needs
`SDL_AddEventWatch` to draw from inside SDL's own callback, which is a different
shape of frame loop and waits for a renderer worth driving from it. On Wayland and
X11 — where this was reported — the event path is enough.
