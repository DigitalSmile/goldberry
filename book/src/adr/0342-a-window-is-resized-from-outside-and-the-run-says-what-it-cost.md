# 342. A window is resized from outside, and the run says what it cost

Date: 2026-09-17

## Status

Accepted. Closes the frame-evidence half of M5 in `status.md`; the number M1's
claim was waiting on is now a line in a log and a ceiling in `showcase.yml`.

## Context

M1 claims a paragraph *resized* at 60 fps, and nothing could measure it. Three
things were missing, in the order the work fell:

- **Nothing could resize a window but a hand.** `SDL_SetWindowSize` was bound in
  `:natives` and used for popups, and `BackendWindow` never exposed it for a
  window — so an application could not size its own window after opening it,
  and a CI run could not drive one. The load that matters is a drag, which is a
  resize event per pointer motion, each a pixel or two from the last: it is what
  found the damage-clamp bug (ADR-0072) and what a frame loop has to be measured
  under. A jump from one size to another is one reallocation and says nothing.
- **Nothing said what a run cost.** `FrameRing` keeps the last sixty frames,
  because that is what a HUD wants to watch (ADR-0146, ADR-0153); a run that
  exits after three hundred wants every frame it painted and every refresh it
  missed (ADR-0271), and the ring had forgotten most of both by the end.
- **Nothing failed.** `showcase.yml` opened a window on three runners and
  asserted three frames were drawn — a smoke test of the packaging.

Building the first found a fourth. `Launcher.run` registered its own
`window.onResize` and `window.onMove` **after** `Application.start`, into the
one handler slot a window has. An application's handler was replaced without a
word. The showcase's `resized to …` line, written in `start`, had never once
fired; the comment eleven lines further down, on `onSystemThemeChanged`,
explains exactly this hazard for the theme slot and takes it first for that
reason.

And the second draft of the walk found a fifth. Asking for the resize from
inside the painter worked on X11, where the window manager answers a request
later, and failed everywhere `SDL_SetWindowSize` takes effect on the spot — the
headless backend's first draft, the dummy driver, and, by SDL's documentation,
Windows and macOS: the size changed under the frame being painted, the platform
refused the frame, and every frame of the run was counted late. Fifty-nine of
sixty, on the first headless run.

## Decision

**A resize is a request on the SPI, the walk steps between frames, the ring
keeps totals, and the showcase runs under the load and fails over budget.**

- **`BackendWindow.resize(LogicalSize)`**, defaulting to nothing, is what
  `BackendPopup.resize` already was, made available to a window — the popup's
  declaration now overrides it. `Sdl3Window` hands it to `SDL_SetWindowSize`,
  rounded, and reports nothing itself: the compositor answers with a `Resized`
  and `size()` reads what it decided. `HeadlessWindow` plays the window manager
  the way `HeadlessPopup` already did — clamps to the floor, posts the event,
  and **applies the size as the event is delivered**, so a caller that measured
  straight after the call would be as wrong here as on two of the three
  desktops. The mechanism moved up from the popup; `HeadlessBackend` delivers
  it for either. `Window.resize` is the public face, ignored on a closed window.
- **`--resize=WxH`** walks the window a pixel a frame on each axis from its
  opening size to `WxH` and back, for as long as the run lasts. `ResizeWalk`, in
  a new `drive` package, is told the window's **current** size on every step,
  so a manager that clamped, rounded or lagged the last request is walked from
  its answer rather than from the ask. The step is scheduled on a zero-delay
  timer, which runs on the next pump after the frame has been presented.
- **`FrameRing` keeps three totals** beside the window — late refreshes, paint
  time and the worst frame — and `FrameStats.summary()` hands them out as a
  `FrameSummary`. The launcher logs `frames: 300 frame(s) painted, 2 late;
  paint mean 1.31 ms, worst 8.90 ms; display 60.0 Hz` after the window has
  closed, in `Locale.ROOT`, because a workflow greps it.
- **`--late-budget=N`** makes that a verdict: past `N` late refreshes the
  launcher throws `FrameBudgetException` **after** shutdown, so the process
  exits non-zero with the summary in its message and nothing left open.
- **`showcase.yml`** runs the native image on each platform for 300 frames with
  `--resize=1580x1100 --late-budget=30`, greps the three-hundredth frame, and
  writes the summary line into the step summary. The X server is 1920×1200 so
  the walk has room.
- **The launcher's resize and move hooks are its own.** `Window` gained
  package-private `launcherOnResize` and `launcherOnMove`, run before the
  application's handler; `onResize` and `onMove` are the application's alone.

## The caveat, written down with the numbers

GitHub's runners are GPU-less virtual machines. On Linux the image paints into
Xvfb, which reports no refresh rate, so the pacer does not pace and no refresh
can be missed — a Linux run can only be late by refusing frames. macOS and
Windows have a compositor and a rate. Measuring there is real evidence about
three platforms' drivers, and far better than one VirtualBox VM, but it is not
a claim about hardware: a run over budget has regressed, and a run well under
it has not proved 60 fps on a desktop. The budget is a tenth of the frames,
chosen as a ceiling and not a target, and the summary line is the number.

The walk's only run so far is headless on this machine, in a Gradle-launched
JVM: 60 frames, 0 late, paint mean 22 ms with a worst of 446 ms — the JIT
warming up, and the reason the mean is not a claim either.

## Consequences

- An application can size its own window: `host.window().resize(size)`.
- An application's `onResize` and `onMove` handlers survive `start`. Anything
  that relied on them *not* firing was relying on a bug.
- `--frames`, `--size`, `--resize` and `--late-budget` are the launcher's four
  flags; everything else on the command line is the application's.
- A frame the platform refuses because the window changed under it is still a
  late frame, and a driver that changes the size synchronously will show it.
  Anything that resizes a window from inside a painter is doing it wrong.
- The Showcase workflow can go red for a reason a diff did not cause. That is
  the point of a ceiling, and the summary line says by how much.
