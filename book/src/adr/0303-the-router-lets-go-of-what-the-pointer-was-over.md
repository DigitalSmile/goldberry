# 303. The router lets go of what the pointer was over

Date: 2026-09-13

## Status

Accepted. Extends [ADR-0180](0180-the-keyboard-goes-back-where-it-was.md)'s
rule from the keyboard to the pointer, and closes the hole that left a tooltip
open over content that no longer existed.

## Context

[ADR-0180](0180-the-keyboard-goes-back-where-it-was.md) states one rule and
gives it a hook: **the router never holds an element that is not in the tree.**
`refocus()` enforces it for `focused`, runs once a frame from `updateRegions`,
and its own record explains why it had to exist — `Element.unmount` tells the
element tree and nothing else, so a router whose focused element was inside a
closing dialog went on holding an unmounted element.

`hovered` had the identical hole. Nothing enforced the rule for it, because
`updateHover` is the only thing that ever clears the field or tells
`onPointingChanged` anything, and `updateHover` runs on **pointer motion**.

The frame hook did call into the hover state — `restate()`, added by
[ADR-0237](0237-the-pointer-state-follows-the-frame.md) — and it is worth reading what
that method says about itself, because it is exactly right and exactly not this:

> **No `ENTERED` or `EXITED` is emitted**, deliberately. Nothing entered or
> exited anything: the pointer has not moved and the element under it is the one
> that was there.

Both clauses of that second sentence have to hold, and after a rebuild the
second one does not. The element under the pointer is **gone**. So the sequence
was:

1. The pointer stops over a button. `updateHover` fires, `hovered` is the button,
   the launcher starts its tooltip timer, the tooltip opens.
2. The user clicks. The button's handler switches a tab, closes a dialog, deletes
   the row — anything that rebuilds.
3. The tree flushes, the frame paints, `updateRegions` runs. `refocus()` puts the
   keyboard somewhere sensible. `restate()` re-asserts `:hover` on a chain of
   unmounted elements and says nothing to anybody. `hovered` still points at the
   dead button.
4. `Launcher.pointingChanged` — the only caller of `hideTooltip` — is never
   reached, because nothing called `notifyPointing`.

The tooltip stayed up, anchored to a rectangle nothing paints any more, until the
user moved the mouse. On a keyboard-driven step, or a pointer resting still while
reading, that is indefinitely.

The launcher could have defended itself — `showTooltip` already checks
`isMounted` before opening one — but a per-frame "is my anchor still there?" in
`Launcher` would be the second copy of a question the router is the one that can
answer, and the next thing to watch a hover would need a third.

## Decision

**`rehover()`, `refocus()`'s twin, called from the same frame hook.**

When the hovered element is no longer mounted, the router re-resolves what the
pointer is over against the regions of the frame **just painted**, and routes it
through `updateHover` — so `:hover` moves, `ENTERED` and `EXITED` are emitted,
and `onPointingChanged` listeners are told. A pointer that is not in the window
at all (`pointerX` is `NaN`) drops the hover rather than hit-testing a position
that means nothing.

`restate()` is untouched and keeps its rule. The two are answering different
questions on the same frame: `restate` asks what a control should *look* like
when the tree it is in stood still, and `rehover` asks who the pointer is over
when it did not.

**Only when the element is gone.** The guard is one `isMounted` read on the frame
path, and the hit test happens only in the frame where something really was
unmounted.

## Consequences

**A tooltip closes when the thing it describes goes away**, which is the
user-visible fix and the reason this was found.

**`:hover` is correct a frame after a rebuild rather than at the next mouse
move.** A button that replaced the one under the pointer no longer inherits the
hover wash of its predecessor.

**`ENTERED` and `EXITED` now arrive from a frame rather than only from an
event.** Any `Handles` widget that assumed a pointer event means the pointer
moved is now wrong — though it was already wrong, because the same pair was
already emitted by the *next* move after a rebuild. This makes it prompt, not
new.

**`EXITED` is delivered to widgets that have been unmounted.** Also not new, for
the same reason, and safe by construction: `Element.markNeedsBuild` is a no-op on
an unmounted element, so a handler that calls `setState` in response does
nothing.

**The obvious next question is deliberately not answered here**: should hover
follow content that *scrolls* under a still pointer, where nothing unmounts at
all? That is a real behaviour with its own costs — a hit test every frame, and
`ENTERED`/`EXITED` pairs for a pointer that has not moved — and it is a separate
decision. `RehoverTest` pins the current answer so that changing it is a choice
somebody makes rather than a side effect.

## Alternatives considered

**Make `restate()` emit the events.** Rejected, and it is the smallest diff: it
would make the method's own documented contract false for the case where it is
currently right, which is the common one. Two questions, two methods.

**Re-hit-test on every frame, unconditionally.** Rejected *for now* — see above.
It subsumes this fix and adds behaviour that has not been asked for, and mixing
"stop holding a dead element" with "hover follows moving content" would land both
under a bug report about a tooltip.

**Have `Launcher` check its tooltip anchor each frame.** Rejected: the launcher
would be asking a question only the router can answer, and the router would still
be holding an unmounted element for everything else that reads `hovered()` —
`openContextMenu(x, y)` among them, which would open a menu for a dead node.

**Have `Element.unmount` tell the router.** Rejected for the reason ADR-0180 gave
when it rejected the same idea for focus: the element tree does not know about
the router, must not, and a frame-late answer is not a compromise — nothing can
move the pointer between a tree flushing and the frame it produces.
