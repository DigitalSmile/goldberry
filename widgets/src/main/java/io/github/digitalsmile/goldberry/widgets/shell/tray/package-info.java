/// `docs/core-widgets.md` §9's `tray-icon` — an icon, a tooltip and a menu that the
/// application asks the desktop's shell to show.
///
/// The first member of the shell group that is not a widget: the shell draws it, so
/// there is no box, no style and no event to route (ADR-0191).
/// [io.github.digitalsmile.goldberry.widgets.shell.tray.TrayIcon] is a value carrying
/// the same [io.github.digitalsmile.goldberry.widgets.menu.Menu] a `menubar` holds,
/// and [io.github.digitalsmile.goldberry.widgets.shell.tray.Trays] is the call that
/// shows it, reporting absence where the platform has no tray.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.shell.tray;

import org.jspecify.annotations.NullMarked;
