/// macOS desktop integration that is not SDL's: the application's menu bar,
/// notifications and the dock badge, through the Objective-C runtime and FFM.
///
/// Nothing here is in the native build. `libobjc` is in every macOS process,
/// AppKit is already loaded by SDL, and the `UserNotifications` framework is
/// opened by path when a notification is first posted. Off macOS every entry
/// point answers empty.
///
/// Exported to `:core` alone, which puts the toolkit's own words on it.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-macos-menu-bar).
@org.jspecify.annotations.NullMarked
package dev.goldberry.natives.desktop.macos;
