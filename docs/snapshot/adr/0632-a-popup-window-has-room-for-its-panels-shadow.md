# ADR-0632: A popup window has room for its panel's shadow

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** ADR-0104 (a popup is placed by its measured size), ADR-0312
  (the catalog puts the two new properties on), on `popover`, `menu` and
  `tooltip` going without a shadow

## Context

`card`, `dialog`, `tour-card`, `toast` and an affixed bar wear the design
system's elevation shadows (ADR-0312). The three panels that float in popup
windows did not, because a popup window was created at exactly its panel's
measured size (ADR-0104). A shadow is drawn outside the box that casts it, so
all of it fell outside the window and was clipped.

The fix the TODO entry named needs a window larger than the panel, with the
extra transparent. That only works where the platform shows a transparent
pixel as transparent. Wayland, Windows and macOS composite every window.
X11 does when a compositing manager runs, and since ADR-0628 a popup on X11
gets a 32-bit visual so its alpha survives. Without a compositing manager, X11
shows those pixels black, so the margin would be a black frame around every
menu.

## Decision

**A popup is its panel plus the room the panel's `box-shadow` reaches, where
the platform shows transparency.**

- `Backend.seeThroughPopups(owner)` answers whether it does. It defaults to
  false. SDL answers true on `wayland`, `windows` and `cocoa`, and on `x11` only
  while the screen's `_NET_WM_CM_S<n>` selection has an owner, asked through
  `XInternAtom` and `XGetSelectionOwner` (`XlibCompositing`) for each popup. The
  headless backend answers false unless a test turns it on.
- The room is `PopupMargin.of(shadows)`: on each side, the farthest any outer
  shadow of the popup's **root** box reaches, rounded up to whole pixels so the
  panel stays on the pixel grid. Level 2 (`0 8px 32px`) is 16, 8, 16 and 24.
- **Placement measures the panel, never the margin.** The panel is placed
  against its anchor as before, and the window opens at that place minus the
  margin, grown by it. `Popup.offset()`, `bounds()`, `move` and `resize` keep
  speaking of the panel, so a submenu beside a menu and a popover following
  its anchor are unchanged.
- The panel is painted inset by the margin inside a padding box that owns
  nothing, so nothing in the margin is hit. **A press in the margin is a press
  outside**: it dismisses as a press on nothing in the owner window does.
- `popover` and `menu` wear `--gb-elevation-2`, and `tooltip` wears
  `--gb-elevation-1`. The edges stay, for ADR-0166's reason.

Rejected:

- **Letting the margin pass input through to the window below**: SDL has no
  per-region input shape for a popup on every driver, and the owner window
  already takes a dismissing press rather than letting it through as a click,
  as every desktop menu does.
- **A margin everywhere, and black corners as the price**: a black frame around
  every menu on an uncomposited X11 desktop is worse than no shadow.
- **Reading the shadow from anywhere in the panel**: a shadow inside the panel
  is inside the window already. Only the root's reaches past it.

## Consequences

- `PopupShadowTest` opens a level-2 panel on the headless backend with
  transparency on. The window is 132×72 for a 100×40 panel, the panel's bounds
  match a run with transparency off, the shadow's pixels are under the panel,
  a press in the margin dismisses, and one on the panel does not.
  `PopupMarginTest` pins the arithmetic. `Sdl3PopupTest` asks the real driver.
- Eight menu goldens, five tooltip goldens and the `menu` and `popover` book
  pictures moved: the panels drawn inline show their shadows now.
- On this machine's X11 session, XWayland under GNOME, the selection has an
  owner, so popups get the room. Wayland, Windows and macOS were not checked by
  eye. On Wayland a compositor that slides a popup back on screen may move a
  panel near the screen's edge by up to the margin.
- A menu taller than the screen is wrapped in a viewport, whose root is the
  viewport and not the menu, so it opens without a shadow.
- The guide's elevation paragraph and its popup section change at release
  (`docs/snapshot/guide-popup-shadows.md`).
