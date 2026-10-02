/// The backend SPI's tray icon: a picture, a hover text and a menu in the
/// desktop's notification area.
///
/// `TraySpec` describes the icon and `TrayItem` one row of its menu; `BackendTray`
/// is the icon once the desktop shows it. Exported to every module, because an
/// application describes its tray in these and the `tray-icon` widget is built
/// from them.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-tray-icon).
@NullMarked
package dev.goldberry.render.tray;

import org.jspecify.annotations.NullMarked;
