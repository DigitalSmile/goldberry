# ADR-0640: A tray row's accelerator is bound on the window that showed it

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0191](0191-a-tray-is-a-menu-somebody-else-draws.md)

## Context

`Trays.rowsOf` dropped an `Item`'s accelerator with a warning: a shortcut is
bound to a window, and a tray has none. The same `Item` in a `menubar` registers
one through `Accelerators`. That made the tray the one place in the catalog
where one value meant two different things depending on who drew it, which is
how `TODO.md` put it.

A tray is not windowless in practice, though. `Trays.show` takes the `Host` of
the window that showed it, and that window is where a key would arrive.

## Decision

**The tray binds its rows' accelerators on the window that showed it, as a
`menubar` binds its own.** `ShownTray` calls `Accelerators.bind(host, rows,
this)` when the tray goes up and `Accelerators.unbind(host, bound, this)` when
its menu changes or it closes. The menu's new accelerators are bound in place of
the old ones. The rules are `Accelerators`': a disabled row, a submenu and a
row with no command bind nothing, and a key that does not parse is logged and
skipped.

**The key is not drawn.** No platform's tray menu shows an accelerator beside a
row and SDL takes none, so a tray row binds a key it cannot show. That is the
same as a `menubar` row whose menu is shut.

Rejected:

- **Keeping the warning.** The entry's point stands: the same `Item` should mean
  the same thing wherever it is put.
- **Binding on every window of the application.** A `menubar`'s keys are its
  window's, and a key pressed in a second window belongs to that window's
  bindings.

## Consequences

- An application's tray `Quit` with `Ctrl+Q` quits from the keyboard while its
  window is focused, as the same row would in a menu bar.
- The keys are given back when the tray closes, so a tray closed in `stop()`
  leaves no binding behind.
- `TraysTest` binds, fires, rebinds on a new menu, and gives back on close.
- The `TODO.md` entry *An accelerator on a tray row is dropped* moves to
  *Answered*.
