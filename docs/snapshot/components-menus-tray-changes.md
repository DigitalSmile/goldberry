# A tray's menu changes in place, and its accelerators are bound

Held back until the release (ADR-0639, ADR-0640). Three pieces; each says where
it goes.

## 1. `book/src/components/menus.md`, `## The tray icon`, the Java sample

`Optional<BackendTray> tray` becomes `Optional<ShownTray> tray`, and
`tray.ifPresent(BackendTray::close)` becomes `tray.ifPresent(ShownTray::close)`.

## 2. The same section, the table

Replace the `BackendTray.close()` row with these two rows:

| `ShownTray.menu(Menu)` | Gives the menu new rows while the icon is up. The same shape changes in place; another shape replaces the rows under the same icon. |
| `ShownTray.close()` | Takes it down and gives its accelerators back. The tray outlives the process if you forget, so close it in `stop()`. |

## 3. The same section, the paragraph after the table

Replace "Icons and accelerators on rows are dropped with a warning, because the
shell draws the rows and takes neither. The menu cannot be changed in place.
Close the tray and show a new one." with:

An icon on a row is dropped with a warning, because the shell draws the rows
and takes none. An accelerator is bound on the window that showed the tray, as
a menu bar binds its rows', and is not drawn beside the row. The menu is what
the application last described: the shell ticks a checkbox before its command
runs, so a command that declines the click gives the menu again with the tick
as it was.

```java
var notifications = new Item("Notifications", () -> {
    if (!settings.mayNotify()) {
        tray.ifPresent(shown -> shown.menu(menuWith(false)));   // say no: the tick goes back
    }
}).checked(false);
```
