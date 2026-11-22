# ADR-0726: A popup fits each new description, and a tree can change its root

- **Status:** Accepted. A fix found on the showcase's Capture screen (W3.4
  phase 6), in the toolkit rather than the screen.
- **Date:** 2026-11-22
- **Relates to:** ADR-0725 (the Capture screen, where it was found)

## Context

A popup opened through `Host.popup` with a `Host.Fit` shows what the fit
answered. `Fitted`, the fit `select` and `menu` use, answers with the content
itself when it fits the work area, and with the content inside a `scroll`
viewport when it does not.

`select` re-describes its open list on every build (`Popup.content(panel())`),
so the list follows a model that moves under it. `Popup.content` handed that
description straight to `ElementTree.update`, which refuses a root of another
type. For a list taller than the screen the popup's root was the `scroll`, and
the description was the bare `select-list`. The first rebuild of a select's
owner while its list was open threw `IllegalArgumentException: a tree rooted at
Scroll cannot be re-described as a SelectList`, out of the frame loop, and
ended the application.

The camera card's mode picker found it. A Logitech C922 offers 218 modes, and
the card rebuilds twice a second for its metrics. Any select with more options
than the screen holds was exposed. The measure path had the same mismatch the
other way round: a popup whose content outgrew the screen called `update` with
the viewport over a bare root.

## Decision

1. **`ElementTree.describe(Widget)`** updates the root in place when the new
   widget has its type and key, and otherwise **`ElementTree.replace(Widget)`**
   disposes the old tree and builds the new one, keeping the tree's own
   settings (its host, the element it was opened from).
   **`ElementTree.canUpdateTo(Widget)`** says which it will be. `update` keeps
   its refusal, since a caller that asks for an update means one.
2. **`Popup.content` fits a description that no longer matches its root.** The
   popup's measurer measures the new content on a tree of its own, opened from
   where the live one was, and asks the fit it was opened with. A list still
   too tall comes back inside a viewport of the same type, so the live
   viewport is updated and keeps its scroll position. A list that now fits
   comes back bare and replaces the root. A description that matches the root
   costs nothing more than before.
3. **The measure path uses `describe`**, so a popup whose content outgrows the
   screen takes its viewport as a new root rather than throwing.

## Consequences

- A select or menu of any length can stay open while its owner rebuilds.
  `SelectLoopTest` drives one of 120 options in a 400-tall work area through
  the real launcher, rebuilds its owner twice with the list open, and checks
  the list stays open at the same height. Without the fix it throws the
  reported exception.
- Re-describing a fitted popup measures the new content once more. That is
  paid only by a popup that needed a viewport, and only while it is open.
- Crossing the screen's height in either direction rebuilds the popup's tree,
  so state inside the list (a scroll offset, a focused row) starts again at
  that moment. It does not happen while the list keeps its shape.
