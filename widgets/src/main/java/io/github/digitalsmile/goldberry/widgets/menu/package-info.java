/// `docs/core-widgets.md` §8's `menu` group — lists of commands, as popups and as an
/// in-window bar.
///
/// [io.github.digitalsmile.goldberry.widgets.menu.Menu],
/// [io.github.digitalsmile.goldberry.widgets.menu.Item] and
/// [io.github.digitalsmile.goldberry.widgets.menu.Separator] are the popup menu, its
/// commands with their icons, accelerators, check state and submenus, and the rules
/// between groups. [io.github.digitalsmile.goldberry.widgets.menu.MenuBar] is
/// `menubar`. A widget cannot open a popup, since that needs a `Host`, so
/// [io.github.digitalsmile.goldberry.widgets.menu.Menus] is the half that opens one
/// (ADR-0106). [io.github.digitalsmile.goldberry.widgets.menu.Accelerators] collects
/// the shortcuts a description names so that they work while the menu is shut, and
/// [io.github.digitalsmile.goldberry.widgets.menu.MenuSignals] is how an item asks
/// its menu to open a submenu or close. The title, row, lead and chevron are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.menu;

import org.jspecify.annotations.NullMarked;
