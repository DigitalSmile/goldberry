# A popup window has room for its panel's shadow

Held back until the release (ADR-0632, ADR-0648). Two pieces; each says where it goes.

## 1. `book/src/guide/design-system.md`, `## Shape, elevation and materials`, the paragraph after the token table

Replace "a dialog, a toast and a tour card wear level 2; a popup is an edge
rather than a shadow, because it is drawn in a window sized to itself and a
shadow would be clipped." with:

a dialog, a toast, a tour card, a `popover` and a `menu` wear level 2, and a
`tooltip` level 1. A popup's window has room for its panel's shadow where the
desktop shows transparency; where it would not, on X11 with no compositing
manager, the window is the panel alone, the shadow is not seen and the panel's
corners are square.

## 2. `book/src/guide/windows.md`, `### In a window of its own`, after the paragraph that ends "it opens where the rectangle was."

Where the desktop shows transparent pixels as transparent, the window is
larger than the content by the room the content's `box-shadow` reaches, and the
content is drawn inset by it. Placement, `bounds()`, `offset()`, `move` and
`resize` all speak of the content, never of that room, and a press in the room
is a press outside the popup.
