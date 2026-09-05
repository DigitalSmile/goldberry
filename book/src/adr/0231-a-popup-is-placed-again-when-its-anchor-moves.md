# 231. A popup is placed again when its anchor moves

Date: 2026-08-30

## Status

Accepted. **Narrows** a `TODO.md` entry opened by
[ADR-0104](0104-a-popup-is-measured-then-placed.md): the resize half ships, and
what is left needs an SPI event that does not exist.

## Context

> **Nothing re-places an open popup.** Move or resize the window with a menu open
> and the menu stays where it was put; `Popup.move` exists and nothing calls it.

Half of that sentence turns out to be wrong, and finding out which half is most
of this record. A popup is positioned as an **offset from its owner window** —
`PopupSpec` takes a point in the owner's coordinates and `Popup.offset()` answers
in them — so **moving the window carries its popups along**; the platform does
it. What a move can invalidate is the *clamping*: a menu placed near the bottom
of the screen was flipped or clamped against the work area at the position the
window used to be in, and the toolkit gets no event when a window moves. There is
no `BackendEvent.Moved`.

A **resize** is different in two ways. It is reported — `BackendEvent.Resized`
exists and reaches `Window.handleResize` — and it moves the thing the popup was
anchored *to*: a heading at the bottom of a column, a control aligned to the
right edge, anything the layout places relative to a size. The popup stays at its
old offset and the anchor is somewhere else.

## Decision

**The launcher remembers how each popup was placed and puts it back after a
resize.** A `Placed` record per open popup — the anchor rectangle, the placement,
and the anchor's **id** when it had one — kept in an `IdentityHashMap` keyed by
the popup, because a popup is looked up by which popup it is.

**An id is worth more than a rectangle**, and that is the interesting half. A
popup opened by `popup(content, anchorId, placement)` re-resolves its anchor
against the frame the resize produced, so it follows a heading that moved. One
opened against a rectangle the caller computed is re-placed against that same
rectangle, which is right: the caller said where, and nothing has told the
launcher otherwise.

**A move, not a reopen.** `Popup.move` exists for exactly this and is what the
entry noticed nobody called. The tree stays mounted, the keyboard stays where it
is, and nothing flickers.

**And it happens at the end of the next paint, not in the resize handler.** This
is the part that is easy to get wrong and impossible to notice: `anchor(id)`
answers from the hit-test capture the **last** paint produced, and during the
resize handler that capture is still the *old* window's. Re-placing there would
put every menu back where its heading used to be — the bug wearing the fix's
clothes. So the handler sets a flag and the paint acts on it, after
`router.updateRegions`.

## Alternatives considered

- **Re-placing in the resize handler**, which is where the event arrives and
  where it looks like it belongs. It reads the previous frame's geometry, so it
  is confidently wrong rather than absent.
- **Closing popups on resize.** Every desktop menu survives a resize, and a menu
  that vanished because the user dragged a window edge would be worse than one
  that lagged.
- **Adding `BackendEvent.Moved` now** and re-clamping on window moves too. It is
  the honest completion and it is a different change: an SPI event, an SDL
  translation for `SDL_EVENT_WINDOW_MOVED`, a headless implementation, and a test
  that can only be written the way ADR-0061 wrote the wheel's — by pushing a
  fabricated event onto SDL's own queue. Worth doing; not worth bundling.
- **Having the popup watch its own anchor through `Located`.** The right answer
  for the case the entry names next — "a `popover` that follows a scrolling
  anchor" — and it needs the anchor widget to implement `Located` and to have
  somewhere to send the report. A resize is a window-level event with a
  window-level owner, and that is the launcher.

## Consequences

- **A menu follows its heading across a resize**, which is what every desktop
  does and what the entry asked for.
- **One flag, one map and one method** in `Launcher`, and the map is cleaned of
  closed popups on every re-placement rather than by a second bookkeeping path.
- **A test that fails by 200 pixels without the fix.** `replacedOnResize` shrinks
  a window whose anchor sits at the bottom of a filling column and asserts the
  popup came with it — 504 before, 304 after.
- **Writing that test found a trap worth recording.** A run bounded by `--frames`
  finishes in whatever wall-clock time the machine takes, so a callback scheduled
  300ms out can arrive after the loop has gone — and then reads a live-looking
  `anchor()` from a dead launcher and gets an empty `Optional` from a shut-down
  backend. The test schedules by **turns of the event loop** instead, which cannot
  outlive it.
- **What is still open**: a window *move* does not re-clamp, because nothing
  reports one; and a popup does not follow an anchor that scrolls, because nothing
  reports that either. Both are named in the entry, which stays open with those
  two halves.
