# 270. A popup is placed again when its window moves, and when its anchor does

Date: 2026-09-05

## Status

Accepted. Finishes [ADR-0231](0231-a-popup-is-placed-again-when-its-anchor-moves.md), whose
resize half shipped and whose other two halves were left as a TODO entry naming
what each of them needed.

## Context

The entry was precise about what was missing, which is what made it answerable:

> **A window that *moves* does not re-clamp its popups, and a scrolling anchor
> does not drag one.** The **resize** half is built […] A window move is not an
> event: there is no `BackendEvent.Moved`, so re-clamping a menu that was flipped
> against the work area at the old position needs an SPI event, an SDL
> translation and a fabricated-event test of ADR-0061's shape. And a `popover`
> following a scrolling anchor needs the *anchor* to report that it moved, which
> is `Located`'s shape and a widget-level wiring rather than a window-level one.

Both halves are the same sentence — *put the popup back where it belongs* — and
they are missing for two different reasons. A **move** changes nothing about the
anchor and everything about the screen: a popup is placed as an offset from its
owner, so dragging the window carries the menu along, and the only thing that
moved underneath is the work area's position *in the window's own coordinates*.
A menu flipped above its button because there was no room below it has to be
asked the question again at the new position, and nothing was asking.

A **scroll** is the mirror image. The window is where it was, the work area is
where it was, and the widget the menu hangs off is drawn a hundred pixels higher
than it was last frame — because a scroll is a translation on the content and
Yoga never sees it ([ADR-0114](0114-a-clip-is-a-rectangle-the-painter-carries.md),
[ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).

## Decision

### A move is an event, because a move is not a resize

`BackendEvent.Moved` carries the window and its new position in the desktop's
logical coordinates. `SdlEventType.WINDOW_MOVED` is `0x205`, and like every
constant in that enum it is checked against the compiled SDL by the layout probe
— which caught it being unregistered in `goldberry_shim.c` before anything else
did, exactly as `SdlEventType`'s own javadoc promises it would.

Three things fall out of it being a separate case rather than a flavour of
`Resized`:

- **No repaint follows one.** The frame on screen is still correct: nothing
  inside the window moved. `Window.handleMoved` therefore does not call
  `repaint()`, which is the one line that distinguishes it from `handleResize`.
- **The re-placement happens immediately**, not after the next paint. ADR-0231
  had to defer the resize case because `anchor(id)` answers from the capture the
  *last* paint produced, and during a resize handler that capture is the old
  window's. A move produces no new capture and invalidates none: the current one
  is the right one, and waiting for a paint would mean waiting for an unrelated
  frame that may never come.
- **The position is read off the window, not out of the event**, for the reason
  the sizes already were: one place asks the platform, and `position()` is what
  every other caller uses.

`Sdl3Window.movedTo` deduplicates, because SDL sends `WINDOW_MOVED` for every
pixel of a title-bar drag and again for a move that put the window back where it
was. What a move costs above the SPI is a re-placement per open popup.

**The event watch is deliberately not extended to it.** ADR-0060 has the watch
draw during a resize drag because the contents change while the platform's modal
loop is running; during a *move* drag they do not, so the queued events are
enough and a popup is re-placed once the drag ends rather than per pixel of it.

### A scrolling anchor is a frame, not a report

The entry proposed `Located`, and the anchor does not need it. `Host.anchor(id)`
already answers from `HitTest.capture`, which is taken **every frame** — so the
question "where is that widget now" has a fresh answer on every frame without
anybody reporting anything. What was missing was somebody asking it.

So `replacePopups` runs at the end of any frame in which a popup is anchored **by
id**, alongside the two events. Anchored by id and not by rectangle, which is the
whole of the guard: a popup opened against a rectangle a caller computed has
nothing to re-resolve — the rectangle is all there ever was — so a window with no
id-anchored popup open pays one field read per frame and nothing else.

`Located` would have been the wrong shape twice over. It reports **on a change**,
which is one frame after the change for the first frame of a scroll; and it would
put the wiring on the anchor, so every widget an application wants to hang a
`popover` off would have to opt in. The popup is what wants to follow, and the
popup is where the wiring now is.

### An anchor rectangle is the painted one

This is the part that had to change rather than be added, and it was wrong before
anything scrolled.

`HitTest.Region` has two rectangles. `bounds()` is what layout produced;
`painted()` is where the box was drawn, which differs exactly when something
above it was transformed. Both javadocs said, in as many words, that *a popup
anchors to `bounds()` — a menu belongs under where its button sits in the flow*.

A button inside a `scroll` sits in the flow four hundred pixels below the
viewport it is drawn in. Anchoring to the flow rectangle opens its menu four
hundred pixels away from it, and this was true on the day a menu was first opened
from a scrolled list — following the anchor afterwards would only have kept it
faithfully in the wrong place.

So `Launcher.anchor`, `Popup.anchor` and `Menus.open` all read `painted()`. **For
every box nothing transformed the two rectangles are identical**, which is nearly
every anchor there has ever been and the reason this was not a visible bug
sooner. `tour` had already reached the same conclusion for its veil
([ADR-0123](0123-a-pinned-box-paints-after-its-siblings.md)) and named the popup rule as
its contrast; that note now says the two agree.

## Alternatives considered

- **Re-placing every popup on every frame.** Simpler by one method, and it moves
  popups that have nothing to follow: a rectangle a caller computed is not a
  question with a new answer, and re-clamping it per frame is a window that can
  drift under a control that never asked to move.
- **`Located` on the anchor.** What the entry proposed. One frame late at the
  start of a scroll, and it makes following a property of the widget being
  anchored to rather than of the popup that wants to follow.
- **Re-opening the popup rather than moving it.** `Popup.move` exists for this
  and is cheaper: the tree stays mounted, the keyboard stays where it is, and
  nothing flickers. ADR-0231 settled this and it has not changed.
- **Treating a move as a resize.** It would repaint the window for a move, which
  is a full frame per pixel of a title-bar drag for a picture that did not change.

## Consequences

- **A menu re-clamps when its window is dragged near a screen edge**, and a
  `popover` travels with an anchor that scrolls under it. §7's "placement with
  flip/shift when near edges" is now a promise about where the window *is* rather
  than about where it was when the popup opened.
- **`BackendEvent` gained a case**, so every exhaustive switch over it had to say
  what it does with a move — which is what that interface being sealed is for.
- **The headless backend can produce one.** `HeadlessWindow.moveTo` now posts the
  event a window manager would send, so the whole path is reachable in CI, and
  the SDL translation has a fabricated-event test of
  [ADR-0061](0061-the-events-a-test-cannot-produce-are-pushed.md)'s shape under the `dummy`
  driver.
- **A `popover` follows; a `menu` and a `select` do not.** Following is a
  property of having been opened **by id**, and `Popover` is the widget that is —
  `host.popup(new Popover(items), "menu-button", Placement.BELOW)` is its
  documented shape, and it is the widget the entry named. `Menus` and
  `SelectState` resolve their anchor to a rectangle themselves because they need
  a minimum width and a `Fit` as well, and there is no `Host` overload that takes
  all three. Giving them one is a small piece of work nobody has asked for; it is
  in `TODO.md` rather than done here on the guess.
- **A popup whose anchor scrolls out of sight follows it out of sight**, clamped
  to the work area rather than dismissed. Whether it should instead close is a
  behaviour decision nobody has asked for; it is in `TODO.md` rather than guessed
  at here.
