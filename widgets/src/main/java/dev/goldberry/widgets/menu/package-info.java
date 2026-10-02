/// Menus: lists of commands, as popups and as an in-window bar.
///
/// [dev.goldberry.widgets.menu.Menu], [dev.goldberry.widgets.menu.Item] and
/// [dev.goldberry.widgets.menu.Separator] are the popup menu, its commands
/// with their icons, accelerators, check state and submenus, and the rules
/// between groups. [dev.goldberry.widgets.menu.MenuBar] is `menubar`. A widget
/// cannot open a popup, since that needs a `Host`, so
/// [dev.goldberry.widgets.menu.Menus] is the half that opens one.
/// [dev.goldberry.widgets.menu.Accelerators] collects the shortcuts a
/// description names so that they work while the menu is shut, and
/// [dev.goldberry.widgets.menu.MenuSignals] is how an item asks its menu to
/// open a submenu or close. The title, row, lead and chevron are parts.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html).
@NullMarked
package dev.goldberry.widgets.menu;

import org.jspecify.annotations.NullMarked;
