# ADR-0638: No popup has the platform's focus, so none gives it back

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0144](0144-a-popup-goes-away-when-the-application-does.md), [ADR-0180](0180-the-keyboard-goes-back-where-it-was.md)

## Context

Two `TODO.md` entries under *Overlays, popups and windows* rested on one
premise: SDL gives a `POPUP_MENU` window the keyboard on some drivers and not on
others.

- *A popup may not give the platform's keyboard focus back.* The widget layer
  had already been measured clean (ADR-0180). What was left was the platform's
  own window focus, which no test could reach.
- *60 ms is how long a focus-lost is disbelieved for.* ADR-0144 deferred the
  check because opening a popup sent a focus-lost for the owner and then a
  focus-gained for the popup. The entry worried that a slower driver would make
  a menu close as it opened, and that 60 ms could not be derived.

ADR-0189 had since made every popup `NOT_FOCUSABLE`, of every kind, so that
`anyWindowFocused` would mean what it says. Neither entry had been read again in
that light.

## Decision

**Both entries close on what SDL does with a popup that is not focusable.**
Read in SDL 3.4.18, the version the build pins: every driver moves the keyboard
to a popup menu on show, and back to its parent on hide, only when the popup
lacks `SDL_WINDOW_NOT_FOCUSABLE`. That is `X11_ShowWindow` and
`X11_HideWindow`, `Wayland_ShowWindow` and `Wayland_ReleasePopup`,
`WIN_ShowWindow` and `WIN_HideWindow` (and `GetWindowStyleEx` gives every
popup `WS_EX_NOACTIVATE`), and Cocoa's `canBecomeKeyWindow`. X11 popups are
override-redirect, so no window manager hands them focus either, and SDL takes
no Wayland popup grab.

- **Nothing to give back.** The platform's focus stays on the owner from the
  moment a popup opens until it closes.
- **No pair to wait out.** Opening a popup sends no focus-lost, so a menu cannot
  close as it opens whatever the settle is set to. The 60 ms deferral stays as a
  debounce: focus moving between two of the application's own windows is still
  a lost and a gained, and a window manager can still flicker.
  `goldberry.popup.settle` stays too.
- **The flag is pinned where it is chosen.** The flags moved out of
  `Sdl3Backend.createPopup` into `PopupFlags`, and `PopupFlagsTest` holds every
  kind to `NOT_FOCUSABLE` and to exactly one kind flag. The headless backend has
  no window flags, which is why `SelectLoopTest` could not hold it.

Rejected:

- **Binding `SDL_GetWindowFlags` to read the flag back from a real window.** It
  means a new export from the native library and a rebuild on every platform,
  only to read back a value this repository chooses one line earlier.
- **Removing the deferral.** It costs nothing, and the window-to-window pair is
  real.

## Consequences

- Both entries move to *Answered*, and the open half of *Overlays, popups and
  windows* is empty.
- The comments in `Launcher`, `GoldberryRuntime`, `Popup`, `FocusSettleTest` and
  `PopupLifecycleTest` that described the popup's focus pair as current now
  describe it as history. `PopupLifecycleTest` keeps the case of a popup that
  takes the focus, as a defence.
- If a later SDL starts focusing popups that are not focusable, this rests on a
  reading of SDL's source that no test here repeats. Upgrading SDL is when to
  read it again.
