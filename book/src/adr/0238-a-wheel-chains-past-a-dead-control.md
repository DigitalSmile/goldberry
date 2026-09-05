# 238. A wheel chains past a dead control

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0236](0236-a-wheel-is-consumed-by-whatever-it-moved.md), and narrows the cut
[ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md) put in
`PointerRouter.dispatch`.

## Context

ADR-0236 gave a knob the rule that it consumes what it moved, so a knob at the
end of its travel lets an ancestor `scroll` have the wheel. Measuring that turned
up a case the knob cannot fix for itself:

> A disabled control still swallows a wheel outright, and it is not this widget's
> doing. `PointerRouter.dispatch` returns before the chain is built when the
> target is in a disabled subtree, so an ancestor `scroll` never gets a turn —
> measured, not deduced: a disabled knob in a scrolling column stops the list
> dead.

The cut is ADR-0059's, and its reasoning is sound as far as it goes:

> One choke point for every control, present and future — the same argument that
> put the `:hover` refusal in `mark` rather than in each widget. A control's own
> `disabled` check is then a second line of defence rather than the only one, and
> a control that forgets to write it is still unavailable inside a disabled
> container.

That argument is about **the thing being aimed at**. A click on a disabled button
must not become a click on the list row holding it; ADR-0059's companion rule —
a disabled control still hit-tests, so a click cannot fall through to whatever is
behind it — is the same thought. Both are right and neither is being undone here.

**A wheel is not aimed at a control.** It is aimed at whatever scrolls, and every
platform treats it that way: a disabled `<button>` in a scrolling page, a
insensitive GTK widget in a `GtkScrolledWindow`, a disabled Qt control in a
`QScrollArea` — the wheel reaches the scroller in all three. Nobody positions a
pointer over a dead control in order to scroll; they position it over the *list*,
and the dead control happens to be under it. So "unavailable" was being read as
"opaque to a gesture that was never about it".

The existing test in `DisabledPropagationTest` could not see the difference,
which is worth writing down. Its tree is a disabled `form` holding a `button` and
**nothing above it**, so "the subtree refuses the wheel" and "the wheel is
swallowed" produce identical logs. The distinction only appears when something
live is above the dead thing.

## Decision

**The disabled cut is per event kind, and `WHEEL` is the one that chains.**

`dispatch` still refuses a press, a release and a click aimed into a disabled
subtree, exactly as before and for exactly ADR-0059's reason. For a wheel it
builds the chain and **drops the disabled prefix** instead of returning:

- **The dead subtree still handles nothing.** What changes is only who gets a
  turn afterwards. A disabled knob does not turn — the trimmed chain never
  reaches it — and a `scroll` above it scrolls.
- **The disabled elements are a prefix**, and that is a fact rather than an
  assumption. `chain` is deepest-first and `isDisabled` walks *up*, so it is true
  from the target to the outermost disabled ancestor and false at every step
  above. `dropWhile` is therefore exact, and it is one pass.
- **A wholly disabled tree still dispatches nothing.** The trimmed chain is
  empty, which is the old behaviour arrived at by the new route — and is why the
  existing test's assertion is unchanged rather than merely still passing.
- **`isInput` is untouched.** The kinds it partitions are "the user *doing*
  something", and a wheel still is one; taking `WHEEL` out of that set to get
  this would have made a second question share an answer with the first, and
  broken the hit-test guarantee that a wheel over a disabled control is not a
  wheel over whatever is painted behind it.

## Alternatives considered

- **Remove `WHEEL` from `isInput`.** One character of diff and wrong: `isInput`
  also governs whether the disabled subtree is skipped at all, so the disabled
  knob itself would start handling wheels and turning.
- **Let the widget opt in — a `chainsWheelWhenDisabled()` on `Handles`.** The
  question is not the widget's. A control does not know whether anything above it
  scrolls, and the answer is the same for every control there will ever be.
- **Dispatch to ancestors for every kind, and let each control's own `disabled`
  check refuse.** This is the cut ADR-0059 explicitly rejected — it makes a
  control that forgets the check a live control inside a disabled container — and
  it would let a click on a disabled button reach the row underneath.
- **Trim by "the outermost disabled ancestor" found in one upward walk**, rather
  than by `dropWhile` over the chain. Identical result; `isDisabled` per element
  is O(depth²) on a path that is a handful deep and only walked for a wheel over
  a disabled subtree, and the `dropWhile` says what the rule *is* rather than how
  to find it.
- **Leave it, and let applications not disable controls inside scroll views.**
  The list stopping dead under the pointer is the kind of bug that gets reported
  as "scrolling is broken", with nobody suspecting the greyed-out knob.

## Consequences

- **Three tests, two of which fail against the old code.** In
  `DisabledPropagationTest`, a live `scroll` above a disabled `form` gets the
  wheel, and a press through the same tree still stops dead — the second is what
  keeps the change from being "let everything through". In `KnobChainingTest`, a
  **disabled** knob in a real scrolling column no longer stops the list, which is
  the case that opened the entry.
- **The existing "the wheel is refused too" test is unchanged and still
  passing**, because its tree has nothing above the disabled container. That is
  the honest reading: it asserts a disabled subtree does not *handle* a wheel,
  which is still true, rather than that a wheel dies there.
- **`event.target()` is still the disabled element** for the ancestors that now
  receive the wheel. That is correct and worth stating: the target is where the
  pointer *is*, and `bounds`/`part` are re-measured per handler anyway, which is
  what a `scroll` reads.
- **The catalog gains a second line of defence it already had.** `Knob.moves`
  refuses when `disabled` (ADR-0236), so the knob would not have turned even if
  the trimmed chain had reached it. Both checks stay: ADR-0059's argument for the
  choke point is unchanged.
