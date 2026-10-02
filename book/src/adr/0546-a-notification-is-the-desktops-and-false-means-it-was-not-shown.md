# ADR-0546: A notification is the desktop's, and false means it was not shown

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0191](0191-a-tray-is-a-menu-somebody-else-draws.md),
  [ADR-0291](0291-a-url-scheme-is-packaging-and-packaging-is-the-applications.md),
  [ADR-0325](0325-a-build-says-what-it-can-ask-the-desktop.md),
  [ADR-0441](0441-a-web-page-is-a-window-not-a-box.md),
  `docs/goldberry-gaps.md` #24

## Context

Deploy Orc needs to say "a Gate is waiting" and "the rollout failed" while its
window is behind something else. Nothing in `Host` or `Window` could, so its
macOS build shelled out to `osascript`, which posts as Script Editor and does
nothing on Linux or Windows. It also wants a count on its dock icon. Window
attention (`SDL_FlashWindow`) is the other half of the same entry and is not
this record's.

Each desktop has its own service, and each has a condition the toolkit cannot
meet for the application:

- **Linux:** the freedesktop `org.freedesktop.Notifications` service on the
  session bus, which GNOME Shell, Plasma, dunst and mako all serve. A click on
  a notification comes back as an `ActionInvoked` **signal**, later. A launcher
  badge is the `com.canonical.Unity.LauncherEntry` signal, which names the
  application by its **desktop entry**.
- **macOS:** `UNUserNotificationCenter`, which attributes a notification to an
  application **bundle** and asks the user's permission once per bundle. A
  process with no bundle identifier, which is every plain `java` launch, makes
  it raise an Objective-C exception, and the older `NSUserNotificationCenter`
  answers nil. The dock badge is `NSDockTile.badgeLabel` and needs no bundle.
- **Windows:** a real toast is WinRT and is shown only for an application with
  an **AppUserModelID** registered against a Start-menu shortcut or package.
  The notification area's balloon (`Shell_NotifyIconW` with `NIF_INFO`) needs
  neither, and Windows 10 and 11 show it as a toast.

The desktop entry, the bundle and the AUMID are all installation, which
ADR-0291 leaves to the application.

## Decision

**`Host.notify(Notification)` and `Host.badge(…)` post through the desktop's
own service, bound from Java over FFM against a library the process already
has, and answer false, never throw, where the desktop will not show it.**

- `Notification` (`render.desktop.notify`) is a value: a title, a body, an
  optional image, and an optional `onActivate` that runs on the UI thread when
  the user clicks it, followed by the window's repaint, for the tray's reason
  (ADR-0191: input with no event behind it).
- `BackendNotifier` is the SPI, on `Backend.notifier()`. The headless backend
  answers `NONE`, so a test never puts a notification on a real desktop.
  `PlatformNotifier` is the SDL backend's:
  - **Linux:** libdbus, `dlopen`ed by name as the settings portal already is,
    on a **private** connection. Signals are read by popping the connection's
    queue, which would steal another reader's messages on the shared one, and a
    private connection can be told not to `_exit()` the process when the bus
    goes. `Notify` carries a `default` action when there is an `onActivate`, and
    a `desktop-entry` hint when the entry is known. The badge is the
    `LauncherEntry` `Update` signal with `count` and `count-visible`.
  - **macOS:** `UNUserNotificationCenter` through `libobjc`, with a delegate of
    the process's own class, so a click is reported and a notification is shown
    even while the application is in front. Permission is asked on the first
    post, through a block the toolkit lays out itself. **Without a bundle
    identifier nothing is asked**: `notify` answers false and says once, at
    info, that macOS posts notifications only for an `.app`. There is no
    fallback that pretends otherwise. The badge is the dock tile's.
  - **Windows:** the balloon, over an icon added to the notification area for
    the first notification and removed when the backend closes. Clicks are not
    reported: Windows sends them to the window the icon names, which is SDL's.
    There is no badge.
- **A Linux click is looked for, not waited for.** Nothing wakes the frame loop
  for a D-Bus signal, so `NotificationCenter`, which the launcher posts
  through, reads the bus every 250 ms on the loop's own timer while a posted
  notification could still be clicked, and stops when none can. An application
  that posts nothing, or nothing with an action, schedules nothing.
- **The desktop entry is the application's to name:**
  `-Dgoldberry.desktop.id=…`, or the `GIO_LAUNCHED_DESKTOP_FILE` a desktop sets
  when it starts an application from its entry. Without one, a Linux badge
  answers false and a notification goes without the hint.
- `Capability.NOTIFICATIONS` is reported where the library behind the
  platform's service loads: libdbus, `libobjc`, always on Windows. Like
  `WEB_VIEW`, it is not a bit in `libgoldberry`. Whether a daemon is running or a
  process is a bundle, only posting can tell.

## Consequences

- Deploy Orc can drop its `osascript` notifier once it is packaged as an `.app`.
  As a plain `java` process on a Mac, `notify` is false and the log says why.
- The Linux path is tested end to end against a bus of the test's own: a
  `dbus-daemon` from a minimal configuration and a fake notification daemon on
  it, in Java over the same libdbus. The test checks the `Notify` arguments, the
  click coming back as an activation, the `LauncherEntry` signal, and that a
  missing daemon answers 0. One notification was posted to this machine's real
  GNOME Shell and accepted, with id 10.
- The macOS and Windows paths are written against the documentation and are
  **unverified**. The `NOTIFYICONDATAW` offsets are checked on every platform
  against the structure spelled out field by field.
- No new symbol in `libgoldberry` or in the web view shim, and nothing is added
  to the native build.
- Not done: Windows toasts with an AUMID, action buttons beyond the default
  click, replacing or withdrawing a notification, and the badge on Windows.
