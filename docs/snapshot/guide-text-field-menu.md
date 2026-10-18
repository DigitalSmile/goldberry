# A text field's own menu, and the clipboard telling when it changed

Held back until the release (ADR-0631). Four pieces; each says where it goes.

## 1. `book/src/guide/input.md`, `## Context menus`, after "Bare `F10` is the menu bar's."

A widget can also have a menu of its own, which it opens without a name:
`text-input` and `text-area` open Cut, Copy, Paste and Select all. At each
widget on the walk a name comes first and the widget's own menu second, so a
field you gave a `context-menu=` opens yours, and a field inside a panel that
named one opens its own. A custom widget offers one by implementing
`ContextMenuOwner`; answering `false` passes the click on to the widgets above.

## 2. `book/src/guide/text.md`, `### The clipboard`, after the last paragraph

```java
Subscription watching = host.clipboard().onChange(() -> paste.disabled(!host.clipboard().hasText()));
```

`onChange` says that the clipboard changed, whoever changed it, your own
copies included. It does not say what to: ask `hasText()` or `types()`. A
platform that cannot tell never calls it.

## 3. `book/src/components/forms.md`, `## text-input`, `### Keyboard`, after the primary-selection paragraph

A secondary click, the `Menu` key or `Shift+F10` opens the field's menu: Cut,
Copy, Paste and Select all, each disabled while it cannot run. Paste follows
the clipboard while the menu is open, so copying in another window enables it.
A `password` field's Cut and Copy stay disabled. A `context-menu=` on the field
replaces this menu.

## 4. `book/src/components/forms.md`, `## text-area`, `### Keyboard`, at the end

The same menu as `text-input`'s opens on a secondary click.
