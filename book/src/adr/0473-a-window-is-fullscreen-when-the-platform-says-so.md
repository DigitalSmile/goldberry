# 473. A window is fullscreen when the platform says so

Date: 2026-09-24

## Status

Accepted. Unblocks `F` in `docs/goldberry-media.md` §6 and the last item of
phase 7 in `docs/media-plan.md`. Applies
[ADR-0252](0252-a-window-is-maximized-when-the-platform-says-so.md)'s rule to
the other window state a platform owns.

## Context

`media-player` was designed with a fullscreen button and an `F` key, and both
waited because `:core` had no way to make a window fill its display: nothing in
`BackendWindow`, nothing bound in SDL, no event plumbed. `docs/core-widgets.md`
§6 lists a fullscreen toggle among the window chrome helpers for the same
reason.

Fullscreen is also a window state the platform owns, and it behaves the way
maximizing does, only more so:

- **It is asynchronous.** On macOS SDL moves the window to a Space of its own,
  animated, and the state lands several frames after the ask. SDL's own header
  says the request "can be denied by the windowing system".
- **The user can change it without the application.** The green button on
  macOS, a window manager's key on Linux.
- **A hidden window defers it.** Goldberry creates windows hidden until their
  first frame. SDL keeps a fullscreen request made on a hidden window as a
  pending flag, applied and reported when the window is shown. A test on the
  dummy driver found this: the request was accepted and no event came back
  until a frame had been presented.

A media player adds a second question the window cannot answer: fullscreen
video in a browser means the **element** fills the screen, not the page. A
window made fullscreen with the player still one pane of a layout shows the
player at the size it had.

## Decision

**In `:core`, fullscreen is maximizing's shape.**

- `BackendWindow.setFullscreen(boolean)`: a request, a default no-op.
  `Sdl3Window` calls `SDL_SetWindowFullscreen` with no display mode set, so it
  is borderless fullscreen on the desktop's own mode, never an exclusive mode
  change. `HeadlessWindow` agrees and reports, and `reportFullscreen` drives the
  user's route in a test.
- `BackendEvent.FullscreenChanged(window, boolean)`, from SDL's
  `ENTER_FULLSCREEN` (0x217) and `LEAVE_FULLSCREEN` (0x218), checked against the
  compiled header by the constant probe.
- `Window.isFullscreen()` answers what the platform last **reported**, and
  between the ask and the event it still answers the old state.
  `Window.setFullscreen` asks. `Window.onFullscreenChanged` is a `Subscription`
  with any number of listeners, told of changes only.
- `Host` gains `canFullscreen`, `isFullscreen`, `setFullscreen` and
  `onFullscreenChanged`, defaulting to "no window". A widget asks its host, not
  `host.window()`: `Host`'s own docs say reaching for the window means the call
  belongs on `Host`, and a test host with no window has an honest answer.

**In `media-player`, fullscreen is the element's, as in a browser.** Entering
lays a full-window copy of the player over the window with `Host.fill` (the
same player, fit and classes, `.is-fullscreen`, no id) and then asks the window
to go fullscreen, unless it already is. Leaving (the button, `F`, `Esc`) takes
the copy away and gives the window back as it was. When the window leaves
fullscreen by the platform's own button, the copy goes too. While the copy
shows, the player in the layout stops rebuilding for pictures nobody can see.

The button is offered only where `Host.canFullscreen()` is true. Every golden
image is taken with no window, so none of them changed.

## Alternatives considered

- **`isFullscreen()` set when asked.** That's wrong the moment a window manager
  refuses, and wrong for several frames on every Mac. ADR-0252 already
  rejected this for maximizing.
- **Window fullscreen only, the player left in its layout.** This is simpler,
  and on a showcase with tabs and cards it shows a small video on a big black
  screen. It isn't what anyone means by fullscreen video.
- **A CSS rule that takes the player out of its layout.** §8's subset has no
  `position: fixed`, and an absolute box is placed against its own parent. The
  overlay layer is the one place a widget can cover the whole window
  (ADR-0100).
- **Exclusive fullscreen at a chosen display mode.** SDL supports it
  (`SDL_SetWindowFullscreenMode`), but a video wants the desktop's mode: a mode
  switch blanks every monitor for a second and scales the picture twice.
- **Media-player listening on `host.window()` directly.** `TestHost.window()`
  throws, so every widget test would need a real window.

## Consequences

- One more SDL export (`SDL_SetWindowFullscreen`) and two more checked
  constants. The ABI version is unchanged, as it was when the ninth SDL audio
  export was added.
- Between the ask and the event, `isFullscreen()` is stale, and a toggle driven
  by it can ask twice during macOS's animation. That is harmless: the second
  ask repeats the first.
- A window manager that refuses leaves the player's copy filling the window
  without the window being fullscreen. `F` and `Esc` still leave, so nobody is
  stuck, but the player is only "full window".
- Keyboard focus stays where it was when the copy opens. The copy and the
  player in the layout drive the same player, and either one's `F` or `Esc`
  leaves, so the keys work whichever one has focus. Moving focus into the copy
  would need an id and a frame's wait, and nothing needs it yet.
- A fullscreen request made before the first frame is applied as the window
  appears. That makes "open straight into fullscreen" an ordinary call at
  start-up, and is documented on `Window.setFullscreen`.
