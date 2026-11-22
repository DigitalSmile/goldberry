# ADR-0730: An animation is paced by what the window can show

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0729 (damage), the frame cap (`Window.frameCap`), the frame
  pacer, native ABI 29

## Context

The hosted frame loop asked for the next frame whenever
`renderer().isAnimating()`, and nothing else gated it. SDL's
`SDL_EVENT_WINDOW_OCCLUDED` was not handled (nor `HIDDEN`, `SHOWN`,
`MINIMIZED`), so a window covered by others or on another Space painted every
frame it would paint in front; only a minimized window stopped, because its
size read empty. An application could not do better: `Window.isFocused()`
existed, but there was no focus or occlusion callback and no CSS state for an
inactive window, and nothing in the toolkit read the power source or a saving
mode. A board left open all day beside other work paid for its animations in
full, on battery as on power.

## Decision

1. **An occluded window asks for no animation frames.** SDL's `OCCLUDED`,
   `HIDDEN` and `MINIMIZED` become `BackendEvent.OcclusionChanged(true)`;
   `EXPOSED`, `SHOWN` and `RESTORED` become `false`. `Window` keeps only the
   changes, answers `isOccluded()`, tells `onOcclusionChanged`, and repaints
   when it can be seen again. Only the frames an animation asks for wait: a
   transition, a keyframe animation, a spinner. A frame something asked for —
   a state change, a new video picture — is painted as before. A platform that
   never reports occlusion (Wayland) leaves the window counted as visible.
2. **Pacing is a pure policy** (`dev.goldberry.frame.pacing.AnimationPacing`):
   given the window's activity and the time since its last frame it answers now,
   after a delay, or paused. `HostedWindow` runs a delay as a loop timer, so the
   wait is the event loop's and a test moves it with the loop's clock.
3. **A saving mode caps animation at 30 Hz** (`PowerState.SAVING_FRAME_RATE`),
   what a browser's energy saver does. On battery alone the toolkit changes
   nothing and says so: `Window.powerState()` and `onPowerChanged` are there for
   an application that wants to do less.
4. **An unfocused window is not slowed unless asked.** A window beside the one
   being typed in is still being looked at, and a video in it is still watched;
   a browser does not slow a visible window either. `Window.inactiveFrameCap(hz)`
   holds an unfocused window's animation frames to `hz`, off by default, and
   the lower of two caps wins. `Window.onFocusChanged` is new.
5. **`:window-inactive`** matches every element of a window the focus is not in.
   It is WebKit's name. A popup the window opened counts as the window, so a
   menu does not make what is under it look inactive. It is answered by the
   tree, as `:root` is, and a change restyles the tree once.
6. **Power is read, not heard.** `SDL_GetPowerInfo` (new export, bound as
   optional) says where the power comes from and the battery left; the saving
   switch is each platform's own and read through libraries the process already
   has (`DesktopPower`): `NSProcessInfo.isLowPowerModeEnabled` through the ObjC
   bridge, `GetSystemPowerStatus`'s `SystemStatusFlag` in `kernel32`, and
   power-profiles-daemon's `ActiveProfile` over the system bus, under both of
   its names. A hosted window reads it every 20 s (`Window.POWER_POLL`); no
   platform raises an event SDL forwards.
7. **Native ABI 30.** Four window event types and the six `SDL_PowerState`
   values join the shim's constant table; `SDL_GetPowerInfo` joins the export
   list.

## Consequences

- One spinner in a covered window costs nothing until the window is seen; the
  animation catches up to the clock on the first visible frame.
- An application that wants decorative motion paused while unfocused writes
  `:window-inactive .spinner { animation-play-state: paused }`, or sets an
  inactive cap, or listens to `onFocusChanged`.
- The jars refuse a `libgoldberry` built for ABI 29.
- macOS Low Power Mode, Windows battery saver and the Linux power-saver
  profile are read on each poll, so the 30 Hz cap follows the switch within
  20 s. Linux's reading needs the system bus and the daemon; without them the
  answer is "off".
- Hand checks owed on the four desktops: an occluded window going quiet
  (macOS, X11), Low Power Mode and battery saver turning the cap on.
