# ADR-0545: On macOS a menubar is the application's menu bar

- **Status:** Accepted. Amends [ADR-0163](0163-a-menu-bar-owns-its-menus.md)
  for macOS; extends [ADR-0191](0191-a-tray-is-a-menu-somebody-else-draws.md)'s
  platform-drawn rows to a second surface.
- **Date:** 2026-10-02
- **Relates to:** [ADR-0039](0039-macos-needs-the-first-thread.md),
  `docs/goldberry-gaps.md` #23

## Context

`menubar` is a widget in the window (ADR-0163). On Windows and Linux that is
what an application's menu bar is. On macOS it is not: the menu bar is at the
top of the screen, it belongs to the application rather than to a window, and
it starts with a menu named after the application holding About, Hide and Quit.
Deploy Orc's Release, View, Window and Help menus were drawn inside its window
on a Mac, under an application menu SDL had made with nothing of the
application's in it. Nothing in the toolkit built an `NSMenu` except the tray.

Two more facts shaped the decision:

- A shortcut means the modifiers it names, and the toolkit never quietly turns
  `Ctrl` into `Cmd` (`Shortcut`, `PrimaryModifier`). But a menu's accelerators
  are written once for every platform, almost always as `Ctrl+…`, and a Mac
  File menu that says ⌃O is one nobody can use.
- `NSMenu` performs its own key equivalents, and SDL hands every Cmd key event
  to `NSApp` as well as to its own queue. An accelerator bound both in the
  window's shortcut map and as a key equivalent would run twice.

## Decision

**On macOS a `menubar` hands its headings to `NSApp.mainMenu`, after the
standard application menu, and draws nothing in the window. Everywhere else it
is unchanged.**

- `Host.applicationMenu(List<AppMenuItem>)` asks the backend's `BackendMenuBar`
  to show the headings and answers whether it did. An empty list puts back the
  menu that was there before. `NONE` is the answer off macOS and in the headless
  backend, so the bar stays in the window there with its keys bound as before.
- `AppMenuItem` (`render.desktop.menubar`) is the platform-drawn row, as
  `TrayItem` is for the tray: a command, a submenu or a separator, with a label,
  an optional `Shortcut`, a tick, and enabled. `AppMenus.rowsOf` translates a
  `menubar`'s `item`s and `separator`s, and other widgets in it are left out.
- The `menubar` widget asks on every build. When the platform shows the bar, it
  builds `Widget.nothing()` and binds no accelerator, F10 or Alt tap, because
  the platform fires the key equivalents itself. It puts the platform's bar back
  when it is disposed.
- **In the platform's menu bar, `Ctrl` with no `Cmd` beside it is `Cmd`.**
  `KeyEquivalent.of(Shortcut)` makes `Ctrl+O` ⌘O, keeps both when both are
  named, and spells function, arrow and editing keys as `NSEvent.h`'s
  private-use characters. The exception is scoped to the native bar, where
  AppKit and not the toolkit matches the key, and `Primary+O` needs no
  reading at all.
- The application menu is AppKit's own: About (`orderFrontStandardAboutPanel:`),
  Hide ⌘H, Hide Others ⌥⌘H, Show All, Quit ⌘Q (`terminate:`, which SDL turns into
  a quit event), all sent up the responder chain to `NSApp`.
- **AppKit is bound from Java through FFM and `libobjc`**, not through the
  native library: `ObjC` in `natives.desktop.macos` sends `objc_msgSend` in the
  shapes these calls need and registers one class of its own, whose
  `goldberryChoose:` method is an upcall stub. Every command row targets one
  object of that class and carries a **tag**, an index into the list of actions
  `MacMenuBarProjection` keeps. Actions never cross the boundary, and a rebuilt
  bar with new lambdas replaces the list. AppKit is asked again only when what it
  draws changed. `libgoldberry`'s export list and ABI are untouched.
- A top-level row that is not a submenu gets a menu of its own named after it,
  because a macOS menu bar holds only menus. Menus are built with
  `autoenablesItems` off, so a row is enabled exactly as `disabled` says.

## Consequences

- A Deploy Orc window on a Mac has its Release, View, Window and Help menus in
  the screen's menu bar, after an application menu with its own name, and the
  window has no strip of headings in it.
- The translation (headings to `NSMenuItem` descriptions, tags, key
  equivalents) is a pure function, `MacMenuBarProjection.plan`, tested on Linux.
  The AppKit calls themselves are **unverified**: they are written against
  AppKit's documentation and have not run on a Mac.
- There is no Window menu of AppKit's own (Minimize, Zoom, Bring All to Front).
  An application that wants one writes it, and Deploy Orc already has one.
- A second mounted `menubar` replaces the first in the platform's bar, and the
  last one disposed puts back whatever was there before it. Two bars in one
  window on a Mac is not a case the platform has.
- Windows and Linux behave exactly as before.
