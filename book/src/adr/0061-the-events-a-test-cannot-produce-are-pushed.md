# ADR-0061: The events a test cannot produce are pushed

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §7, §14; answers the open question in
  [ADR-0056](0056-the-wheel-is-lines-and-the-sign-is-ours.md)

## Context

`Sdl3Backend.translate` turns SDL event numbers into `BackendEvent`s. Every branch
of it was covered except one, and the gap was recorded honestly rather than
papered over: **the `MOUSE_WHEEL` branch had never run.** Everything around it was
checked — the struct offsets by the layout probe, the buffer's readers by a
fabricated event written at those offsets, the route from a `BackendEvent` to a
widget on the headless backend — but the eight lines joining them had never
executed anywhere, because a test cannot turn a wheel, the showcase scrolls
nothing, and CI's Xvfb run therefore never reached them.

That is not a small gap in an important place. It is a small gap in the *only*
place the two halves meet: the accessor is right, the router is right, and the
line that picks `wheelPointerX()` rather than `pointerX()` is the one nothing had
ever run. Reading a wheel event's position through the motion arm's accessor
returns a plausible float — it is the vertical delta, which lands at exactly that
offset — so the failure mode is a scroll that works and a hit test that lands
somewhere else.

[ADR-0060](0060-a-resize-draws-from-inside-sdls-event-watch.md) then added a
second path with the same shape: the event watch fires from inside a resize
gesture, and no test can drag a window either.

## Decision

**Fabricate the event and push it through SDL.** `SdlEventBuffer.writeWheel` and
`writeWindowEvent` fill the buffer the way SDL fills one, at the offsets the
layout probe has already checked against the compiled C, and `SdlVideo.push` hands
it to `SDL_PushEvent`. The event joins SDL's own queue, comes back out of the
ordinary pump, and takes the shipping route — the real `translate`, the real
window lookup, the real sink — rather than a test's imitation of it.

The tests run under SDL's `dummy` video driver, so they need no display, no
compositor and no window manager, and run in CI on all three platforms.

The same mechanism reaches the event watch, and this is the part worth stating
plainly: pushing an event **from inside an event handler** puts SDL in exactly the
state a modal resize loop puts it in — a push, from the UI thread, while a pump is
already running. The watch fires, finds an active sink, translates, dispatches and
emits a frame, all before the push returns. A test can assert that the resize and
the frame arrived *re-entrantly*, which is the property that matters and the one
that separates this from "the resize arrived eventually".

## Alternatives considered

**Call `translate` directly with a hand-filled buffer.** Cheaper, and it tests the
branch — but it skips SDL entirely, so it proves nothing about whether SDL
delivers a wheel event to this pump at all, and it cannot reach the watch, which
only SDL can invoke.

**A robot: `SDL_WarpMouseInWindow` and friends.** SDL has no API to synthesize a
wheel turn; warping the pointer is the closest thing and it produces motion, not
scroll. Going below SDL to the platform's own injection APIs — `SendInput`,
`XTestFakeButtonEvent`, `CGEventPost` — means three implementations, a macOS
accessibility permission prompt, and a test that fails on a locked screen.

**Make the showcase scroll something, and check it by hand.** Worth doing when
there is a `scroll` widget to scroll (M3), and it is not a test: it moves the
evidence from "CI asserts it" to "someone remembered to try it".

**Leave the branch uncovered and keep the entry in the open questions.** The
honest option, and the one that had been taken until now. It stops being
defensible once the same technique is needed for the resize watch anyway.

## Consequences

**Two open questions close, and one narrows.** The wheel branch runs, on every
platform, on every CI run. The watch's re-entrant path runs with it. What is still
unproven is the platform half of ADR-0060 — that Windows' and macOS' modal loops
really do pump events during a drag — and that needs a human with a mouse, not a
better test.

**`SDL_PushEvent` is now part of the public binding surface**, not just the
cross-thread wakeup's private business. That is fair — synthesizing input is what
the call is for, and accessibility tooling and UI automation are the same need —
but it is API, so it is documented as API rather than as a test hook.

**A fabricated event is only as good as its offsets.** The writers use the same
`Layouts` entries the readers do, so a wrong offset writes and reads the same
wrong place and the test passes. What stops that is the layout probe, which checks
those entries against the compiled C — this decision leans on
[ADR-0010](0010-hand-written-ffm-bindings.md) rather than duplicating it.

**The tests construct a real `Sdl3Backend`, which initializes and quits SDL.**
Under `dummy` that is fast and harmless, but it is process-global state in a test
suite: the video driver and frame rate are set as system properties and restored
afterwards, and a test that forgets to restore them changes what a later test
measures.

**A pushed event runs every event watch, including Goldberry's own.** That is what
makes the re-entrancy test possible and it is also a trap: a test that pushes an
event while a pump is running is not simulating the modal loop, it *is* the same
code path, and anything the watch does — including painting — really happens.
