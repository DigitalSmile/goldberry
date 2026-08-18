# ADR-0102: A popup is a window the platform may refuse

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §4, `docs/core-widgets.md` §3, §7 and §8,
  completes the other half of
  [ADR-0100](0100-a-window-has-a-layer-above-its-application.md), settles a
  deferral in [ADR-0019](0019-the-backend-spis-first-cut.md)

## Context

[ADR-0019](0019-the-backend-spis-first-cut.md) left four things out of the
backend SPI — popups, a tray icon, a clipboard and a GPU surface — with a rule
for putting them back: "each needs a consumer before its shape can be decided,
and an interface designed against nothing is an interface that gets designed
twice."

Popups now have four, and they are not speculative. `select` is the one control
in `docs/core-widgets.md` §3 that M2 did not build, and the reason recorded at
the time was exactly this: "closed control + popup list (**backend popup window,
so it escapes window bounds**)". §7's `popover` is "the primitive under menus,
dropdowns, `date-picker`, `color-picker` and autocomplete", §7's `tooltip` is
attached by attribute to any widget, and §8's menus are "rendered in backend
popup windows so menus escape window bounds".

[ADR-0100](0100-a-window-has-a-layer-above-its-application.md) built the other
place an overlay can go and drew the line precisely: the in-window layer floats
things over the window, and cannot put anything outside it. A dropdown near the
bottom of a window is routinely taller than the space below its button. Clipped
to the window, a nine-item list shows four.

## Decision

**`Backend.createPopup(owner, spec)` returns `Optional<BackendPopup>`, and empty
is a normal answer.**

```java
default Optional<BackendPopup> createPopup(BackendWindow owner, PopupSpec spec) {
    return Optional.empty();
}
```

### Optional, because popup support is a property of the driver

Not of the request. `SDL_CreatePopupWindow` fails with `SDL_Unsupported` unless
the video driver declares `VIDEO_DEVICE_CAPS_HAS_POPUP_WINDOW_SUPPORT`. The four
drivers Goldberry ships against — x11, wayland, cocoa and the Windows one — all
declare it; SDL's `dummy` driver, which is what every headless test in this
repository runs under, does not.

So the refusal is a branch that runs in this repository's own CI on every
platform, not a hypothetical. A caller has to have an answer for it, and there is
one: the in-window overlay layer, at the cost of being clipped to the window.

`SDL_Unsupported` is told apart from a caller's mistake by SDL's own message. A
null parent or two conflicting kind flags is a bug and is thrown; "not supported"
is the platform and is a value.

### `BackendPopup extends BackendWindow`, and `Sdl3Popup extends Sdl3Window`

A popup acquires a frame, is painted into, presents, paces and closes exactly as
a window does, and its events arrive through the same pump under their own window
id. Modelling it as a different thing would mean a second present path, a second
pacing path and a second event lookup, all identical.

What it adds is three things that only a popup has: an owner, a kind, and a
position that means something relative to that owner. `Sdl3Window` became
`sealed … permits Sdl3Popup` rather than gaining a boolean, so a popup lands in
the backend's window map and its events find their way home by the code that was
already there.

**Popups are in `windows()`.** A caller enumerating windows to shut them down
must not leave one open because it was the wrong shape.

### Exactly one kind, and a tooltip is not focusable by being a tooltip

`PopupKind` is `MENU` or `TOOLTIP` because SDL refuses a popup that claims to be
both, and because every window manager treats the two differently — animation,
shadow, whether it appears in the window list, when it is dismissed.

The trap is that `SDL_WINDOW_TOOLTIP` alone does **not** stop a popup taking
focus: `SDL_WINDOW_NOT_FOCUSABLE` is a separate flag and SDL checks it
separately. §7 says a tooltip is "never focusable itself" and shows "on hover
*and on keyboard focus*" — which only works if showing it does not move the focus
that summoned it. So `TOOLTIP` sets both flags, and that is the whole of the
difference in the backend.

`SDL_WINDOW_NOT_FOCUSABLE` is `0x80000000`, which turned out to be the first
constant in the toolkit with the top bit set. The layout probe read every
constant into a signed `int` and refused negative values — right for a size,
wrong for a bit pattern — so a constant row's value is now read unsigned, and the
four new flags are checked against the compiled SDL headers like every other.

### Position is in the owner's coordinates, and is what was asked for

A popup's position is logical pixels from its owner's top-left — the same space a
hit test reports in, so anchoring a menu under the button that opened it needs no
conversion.

It is **remembered rather than read back**: `SDL_GetWindowPosition` reports the
display's coordinates on some drivers and the parent's on others, and the request
is the one answer that is the same everywhere.

### A resize is a request, and the fake makes you believe it

On X11 and Wayland the window manager decides when a resize happens. `size()`
keeps reporting the old size until it has — one event pump later, in practice —
and a `BackendEvent.Resized` is what says otherwise. Measured straight after the
call, a popup that was just resized reports the size it had before, which is what
`Sdl3PopupTest` found on the first run.

`HeadlessPopup` therefore **defers its resize the same way**: the size is applied
when the event is delivered, not when `resize` is called. A fake that applied it
instantly would be the one place a caller measuring too early passes its tests,
and the desktop would be where it failed.

### Placement policy is not in the SPI

Nothing here decides where a menu near a screen edge should flip to. That needs
the display's work area, the anchor's rectangle and a preference order, and it
belongs with the widget that has all three — `popover`, which §7 calls the
primitive under the rest. `PopupSpec` is the platform request such a policy ends
in.

## Alternatives considered

- **Do everything in the in-window overlay layer.** Cheaper, portable, and wrong
  for the four consumers: they are the ones whose content routinely does not fit
  in the window. It remains the *fallback* when `createPopup` is empty, which is
  a real configuration and not a theoretical one.
- **Throwing rather than an `Optional`.** It makes "this platform has no popups"
  an exception, and the caller writes a catch block to do what an `if` would
  have. The SPI already draws this line: `acquireFrame` returns empty for a
  backend with no buffer to lend.
- **A separate `PopupWindow` type not extending `BackendWindow`.** It would keep
  a popup out of `windows()` — which sounds tidy until shutdown misses one — and
  duplicate the entire present and pacing path for no difference in behaviour.
- **Binding `SDL_CreateWindowWithProperties` instead** and setting the popup
  properties by hand. It is what `SDL_CreatePopupWindow` does internally, and it
  trades one symbol for six property names typed as strings — the exact failure
  mode the layout probe exists to prevent, with no probe to catch it.
- **Deferring popups until `select` is built.** ADR-0019's rule is that an
  interface needs *a consumer*, not that it must be written in the same commit as
  one. Four are specified, and the SPI is the part that has to exist before any of
  them can start.

## Consequences

- **`select`, `menu`, `tooltip` and `popover` are unblocked at the platform
  layer**, and blocked at the widget layer on three things this record does not
  build: rendering a widget subtree into a second window's frame, routing input
  to it, and light-dismiss.
- **Nothing paints into a popup yet.** The launcher owns one window, one element
  tree and one render tree ([ADR-0093](0093-an-application-is-a-root-widget.md)),
  and a popup needs a second render tree over a *subtree* of the same element
  tree. That is the next piece of work and it is a widget-layer one.
- **Nothing dismisses a popup yet.** Light-dismiss — outside click, `Esc`, focus
  loss — is policy over events that now arrive, and belongs with `popover`.
- **Three new SDL symbols** (`SDL_CreatePopupWindow`, `SDL_SetWindowPosition`,
  `SDL_SetWindowSize`) and four new window flags, all exported from
  `libgoldberry` and all checked against the compiled headers.
- **The layout probe now reads a constant's value unsigned.** A struct's or a
  scalar's size column still refuses a negative, because a negative *there* still
  means the table is being read wrongly.
- **macOS is fine, and it was worth checking.** `VIDEO_DEVICE_CAPS_HAS_POPUP_WINDOW_SUPPORT`
  is declared by the cocoa driver as well as x11, wayland and the Windows one —
  so a menu is a real popup window on all three platforms, and the fallback path
  is for the dummy driver and for whatever a future embedded backend cannot do.
