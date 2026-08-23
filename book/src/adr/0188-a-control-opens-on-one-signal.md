# 188. A control opens on one signal, and the loop is what proves it

Date: 2026-08-23

## Status

Accepted. Builds the real-loop harness for §3's `select` family, and fixes the
defect it found in its first run.

## Context

Six defects reached a running application across ADR-0182 to ADR-0187 while every
unit test passed, and three of my fixes for them were wrong. The remedy was filed
four times and not built: `MenusTest` drives menus through the **real launcher**,
the headless backend and **posted events**, and nothing did that for `select`,
`tree` or the suggestion panels.

Every one of the six lived in a seam a hand-driven test cannot reach — the
application's rebuild, the platform's window flags, the pointer, the popup's own
build schedule.

## Decision

### `SelectLoopTest` posts events at a window

Four tests, one per defect class that got through: the list opens **below** the
field, the field keeps the keyboard, a value can be picked with the **mouse**,
and the popup **grows** when a tree branch expands. Nothing in it calls a handler
directly — anything assertable that way belongs in `SelectTest`.

It found a defect on its first run, which is the argument for it.

### A control opens on one signal

A click on a combobox opened the list and closed it again in the same gesture.

The **press** focuses the editor, and focus is what opens an editable control
([ADR-0185](0185-a-list-that-hangs-off-a-field-does-not-take-the-keyboard.md)).
Then the **click** arrives at the field and toggles — reading `open` from the
description that was built *before* the press, seeing false, and toggling a list
that was by then already open. Shut.

So the pointer does nothing on an editable field. One signal opens it, and the
signal is focus, because focus is what a click, a `Tab` and `Alt+Down` all
produce.

The general fault is worth naming because it is the second time this session:
**a widget's flags describe the frame that built it, not the moment the event
arrives.** ADR-0185 recorded the same thing about `widget()` after reporting
upward. `open` here is the same mistake in a different field — two paths that
each reason from a stale flag will contradict each other whenever both fire.

## Consequences

- **The harness proves three of the four fixes and cannot prove the fourth.**
  Reverting the popup resize fails the tree test; reverting `Popup.content`'s
  role, the placement, or the click path fails others. Reverting the
  `NOT_FOCUSABLE` flag fails **nothing**: the headless backend has no window
  flags, so a popup there never takes platform focus whatever kind it is. That
  test covers the router half only, and says so in its own documentation — a test
  that looks like it guards something it does not is worse than no test.
- **Anchoring is now covered**, which was the open question from the last round.
  The list opens below the field's painted rectangle, asserted in the window's own
  coordinates against the position the backend was actually given. If the reported
  mis-placement persists on a real compositor it is not the anchor, and this test
  is what narrows it.
- **This should have been built four rounds ago.** The cost of not having it was
  six defects reaching a user and three wrong fixes; the cost of having it was an
  afternoon and it found a seventh defect immediately. The lesson is not about
  `select` — it is that a widget whose behaviour lives in a seam needs a test that
  drives the seam, and that "the unit tests pass" is not evidence about a control
  nobody can use.
