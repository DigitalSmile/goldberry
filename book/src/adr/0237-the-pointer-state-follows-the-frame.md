# 237. The pointer's state follows the frame, not only the pointer

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0057](0057-the-cursor-rides-on-the-painted-box.md), and corrects a claim
[ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md) left in the code.

## Context

The entry stated the gap and its fix in two sentences:

> Nothing recomputes the cursor when the tree changes under a still pointer. A
> widget that becomes disabled without the pointer moving keeps the shape it had.
> The fix is re-running `cursorAt` after each paint against the last known
> position; it is worth doing when something can actually change that way.

Something can. `controls.css` gives every disabled control `cursor: not-allowed`
in two places, and the sequence that reaches it is the ordinary one: a button
disables itself in its own press handler, or a form disables its submit as the
user types, and the pointer does not move because the person deciding whether to
click is the person holding still.

The router only ever recomputed the cursor from `pointerMoved`, so the shape was
a function of the last *motion* rather than of the last *frame*. Everything
needed to fix it was already there — `updateRegions` runs after every paint and
holds the rectangles — except a place to remember where the pointer is. The three
position fields the router had are gesture-scoped: `pressOriginX`/`Y` span a
press-to-release and are `NaN` outside one, which is exactly what makes them
useless here.

**Measuring it turned up a second half the entry did not name, and a comment that
was actively wrong.** `mark` says:

> Only *setting* is suppressed. Clearing always goes through, so a control that
> was hovered before it became disabled does not keep the state — which is a real
> sequence, because a button commonly disables itself in its own press handler
> while the pointer is still over it.

The control *does* keep the state. Clearing is not suppressed, but nothing calls
it: `updateHover` is `mark`'s only caller for `:hover`, and it returns early when
the element under the pointer has not changed. So the hover wash survived every
subsequent move *within* the control and went away only when the pointer left it
— on the exact sequence the comment names as the reason it is safe.
`docs/design-system.md` §2.1 makes that non-discretionary: a disabled control
that still lightened under the pointer would be telling the user it can be used.

Which makes it one defect rather than two. Fixing only the cursor would have left
a control drawing its hover wash while its cursor said `not-allowed` — a state
more confusing than either mistake alone.

## Decision

**The pointer's state is re-asked once per frame, against the position the
pointer is actually at.**

- **A fourth position field, `pointerX`/`pointerY`,** and it is the only one that
  outlives a gesture. Set from every entry point that carries a position — moved,
  pressed, released, wheeled — rather than from `pointerMoved` alone, so a window
  whose first event is a click is not left with nowhere to ask about.
- **`NaN` is the whole of "we do not know"**, and it means it twice: before the
  pointer has ever arrived, and after `pointerExited`, which is another window's
  pointer or none at all. `updateRegions` skips the recompute in both cases
  rather than asking `cursorAt` about a point the pointer is not at.
- **`updateCursor`'s capture freeze is reached through, not around.** A repaint
  during a drag does not thaw the shape: the recompute goes through the same
  method, which returns early while something is captured.
- **`restate()` re-asserts `:hover` and `:active`** over the hovered and pressed
  chains, and its whole implementation is `mark(…, true)` — because `mark`
  already knows the rule. It turns a set into a clear on a disabled element, so
  re-asserting what the pointer is over sets the state where the control is live
  and takes it away where it is not, in one call with no second branch.
- **No `ENTERED` or `EXITED` is emitted.** Nothing entered or exited anything:
  the pointer has not moved and the element under it is the one that was there.
  That is the same line `mark` itself draws — this is about what a control looks
  like, not about what it is told.

## Alternatives considered

- **Recompute from the widget side, when a control learns it is disabled.** It
  needs every control to know, and the states are the router's — the argument
  ADR-0059 already made for putting the `:hover` refusal in `mark` rather than in
  each widget.
- **Call `updateHover` from `updateRegions`** instead of `restate`. It is fewer
  lines and it would fire `ENTERED`/`EXITED` on a repaint, because the early
  return it depends on is keyed on the element rather than on the state. A
  tooltip opening because a list repainted is a worse bug than the one being
  fixed.
- **Recompute only when the frame's regions differ from the last.** The
  comparison costs more than the recompute: `cursorAt` is a walk of the
  rectangles under one point, and `setCursor` and `setPseudoClass` are both
  already edge-triggered, so an unchanged frame is silent without help.
- **Track the position in `Window` and pass it back in.** The router is the thing
  that already holds "where the pointer is" for hover, capture and hit testing;
  a second copy a window kept in sync would be a second thing to get wrong.
- **Fix the cursor only, and record the `:hover` half as a new entry.** It is the
  narrower reading of the entry, and it ships a control whose cursor and whose
  paint disagree. §2.1 already says what the answer is, so there was no decision
  left to defer.

## Consequences

- **This runs once per frame rather than once per motion**, which makes the
  edge-triggering in `setCursor` and `setPseudoClass` load-bearing in a way it
  was not before. A 120 Hz repaint over a still pointer is 120 comparisons and no
  platform calls; a test asserts exactly that.
- **Eight cases in `CursorTest`, under a new group.** Five for the cursor — the
  repaint that recomputes, the unchanged frame that is silent, the frame before
  the pointer has arrived, the frame after it has left, and the drag that stays
  frozen — and three for the pseudo-classes: `:hover` lost on disabling, `:hover`
  returned on re-enabling, and `:active` lost mid-press. Three of them were
  checked against the old code and fail on it.
- **`Switchable` is a new test widget**, because a record cannot be disabled
  after the tree holding it is built and the whole point is that the widget
  changes under a pointer that does not.
- **`mark`'s comment is now true.** It was describing an intended behaviour as an
  achieved one, which is the kind of comment that stops the next person looking.
- **A window that never sees a pointer pays nothing**: the `NaN` check is the
  first thing the frame hook does.
