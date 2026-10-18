# ADR-0635: `Goldberry.run()` shuts the runtime down when it returns

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0022](0022-window-is-the-front-door.md)

## Context

ADR-0022 made `Window.open` plus `Goldberry.run()` the low-level front door, and
`run()`'s contract said it returns once the last window closes and that
`Goldberry.shutdown()` is rarely needed. That was true for the way the loop
usually ends. It was not true for `Goldberry.stop()`, which ends the loop with
windows still open. A process that returned from `run()` that way and exited
held a live Wayland surface and never called `SDL_Quit`. GNOME 46's Mutter once
crashed unwinding the connection (`wl_client_destroy` → a destroy listener → a
freed GObject). That is a compositor bug, but handing the surface back is right
anyway.

`Goldberry.launch()` (ADR-0093) has owned the runtime and shut it down itself
since then. So the open question in `TODO.md` was only about applications that
assemble the loop by hand, and they were the ones still exposed.

## Decision

**`run()` shuts the runtime down in a `finally` around the loop.** However the
loop ends (the last window closed, `stop()`, or a handler that threw), every
window still open is closed and the backend is released, as `shutdown()` does.

- **Refusals come before the `try`.** `run()` from off the UI thread, or a
  second `run()` from inside a handler while the loop is running, throws
  without touching the runtime. Shutting down there would take the running
  loop's windows from under it.
- **Only its own runtime.** The `finally` shuts down the runtime it ran, and
  does nothing if that one is already gone. A handler that called `shutdown()`
  and then opened a window has started a second runtime, which is not this
  loop's to close.
- **`shutdown()` is idempotent** and stays public, for an application that
  opens a window and never reaches `run()`.
- **The launcher runs the runtime's loop directly**, not `Goldberry.run()`. Its
  render trees and fonts have to go before the backend: a render object's Yoga
  measure callback closes over a paragraph, and the paragraph over a font.
  Releasing the backend first would undo that order.

Rejected:

- **Leaving the contract and documenting `shutdown()` harder.** The failure is
  silent until a compositor trips over it, and the only users of the hand-built
  loop are the ones least likely to read the paragraph.
- **A shutdown hook.** It runs after `main` returns on a thread that is not the
  UI thread, and every backend call must be on the UI thread.

## Consequences

- An application that returns from `run()` has nothing left open. One that
  used to reuse its windows after `run()` returned (to show them again and run
  a second loop) opens new ones instead. A window opened after `run()` starts a
  fresh runtime. Listed in the breaking changes.
- `RunShutsDownTest` covers `stop()` with two windows open, a handler that
  throws, a refused re-entry with the loop going on, a refused call from another
  thread, a window opened afterwards, and a stale runtime's shutdown. The
  existing tests that call `run()` and then read their windows still pass,
  because they read Java-side state.
- The `TODO.md` entry *The toolkit never shut SDL down, and a compositor died of
  it* moves to *Answered*.
