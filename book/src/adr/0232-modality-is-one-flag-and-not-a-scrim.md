# 232. Modality is one flag, and not a scrim

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0100](0100-a-window-has-a-layer-above-its-application.md).

## Context

> **Nothing hit-tests an overlay by rule.** The pointer router tests against the
> painted frame and an overlay is in that frame, so a button inside one is
> reachable today by the accident of paint order rather than by anything anyone
> wrote down. A modal `dialog` needs the rule stated — the topmost overlay takes
> the pointer first, and a modal one takes it exclusively.

Both halves were true and neither was written anywhere.

The **first** was already the behaviour: `HitTest.at` scans the capture backwards
and the capture is in paint order, so whatever was drawn last answers first. The
window's overlay layer is described after the application's root, so a button in
a `toast` takes the pointer from what is under it. Nothing said so, and nothing
asserted it.

The **second** was not one mechanism but two, and `Handles.isModal` said as much
in its own documentation:

> **The pointer is not this flag's business.** A dialog is unreachable by mouse
> because its scrim covers the window and takes every press […] — modality by
> geometry rather than by a rule.

That is a defensible design and it has a hole in it exactly where the sentence
stops. A widget that answers `isModal()` and is *not* wrapped in something that
fills the window traps the keyboard and lets every click through. Nothing warns,
nothing fails, and the two halves of "modal" disagree — which is the worst kind
of bug to have in an accessibility feature, because the keyboard user is
protected and the pointer user is not.

## Decision

**Modality is one flag.** While a modal is mounted, the pointer reaches its
**subtree** and its **ancestors**, and nothing else.

The ancestors are not a loophole; they are the point. A `dialog`'s scrim is the
panel's *parent*, and a click on it is what closes the dialog. An ancestor is on
the **path** from the modal to the root — a path, not a subtree — so a button in
the application is neither inside the modal nor on that path, and is unreachable.

**Enforced in `elementAt`**, which is the one place every pointer entry point
resolves a target. So a press, a release, a wheel and a *hover* all obey it
together: a control behind a dialog that lit up under the pointer would be
claiming to be pressable when it is not.

**The modal is found once per frame**, in `updateRegions`, and kept beside the
regions. That is the rule ADR-0054 already states for hit testing — input is
answered against the frame the user can see, so the tree that frame came from is
the tree to ask — and it turns what would be a tree walk per mouse move into one
walk per paint.

**A press on the unreachable application does not empty the trap.** Normally a
press on nothing moves focus off whatever had it; behind a modal, "nothing" is
the application, and clearing focus only for the next frame's `refocus` to put it
back is a frame with nothing focused.

**And the paint-order rule is written down** on `elementAt`, where the code that
implements it is, with a test that fails if it ever stops being true.

## Alternatives considered

- **Leaving it to geometry and documenting the requirement** — "a modal must be
  inside something that fills the window". It is a rule a compiler cannot check
  and a reviewer will not remember, protecting a feature whose whole point is
  that it cannot be got wrong.
- **A separate `blocksPointer()` flag.** Two flags that must agree, and the
  disagreement is the bug this closes.
- **Blocking at dispatch rather than at `elementAt`.** It would leave `hovered`
  pointing at an unreachable node, so `:hover` would still light up behind the
  dialog — the same split, one layer down.
- **Confining the pointer to the modal's subtree alone**, without the ancestors.
  Simpler to state and it breaks click-outside-to-dismiss on every dialog in the
  toolkit, because the scrim is not inside the panel.
- **Making the overlay layer intercept by kind** rather than the router by rule.
  It puts the decision in the layer, and `Handles` was right that the layer is
  the wrong owner: the next modal is a `wizard` step or a `sheet`, and neither
  will be the same shape as a dialog.

## Consequences

- **No visible change to `dialog`**, whose scrim already covered the window. The
  change is for the modal that does *not* have one, which is the case that was
  silently broken.
- **`Handles.isModal`'s note is now wrong in one sentence** — "the pointer is not
  this flag's business" — and the flag is better for it. Left in the record rather
  than only in the code, because the reasoning it gives is the reasoning that
  produced the hole.
- **One field and two loops** in the router, and the field costs one tree walk per
  paint where the naive version would cost one per pointer motion.
- **A new `ModalPointerTest` in `:core`**, built from bare widgets rather than
  from `dialog` — `FocusTrapTest`'s reason: the mechanism is the router's and has
  to hold for whatever declares itself modal next.
- **The test's overlay deliberately does not fill the window.** A `dialog`'s does,
  and that is precisely why nothing could tell the rule from the geometry before:
  a filling scrim takes every press whether or not anything is modal. Removing it
  is what makes the assertions about the rule.
- **What this does not do**: nothing stops an application from putting a modal in
  a corner overlay and leaving the rest of the window *visible* but dead. That
  reads as a bug and is now a bug the toolkit implements faithfully — the veil is
  a design decision, and §7 gives it to `dialog` and to `tour` by geometry.
