# ADR-0682: A window is captured while it runs

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** the launcher's `--capture=` (2026-10-06)

## Context

A downstream game asked for a picture of the composed window, the game
scene and its HUD as the player sees them, because nothing on its machines
could take one. `Window.capture()` and `--capture=PATH` already existed, and
had been in the game's snapshot. But `--capture` wrote only the last frame of a
`--frames=N` run, which then exits, and no `Host` call wrote a file.

## Decision

- **`--capture-at=N`** writes frame N to the `--capture=PATH` file, and the
  application keeps running. Without `--capture=` it is refused by name, and
  so is a negative frame. With `--frames=M` as well, the picture is of frame N,
  not of the last frame.
- **`Host.capture(Path)`** writes what the window shows now, GPU layers
  included, as a PNG, and makes the directories on the way. It returns false
  when the window has no frame yet. It is a default method over
  `Host.window()`, sharing the writing with the launcher in `Captures`.

## Consequences

- `Launcher.Options` gained a component, and its compact constructor checks
  the pair.
- Tested by `LauncherOptionsTest.captureAt` and by `LauncherCaptureTest`:
  frame N written while the run goes on, and `host.capture(path)` from a timer
  in a running application, on the headless backend.
