# ADR-0628: A transparent popup on X11 is given the screen's 32-bit visual

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** ADR-0102 (a popup is a window the platform may refuse)

## Context

A menu, a `select`'s list or a tooltip opens in a popup window of its own. Its
panel has rounded corners, and `Popup.paint` clears the frame to transparent so
that what is outside the corners is nothing. The backend creates every popup
with `SDL_WINDOW_TRANSPARENT`. The user reported black corners on rounded
popups anyway.

The showcase runs on X11, which is XWayland on this machine's GNOME session. A
probe opened a real popup and read back its X window:

| | Depth | Corner pixel | Panel pixel |
|---|---|---|---|
| as created | 24 | `00000000` | `00ff0000` (no alpha) |
| with a 32-bit visual | 32 | `00000000` | `ffff0000` |

SDL's X11 driver uses `SDL_WINDOW_TRANSPARENT` only when choosing an OpenGL
visual (`X11_CreateWindow`, `SDL_x11window.c`). A window presented through
its window surface, which every popup is, always gets the display's default
visual. That visual is 24-bit, so the window has no alpha channel and the
compositor draws the transparent corners as black. The only way SDL offers
around this is the `SDL_VIDEO_X11_WINDOW_VISUALID` hint, which names the
visual the next window is created with.

## Decision

**When SDL creates a transparent popup on X11, `SdlVideo.createPopupWindow`
names the screen's 32-bit `TrueColor` visual in
`SDL_VIDEO_X11_WINDOW_VISUALID` for that one creation, then clears the hint.**

- The visual id comes from `XMatchVisualInfo`, bound in a new unexported
  package `dev.goldberry.natives.x11` (`XlibVisuals`). Like GLib and libdbus,
  libX11 is the system's library and is found by soname, not on the export
  list. It is asked for only once SDL has reported an X11 `Display*` for the
  owner (`SDL.window.x11.display`), so the library is already loaded. No
  native ABI change.
- The hint is set to `""` afterwards, not left in place. SDL reads it only
  when it is non-empty, and an ordinary window created next keeps the default
  visual.
- If the user has set `SDL_VIDEO_X11_WINDOW_VISUALID` in the environment, it
  is left alone. SDL would let it win over the hint anyway.
- A server with no 32-bit visual leaves the popup as it was, with a debug line
  that says its corners will be black.

The same change gives `SdlVideo.SurfaceBuffer` its `SdlPixelFormat`, so a
caller can tell whether a surface has an alpha channel. `SdlPixelFormat` gains
`of(int)` and `hasAlpha()`.

## Consequences

- Rounded popups on X11 have transparent corners where a compositor is
  running. Without a compositor, X draws the corners of an ARGB window
  however it likes, as it does for every application.
- Wayland, macOS and Windows are unchanged: their drivers honour the flag
  themselves.
- `XlibVisuals` is listed in `ForeignSurface.SYSTEM_LIBRARY_SHAPES`, so a
  native image registers its downcall shape on a build machine without
  libX11.
- `Sdl3PopupTest` asserts, on a real X11 display, that a popup's surface is
  `ARGB8888` and that the next top-level window's is not. `XlibVisualsTest`
  pins the LP64 `XVisualInfo` layout and the hint's spelling.
