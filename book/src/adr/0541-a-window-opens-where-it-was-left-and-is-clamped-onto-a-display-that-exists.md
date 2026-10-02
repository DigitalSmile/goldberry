# ADR-0541: A window opens where it was left, and is clamped onto a display that exists

- **Status:** Accepted. Bumps libgoldberry's ABI from 17 to 18, together with
  [ADR-0542](0542-an-application-may-open-more-than-one-window.md) and
  [ADR-0543](0543-a-window-asks-for-attention-and-the-desktop-decides-how.md).
- **Date:** 2026-10-02
- **Relates to:** [ADR-0104](0104-a-popup-is-measured-then-placed.md),
  [ADR-0221](0221-a-window-may-open-maximized.md),
  [ADR-0304](0304-a-window-has-a-floor-and-the-desktop-enforces-it.md),
  [ADR-0504](0504-a-selection-is-published-where-the-platform-has-a-primary-selection.md),
  `docs/goldberry-gaps.md` #6

## Context

Deploy Orc runs on two displays, and its window always opened on the primary
one. It could not be put back where the user left it, because nothing in the
toolkit said where a window was or let an application say where it should
open:

- `Window` had `onMove` but no `position()`, so the starting position could not
  be read, and no `move()`;
- `Application` had `size`, `minimumSize` and `maximized`; `WindowSpec` had no
  position;
- there was no list of displays. `SDL_GetDisplays`, `SDL_GetDisplayBounds` and
  `SDL_GetDisplayName` were not bound. `SDL_SetWindowPosition` was, but only
  popups used it;
- nothing kept the bounds a maximized window restores to, so an application
  that saved the window's size on close saved the maximized one.

Two things make the naive fix wrong. A saved position is a promise about a
desktop that may not exist next time: a monitor is unplugged, a laptop leaves
its dock, and a window opened at the saved point is a window nobody can see.
And Wayland does not let an application place a top-level window at all, or
tell it where one is; SDL refuses the move with "wayland cannot position
non-popup windows".

## Decision

**A window opens where its spec says, decided while it is still hidden, and
every position is clamped onto a display that exists.**

- `WindowSpec` gains `position` (the top-left in the desktop's coordinates) and
  `display` (a display's name), with `withPosition` and `withDisplay`.
  `Application` gains `position()` and `display()`, both `Optional` and empty
  by default, which the launcher passes into the spec.
- `Window.open` applies them through `BackendWindow.place` **before the window
  is first shown**. The SDL backend creates every window hidden and shows it on
  its first present, so the window appears where it was put rather than
  jumping there.
- The rule lives in one place, `render.display.DisplayLayout`:
  1. a position on a display that exists opens there, clamped into that
     display's usable bounds;
  2. otherwise, centred on the display named by `display`;
  3. otherwise, for a position that is on no display, centred on the primary;
  4. otherwise the platform decides.

  A window too big for the area keeps its top-left corner on it, so the title
  bar stays reachable. `Window.move` clamps the same way, onto the display most
  of the window would be on, or the nearest.
- `render.display.Display` is a record: `id`, `name`, `bounds`,
  `usableBounds`, `scale`, `primary`. The id is SDL's and good for one run, so
  an application remembers a display by name. Its scale is the display's content
  scale times its mode's pixel density, which is what a window on it is drawn
  at. `Host.displays()`, `Window.displays()` and `Backend.displays()` list them,
  fresh on each call.
- `Window.position()`, `display()`, `normalBounds()` and `normalSize()` are
  public. The normal bounds follow the moves and resizes the platform reports
  while the window is neither maximized nor fullscreen, which is the rule SDL
  keeps for its own floating rectangle. A window that asked to open maximized
  ignores sizes until the platform first reports the state. They are still
  answered after the window has closed, which is when an application saves them.
- **Wayland is a capability, not an error.** `Backend.placesWindows()` is true
  for SDL's `x11`, `windows` and `cocoa` drivers and false otherwise. Where it is
  false, `position()` and `normalBounds()` are empty, `move` returns false and
  the spec's position is ignored with a debug line. `normalSize()` still works.
- All coordinates are SDL's window coordinates, the space `SDL_GetWindowPosition`
  and `SDL_GetDisplayBounds` share: points on macOS, the desktop's own units on
  X11 and Windows.
- Four symbols are exported and bound (`SDL_GetDisplays`, `SDL_GetDisplayName`,
  `SDL_GetDisplayBounds`, `SDL_GetDisplayContentScale`), the list SDL allocates
  is freed with `SDL_free`, and `GOLDBERRY_ABI_VERSION` and
  `GoldberryShim.SUPPORTED_ABI_VERSION` go to 18 for this batch.
- `HeadlessBackend` has one display by default, whose usable bounds are its
  work area, takes a desktop of several with `displays(list)`, and behaves like
  Wayland with `placesWindows(false)`.

## Consequences

- Deploy Orc saves `normalBounds()`, `isMaximized()` and `display().name()` on
  close and hands them back through `Application.position()`, `size()`,
  `maximized()` and `display()`.
- A window can never be moved off every display by the toolkit. An application
  that wants a window straddling two displays gets it moved onto the one holding
  most of it. That is a deliberate loss: a remembered straddle is rare, and a
  window lost off-screen is the bug.
- Popups are unchanged. They are still placed in their owner's coordinates
  against the work area ([ADR-0104](0104-a-popup-is-measured-then-placed.md)),
  and `BackendWindow.place` is not a popup's `move`.
- Multi-display behaviour on macOS and Windows was not run here. The binding is
  exercised on Linux against the real library, and the rule in `DisplayLayout`
  is platform-free and unit-tested.

## Alternatives considered

- **Restoring the saved position as given.** It is right until the monitor goes
  away, and then the window opens where nobody can see it.
- **Identifying a display by its SDL id.** It is not stable across runs, or
  even across an unplug in one run.
- **Clamping only the title bar onto a display.** It keeps a straddling window
  where it was, but there is no portable title-bar height to clamp against, and
  a window half off-screen is still not where the user can work with it.
- **Treating Wayland's refusal as an exception.** A position that cannot be
  honoured there is the normal case on a large share of Linux desktops, and an
  application should not need a `try` to open a window.
