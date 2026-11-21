# ADR-0711: Global shortcuts are asked of the desktop

- **Status:** Accepted, as W3.8 of the downstream gaps plan (Tessera's T-29,
  owner's D15).
- **Date:** 2026-11-21
- **Relates to:** ADR-0700 (the D-Bus binding this speaks the portal over, and
  the shape of a system-library backend per platform), ADR-0701 (the Windows
  helpers beside it)

## Context

A messenger's push-to-talk has to hear a key while another application has
the keyboard, and hear it come up again. An accelerator cannot: it fires while
one of the application's windows has focus. Tessera asked for press and
release delivered while unfocused, on all four desktops (T-29), and the owner
took it (D15).

Each desktop has its own way, and none of them is SDL's:

- **Wayland** gives no client the keyboard while another has focus. The only
  way is `org.freedesktop.portal.GlobalShortcuts`, where the desktop asks the
  user, who may choose a different trigger, and reports press and release as
  `Activated` and `Deactivated`.
- **X11** has `XGrabKey` on the root window: first come, first served, with
  `BadAccess` for the second client.
- **Windows** has `RegisterHotKey`, which reports the press and nothing else.
- **macOS** has Carbon's `RegisterEventHotKey`, which reports both and asks for
  no Accessibility permission.

So a shortcut is granted, not assumed, and the API has to say why one was not.

## Decision

**`dev.goldberry.input.global`** in `:core`:

- `GlobalShortcut(id, description, trigger)`, the trigger an
  `input.key.Shortcut`, so `GlobalShortcut.of("talk", "Push to talk",
  "Ctrl+Alt+T")` parses as an accelerator does.
- `GlobalShortcuts`, sealed, with `open()`, `open(Executor)` and
  `simulated()`, and `bind(List<GlobalShortcut>, Consumer<GlobalShortcutEvent>)`
  answering `CompletableFuture<List<Grant>>`. A bind replaces the one before
  it; an empty list unbinds; `close()` lets go.
- `Grant`, sealed: `Granted(id, trigger)`, `Refused(id, Reason, detail)` with
  `DENIED`, `TAKEN` and `UNMAPPABLE`, and `Unsupported(id, detail)`.
- `GlobalShortcutEvent(id, PRESSED | RELEASED)`, one press then one release.
  Repeats are dropped. A shortcut held when its bind is replaced or closed is
  released then, so a push-to-talk never stays open.
- Opened on the UI thread, events and answers arrive there (`Goldberry.ui()`);
  `open(Executor)` names another executor.
- `SimulatedGlobalShortcuts` grants what it is asked, refuses what a test says,
  and presses on the test's thread.

**`dev.goldberry.natives.desktop.shortcuts`** in `:natives`, exported to
`:core` alone. `HotkeyBackend.forThisPlatform(applicationId)` picks one of
four backends, each bound at run time against a library the system has, so
nothing is added to the native build and the native ABI does not move:

- **`PortalHotkeys`** (Wayland): `CreateSession`, then `BindShortcuts` with
  each id, description and `preferred_trigger` (`CTRL+ALT+t`), each request's
  `Response` heard on the connection, then `Activated` and `Deactivated` for
  the session. A shortcut missing from the response is `DENIED`, a cancelled
  dialog denies all, and a portal without the interface (its `version`
  property unreadable) answers `UNSUPPORTED`. A rebind closes the session and
  opens another, because a session's shortcuts are bound once. The
  application's desktop entry id (`PlatformNotifier.desktopId()`, now public)
  is registered with `org.freedesktop.host.portal.Registry` first; a portal
  without the registry ignores it. A Wayland session never falls back to an
  X11 grab, which would hear keys only while an XWayland window of the
  application had focus.
- **`XlibHotkeys`** (X11 session): a `Display` of its own, the root window,
  `XKeysymToKeycode` on the keysym, and four `XGrabKey`s per hotkey (with and
  without Caps Lock and Num Lock, Num Lock's bit read from the modifier map).
  `XkbSetDetectableAutoRepeat` makes a held key repeat as presses only. A
  press matches key and modifiers; a release matches the key alone, because
  the active grab hears whichever key the user lets go of first. `BadAccess`
  is collected by an error handler of the class's own, installed around the
  grabs with `XSync` and put back after; an error on another display meanwhile
  goes to the handler that was there. That handler is the seventeenth upcall
  shape, `int (Display*, XErrorEvent*)`.
- **`WindowsHotkeys`**: `RegisterHotKey` with no window and `MOD_NOREPEAT`, on
  a thread whose queue receives `WM_HOTKEY`. A failed registration is
  `TAKEN`. The release is found by a 20 ms thread timer asking
  `GetAsyncKeyState` while a hotkey is held.
- **`CarbonHotkeys`** (macOS): `RegisterEventHotKey` on the application event
  target, `kEventHotKeyPressed` and `kEventHotKeyReleased` through
  `InstallEventHandler`, `eventHotKeyExistsErr` as `TAKEN`. It must run on the
  main thread (`bindsOnMainThread()`), so `:core` runs its bind and close on
  the executor. The handler is the eighteenth upcall shape, `OSStatus
  (EventHandlerCallRef, EventRef, void*)`.

The portal and X11 backends have a thread each that sleeps in `poll` on the
connection's descriptor and a wake pipe (`PollWakeup`, libc's `pipe2` and
`poll`), so nothing wakes on a timer. `Dbus` gained `unixFd()` for it.
`HotkeyKey` holds each key's keysym, keysym name, Windows virtual key and
macOS virtual key code, named as `Key` names them; `Hotkey`'s modifier bits
are `Mod`'s.

### Not the plan's low-level keyboard hook

The plan named a `WH_KEYBOARD_LL` hook for the Windows release. It was not
used. A low-level hook hears every key the user types in every application,
and Windows removes one silently when it does not return within
`LowLevelHooksTimeout`, which a garbage-collection pause in a Java upcall can
exceed: the release would then never come, and the push-to-talk would stay
open. Polling `GetAsyncKeyState` only while a hotkey is held costs up to 20 ms
on the release and needs neither a hook nor an upcall.

## Consequences

- Tested by `PortalHotkeysTest` (10) against a fake portal on a private
  `dbus-daemon`: registration, grants with the asked and the chosen trigger, a
  partial grant, a cancelled dialog, a portal without the interface, press and
  release with a repeated `Activated`, a rebind and an unbind closing the
  session, and close releasing what is held. `XlibHotkeysTest` grabs on an X
  server, refuses a second client's grab as `TAKEN` and lets go on unbind; it
  presses keys through XTest only into an `Xvfb` of its own (none on this
  machine, so that case is skipped here) or with
  `-Dgoldberry.test.x11.inject=true`. `HotkeyCodesTest` and `HotkeyStateTest`
  cover the tables, the modifier arithmetic and the press/release state on any
  platform; `GlobalShortcutsTest` (13) covers the core API with a fake backend.
- `ForeignSurface` lists `XlibHotkeys` and `CarbonHotkeys` as upcall owners and
  the four classes with system-library shapes; `ForeignSurfaceTest` counts
  eighteen upcall shapes.
- The Windows and macOS backends are **UNVERIFIED**: written against the
  documentation, marked so in the code, and run by no lane here. Their
  arithmetic is tested anywhere.
- The Wayland path has run only against the fake portal. A real desktop's
  dialog was not exercised here.
- The X11 error handler is process-wide. If another thread swaps its own
  handler in during the few milliseconds of a grab, a `BadAccess` could go to
  that handler and the grab be reported granted.
- Shortcuts on keys `Key` does not name (F13 to F24, media keys) cannot be
  asked for yet.
