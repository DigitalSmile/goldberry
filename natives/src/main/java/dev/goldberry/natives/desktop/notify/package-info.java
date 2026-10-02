/// Desktop notifications and the launcher or dock badge, one platform at a
/// time: the freedesktop notification service over D-Bus on Linux,
/// `UNUserNotificationCenter` and the dock tile on macOS, and the notification
/// area's balloon on Windows.
///
/// Each is a binding against a library the process already has, loaded at run
/// time — libdbus, libobjc, `shell32` — so nothing here is in the native build,
/// and each answers "not shown" rather than failing where its platform will not
/// do it.
///
/// Exported to `:core` alone, which puts the toolkit's own words on it.
///
/// Read more: [Notifications](https://goldberry.dev/docs/guide/windows.html#notifications).
@org.jspecify.annotations.NullMarked
package dev.goldberry.natives.desktop.notify;
