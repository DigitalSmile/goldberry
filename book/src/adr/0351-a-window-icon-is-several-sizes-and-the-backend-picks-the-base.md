# 351. A window icon is several sizes, and the backend picks the base

Date: 2026-09-17

## Status

Accepted. Closes `docs/gaps.md` G40. Binds two more SDL symbols, optional in the
way ADR-0346's `SDL_OpenURL` is.

## Context

A window run from a jar or `gradlew run` showed the platform's generic icon in
the taskbar, the dock and the window switcher. Installer packaging puts an icon
in the launcher, but a running window has only what the toolkit sets on it, and
the toolkit set nothing.

Setting one is `SDL_SetWindowIcon`, a platform call behind the backend
(ADR-0004). SDL's model for sizes decides the shape of the API. The surface
passed is the **100% display scale** picture, and other sizes are hung off it
with `SDL_AddSurfaceAlternateImage` for high-DPI displays. The pinned SDL source
shows that the platforms do not read these the same way:

- **X11** copies the base surface alone into `_NET_WM_ICON`.
- **Wayland** sends every size through `xdg-toplevel-icon`, and compositors
  without the protocol refuse.
- **Windows** builds an `HICON` from the base and the alternates.
- **macOS** shows the bundle's icon in the dock whatever the window says.

And `SDL_PIXELFORMAT_ARGB8888` is straight alpha. The toolkit paints in
premultiplied BGRA, so passing its buffers through would dim every
anti-aliased edge of a mark by its own coverage.

## Decision

**`Application.icon()` returns several sizes of one picture as `Image`s. The
launcher sets them before `start`, and the SDL backend chooses the base.**

- **`Image`, not a path.** `Image.decode` already exists, and an application
  decodes from its own resources.
- **A list, not one image.** Windows wants 16 and 32, a dock 48 or more, and a
  scaled-down PNG is the blur a hand-drawn icon set exists to avoid.
- **`render.window.IconImage`** is one size in straight-alpha, native-order ARGB
  in a direct buffer, read through `Image.argb`, which unpremultiplies.
  `Window.icon(List<Image>)` converts, and `BackendWindow.setIcon(List<IconImage>)`
  receives. It defaults to false, the headless backend records the sizes, and
  the SDL backend hands them over.
- **The base is the smallest size at least 48 pixels wide, or the largest when
  none is.** X11 reads only the base, and a dock scales it, so 48 is the smallest
  base that a dock does not blur. On Windows and Wayland the alternates cover the
  other scales. This rule is the backend's because the reason for it is SDL's,
  and a caller passes sizes in any order.
- **`SDL_SetWindowIcon` and `SDL_AddSurfaceAlternateImage` are optional symbols.**
  A library built before them keeps opening windows, and the answer is false.
  Every surface is a view over Java's buffer and is destroyed before
  `SdlVideo.setWindowIcon` returns. SDL converts the base and its alternates into
  its own copy first, and the pinned source confirms that
  `SDL_ConvertSurface` carries the alternates.
- **False is an answer.** A Wayland compositor without the protocol refuses, and
  so does macOS. The launcher logs it at debug level and does not warn, because
  the window works and the desktop file or the bundle provides the icon.

## Consequences

- `libgoldberry` has to be rebuilt to export the two symbols. A stale library
  shows the generic icon.
- The showcase has an icon, `example.brand.ShowcaseIcon`: four tiles on a rounded
  plate, computed at 16, 32, 48 and 256 pixels, so the example ships no binary
  assets to demonstrate this.
- There is no `Host` method to change the icon at runtime, for example to show an
  unread badge. `Window.icon` exists, so an application can reach it through
  `host.window()`, and a `Host` method is one line when something asks for it.
