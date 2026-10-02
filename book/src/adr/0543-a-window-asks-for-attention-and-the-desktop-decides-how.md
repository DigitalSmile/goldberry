# ADR-0543: A window asks for attention, and the desktop decides how

- **Status:** Accepted. Closes the attention half of `docs/goldberry-gaps.md`
  #24. Desktop notifications and dock badges are decided separately.
- **Date:** 2026-10-02
- **Relates to:** [ADR-0541](0541-a-window-opens-where-it-was-left-and-is-clamped-onto-a-display-that-exists.md),
  [ADR-0542](0542-an-application-may-open-more-than-one-window.md)

## Context

Deploy Orc runs long deployments and the user goes elsewhere while they run.
When a gate needs approval or a step fails, the application has to say so from
behind other windows. Nothing in `Host` or `Window` could. orc-macos shelled out
to `osascript`.

Each desktop has a native way to point at a window without saying anything:
macOS bounces the dock icon, Windows flashes the taskbar button, and X11 has the
urgency hint, which each desktop shows its own way. SDL wraps all three as
`SDL_FlashWindow` with three operations: cancel, briefly, and until focused. It
was not exported.

## Decision

**`Window.requestAttention(Attention)` and `Window.cancelAttention()`, through
`SDL_FlashWindow`.**

- `render.window.Attention` has two values, `BRIEFLY` and `UNTIL_FOCUSED`, the
  two SDL operations that ask for something. Cancelling is its own method rather
  than a third value, because it is not a kind of attention.
- `BackendWindow` gains `requestAttention` and `cancelAttention`, both answering
  whether the platform could. The defaults answer false. `Sdl3Window` calls
  `SDL_FlashWindow`. `HeadlessWindow` records the requests so a test can assert
  on them.
- Both answer `false` on a closed window rather than throwing, because "the
  window has gone" is not a failure of the request.
- `Window.raise()` (`SDL_RaiseWindow`, bound for
  [ADR-0542](0542-an-application-may-open-more-than-one-window.md)) is the
  stronger sibling. A desktop with focus-stealing prevention answers it with a
  flash, which is what the modal-owner press in ADR-0542 falls back to
  explicitly.
- `SDL_FlashWindow` joins the export list in this batch's ABI bump to 18.

## Consequences

- Deploy Orc calls `requestAttention(UNTIL_FOCUSED)` when a gate opens, and
  drops its `osascript` call for this half.
- What the user sees is the desktop's choice: a Wayland compositor shows
  `xdg-activation`'s request its own way, and some X11 window managers ignore the
  urgency hint. The call answers what SDL answered.
- On macOS a bounce comes from the application, not the window. On Windows and
  X11 it is the window's. `requestAttention` on any window of the application is
  a bounce on macOS.
- Only the Linux paths were run here; the macOS and Windows behaviour is SDL's
  and was not observed.

## Alternatives considered

- **Putting it on `Host`.** Attention names a window, and a second window
  ([ADR-0542](0542-an-application-may-open-more-than-one-window.md)) is one an
  application may want to point at. `Host.window()` reaches it.
- **One method with a nullable `Attention` for cancel.** It reads as "attention
  of no kind" rather than "stop".
- **Building notifications here.** A notification says why. That needs a bundle
  id on macOS and an AUMID on Windows, and it is a separate entry.
