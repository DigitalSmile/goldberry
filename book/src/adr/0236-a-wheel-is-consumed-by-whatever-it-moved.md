# 236. A wheel is consumed by whatever it moved

Date: 2026-09-05

## Status

Accepted. Closes the two `TODO.md` entries opened by
[ADR-0089](0089-a-knobs-gesture-is-a-rate.md) — "a knob inside a scroll view is
still untested, though both now exist" and "`Kind.WHEEL` had exactly one
consumer, and it showed" — and opens one, recorded under **Consequences**.

## Context

`knob` was the first widget in the toolkit to *handle* a wheel, and for a long
time it was the only one. ADR-0089 wrote down what that cost:

> `Knob.wheel` consumes unconditionally, which is the safe half of that pair and
> the wrong half if the other one turns out to matter.

The other half is `scroll`, which arrived in
[ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md) and had
already answered the same question for itself, in `ScrollViewport.onPointer`:

> Returns whether anything actually moved — which is what the caller turns into
> consuming the event, and therefore what decides whether an ancestor scroller
> gets a turn.

So there were two rules in the toolkit for the same event. A viewport consumed
what it moved; a knob consumed everything it was handed. With nothing above a
knob for an unconsumed wheel to reach, the difference had never shown — which is
exactly why the entry stayed open rather than being closed as theoretical.

Put a knob in a list and it shows immediately. A knob pinned at its maximum sits
in a scrolling column swallowing every upward scroll: the list stops dead under
the pointer, and the only way past is to move the pointer off the control. No
desktop behaves that way, and the toolkit already knew it shouldn't — it had
written the rule down next door.

## Decision

**What is consumed is what moved.** `Knob.wheel` computes the value it would ask
for, compares it against what the user can currently see, and returns without
consuming when they are the same. `ScrollViewport`'s rule, applied to a second
widget rather than restated as a new one.

Three details decide whether it is the right rule or merely a plausible one:

- **The comparison is against what `ask` would pass on**, not against the raw
  arithmetic. A stepped knob two units from its end on a grid of five still moves
  those two: `snap(clamp(98 + 5))` is 100, which differs from 98, so the wheel
  moved something and is consumed. Comparing the raw 103 against the maximum
  would have called it "past the end" and thrown away the last part-step.
- **Only the direction with nowhere to go chains.** A knob at its maximum still
  takes a wheel that turns it *down*, so a control being used does not let the
  list lurch out from under it halfway through.
- **A knob nobody is listening to is not a place a scroll stops.** The same
  question asked of the wiring instead of the range: `disabled`, or a null
  `onChange`, means the value cannot change, so the event is not consumed. The
  router already refuses input to a disabled subtree; repeating the check here is
  what makes *not consuming* the answer rather than merely doing nothing.

## Alternatives considered

- **Leave it, and let the application not put knobs in lists.** This is the
  status quo and it is a constraint the document never stated. §2.4 gives
  chaining as the toolkit's behaviour for a wheel, and a control that opts out of
  it silently is worse than one that never chained at all.
- **Consume whenever the pointer is over the knob, and let `scroll` look through
  it.** It inverts the ownership: the ancestor would have to know which
  descendants "really" wanted the event. Chaining is a bubble, and a bubble is
  the child deciding.
- **A `chainsWheel()` on `Handles`,** so a widget declares the policy rather than
  deriving it. Two implementors and both would answer the same way. The rule "you
  consumed it if you moved" needs no interface, because it is a fact about the
  event rather than a property of the widget.
- **Making `ask` return whether it asked**, and consuming on that. It is tidier
  in `wheel` and wrong for the drag: a drag that runs past the end goes on
  reporting the clamped value every frame, which is the behaviour the slider and
  the knob have always had, and changing it to close this would be a second
  decision smuggled in under the first.
- **Accumulating unspent wheel across events**, so three lines into a knob one
  step from its end spends one and passes two on. There is no state for a partly
  spent event and no toolkit hands one on in halves.

## Consequences

- **`KnobChainingTest` is new**, and is the first test in the catalog to drive a
  wheel through a **real bubble between two widgets**. Four cases, through the
  real router against painted regions: the knob takes its own wheel without
  moving the list; the knob at its maximum lets an upward scroll through; the
  same knob still turns downward; and the minimum chains the other way, which is
  the pair of signs the maximum case cannot check on its own.
- **The list is scrolled off its top before every case**, and this is
  load-bearing rather than tidy. The direction a knob at its maximum rejects is
  the one that scrolls a list *up* — so against a list left at its top the wheel
  would have been refused by the *viewport's* own edge rule, and the test would
  have passed before the fix for a reason that had nothing to do with it.
- **Three of the new assertions fail against the old code**, which was checked by
  neutralising the range comparison and re-running: the two chaining cases and
  `KnobTest`'s unit-level "a wheel past the end is left for an ancestor".
- **A disabled control still swallows a wheel outright, and it is not this
  widget's doing.** `PointerRouter.dispatch` returns before the chain is built
  when the target is in a disabled subtree, so an ancestor `scroll` never gets a
  turn — measured, not deduced: a disabled knob in a scrolling column stops the
  list dead. That cut is ADR-0059's and it is right for a click, where bubbling
  past a disabled button to the row underneath would activate something the user
  did not aim at. A wheel is the kind of event where it is wrong, and *"is the
  disabled cut per-event-kind"* is a decision about the router rather than about
  `knob`. It is recorded in `TODO.md` rather than answered here.
- **`Knob` gained one private predicate, `moves`**, and no public surface. The
  markup, the bindings and the keyboard are untouched.
