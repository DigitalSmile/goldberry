# ADR-0523: A dialog takes itself off the window

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0176](0176-a-dialog-is-a-widget-and-showing-one-is-not.md),
  [ADR-0232](0232-modality-is-one-flag-and-not-a-scrim.md),
  [ADR-0234](0234-the-overlay-lifecycle-is-a-departure-and-a-phase.md),
  [ADR-0522](0522-each-overlay-is-its-own-node.md),
  `docs/goldberry-gaps.md` entries 3 and 22

## Context

ADR-0176 left removing a dialog to the application: every route out fades the
panel and then calls the handler, and the handler removes the `Overlay`. Deploy
Orc's report showed three problems with that.

- **A closed scrim still took the pointer (entry 3).** A dialog whose fade was
  over drew nothing, but its `DialogScrim` stayed in the tree. It filled the
  window and consumed every event. Any dialog left attached after its fade
  locked the window with nothing on screen to explain it: a handler that forgot
  `remove()`, a handler that threw, or the shared state of ADR-0522.
- **The application could not close a dialog with the fade (entry 22).**
  `Overlay.remove()` takes an overlay away at once. A dialog the application
  finishes itself, such as a sign-in that completes, could only vanish.
- **A tall dialog ran off the window (entry 22).** The `dialog` rule had a width
  cap and no height cap, and `dialog-body` did not scroll. Deploy Orc wrapped
  each wizard page in a `scroll` with a fixed `max-height`. Its wizard in a dialog
  had two button bars, so it moved Cancel into the title row with
  `position: absolute`.

## Decision

**A dialog finishes its own departure. The application can start one without
pressing anything, and the dialog fits the window.**

- **No closed scrim.** Once its `Departure` is over, `DialogState` builds
  `Widget.nothing()`. A scrim that draws nothing is still a hit target the size of
  the window, so there is now no such scrim to hit. The `closed` flags on the
  scrim and the panel are gone.
- **The fade ends by removing the overlay.** `DialogState` finds its handle with
  `WindowRoot.overlayOf`. When the fade is over it runs the handler and then
  calls `remove()`, in a `finally`, so a handler that throws still clears the
  window. `remove()` was already idempotent, so a handler that also removes is
  harmless.
- **`Overlay.dismiss()`** takes an overlay away the way its widget leaves. A
  widget registers its exit with `Overlay.dismissWith(Runnable)`, and the exit
  ends in `remove()`. With no exit registered, `dismiss()` is `remove()`. A
  dialog registers a departure with no handler, since nothing was pressed. This
  lives on `Overlay` rather than in a `Dialogs.dismiss(Overlay)`, because the
  handle is what the application holds and any overlay may have an exit.
- **A height cap with a scrolling body.** `dialog { max-height: 100% }` resolves
  against the scrim's content box, which is the window less 24px at the top and
  bottom. The body sits in a `scroll.dialog-scroll` that may shrink, while the
  title and the action bar keep their size. The viewport reaches 4px past the
  body and the body pads 4px back, so a full-width field's focus ring is inside
  the clip and nothing moves.
- **The body's viewport is a Tab stop only while it overflows.** A `scroll` is
  always a Tab stop, and a dialog would then have a stop before every field and
  would open with focus on the viewport. `Scroll.tabStopOnlyWhenScrollable()`
  makes the viewport focusable only when its last measurement overflowed. Every
  other `scroll` keeps the old rule.
- **An opt-in × in the title bar.** `dismiss=` in markup, or
  `Dialog.dismissible(handler)`, adds a `dialog-header` row with the title and a
  `dialog-dismiss`. The × closes the dialog and then runs the handler. In such a
  dialog `Escape` and the scrim do the same, so the × is not a Tab stop, for
  `tab-close`'s reason. Without `dismiss`, `Escape` presses the dismissive action
  as before.

## Consequences

- A forgotten or throwing handler no longer locks the window. The pattern of
  ending every handler with `open.remove()` still works, and is no longer needed.
- `open.dismiss()` closes a dialog from the application with the 160ms fade.
- A dialog taller than the window keeps its title and buttons on screen and
  scrolls its body. The golden images are unchanged: layout and paint are the
  same for a dialog that fits.
- A selector that relied on `dialog > dialog-body` no longer matches, because the
  viewport sits between them. `dialog-body > …` still reaches the author's
  content.
- `Scroll` has a new record component, `tabStopWhenFits`. The seven-argument
  constructor is kept and means `true`.
- Deploy Orc can drop its fixed-height `scroll` around wizard pages and its
  absolutely positioned Cancel, and use `dismiss=` for the wizard's way out.
