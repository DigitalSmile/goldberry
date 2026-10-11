# Popup corners and the overlay scroll bar (2026-10-11)

Two things the user reported, fixed together, each with a parked record in
`docs/snapshot/adr/`.

| # | Report | Status | Record |
|---|---|---|---|
| 1 | "The vanilla scroll bar is too big" | Done | ADR-0629 |
| 2 | "Black corners in rounded popups, like menus" | Done | ADR-0628 |

## 1. The overlay scroll bar

"Vanilla" was read as the stock overlay bar, not `Scrollbars.ALWAYS`.
`controls.css` now has `--gb-scrollbar-size: 8px`, `--gb-scroll-thumb-size: 4px`
and `--gb-scroll-thumb-hover-size: 8px` (it was 10/6/10). `Scrollbars.ALWAYS`
and the tab strip's bar are unchanged.

- `ScrollGutterTest.overlaySize` pins 8px and 4px.
- `docs/design-system.md` §2.4 updated. The guide's numbers are parked in
  `docs/snapshot/layout-scroll-overlay-size.md`.

## 2. Black corners on popups

Measured, not guessed. A throwaway probe opened a real popup under the default
X11 driver and read the X window back. It was 24-bit, so the alpha of the
transparent clear was dropped. SDL's X11 driver uses `SDL_WINDOW_TRANSPARENT`
only for OpenGL visuals. With `SDL_VIDEO_X11_WINDOW_VISUALID` naming the
32-bit TrueColor visual, the window was 32-bit, the corners read `00000000`
and the panel read `ffff0000`.

- `natives/.../x11/XlibVisuals` — `XMatchVisualInfo` via the system libX11,
  the GLib/libdbus pattern. It is registered in
  `ForeignSurface.SYSTEM_LIBRARY_SHAPES`.
- `SdlVideo.createPopupWindow` sets the hint around the one creation and
  clears it after. The user's own environment variable wins.
- `SdlVideo.SurfaceBuffer` carries its `SdlPixelFormat`, which gains `of` and
  `hasAlpha`.
- Tests: `XlibVisualsTest` and `SdlPixelFormatTest` (`:natives`), and
  `Sdl3PopupTest.popupIsSeeThroughOnX11` (`:core`, real display, X11 only).

Not covered: Wayland, macOS and Windows honour the flag themselves and were
not re-checked by eye here. A Wayland run would be
`-Dgoldberry.backend.videoDriver=wayland`.
