# 320. A popup reports where it is in the window that owns it

Date: 2026-09-15

## Status

Accepted. Closes `docs/gaps.md` G28. Generalises the correction
[ADR-0113](0113-a-submenu-is-placed-beside-its-menu.md) made for
`Popup.anchor`, one layer down and for everyone.

## Context

`color-picker` in a floating options bar opened its saturation/value plane in the
**top-left corner of the window**, however far across the screen the bar was.

`PickerField` is `Located`, so the router tells it where the frame put it, and the
state hands that rectangle to `Host.attachedPopup`. The two are in different
coordinate spaces whenever the control is inside a popup:

- `Located` is notified by the `PointerRouter` that painted the node, and a
  `Popup` has **its own** router — so a swatch 8 points from the bar's left edge is
  told it is at x=8.
- `Host.attachedPopup` places in the **owner window's** logical coordinates.

So the plane opened at (8, 30) of the main window. `date-picker`, `time-picker` and
`select` are the same shape and were the same bug: anything with a popover was
unusable inside a popup.

The application cannot correct it, because it never sees the rectangle — it passes
between two pieces of toolkit inside a widget the application does not own.

The toolkit already knew this class of problem exists and had solved it once.
`Popup.anchor(String)` says so in as many words:

> `Host.anchor` answers from the main window's geometry and knows nothing about
> what is in a popup … the answer is translated by this popup's own offset

but that route is for a menu opening a submenu, and a picker has no way to reach
it.

## Decision

**The translation goes where the mismatch is: the router.**

`PointerRouter` gains an origin — where its window sits in the coordinates
`Located` reports in — and `Popup` sets it from the backend offset on every frame,
before handing over the regions:

```java
router.locationOrigin(backend.offset());
router.updateRegions(regions);
```

Both rectangles a `Located` widget is handed are translated: the painted rectangle
and the clip. The clip has to move with it, because the two are documented as
comparable — an `affix` subtracts one from the other — and a clip in one space
beside a rectangle in another is worse than either.

**Every frame rather than once**, because a popup moves: a popover following a
scrolling anchor is *moved* rather than closed and reopened — `Popup.move` exists
for exactly that — so `move` now also asks for a repaint. The tree did not change
and the pixels did not either, but every rectangle those widgets were told is in
the wrong place, and a frame is the thing that re-reports them.

**Nothing else is translated.** Hit testing, hovering, capture and the cursor are
all answered against the window the pointer is actually in, and translating those
would be translating them twice. `Popup.anchor` reads the captured regions directly
rather than through the router, so it is unchanged and does not double-count.

For a window, the origin is `(0, 0)` and the translation is skipped by an `if`: a
window's own space *is* the space its popups are placed in.

## Consequences

Four controls became correct inside a popup without being touched — `color-picker`,
`date-picker`, `time-picker` and `select` — and so does the fifth, whatever it turns
out to be. That is the whole argument for fixing it here: the alternative fixes the
four that happen to have popovers today.

`Located`'s contract is now "the window's logical coordinates, and for a widget
inside a popup, the coordinates of the window that owns it", which is a sentence in
its javadoc rather than a caveat at four call sites.

A widget that wants to know where it is **inside its popup** cannot ask `Located`
any more. Nothing does, and the honest place for that question is the popup, which
knows its own size.

## Alternatives considered

**Have `BuildContext` carry the popup a build is happening inside**, so a picker
asks *it* for the anchor rather than the `Host`. This was the gap's second sketch
and it is a bigger surface: a new thing on every build context, a second anchoring
route for widgets to choose between, and the four controls each needing to know
which one applies. The gap itself said which it would bet on, and for the reason
above.

**Translate in `Host.attachedPopup`** by asking whether the anchor came from a
popup. It cannot: a rectangle is four numbers and carries no provenance.

**Make the picker translate it.** It would need the popup's offset, which is the
thing it has no route to, and every `Located` widget would have to do the same.
