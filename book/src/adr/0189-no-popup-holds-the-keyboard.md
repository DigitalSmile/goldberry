# 189. No popup holds the keyboard

Date: 2026-08-23

## Status

Accepted. Fixes popups and tooltips outliving the window they belong to.

## Context

A menu, a dropdown or a tooltip left open when the user switched to another
application stayed on screen, over somebody else's window, after the owner had
hidden. [ADR-0186](0186-a-panel-that-hangs-off-a-field-is-not-a-menu.md) narrowed
it and could not close it: `anyWindowFocused()` counts popup windows, so a popup
that held platform focus kept the check true and the dismissal never fired.

`ATTACHED` popups had already been made `NOT_FOCUSABLE` for an unrelated reason,
which is why suggestion panels stopped hanging and menus did not.

## Decision

### `NOT_FOCUSABLE` on every popup, of every kind

Which reads like a restriction and is the opposite. **A popup was never allowed
to rely on having focus.** SDL gives a `POPUP_MENU` window focus on some drivers
and not on others, so the owner has forwarded keys to whatever popup is open
since [ADR-0104](0104-a-popup-is-measured-then-placed.md), and a menu is operable
by arrows either way.

So what varied by driver was never the behaviour — only whether the application
still looked focused *to itself*. And that is precisely what
[ADR-0144](0144-a-popup-goes-away-when-the-application-does.md)'s check reads to
decide a popup has been left behind.

Taking focus off all of them makes that check mean what it says: **the
application is focused exactly when one of its own real windows is.** The
dismissal then works for menus, dropdowns and tooltips alike, and works the same
way on every driver rather than on the ones that happened not to focus popups.

## Consequences

- **One fewer thing that varies by driver.** The forwarding path in ADR-0104
  existed to tolerate both behaviours; now only one of them happens, and the
  tolerance is what makes removing the other safe rather than being made
  redundant by it.
- **The reported popup mis-placement is still not reproduced.**
  `SelectLoopTest` now asserts the list opens below the field from a window at the
  origin *and* from a window moved to (220,160) on a display with a 48px taskbar —
  the case that hides a coordinate-space mistake, because at the origin screen and
  window coordinates are identical. Both pass. `placeableArea` converts the work
  area into the window's space correctly, `Placement` clamps into it correctly,
  and the position handed to SDL is in the logical points SDL3 wants.

  What is left is below the harness: `SDL_CreatePopupWindow`'s interpretation of
  the offset on the reporter's compositor. The debug line added in `6c0618e`
  prints the anchor the list was placed against; with the popup's own reported
  position beside it, the two numbers say whether the fault is before SDL or
  inside it. I have stopped guessing at this one.
