# 401. The router tells the living, and finishes the application's pair

Date: 2026-09-18

## Status

Accepted. Answers C7 of the whole-tree review recorded in
`docs/review-2026-09-18.md`.

**Corrects [ADR-0303](0303-the-router-lets-go-of-what-the-pointer-was-over.md)**,
whose "safe by construction" no longer holds. Extends
[ADR-0317](0317-a-router-does-not-talk-to-the-dead.md) with a second
clause, and depends on [ADR-0327](0327-a-hover-is-a-node-property-not-a-menus.md)
for the half of `emit` that must keep running.

## Context

`PointerRouter.updateHover` walks the chain under the pointer and tells each
element it was entered or exited. Its `emit()` has no `isMounted()` check, where
`mark()` and `notifyFocus` both do.

ADR-0303 argued that this was safe by construction: a rehover is asked on the
next *frame*, not inside the handler that caused it, so by the time `EXITED` is
sent the tree has settled. That was true when it was written. It stopped being
true once a widget could unmount the element under the pointer and the same
dispatch could reach it — and `State.setState` on an unmounted state **throws**,
deliberately, because ADR-0317 decided that a callback outliving its widget
should be loud rather than silent. `CanvasScreen.java` calls `setState` on
`EXITED`, so the showcase contains the shape that crashes.

The obvious fix is an `isMounted()` guard around `emit`, which is what the review
proposed. It is too broad, and the suite says so: `emit` speaks to **two
audiences** in one method.

## Decision

**The router does not call a widget it has disposed. It does finish the
application's own enter/exit pair.**

- **The widget's handler is not called on an unmounted element.** There is nobody
  left to hear it: `State.dispose` has run, the bindings are closed, the subtree
  is gone. The one thing a final `EXITED` could have been for — releasing
  something the enter acquired — is what `dispose` is, and `dispose` has already
  run. Nothing is lost by not telling them, which is ADR-0317's argument
  transferred unchanged.
- **The `Attributes` hook beside it still runs.** `onPointerEnter` /
  `onPointerExit` are the *application's* half of a pair, held on a widget value
  rather than in element state, and nothing disposes them.
  [ADR-0327](0327-a-hover-is-a-node-property-not-a-menus.md) added them precisely
  so that a hover-hold timer can be cancelled from the hook rather than from two
  places, and `HoverHookTest.anUnmountedSubtreeIsToldItLostThePointer` pins it.
  Dropping the exit would leave every enter unmatched at exactly the moment the
  hook exists for. A blanket guard was written first and failed that test, which
  is how the line was found.
- **The check is read inside `emit`, per element, at the moment of telling** —
  not hoisted, not computed once per move. `updateHover` tells a chain one
  element at a time and any handler may rebuild, so one read covers both cases:
  the element unmounted by the action that moved the pointer (ADR-0303's path,
  on the next frame) and the element unmounted by a handler two steps earlier in
  the same loop.

So ADR-0317's rule — "a guard is per element, not per notification" — gains a
second clause: **per audience, too.** Two things are being told; only one of them
dies with the element.

## Alternatives considered

- **An `isMounted()` guard over the whole of `emit`.** What the review asked for.
  It breaks ADR-0327 and its test, silently, by dropping the application's exit.
- **Making `State.setState` tolerant of an unmounted state.** Rejected in
  ADR-0317 and rejected again here: the throw is the assertion that catches a
  real leak, and a toolkit that swallows it trades one visible crash for a class
  of invisible ones.
- **Sending `EXITED` before unmounting, from the reconciler.** It would make the
  contract "you always get an exit" true rather than nearly true, at the cost of
  a reconciler that knows about pointer state. The router is where hover lives.

## Consequences

- A handler that unmounts the element under the pointer no longer takes the
  window down. `RehoverTest.theDeadAreNotToldTheyExited` holds the crash with the
  showcase's own shape — a stateful panel whose leaf calls `setState` on `EXITED`
  — and `anAncestorUnmountedMidDispatchIsSkipped` holds the mid-loop case.
- The contract a widget can rely on is now precisely: **you will not be told
  anything after you are unmounted**, and an `EXITED` is not guaranteed. A widget
  that needs to release something on the way out releases it in `dispose`. That
  is written at `emit` rather than only here.
- An application hook can still be called once after the widget it was attached
  to is gone. That is the ADR-0327 contract and it is now the *only* asymmetry in
  the method, which is better than an undocumented one in both directions.
