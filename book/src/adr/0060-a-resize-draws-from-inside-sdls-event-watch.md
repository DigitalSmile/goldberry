# ADR-0060: A resize draws from inside SDL's event watch

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §4, §12; supersedes the open question in
  [ADR-0024](0024-a-repaint-must-wake-the-loop.md)

## Context

Windows and macOS run a **modal loop** while a window is being resized. `WM_SIZE`
arrives from inside `DefWindowProc`'s move/size loop; AppKit runs its own
event-tracking mode for the duration of the drag. In both cases the platform
takes the thread when the gesture starts and does not give it back until it ends.

Goldberry's frame loop is a `while (running)` around `SDL_WaitEventTimeout`
([ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md)). During a drag
that call does not return, so the loop does not iterate, so nothing translates the
resize, lays anything out, or presents a frame. What the user sees for the length
of the drag is the last frame drawn before they grabbed the edge — stretched,
cropped, or blank, depending on the compositor. Wayland and X11 have no such loop
and were prompt from the first day, which is exactly why this went unnoticed for
so long: it is invisible on the platform the toolkit is developed on.

The loop is not stalled in any sense we can fix from Java. It is not slow, not
blocked on a lock, and not waiting for work. It is inside SDL, which is inside the
platform, which is running its own message pump — several frames deep in C, on our
thread.

What the platform *does* keep doing inside that loop is pumping events. SDL sees
every one of them. And SDL offers `SDL_AddEventWatch`: a callback invoked as each
event arrives, from inside whatever pump is running, before the event reaches the
queue.

## Decision

**Install an event watch, and draw from inside it.** `SdlEventWatch` binds
`SDL_AddEventWatch`/`SDL_RemoveEventWatch` with an FFM upcall stub;
`Sdl3Backend` installs one at start-up and, when a `WINDOW_RESIZED` or
`WINDOW_EXPOSED` arrives, translates it, hands it to the sink that the current
`pumpEvents` published, and emits any frame that is due — all before the callback
returns to SDL, and therefore before the platform's resize loop takes the thread
back.

Four guards decide whether the callback does anything at all, and every one of
them exists because a watch is called in circumstances a pump never is:

- **Not on the UI thread.** SDL runs the watch on whichever thread pushed the
  event, and `wakeup()` pushes from background threads by design
  ([ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md)). Painting
  there would be a data race on every object the frame touches.
- **No active sink.** Between pumps there is nowhere for an event to go. The
  queue will deliver it in the ordinary way.
- **Already inside the watch.** Painting asks for the next frame, `requestFrame`
  wakes the loop, and the wakeup is a pushed event — which runs the watch again,
  on this thread, from inside the paint it would restart.
- **Not a window event.** A modal loop starves frames, not input: keys and
  pointer events are delivered by the pump as usual, and translating them twice
  would double every click.

The event still reaches SDL's queue afterwards, so the pump translates it again
when the drag ends. `Sdl3Window.resizedTo` is what keeps that from costing a
second layout pass: a resize to the size the window already has is not news, and
is reported once.

The frame pacer applies inside the watch as well as outside it. A drag that outran
the display would be spending frames nobody scans out
([ADR-0047](0047-a-frame-nobody-sees-costs-full-price.md)), and a frame held back
during a drag is emitted by the next resize event, of which there are many.

## Alternatives considered

**Leave it, and document it.** What ADR-0024 did — "waits for a renderer worth
driving from it". Two years of that reasoning would still leave a toolkit whose
windows go blank when resized on two of its three platforms, and the renderer it
was waiting for has arrived: Blend2D paints a 960×640 frame in 1.6 ms, so there is
nothing left to wait for.

**Drive the whole loop from the watch.** Make the callback the frame loop and let
`pumpEvents` become a shell. This is what a toolkit built around SDL's callback
API would do, and it is a larger change than the problem justifies: the loop would
then be re-entrant everywhere rather than in one guarded place, and every
invariant that currently holds because the loop is a loop would need restating.

**A timer thread that paints during the drag.** Rejected outright — it would paint
from a thread that is not the UI thread, which is the one rule the whole SPI is
built on, and AppKit would refuse it anyway.

**`SDL_SetEventFilter` instead of a watch.** The filter runs at the same point and
can *drop* events by returning false. That is more power than this needs, there is
one filter per process where there may be many watches, and a filter that
accidentally returns false eats input.

**Handle the resize only in the watch, and stop queueing it.** Not possible: a
watch cannot remove an event, only a filter can, and using the filter for this
would mean the drag's events never reach the queue — so a pump that ran without a
frame outstanding would never learn the window had changed size.

## Consequences

**Windows and macOS redraw while the window is being dragged.** That is the whole
point, and it is the half of this record that cannot be tested here: it needs a
human dragging a window on a platform that has a modal loop. What CI proves is
everything up to that — that the watch is installed, that SDL calls it, that a
translated event and a frame come out of it re-entrantly — because a test can push
an event from inside an event handler, which puts the callback in exactly the
situation a modal loop puts it in
([ADR-0061](0061-the-events-a-test-cannot-produce-are-pushed.md)).

**The event path is re-entrant now, in one place.** `Sdl3Backend` has two fields
it did not have — the active sink and a re-entrancy flag — and a bug in either is
a bug that only shows up during a resize on a platform with a modal loop. The
guards are cheap; the reasoning behind them is the expensive part, which is why
each one is written down next to the code as well as here.

**A resize is now reported once rather than twice.** Independently correct — SDL
sends `WINDOW_RESIZED` liberally and a re-layout to the size the window already
has is pure cost — but it is load-bearing here, so `resizedTo` cannot be "tidied
up" without live resize doing double work.

**Every event now crosses into Java twice.** Once through the watch, once through
the pump. The watch's own cost is four field reads for the events it declines,
which is nothing next to what the pump already does per event — but it is a cost
paid on every event, including the ones this exists for none of.

**A `libgoldberry` without the two new symbols still works.** The watch is
optional in the same way and for the same reason the cursors are: an artifact
built before these symbols were exported loses live resize, not the ability to
open a window.

**The upcall stub lives in a shared arena, not a confined one.** SDL calls the
watch on whichever thread pushed the event, and a confined arena's stub invoked
from another thread is a failed crossing rather than a callback that declines.
Shared arenas are more expensive to close; this one is closed once, at shutdown.
