# ADR-0631: A text field has its own menu, and its Paste follows the clipboard

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** ADR-0286 (a clipboard write is an offer), on the clipboard not
  being watched

## Context

`Clipboard`'s note said nothing watches it: "the only thing that would use it
is a paste button greying itself out", and a paste button that asks
`hasText()` when its menu opens gets the same answer without any machinery.
`TODO.md` kept the entry open for "a consumer worth the plumbing".

The plan of 2026-10-12 named that consumer as "the stock text context menu's
Paste state", and the menu did not exist. `text-input` and `text-area` cut,
copy and paste on their keys only. A right-click on a field opened whatever
`context-menu=` an ancestor named, or nothing. The user chose to build both on
2026-10-18.

Two facts shaped the design. A context menu is found by a walk up from the
target to the nearest `context-menu=` name, and the application's handler
turns the name into a menu (ADR-0108; the keyboard's half is ADR-0208). A field cannot be given a name
by the toolkit, because the name belongs to the application. And a menu row
is a value described when the menu opens, so a row that should change while
the menu is open needs the menu to be told.

## Decision

**A widget may own a context menu.** `ContextMenuOwner` in `:core`'s
`input.handler` is one method, `openContextMenu(LogicalRect)`. The walk asks,
at each widget, for an application name first and then for an owner. The
first answer wins, so:

- a field the application gave a `context-menu=` opens the application's menu;
- a field inside a named panel opens its own, the deepest answer;
- an owner that declines (a disabled field) lets the walk go on;
- an owner needs no application handler, and a name with no handler no longer
  stops the walk.

The walk focuses the owner before asking, so the commands act where the caret
then shows.

**`text-input` and `text-area` own one: Cut, Copy, Paste, a separator, and
Select all.** It lives in a new `widgets.form.edit` package: `TextCommands`
is the four commands and four `can…` answers both field states already had
on their keys, and `TextMenu` builds the rows from them and opens them. The
accelerators are `Shortcut.primary`, so `Cmd` on macOS. A password field's
Cut and Copy are disabled, as its keys refuse them.

**The clipboard says when it changed.** `Clipboard.onChange(Runnable)`
returns a `Subscription`, and its default never calls, so a backend that
cannot tell is not wrong. SDL's `SDL_EVENT_CLIPBOARD_UPDATE` (0x900, checked by
the layout probe) is translated into a call on the SDL clipboard's
`ClipboardListeners`, with no window and nothing queued. The headless
clipboard tells its listeners after every write it accepts and never after a
refused one. It says *that* the contents changed and not what to: it is still
not a `Property`, because the value would be a round trip per change.

**An open menu can be given new rows.** `Menus.show(host, anchor, menu)`
answers a `ShownMenu`, whose `update(Menu)` reconciles the rows in place, so
the keyboard keeps its row. The text menu listens to the clipboard while it is
open and rebuilds its rows on each change, so Paste enables when another
application copies.

Rejected:

- **A name the toolkit registers for every field**, such as `text-edit`: the
  names are the application's, and an application that registered its own
  handler for the name would have replaced the stock menu without meaning to.
- **Asking `hasText()` when the menu opens, and nothing more**: right when the
  menu opens, wrong for as long as it stays open, which is the case the user
  sees when copying from another window to paste here.
- **Moving the caret to the right-click**: Firefox does, GTK does not, and a
  right-click inside a selection must keep it either way. Left as it is.

## Consequences

- `TextFieldMenuTest` drives the menu through the real launcher: Paste enables
  while open and pastes, Select all then Cut empties the field onto the
  clipboard, a `text-area` has the same menu, a disabled field opens none, and
  a field inside a named panel opens its own. `ContextMenuOwnerTest` holds the
  walk's order without a text field.
- A right-click on a field inside a panel that named a menu now opens the
  field's menu. That is a change an application can see; it does not break
  its code or its build.
- The native library has one more constant row and the same ABI: the exported
  surface did not change shape.
- The guide's context-menu section, the clipboard section and the two
  fields' keyboard sections gain a paragraph each at release
  (`docs/snapshot/guide-text-field-menu.md`).
