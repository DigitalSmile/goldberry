# ADR-0570: A session's advance fires what is due, then steps to each later timer

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:** the Gwent clone's issue list (GB-014)

## Context

`Session.advance(by)` steps a virtual clock to each pending timer in turn,
with a frame after each, so a timer's action runs at its own time and what it
schedules runs at its time too. The loop stopped as soon as the earliest
pending timer was due at or before the clock, reasoning that such a timer is
a chain that re-arms itself, and that stepping to it again would never end.

That test caught more than chains. `Session.frame()` builds and lays out the
tree, then paints, and fires no timers after the paint. A build in that frame
that asks for "next turn" with `host.after(Duration.ZERO, …)` leaves a timer
due at the clock. The next `advance` then stopped at once, jumped to its
target, and fired everything at the end. The downstream's match screen
scheduled its opening for 1 ms after the first build. After an 8 s advance
the opening had fired at 8,000 ms, and the steps it scheduled had not run.
The test had to step 5 ms first.

## Decision

**`advance` settles first, then steps to each timer due strictly after the
clock.** The settle fires whatever is due at the current time, a chain
included, bounded by the settle's own turns as it always was. The loop then
asks `TimerQueue.nextDueMillisAfter(now)` for the earliest pending timer due
**after** the clock. A timer due at or before it is never a step, so a chain
that re-arms itself with no delay fires once per step and does not hold the
clock back, and it is not a reason to stop stepping either.

`TimerQueue` gains `nextDueMillisAfter(double)`. `nextDueMillis()` stays.

## Alternatives considered

- **Firing due timers in `frame()` after the paint.** That would change what
  a frame is: a window fires timers after it pumps events, not after it
  paints. A due timer left by a frame is normal, and `advance` should handle
  it.
- **Telling a chain from a fresh zero-delay timer** by remembering what the
  last settle armed. More state, for a distinction the strict "after the
  clock" rule makes unnecessary.

## Consequences

- A timer set by a first build fires at its time in the first advance that
  passes it, whatever else is due when the advance starts.
- `SessionTest` holds both halves: a timer due at the start no longer delays
  a 1 ms one, and a self-re-arming zero-delay chain does not stop a 20 ms
  timer firing at 20. `TimerQueueTest` covers `nextDueMillisAfter`.
- The behaviour of an advance with nothing due at its start is unchanged.
