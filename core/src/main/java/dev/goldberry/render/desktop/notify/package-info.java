/// Desktop notifications and the badge on the application's dock or launcher
/// icon, in the toolkit's own words.
///
/// `Notification` is the value an application posts through
/// `Host.notify`; `BackendNotifier` is the backend SPI that shows it, and
/// `PlatformNotifier` the one for the desktop the process runs on.
/// `NotificationCenter` is what a host keeps to post through, and to look for
/// clicks on Linux. Everything fails soft: a desktop that will not show a
/// notification answers false. Exported to every module.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Notifications](https://goldberry.dev/docs/guide/windows.html#notifications).
@NullMarked
package dev.goldberry.render.desktop.notify;

import org.jspecify.annotations.NullMarked;
