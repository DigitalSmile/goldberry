# 403. The events a failed handler never saw wait for the next pump

Date: 2026-09-18

## Status

Accepted. Answers the `EventSink` row of §6 of the whole-tree review recorded in
`docs/review-2026-09-18.md`, and records the decisions behind C8, C9, C17 and
C18 alongside it.

## Context

`EventSink`'s javadoc has always said:

> Throwing propagates out of `pumpEvents` — the backend is mid-drain and has no
> way to make sense of a failure here, so it does not try. Events already
> delivered stay delivered; **the rest wait for the next pump.**

Neither backend did that. `HeadlessBackend.pumpEvents` drained its queue into a
batch and lost the tail when the sink threw; `Sdl3Backend` had already pulled its
events out of SDL's queue and dropped them the same way. The test named "a
throwing sink propagates **and leaves the rest queued**" asserted only the throw,
so nothing noticed.

Two readings were available. Either the contract is aspirational and should be
rewritten to describe what happens, or the code is three lines per backend away
from the promise it has been making.

The argument that looks like it favours rewriting — *the SDL events have already
been pulled out of the platform queue* — cuts the other way on inspection. It
does not show the events are unrecoverable; it shows the **backend is the only
place they can wait**, because there is nowhere to put them back.

And what a dropped tail costs is not recoverable further up. The events behind a
throw are disproportionately the ones that *end* something: a pointer release
that leaves a button held, a key release that leaves a modifier stuck, a
`FileDropCompleted` that leaves a drag open. Nothing above the SPI can synthesize
those — the router cannot know a release it was never told about happened.

## Decision

**The code moves to the contract.**

- `Sdl3Backend` keeps an `undelivered` list and delivers through one
  `deliver(sink, events)` used by the pump, by `emitDueFrames` and by the resize
  watch. `HeadlessBackend`'s queue became a `Deque` and a `requeue` puts the tail
  back at the **front** — ahead of anything the failing handler posted on its way
  out, because those events happened later.
- **The event that threw is not redelivered.** The sink saw it; re-offering it
  would fail on every pump for ever.
- Carry-over is cleared in `close()` and pruned in `forget(window)`, so a dead
  window's events do not outlive it.
- `EventSink`'s doc now says what it costs a backend to keep the promise, that
  the throwing event is consumed, and that **throwing is not a way to decline an
  event**.

Three smaller decisions landed with it, each recorded at its own code:

- **A wait shorter than a millisecond still waits** (C8). `(int) wait.toMillis()`
  truncated every sub-millisecond remainder to `0`, which the branch below read
  as "poll" — so the pump returned having delivered nothing and `EventLoop.run`
  came straight back round, spinning for the last millisecond of every frame
  interval whenever the display rate was adopted. `waitMillis` **ceils**, which
  also removes the extra pump a 16.6 → 16 truncation buys, and caps at
  `Integer.MAX_VALUE` because SDL reads a negative as "wait for ever".
- **A directory that will not open is not knowledge** (C9). `listDirectory`
  returned `Optional.empty()` for "not a directory" and threw
  `UncheckedIOException` for "a directory I cannot read" — two ways of not
  knowing, one of them fatal, from a lister called in the `Sdl3Backend`
  constructor for a **cosmetic** diagnostic. The constructor catches only
  `SdlException` and `UnsatisfiedLinkError`, so the throw left SDL initialised
  and the event buffer unclosed. Both cases are `Optional.empty()` now, which the
  comment beside the throw already claimed.
- **The flag `close` sets is the flag `wakeup` reads** (C17). `closed` is
  `volatile`. It has **two** off-thread readers rather than the one the review
  names: `wakeup()`, and `drawDuringModalLoop`, which the class's own doc says
  runs on whichever thread pushed the event.

## Alternatives considered

- **Rewriting the contract to say the remainder is dropped.** Honest, cheap, and
  wrong: it would document a hole an application cannot patch. A toolkit that
  tells a widget "you may or may not hear the release" has made every gesture
  handler defensive for the toolkit's convenience.
- **Catching and logging inside the pump.** It removes the propagation the
  contract also promises, and a swallowed handler failure is the bug that takes
  longest to find.
- **A `synchronized` block for C17 instead of `volatile`.** It orders more than
  is needed and costs more; the residual overlap — a `wakeup()` already past its
  read when `close()` runs — is not a visibility question and no lock on this
  flag would order it either. It is harmless because `close()` sets the flag
  first and reaches `Sdl.quit()` only after taking down the watch, the windows,
  the trays and the cursors, so a push that slips through lands on a live queue.
  That is written on the field rather than claimed to be a race that is gone.

## Consequences

- A handler that throws no longer costs the events behind it. `Sdl3EventPathTest`
  is the strong test: the second pump pushes nothing onto SDL, so anything that
  arrives can only have been held by the backend.
- Both backends now carry a small amount of state they did not have, and it has
  to be cleared in two places — `close()` and `forget(window)`. That is the price,
  and it is the reason this is a record rather than a commit message.
- **What could not be tested here**, stated so the next reader does not assume
  otherwise: this machine has no display server, so `Sdl3EventPathTest` builds a
  real `Sdl3Backend` under SDL's `dummy` driver — which covers C17, C18 and the
  SDL half of the sink contract against shipping code. Out of reach: the C8 busy
  loop end to end (the dummy driver reports no display rate, and "did it spin?"
  is a stopwatch question, so the fix is pinned as arithmetic); the actual
  ADR-0211 failure, a macOS popup's event in its owner's space; anything
  Wayland or libdecor, so C9 is pinned at the lister rather than through
  `diagnose`; and the C17 interleaving itself, which is a race no test can lose
  on demand.
