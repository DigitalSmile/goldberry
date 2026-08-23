# 191. A tray is a menu somebody else draws

Date: 2026-08-23

## Status

Accepted. `docs/core-widgets.md` §9's `tray-icon`, and the first thing M3 owed
that begins in `goldberry.symbols` rather than in a widget.

## Context

`Backend`'s own note said trays were "absent from this cut, not dropped — each
needs a consumer before its shape can be decided"
([ADR-0019](0019-the-backend-spis-first-cut.md)). §9 is that consumer, and the
shape it needs turns out to be unlike every other widget in the catalog.

**Nothing here is painted by Goldberry.** A tray menu is a GTK menu on Linux, an
`NSMenu` on macOS and a Win32 popup on Windows. The shell chooses the font, the
row height, the highlight colour and the animation; it opens the menu, tracks the
pointer through it and closes it. The toolkit's cascade, Blend2D, Yoga and
`PointerRouter` reach none of it. So the parity invariant — Java record, KDL node,
CSS-styleable — has nothing to attach its third clause to, and a `tray-icon` in
the catalog would be a widget no stylesheet could ever affect.

The other half of the context is the export list.
[ADR-0190](0190-a-content-module-brings-its-own-natives.md) predicted, of the
camera and microphone modules, that "already in the binary" is true of the binary
and not of the surface — and named `SDL_Tray*` as the case M3 would meet first.
It did. Before this, 48 of the 192 exported symbols were SDL's and not one was a
tray call.

## Decision

### The value is not a widget, and says so

`TrayIcon` is a record in `…widgets.shell.tray` holding an icon, a tooltip and a
`Menu`; `Trays.show(host, tray)` is what puts it on the desktop. The same split
`menu` has had since [ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md)
and `toast` since [ADR-0177](0177-a-toast-is-a-queue-and-the-stack-is-the-widget.md),
with the argument one step stronger: a toast is at least *drawn* here.

**The menu it holds is an ordinary `Menu`.** Not a parallel description — the
same value a `menubar` holds and `Accelerators` walks, for
[ADR-0163](0163-a-menu-bar-owns-its-menus.md)'s reason: what is short-lived about
a menu is the popup, not the description. A tray menu, which the shell holds for
as long as the icon is up, is the longest-lived opening there is. So an author
writes one description and can show it in a window, in a context menu, or here.

### What the platform cannot draw is dropped loudly

A tray row's whole vocabulary is a label, an enabled state and a tick. Three
things an author may reasonably have written on an `Item` therefore go nowhere,
and each is **logged** rather than ignored:

- an **icon**, which no platform's tray API takes;
- an **accelerator**, which is a key bound to a window and a tray has none — the
  same command in a `menubar` still registers one;
- any widget that is neither `item` nor `separator`, because `Menu.children` takes
  any widget and a shell has nowhere to put a `text`.

A tray that quietly ignored half a description would be a menu an author kept
editing without effect.

### Absence is reported, and no error string is read

`Backend.createTray` returns `Optional`, like `createPopup`. What differs is how
emptiness is *decided*. `SDL_CreatePopupWindow`'s caller reads the error to tell
"this driver has no popups" from "you passed nonsense", because SDL says
`not supported` for the first. The tray has no such line: the Linux path fails
with **`Could not load AppIndicator libraries`**, which is an absence wearing the
words of a failure, and a session that has removed its notification area fails in
a third way. §9 asks for absence to be reported rather than thrown either way, so
**every** null from `SDL_CreateTray` is empty, logged at debug with SDL's own
words. Every platform's own guidance says a tray-using application must work
without one; the showcase logs `tray unavailable on this desktop` and carries on.

### One upcall stub per row, with its index bound in

`SDL_TrayCallback` is `(void *userdata, SDL_TrayEntry *entry)`. The obvious use of
`userdata` is an index cast to a pointer — correct, and a lie in the type: a value
that is never an address travelling in a `void *`. Instead each row gets its own
stub with its index already bound (`MethodHandles.insertArguments`), `userdata` is
NULL, and the stubs share one arena that lives exactly as long as the tray. They
are released **after** `SDL_DestroyTray`, for
[ADR-0060](0060-a-resize-draws-from-inside-sdls-event-watch.md)'s reason: native
code must stop being able to call a stub before the memory holding it goes away.

Labels and the icon surface get a *call-scoped* arena instead. SDL copies a label
into its own storage and converts the icon immediately — a `HICON`, an `NSImage`,
a file in the user's cache directory — which was checked against the pinned SDL
source rather than assumed.

### The checkbox belongs to the platform

SDL toggles a checkbox **before** it calls back, so the handler is told the new
state rather than asked to work it out, and `SDL_GetTrayEntryChecked` is on the
export list for exactly that one read. `HeadlessTray.choose` applies the same
order, which is what makes it a test double rather than a second implementation:
a test that toggled afterwards would be asserting an order no platform uses.

### The menu cannot be changed while it is up

`BackendTray` has setters for the icon and the tooltip and none for the menu. Its
rows are platform objects the shell may have open; replacing one would mean
removing and re-inserting entries underneath a user. A tray whose menu changed is
closed and opened again, which is what a declarative caller does anyway.

### A tray row asks for a frame, because nothing else will

Found by running the showcase, where **every row except Quit did nothing.** Quit
closes a window, which is a platform effect; the rest set a field on a model, and
a jar-bound model is swept at the top of a frame
([ADR-0155](0155-a-jar-binds-at-run-time-an-image-is-woven.md)). A tray row is the
only input in the toolkit that arrives with **no event behind it** — it is
delivered from inside `SDL_PumpEvents` by way of `SDL_UpdateTrays`, and no
pointer moved, no key arrived and nothing asked for a frame. So the sweep never
ran, and a handler that changed the theme changed nothing anybody could see.

`Host.tray` therefore wraps every row with the window's repaint
(`spec.andThen(this::repaint)`), which is what every other input path gets for
free. Submenus and separators are left alone: no platform calls back for either.

The general shape is worth keeping, because it is the third time it has come up
and the first time it was invisible: **a source of input the frame loop cannot
see has to say so itself.** The event watch (ADR-0060) and the timer
(ADR-0105) both had to; a tray is the one that fails silently, because there is
no missing frame to notice — only a menu that does nothing.

## Consequences

- **The export list grew from 192 symbols to 203**, and 48 SDL entries to 59: nine
  tray calls plus `SDL_CreateSurfaceFrom` and `SDL_DestroySurface`, which are how a
  painted BGRA buffer becomes an icon. `SDL_UpdateTrays` is deliberately absent —
  SDL calls it from its own event loop, and this toolkit pumps events. The five
  `SDL_TRAYENTRY_*` values went into the constant probe with everything else,
  which is what catches `DISABLED` being `0x80000000` and therefore a negative
  `int`.
- **`HeadlessTray` is the only place a tray menu can be observed at all.** There is
  no golden image of a GTK popup and nothing to hit-test, so every rule about
  choosing a row — the toggle order, a disabled row refusing, a submenu reached by
  path — is asserted against the headless backend, and the SDL backend's job is to
  be the same translation twice.
- **The `libayatana-appindicator is deprecated` line on Linux is not ours and
  cannot be silenced from here.** It is printed by the distribution's own library
  as SDL loads it, and SDL's loader tries `libayatana-appindicator3.so.1` and
  `libappindicator3.so.1` and nothing else — the `-glib` successor the warning
  names is not on its list. Fixing it is a change to SDL, on a pinned commit.
- **Two platforms are unverified.** This ran for real on Linux/X11 under
  libayatana-appindicator, in the test suite and in the showcase. The Windows and
  macOS paths are SDL's, are compiled, and have not been looked at by anybody. That
  is in `TODO.md` rather than implied by silence.
- **A checkbox's tick can disagree with the application.** The shell has already
  toggled it by the time the handler runs, and an `Item`'s command takes no
  argument, so a handler that *declines* leaves the platform showing a tick the
  application does not believe in. Rebuilding the tray is the way to say so, which
  is the same answer the missing menu setter gives.
- **The window's `menubar` and the tray now share a description and not a
  behaviour.** The same `Item` registers an accelerator in one and has it dropped
  with a warning in the other. That is the platform's limit, and it is the first
  place in the catalog where one value means two different things depending on who
  draws it.
