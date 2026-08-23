# 186. A panel that hangs off a field is not a menu

Date: 2026-08-23

## Status

Accepted. Fixes two defects [ADR-0185](0185-a-list-that-hangs-off-a-field-does-not-take-the-keyboard.md)
reported fixed and did not fix.

## Context

ADR-0185 stopped a suggestion list focusing its first row and called the
keyboard-stealing bug closed. It was not. The field still took one character and
went dead.

The reason is that there are **two** focuses and only one of them was addressed.
`Popup.takesFocus(false)` governs the *router* — which element inside the popup's
own tree holds the keyboard. What actually took the keyboard was the **window**:
a popup opened as `PopupKind.MENU` gets SDL's `POPUP_MENU` flag, which is
focusable by definition, and every window manager hands a focusable window the
keyboard the moment it appears. The owner window stopped receiving text, so the
editor stopped receiving keystrokes.

`PopupKind` had said so all along: "A menu **may** take the keyboard; a tooltip
must never, or the caret leaves the field the tooltip is describing." That
sentence is about tooltips and describes this exactly.

Separately, a `tree` in a popup would expand and the new rows would not appear.

## Decision

### An attached panel is a tooltip-kind window

`Host.attachedPopup` opens a popup as `PopupKind.TOOLTIP` — `NOT_FOCUSABLE` at
the platform level — while still measuring, placing, fitting and light-dismissing
it like any other popup. Both autocomplete forms use it; every other `select`
opens a menu, which is what it is.

The arrows still reach it, because the owner forwards keys to whatever popup is
open ([ADR-0104](0104-a-popup-is-measured-then-placed.md)). That forwarding exists
because a popup may or may not have platform focus depending on the driver — and
it is what makes a deliberately unfocusable panel operable rather than inert.

`takesFocus(false)` stays. The two are not alternatives: one keeps the platform
keyboard on the owner window, the other keeps the router's focus off a row. A
panel that hangs off a field needs both.

### A popup measures itself again when its content changes

A popup was measured once, at open, and never again. For a menu that is correct.
For the two things that now re-describe themselves — a `select multiple`'s list
and a `tree` — it was a bug with no workaround: an expanded branch drew its rows
into a window still the height of the collapsed one, so they were not there, and
no viewport appeared either because the `Fit` that would have added one had
already run.

`Popup.content` now re-measures and asks for the new size, re-applying the `Fit`
with the measurement — which is what puts a viewport in when content *outgrows*
the screen rather than only when it was already too big. The measuring is the
launcher's, handed to the popup as a callback, because it needs the window's scale
and the `Fit` the popup was opened with and neither belongs in `Popup`.

## Consequences

- **Two focuses, and the vocabulary did not distinguish them.** "The popup takes
  focus" meant the router's to me and the window manager's to SDL, and the fix
  for one read as a fix for both. Worth remembering the next time a popup
  misbehaves: ask which focus.
- **The suggestion popup is now unfocusable at the platform level**, which should
  also stop it hanging on screen when the application is deactivated: it can no
  longer be the window that keeps `anyWindowFocused` true. That is a prediction
  from reading the code, not a verified fix — see below.
- **A popup still hangs when the application loses focus to another window, and
  this ADR does not fix it for menus.** ADR-0144's mechanism is wired and the
  fault is inside it. What is now known: `anyWindowFocused()` counts popup
  windows, so a popup that holds or believes it holds platform focus keeps the
  whole check true. A menu popup is focusable and is the likely culprit; a
  tooltip-kind one cannot be. The next step is a real window and a log of
  `FocusChanged` per window id, which the headless backend cannot produce.
- **Neither fix has a test.** Both live where the toolkit has no coverage at all —
  the platform's window flags and a popup resizing itself — and both were found by
  running the application. This is the third time, and it is the same gap
  ADR-0185 named: nothing drives §3's select family through the real loop the way
  `MenusTest` drives menus. That harness is now the most valuable thing left
  undone in this area, above any individual defect.
