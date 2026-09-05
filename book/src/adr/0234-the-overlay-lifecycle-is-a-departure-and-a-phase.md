# 234. The overlay lifecycle is a departure and a phase

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0178](0178-a-stack-closes-its-own-hole.md), and settles the last of the case
for the `AnimationController` that [ADR-0081](0081-a-perpetual-loop-has-no-state.md)
first refused.

## Context

`docs/design-system.md` §1.7 specifies an overlay lifecycle — `opening → open →
closing → removed`, "the element stays mounted through `closing`, **input is
disabled the instant closing starts** (no ghost clicks), removal fires on
animation end" — and for a long time it was a specification with no subject,
because none of the widgets it describes existed.

The entry that tracked it said what to do about that:

> `opening → open → closing → removed` applies to menus, popovers, tooltips,
> dialogs and toasts, which now exist — so this is a **survey of five built
> widgets** that each arrive and depart their own way, rather than a mechanism
> nobody could write. […] What is left for the controller is the overlay sequence
> alone, which is now the whole of its case.

The survey, done:

| widget | arrives | departs | ends the departure |
|---|---|---|---|
| `dialog` | `Phase` 240ms | `Phase` 160ms | a timer, then two flags |
| `message` | `Phase` 160ms | `Phase` 100ms | a timer, then two flags |
| `toast` | `Phase` + slide | `Phase` + reflow | the stack's own queue |
| `tab` | `Phase` | `Phase` | `Phase.hasDeparted`, read in `render` |
| `collapse`, `carousel` | `Phase` | **nothing** — closing is instant | — |
| `menu`, `tooltip` | nothing — they are platform windows | nothing | — |

Two things fall out of that table and neither is what the entry expected.

**The arrival needs nothing shared.** `Phase` is already the whole of it: a
beginning stamped on the first frame that draws, a duration, and a settle. Six
widgets use it and none of them wants anything more.

**The departure was the same code twice.** `dialog` and `message` each held two
flags, a timer and six lines, and independently got the same four rules right.
That is not a coincidence worth admiring; it is a mechanism waiting to be named.

## Decision

**`Departure` — a timer and an ordering, and nothing else.** It holds the four
rules `dialog` and `message` had each written out:

1. **Idempotence.** A second press during the fade is not a second answer, which
   matters most where it costs most: two handlers on a save dialog is two saves.
2. **Two flags, not one.** `hasBegun` means *input is off*, from the instant the
   answer is given; `isOver` means *there is nothing left to draw*. Conflating
   them is why a closing dialog once stopped asking for frames on the frame it
   started closing, and therefore never faded at all (ADR-0176).
3. **Stop drawing, then tell the application** — in that order, and it matters for
   one frame: the handler usually rebuilds the tree without the overlay in it, and
   a state still mid-fade would hand a half-faded panel to whatever element the
   reconciler reused.
4. **No host, or reduced motion, means gone now.** §1.7 asks for movement to be
   removed rather than shortened, and a hundred milliseconds of nothing happening
   is not a courtesy.

**It is not an `AnimationController`.** ADR-0081 refused one for `spinner` and
indeterminate progress because a loop that never ends has nothing to remember;
ADR-0178 refused one for a toast's reflow because the interruption turned out to
be three lines of arithmetic. This is what was left of that idea after both
refusals: it drives no value, interpolates nothing, and owns no clock. It owns a
timer and an ordering, which is precisely the part that was duplicated.

**The `setState` comes in as a `Consumer<Runnable>`.** A departure is a field a
state *has*, not a base class it extends — an overlay's state holds other things
too, and `message`'s holds an arrival as well.

## Alternatives considered

- **A base `OverlayState`** the two extend. It would carry the arrival as well,
  which the table says is not shared, and it would put `dialog`'s focus handling
  and `message`'s binding in a class that has to know about both.
- **Putting the departure on `Phase`.** `Phase` is a value read from `render`,
  where a widget has a clock and nothing else; a departure needs a `Host` and a
  timer, which is a `State`'s world. Merging them would drag the window into the
  one type `collapse` and `carousel` use without ever seeing one.
- **A full `opening → open → closing → removed` state machine**, with `open` as a
  named state. `open` is "neither of the other two", and no widget in the table
  branches on it. Naming it would be a state nothing reads.
- **Leaving the duplication and closing the entry as surveyed.** Defensible — the
  survey is the deliverable the entry asked for — and it leaves the next overlay
  author to get the same four rules right a third time, from scratch, with two
  examples to copy from that do not look alike.

## Consequences

- **About forty lines leave `DialogState` and `MessageState`**, and each loses a
  pair of flags whose distinction was the subject of a previous ADR.
- **The refactor is behaviour-preserving**, which the existing dialog and message
  suites — including their golden images — say by passing unchanged. That was the
  point of doing it this way round: the tests were written first, by somebody
  fixing the bugs the rules exist for.
- **`DepartureTest` is eleven cases**, one per rule and one per way a rule was
  once broken. It also records a fixture fact worth knowing: `TestHost.tick()`
  fires whatever was scheduled whether or not it was cancelled, so a cancellation
  is asserted on the timer rather than on the handler.
- **`toast` and `tab` are not converted.** A toast's departure ends when the
  stack's queue says so and a tab's ends inside `render` through
  `Phase.hasDeparted`; neither is a timer, and forcing them through this would be
  the generalisation ADR-0092 warns about — made from two examples that already
  agree.
- **`stack` is still owed**, and it was bundled into a neighbouring entry with
  this. It is a *layout* widget where all of this is a *window* facility, and
  neither builds the other; it stays open on its own.
