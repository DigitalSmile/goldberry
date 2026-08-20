# 158. A full repaint is a full upload

Date: 2026-08-20

## Status

Accepted. Fixes a hole between
[ADR-0046](0046-what-present-actually-does.md)'s damage list and
[ADR-0072](0072-a-partial-repaint-needs-a-promise.md)'s promise.

## Context

Reported from a Mac: **dragging a window edge flickered, with black areas.**

Two mechanisms were each behaving correctly.

ADR-0072 asks the backend whether the buffer it lends still holds the previous
frame. When it does not — a resize reallocates it — `canRepaintPartially()`
returns false and the painter repaints the whole frame. That is right, and it is
what the frame loop does.

ADR-0046's damage list is a separate question: which regions *changed* since the
last frame, so the platform uploads those and not the whole surface. The frame
loop reported it on every frame:

```java
if (window.canRepaintPartially()) {
    render.paint(frame, damage);
} else {
    render.paint(frame);          // everything
}
window.damaged(damage);           // ... but upload only what changed
```

So the frame after a resize was **painted in full and uploaded in part**. The
buffer was entirely correct; the platform's surface was entirely new; and the
region between the two — everything outside the damage rectangles — was whatever
the compositor had there. Black. During a live resize the surface is reallocated
on frame after frame, so it is black that flickers.

At a steady size the two questions have the same answer, because the surface
holds the last frame and "what changed" is exactly "what is not already right".
They come apart only when the surface underneath is new, which is a resize — and
every damage test, every golden and every headless run was at a fixed size.

## Decision

**A frame that could not be repainted in part is uploaded in whole**, whatever
the painter reported.

```java
window.present(target,
        damage == null || !partialRepaint
                ? List.of(DamageRect.all(frameSize))
                : damage);
```

In `Window` and not at the call site, and that is the substance of the decision
rather than a detail of it. A painter reporting which regions changed is doing
the right thing and has nothing to do differently; the window is the only thing
that knows whether the buffer it is about to present had valid contents to begin
with. Asking every painter to combine the two questions would put the same
three-line conditional into the toolkit's frame loop, into `Popup`, and into every
application that writes its own `onPaint` — and the failure mode of forgetting it
is a bug nobody sees until they own a HiDPI Mac and drag a window edge.

`damaged` keeps its meaning: what changed. Its javadoc now says the other half —
that on a full repaint it is not consulted.

## Alternatives considered

**Force the damage list to be whole after a resize, where the resize is
handled.** It fixes the reported symptom and not the rule. A reallocated buffer
is not only a resize: ADR-0072's own javadoc allows a backend to rotate between
two buffers and copy, and `canRepaintPartially()` already answers for all of
those. Fixing the resize case specifically would leave the others.

**Have `damaged` reject a partial list when the frame was full.** Louder, and
wrong for the caller: the painter's report is accurate. The clamp belongs where
the two questions are combined, not where one of them is asked.

**Always upload the whole frame.** Deletes the mechanism ADR-0046 measured at
about a millisecond a frame at 960×640, to fix a case that arises only when the
buffer changed.

## Consequences

**A resize costs a full upload per frame, deliberately.** That is what it always
should have cost, and it is the one case where a partial upload was never valid.
A steady window is unaffected, which the second of the two new tests pins.

**Two tests, and the second matters as much as the first.**
`WindowDamageTest` now asserts both halves: a frame after a resize uploads the
whole window, and a frame at a steady size uploads only the reported region. The
second is what stops this being fixed by uploading everything always.

**The reported symptom is not confirmed fixed.** This is diagnosed and reproduced
headlessly on Linux, where the cause is present and unambiguous. Whether it is
the *whole* of what a Mac shows during a live resize is not something this
repository can currently answer — macOS drives a live resize from inside a modal
run loop, and there may be a second cause layered on this one. Said here rather
than discovered later.

**The general gap stays open.** Every damage assertion is at a fixed size except
these; the same is true of the golden corpus, and ADR-0157 records the same shape
of gap for display scale. A frame-loop test that resizes is now possible, and
almost none do.
