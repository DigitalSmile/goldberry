# ADR-0701: A Windows notification is clicked, and the taskbar button badged

- **Status:** Accepted
- **Date:** 2026-11-14
- **Relates to:** ADR-0700 (the D-Bus binding it moved, which the Linux
  notifier shares)

## Context

On Linux and macOS a click on a notification runs its action and the badge
shows a count. On Windows a click only focused the window, and the unread
count went into the tray tooltip (Tessera's G-19, here T-19; the owner took
it under D15). The balloon's click, `NIN_BALLOONUSERCLICK`, goes to the window
the notification-area icon names. That was SDL's window, whose window
procedure does not know the message. Windows has no dock badge. Its
equivalent is an overlay icon on the taskbar button, which Windows' own mail
and chat applications use.

## Decision

**The click.** The notification-area icon belongs to a message-only window of
the notifier's own (`HWND_MESSAGE` parent), registered with `NIF_MESSAGE` and
`WM_APP + 1`. Its window procedure is an upcall stub. Windows posts the icon's
messages to the thread that made the window, the UI thread, and SDL's pump
dispatches every message of that thread, so they arrive while events are
pumped and never pass through SDL's window procedure. The procedure hands the
icon's messages to `BalloonEvents` and everything else to `DefWindowProcW`.
A click activates the balloon showing; a timeout, a hide or a newer balloon
closes it. `WindowsNotifier.notify` answers an id, and `poll()` the events.
`PlatformNotifier` polls them on the same 250 ms timer the Linux click uses.
One balloon shows at a time, so only the latest notification's action can
run.

**The badge.** `ITaskbarList3::SetOverlayIcon` on the first window's
taskbar button, through a small `Com` helper in `natives.desktop.windows`
(not exported). It is the first COM vtable call in the module: read the table,
read the slot, call it with a downcall of the method's shape. The picture is
drawn by the toolkit's own painter (`BadgeIcon`): up to three characters,
white Inter on Fluent's red disc, 32 pixels square. A number past 99 shows as
`99+`, and zero or blank takes the overlay away. It becomes an `HICON` through
a 32-bit top-down DIB section; the taskbar keeps a copy, so the icon is
destroyed straight away.

The Win32 half is **UNVERIFIED**: written against the SDK's documentation,
marked so in the code, and run by no lane here.

## Consequences

- A sixteenth upcall shape, the window procedure's `(HWND, UINT, WPARAM,
  LPARAM) -> LRESULT`, with `WindowsNotifier` among the upcall owners and
  `ForeignSurfaceTest` counting it.
- `WindowsNotifier.forWindow(long)` became `open()`, and `notify` answers an
  id rather than a boolean. Both are in a package exported to `:core` alone.
- The window procedure catches everything, because an exception escaping an
  upcall ends the process.
- Tested where Windows is not needed: `BalloonEventsTest` (click, timeout,
  hide, replacement, the low word), `WindowsNotifierLayoutTest` (the callback
  message's offset and `WNDCLASSEXW`), `WindowsTaskbarLayoutTest`
  (`BITMAPV5HEADER`, `ICONINFO`, and both vtable slots counted from the
  header's declaration order), `ComTest` (a GUID's bytes and a vtable read)
  and `BadgeIconTest` (the label rules, a red disc with a white digit and
  clear corners, centred).
