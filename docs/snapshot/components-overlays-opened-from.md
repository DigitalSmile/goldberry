# A popup is styled from where it was opened

Held back until the release (ADR-0637). Two pieces; each says where it goes.

## 1. `book/src/components/overlays.md`, after the paragraph that ends "and `popup.close()` does from Java."

A popup's contents are the root of a tree of their own, so nothing inherits
into them. Selectors still reach in from where the popup was opened: a
descendant or child combinator in an application's stylesheet steps out of the
popup's root into the widget that opened it, and on up the window. A popup
opened against an id is opened from the widget with that id, a context menu
from the widget that was right-clicked, a tooltip from the widget it describes,
a submenu from its row, and a `select`'s list or a picker's panel from the
control itself.

```css
.toolbar > popover { padding: 4px }
.sidebar menu item { min-height: 32px }
```

A popup opened against a rectangle names no widget. `OpenedFrom.call(context,
() -> host.popup(...))` names one. The rules follow the opener while the popup
is open, so a class added to the toolbar restyles a popover already showing.
The toolkit's own stylesheet does not reach in.

## 2. `book/src/components/overlays.md`, `## popover`, **Styling**

Replace "The CSS type is `popover`. A `popover button` rule sizes buttons inside
it." with:

The CSS type is `popover`. A `popover button` rule sizes buttons inside it, and
a rule naming where it was opened from, `.toolbar > popover`, reaches it from
there.
