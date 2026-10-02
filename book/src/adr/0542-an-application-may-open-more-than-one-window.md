# ADR-0542: An application may open more than one window

- **Status:** Accepted. Reverses the one-window premise of
  [ADR-0093](0093-an-application-is-a-root-widget.md),
  [ADR-0102](0102-a-popup-is-a-window-the-platform-may-refuse.md) and
  [ADR-0103](0103-a-popup-is-a-second-tree-in-a-second-window.md) ("the
  launcher owns one window"); their decisions about popups stand.
- **Date:** 2026-10-02
- **Relates to:** [ADR-0100](0100-a-window-has-a-layer-above-its-application.md),
  [ADR-0140](0140-a-widget-may-reach-its-window.md),
  [ADR-0541](0541-a-window-opens-where-it-was-left-and-is-clamped-onto-a-display-that-exists.md),
  `docs/goldberry-gaps.md` #7

## Context

Deploy Orc wants sign-in, settings and a New Release wizard as windows of
their own. Goldberry gave it two routes and neither was a window:

- `Window.open(WindowSpec)` is a raw paint surface, with no widget tree,
  stylesheet, router or host;
- the only `Host` was the package-private `Launcher`, which owned exactly one
  window, and a second `Goldberry.launch` is refused while the loop runs.

`Host.popup(…)` windows are menus, attached panels and tooltips: undecorated,
not movable, light-dismissed and owned. So every dialog had to be an in-window
modal.

Most of what a second window needs was already there. The runtime keeps one
registry of windows and dispatches each event to its own, and a `Popup`
already holds an element tree, a render tree and a router of its own over the
launcher's renderer: ADR-0103's "a popup is a second tree in a second window".
What was missing was the shape of the launcher. Its tree, router, overlays,
popups, tooltips and frame sat in the same class as the stylesheets, fonts,
models and clock, so there was nothing to make a second of.

## Decision

**The launcher is split into what belongs to the application and what belongs
to a window, and a host can open another window.**

- `Launcher` keeps the **application's** state: the stylesheets, the font
  book, the models and their subscriptions, the clock, the command-line flags
  and the shutdown order. It is no longer a `Host`.
- `HostedWindow`, new and package-private, is **one window's** state and that
  window's `Host`: the window, its element tree, render tree, router, frame
  sequence and renderer, its overlays, popups, tooltips, context menus,
  accelerators, focus and theme listeners. The first window is one, and so is
  every window opened after it. The code moved; nothing is duplicated.
- `Host.openWindow(WindowSpec, Widget root)` returns `Optional<WindowHost>`.
  `WindowHost` is a `Host` plus `close()`, `isOpen()` and `onClose(Runnable)`.
  The default is empty, so an offscreen host or a test host says it has no
  desktop to open one on.
- **What is shared:** a `restyle()` on any host restyles every window, because
  the sheets are the application's. A model change repaints and restyles every
  window, and a tray row repaints every window. One font book and one clock
  serve all of them, so a second window's spinner turns with the first one's.
  Each window still builds its own renderer, because a renderer keeps that
  window's frame statistics and its desktop's theme and motion settings.
- **Ownership** is `render.window.Ownership`, on the spec: `NONE` (the
  default), `OWNED` (`SDL_SetWindowParent`: kept above its owner, minimized with
  it, closed with it), and `MODAL` (`SDL_SetWindowModal` as well).
  `Window.open` refuses anything but `NONE`, having no owner to give. An owned
  window with no position of its own opens centred on its owner.
- **A modal window blocks its owner in the toolkit, not only the platform.**
  Windows disables a modal window's owner and an X11 window manager may not, so
  the runtime drops pointer, key, text and drop events for a window that has a
  modal one open. A press there raises the modal window instead, or flashes it
  where the desktop will not raise it. The owner's popups and tooltip close as
  the modal opens.
- **Closing.** The first window is the application's: closing it closes every
  other window and the loop ends, which is what `Goldberry.stop()` already did.
  Closing any other window closes the windows that belong to it first, because
  SDL destroys a parent's children with it; the SDL and headless backends also
  close them themselves, so no handle is left dangling. The window's tree is
  taken down on the next turn of the loop rather than inside the handler that
  asked for the close, and then `onClose` runs. `application.stop()` and the
  font book still close last, after every tree.
- **Focus.** A popup is dismissed when the application loses focus, as before,
  and now also when another of its own windows gains it. Popups are not
  focusable, so the only way the user leaves a menu open over one window is by
  working in another.
- `SDL_SetWindowParent`, `SDL_SetWindowModal` and `SDL_RaiseWindow` are
  exported and bound, in the same ABI bump as ADR-0541's display symbols.
  `Window.raise()` is public, which is what an application calls on a settings
  window that is asked for while it is already open.

## Consequences

- Sign-in, settings and the wizard can be windows. A widget inside one reaches
  its own window's host through `BuildContext.host()`, and so opens popups,
  overlays and file dialogs that belong to that window.
- `Goldberry.launch` still refuses a second launch. A second window is a window
  of the same application, not a second application.
- `Offscreen` and `SessionHost` are unchanged. Their default `openWindow` is
  empty.
- The launcher's per-window logic has one home. ADR-0103's popup still reads
  its window's renderer through a supplier, which is now `HostedWindow`'s.

## Left open

- `--frames=`, `--size=`, `--resize=` and `--late-budget=` still describe the
  first window only.
- A second window has no `Application.icon()` of its own; it gets the
  application's.
- macOS and Windows ownership, modality and the parent's input being disabled
  by the platform were not run here; Linux X11 and Wayland, and the headless
  backend, were.

## Alternatives considered

- **A second `Goldberry.launch`.** Two launchers would mean two font books, two
  clocks and two sets of model subscriptions, so a theme switched in one window
  would not reach the other.
- **Copying the launcher's per-window code into the new window class.** That is
  the duplication `Offscreen`'s `FrameSequence` was built to remove, and every
  fix to tooltips or popups would have to land twice.
- **Leaving modality to the platform.** On X11 that leaves a modal dialog with a
  live window behind it, which is the bug a modal exists to prevent.
