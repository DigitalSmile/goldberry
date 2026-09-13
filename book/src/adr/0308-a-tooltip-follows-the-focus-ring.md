# 308. A tooltip follows the focus ring

Date: 2026-09-13

## Status

Accepted. The second half of
[ADR-0303](0303-the-router-lets-go-of-what-the-pointer-was-over.md), which fixed a
tooltip that outlived the element it was anchored to and left this one standing:
a tooltip that outlives the **pointer**.

## Context

Click a button that has a tooltip, move the pointer off it, and the tooltip stays.

ADR-0303 looked like it had covered this. It had not, and the two are genuinely
different: that one was about the anchor being **unmounted** and nothing telling
the tooltip; this one is about a button that is still there, still mounted, and
still reported as the tooltip's target after the pointer has gone.

The cause is one line, and it is a rule that reads correctly:

```java
private Element tooltipTarget() {
    var hovered = withTooltip(router.hovered());
    return hovered != null ? hovered : withTooltip(router.focused());
}
```

§7 asks for a tooltip "on hover *and on keyboard focus*", so a fallback to the
focused node is exactly right. What the code does not notice is that **a click
focuses things**. So:

1. The pointer rests on the button; `hovered` is the button; the tooltip opens.
2. The user clicks. The router focuses the button — `fromKeyboard = false`.
3. The pointer leaves. `hovered` goes null, so the fallback runs and answers the
   focused node, **which is still the button**.
4. `pointingChanged` compares the target with `tooltipOwner`, finds them equal,
   and returns early. Nothing hides anything.

The tooltip then sits over the window until something else takes the focus. On a
toolbar, where the natural gesture is click-then-move-on, that is most of the
time.

## Decision

**The fallback asks for keyboard focus, not focus.**

```java
return router.focusedFromKeyboard() ? withTooltip(router.focused()) : null;
```

`PointerRouter.focusedFromKeyboard()` is new and exposes a field the router has
always kept: the same one `:focus-visible` is mirrored from, and the same
distinction [ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md)
drew for the focus ring — focus that arrived by pointer is a side effect of the
click rather than a statement about where the user is working. A control clicked
with a mouse is focused and draws no ring; it should not hold a tooltip open
either.

The one-sentence version, which is also what the code now says: **a tooltip
follows the focus ring.**

It answers false when nothing is focused, so the call site needs no null check —
"the keyboard is on this" is false when the keyboard is on nothing.

## Consequences

**A click no longer pins a tooltip.** The reported behaviour, gone.

**Keyboard focus still opens one**, which is the half this fix is one wrong
predicate away from destroying, and is why there is a second test for it: Tab to
a control with nothing hovered, and the tooltip appears.

**A tooltip does not reappear when the pointer leaves a mouse-focused control.**
Before this, moving the pointer off a clicked button and then off the window
would leave the tooltip up; now it closes and stays closed until the pointer
comes back or the keyboard arrives. That is a behaviour change beyond the bug —
and it is the same rule, because both cases were the fallback answering for a
focus the mouse had put there.

**`focusedFromKeyboard()` is public API on the router.** It is the third question
about pointing the router answers — `hovered()`, `focused()`, and now how focus
arrived — and anything else that wants to distinguish "the user is working here"
from "the user clicked here" can ask it.

**A press still does not dismiss a tooltip while the pointer stays on the
control**, which is `showTooltip`'s existing note and is unchanged: a press that
closed it would close it in the same gesture that opened whatever was clicked.

## Alternatives considered

**Hide the tooltip on press.** Rejected, and it is what most toolkits do. It
would have fixed the report and broken the case `showTooltip` already documents —
a click on a control whose tooltip is open, with the pointer staying put, should
not flash the tooltip away and back. It also treats the symptom: the target would
still be wrong, so `openContextMenu` and anything else reading the same fallback
would still be answering for a mouse focus.

**Clear the focus when the pointer leaves.** Rejected outright: focus is not the
pointer's to give up, and a control would lose the keyboard because a mouse
wandered off it.

**Read `:focus-visible` off the element instead of asking the router.** Rejected
as a proxy for the fact rather than the fact. The pseudo-class is the cascade's
mirror of the router's state, it is skipped for unmounted and disabled nodes, and
a launcher that read a *styling* flag to decide *behaviour* would be one
stylesheet change away from a bug nobody could find. The router already answers
`hovered()` and `focused()`; how focus arrived is the same kind of question.

**Compare `tooltipOwner` by more than identity in `pointingChanged`.** Rejected:
the early return is correct — the target really had not changed. The defect was
in what "the target" meant.
