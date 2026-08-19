# 122. A `setState` asks for a frame

Date: 2026-08-19

## Status

Accepted. Fixes a gap left by
[ADR-0052](0052-state-is-a-plain-object-and-setstate-defers.md) and
[ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md) between them.

## Context

Reported as "the scroll starts working on the second or third turn of the
wheel". It is not a scroll bug and it is not about the wheel.

Two rules met and left a hole between them:

- §1.7, through ADR-0067: **the frame loop is idle when nothing is animating.** A
  window paints when something asks it to, and not otherwise.
- ADR-0052: **`setState` defers.** It marks its element dirty and returns; the
  tree flushes once per frame.

Nothing connected them. `Window.handlePointerWheel` did not repaint at all, and
every other input handler called `repaintIfRestyled`, which asks the router
whether a *pseudo-class* changed — a question about `:hover` and `:focus`, and
not about widget state. So a widget that changed its own state in a handler sat
there until some unrelated event caused a frame, and then showed the change one
interaction late. A scroll view made it obvious because scrolling produces a
stream of events: each wheel appeared to do nothing and the next appeared to do
the previous one's work.

Every stateful widget in the catalog had this. It went unnoticed because most of
them change a pseudo-class in the same gesture — a button that is pressed is also
`:active`, so the restyle repaint covered for the state change. A scroll view
changes no pseudo-class at all.

## Decision

**`ElementTree` tells its window when it goes from clean to dirty, and the window
paints.**

```java
tree.onDirty(window::repaint);
```

On the transition into dirty rather than on every mark, so a handler calling
`setState` ten times asks for one frame — the same coalescing `flush` already
does one level down.

A single listener rather than a list, because there is exactly one thing that can
paint a tree, and a second would mean two windows drawing one element tree. Popup
windows get the same wiring against their own window, because a `setState` in a
menu item is as much a reason for a frame as one in the application.

### Why not repaint from `setState` itself

Because `State` has an element and an element has a tree, but a tree does not
have a window — and giving it one would put the whole windowing layer inside the
widget layer to deliver one call. The tree is the last thing that knows about the
change and the first thing the window already owns, so the listener sits exactly
where the two meet.

### Why not simply repaint after every input event

It was the tempting one-line fix, and it is wrong in the direction that matters:
it repaints on every mouse move over a window where nothing changed, which is the
idle frame loop §1.7 spends effort to have. Asking on a *state change* is both
narrower and more correct — it also catches the changes that come from a timer,
a subscription or a completed future, none of which is an input event at all.

## Consequences

The idle guarantee is intact and now means something stronger: the loop is idle
when nothing has changed, rather than when nothing is animating *and* nobody has
touched anything.

`repaintIfRestyled` stays. A pseudo-class change is a repaint reason that
involves no rebuild — hovering a button restyles it without any widget's state
moving — so the two are genuinely separate conditions and neither implies the
other.

**The bug was invisible to every test in the suite**, because a test drives
frames itself: `tree.flush(); render.update(...)` in a loop is a frame loop that
never asks whether anyone wanted one. That is the right way to test a widget and
it cannot catch this, which is an argument for the showcase being run rather than
only rendered.
