# ADR-0641: A tray click runs on the UI thread, and CI clicks a real tray

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0191](0191-a-tray-is-a-menu-somebody-else-draws.md)

## Context

Two more `TODO.md` entries under *The shell*:

- *A tray callback arriving off the UI thread is logged, not handled.* Every
  platform SDL ships calls a row back from inside the pump the UI thread is in,
  so `SdlTray.invoke` warned if one ever did not and ran the handler anyway,
  beside the UI thread.
- *Windows and macOS are unverified.* The Linux tray had run for real. The
  other two were SDL's code, compiled and never exercised, because a CI runner
  was assumed to have no notification area.

GitHub's Windows and macOS runners do have a desktop session, with a taskbar
and a menu bar. What nothing can do is click the shell's menu from outside it.
SDL's `SDL_ClickTrayEntry` reproduces the shell's order on every platform: a
checkbox is toggled, then the row's callback runs.

## Decision

**A tray row's handler runs on the UI thread, wherever the platform called
back.** `Sdl3Backend.onUiThread` runs it at once on the UI thread. From any
other thread it queues it, wakes the loop, and the next pump runs it before
anything else, as it delivers a file dialog's answer. A failure in one is
logged and the rest still run. `Sdl3Tray` routes every row through it, and the
warning in `SdlTray` is gone.

**CI clicks a real tray on Windows and macOS.** `SdlTrayLiveTest` opens a tray,
clicks its rows with `SDL_ClickTrayEntry` (exported at ABI 25, bound as
`SdlTray.click`), and changes its menu while the icon is up (ADR-0639).

- **`goldberry.tray.required`** turns its skip into a failure, as
  `webview.required` does. It is forwarded to every test JVM, decided by
  `TrayRequirement`, and set by `windows.yml`'s build step.
- **macOS runs it on the first thread.** Cocoa's status bar belongs to the
  process's first thread, and only the `gpu` lane starts there.
  `SdlTrayFirstThreadTest` repeats the live tests under the `gpu` tag, and
  `macos.yml`'s `gpuTest` step requires a tray. Linux's lanes have no
  notification area, require none, and skip.

Rejected:

- **Refusing a click off the UI thread.** It would lose the click.
- **A first-thread lane of its own for the tray.** A second launcher and task for
  six tests, when the `gpu` lane already starts where Cocoa needs it to.

## Consequences

- The `TODO.md` entry about a callback off the UI thread moves to *Answered*.
  `HandedOverTest` covers the hand-over under SDL's dummy driver: at once on the
  UI thread, queued and woken from another, and a failure that does not stop the
  rest.
- The Windows and macOS entry is **written, not run**. The workflow changes are
  in, and the first push that runs them is the first time a tray is clicked on
  either platform. It moves to *Answered* when both are green.
