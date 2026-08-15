# ADR-0003: SDL3 as the only desktop backend

- **Status:** Accepted (recorded retroactively)
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3, §4

## Context

Windowing is where cross-platform toolkits go to die. Win32, Cocoa, X11, and
Wayland each have their own model for window lifecycle, input, DPI, clipboard,
cursors, popups, and tray icons — and the differences are not superficial. A
toolkit that writes its own backend for each is signing up for three or four
permanent, specialist maintenance burdens, and the cost is not front-loaded: it
arrives as a decade of platform-specific bug reports.

Goldberry is one project with a large surface area above the windowing layer.
Spending its effort there would starve the layers that are actually its point.

## Decision

Use SDL3 (≥ 3.2) as *the* desktop windowing backend on Linux, Windows, and macOS.
It provides windowing, input, per-monitor DPI, clipboard, cursors, popup windows,
tray icons, and SDL_GPU — the full set the backend SPI needs — and it is a
permanent dependency, not a bootstrapping shortcut to be replaced later.

The `Backend` SPI (§4) exists, but not as an invitation to grow desktop backends.
It has exactly three implementations, and that is the complete list:

- `sdl3` — every desktop OS
- `headless` — renders to a `BLImage` for golden-image tests
- `scarlet` — the Scarlet Macaw compositor, via shm buffers and later dmabuf

No hand-written Win32, Cocoa, or Wayland backend. No AWT bridge, ever.

## Alternatives considered

- **GLFW.** Rejected: no clipboard-image, tray, or popup-window support, and its
  scope is deliberately narrower than what a full toolkit needs.
- **Hand-written per-platform backends.** Rejected on maintenance cost, as above.
  This is the decision that most obviously trades control for sustainability, and
  the trade is made deliberately.
- **Wrapping platform widgets (SWT-style).** Rejected: it makes styling,
  rendering, and testing hostage to the platform, which is incompatible with a
  CSS-styled, golden-image-tested toolkit (ADR-0005, §14).
- **AWT/Swing as the windowing layer.** Rejected: it drags in the entire desktop
  module, defeats native-image startup goals, and its DPI model is a liability.

## Consequences

- One windowing implementation to maintain and reason about, and per-monitor
  fractional DPI works because SDL already solved it.
- The SPI's shape is bounded by what SDL3 can express. Where SDL lags a platform
  feature, Goldberry lags it too, and the honest fix is upstream.
- SDL3 is a hard runtime dependency of every desktop app built on Goldberry. It
  is statically linked into `libgoldberry` (ADR-0008), so this is a build-time
  fact rather than a deployment one, but it is not optional.
- `headless` and `scarlet` keep the SPI honest — an abstraction with one
  implementation rots, and these two ensure it does not.
- The software present path is a clean fit: `SDL_GetWindowSurface` plus
  `SDL_UpdateWindowSurfaceRects` maps directly onto
  `present(PixelBuffer, List<DamageRect>)` with no renderer and no GPU context,
  which is exactly what ADR-0002 requires.
