# ADR-0639: A tray's menu changes in place, and its rows are the truth

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0191](0191-a-tray-is-a-menu-somebody-else-draws.md)

## Context

ADR-0191 built `tray-icon` as a description handed to the shell once. Two
`TODO.md` entries followed from that:

- *The menu cannot change while the icon is up.* `BackendTray` set the icon and
  the tooltip and nothing else, so a caller closed the tray and showed it again,
  which flickers in some notification areas.
- *A checkbox's tick can end up disagreeing with the application.* The shell
  toggles it before the handler runs, and an `Item`'s command takes no argument,
  so a handler that declined left a tick nobody believed in.
  `SDL_SetTrayEntryChecked` had been left unbound on purpose: correcting one row
  would have been the only mutation in an otherwise rebuilt-from-a-description
  menu.

SDL 3.4.18 has the setters: label, checked, enabled, and removing an entry.
Read in its source, `SDL_SetTrayEntryChecked` calls no row callback on Linux,
Windows or macOS (GTK's is suppressed with `ignore_signal`).

## Decision

**A tray takes new rows while the icon is up, and the rows are the truth.**
`BackendTray.rows(List<TrayItem>)`, and at the widget level
`ShownTray.menu(Menu)`, which `Trays.show` now answers with.

- **The same shape is changed in place:** the same kinds of row in the same
  places, the same rows with a handler. A label, an enabled state and a tick
  are set on the platform's entry, and each row's handler is swapped in the
  list its upcall stub indexes. No new stub, no flicker.
- **Another shape replaces the rows** under the same icon: the old entries are
  removed and the new ones inserted. Each shape has its own arena of stubs, and
  each stub carries its shape's number, so a stub left over can never run a row
  of the new menu. The arena a reshape leaves behind is closed at the next
  reshape or when the tray closes, never while one of its stubs can be on the
  stack.
- **A tick is the description's.** A checkbox is set to what its row says,
  compared against what the platform shows rather than against the last
  description. So a handler declines a click by giving the rows again with the
  tick as it was. One that accepts gives the new tick, or nothing at all.
- **A change asked for inside a handler waits for the handler to return**, then
  applies with SDL's own callback the only frame left above it. A handler may
  replace the whole menu, the row it is running for included.
- **The native ABI is 25**: `SDL_SetTrayEntryLabel`, `SDL_SetTrayEntryChecked`,
  `SDL_SetTrayEntryEnabled` and `SDL_RemoveTrayEntry` are exported, with
  `SDL_ClickTrayEntry` for tests (ADR-0641).
- **A host's frame request reaches later rows.** A tray row is chosen with no
  event behind it, so the launcher wraps each row to ask for a frame
  (`TraySpec.andThen`). `AndThenTray` applies the same to rows given while the
  tray is up.

Rejected:

- **Binding only `SDL_SetTrayEntryChecked` and correcting the tick after the
  handler.** A command takes no argument, so nothing would tell the toolkit
  which way to correct it. The description can.
- **A checkbox whose command returns whether it accepted.** A second command
  shape on `Item`, for one platform quirk, when re-describing already says it.
- **Rebuilding the whole menu on every change.** SDL can do it under one icon,
  but every handler would get a new stub and every open submenu would vanish.

## Consequences

- `ShownTray.menu` changes a tray in place. `Trays.show` returns
  `Optional<ShownTray>` rather than `Optional<BackendTray>`, and `BackendTray`
  gains `rows`, an abstract method. Both are listed in the breaking changes.
- `SdlTrayLiveTest` clicks a real tray with `SDL_ClickTrayEntry`: a click on the
  owner's thread, a checkbox toggled first, an in-place change of a label, a tick
  and a handler, a reshape, a handler that declines, and one that replaces the
  menu from inside its own click. All six ran on this machine's GNOME session.
  `TraysTest` covers the widget half on the headless tray.
- The two `TODO.md` entries move to *Answered*.
