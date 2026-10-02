/// The tray icon: an icon, a tooltip and a menu that the application asks the
/// desktop's shell to show.
///
/// This is not a widget. The shell draws it, so there is no box, no style and
/// no event to route, and there is no markup node for it.
/// [dev.goldberry.widgets.shell.tray.TrayIcon] is a value carrying the same
/// [dev.goldberry.widgets.menu.Menu] a `menubar` holds, and
/// [dev.goldberry.widgets.shell.tray.Trays] is the call that shows it,
/// returning empty where the session has no notification area. A tray given a
/// picture for each shade of shell keeps the one for the desktop's setting up,
/// through a handle that stops listening when it closes.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-tray-icon).
@NullMarked
package dev.goldberry.widgets.shell.tray;

import org.jspecify.annotations.NullMarked;
