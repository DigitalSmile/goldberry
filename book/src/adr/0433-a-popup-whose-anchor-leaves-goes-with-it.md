# 433. A popup whose anchor leaves goes with it

Date: 2026-09-19

## Status

Accepted. Closes the second of the two `TODO.md` entries
[ADR-0270](0270-a-popup-is-placed-again-when-its-window-moves.md) left behind, and
depends on [ADR-0432](0432-a-menu-is-anchored-by-the-name-it-was-opened-with.md)
for how much of the catalog it reaches.

## Context

ADR-0270 taught a popup to follow a widget that scrolls under it and recorded, in
its own last consequence, that it had not said where the following stops:

> **A popup whose anchor scrolls out of sight follows it out of sight**, clamped
> to the work area rather than dismissed. Whether it should instead close is a
> behaviour decision nobody has asked for.

The mechanism is exact about the wrong thing. `replacePopups` re-resolves the
name against the last painted frame, asks `Placement` where the popup goes, and
`Placement`'s job is to keep it inside the display's work area — so an anchor
four hundred pixels above the top of its viewport produces a placement four
hundred pixels above the top of the screen, which is clamped back down to the
work area's edge. The popup therefore does not follow its anchor out of sight at
all. It **stops at the edge of the screen and stays there**, beside whatever
happens to be drawn at that edge now, which is the thing it does not belong to.

That is a menu pointing at a widget that is no longer drawn, and it is the defect.
Everything else on the entry is about which of three repairs to make.

### What the clip can actually tell you

The entry says "the region carries the clip that would answer *is it still
visible*, so the mechanism is there". That is true, with one boundary worth
stating because relying on the wrong half of it would be silent.

`HitTest.Region` carries the clip the box was painted under, in the frame's
coordinates, and `Region.contains` already tests it first — which is what makes
"not visible" and "not clickable" the same thing for a pointer
([ADR-0114](0114-a-clip-is-a-rectangle-the-painter-carries.md)). Asking it of the
whole rectangle rather than of one point is `clip.intersect(painted)`, and the
answer is exact for the case that matters: a translation is mapped exactly, so a
row scrolled past the top of an axis-aligned viewport has an empty intersection
and nothing else does.

Two things do *not* fall out of it:

- **A row scrolled entirely away is still reported.** ADR-0114's "an empty clip
  stops the walk" is about a subtree whose *own* clip went empty — a viewport
  that has been scrolled off, not a row inside one. The row is handed to the
  visitor under its parent's clip before that check is reached, so
  `Host.anchor(id)` finds it, with a rectangle a long way outside the clip beside
  it. A caller that trusted the id resolving at all would conclude the anchor was
  fine.
- **A box with no clipping ancestor is painted under `Clip.NONE`**, which is
  infinite and admits everything. `forEachPlacedBox` starts the walk at
  `Clip.NONE` rather than at the frame, so the clip alone would call such a box
  visible for ever, wherever it had got to. Nothing in the catalog scrolls a
  window's root without a viewport in the way, so this is a gap rather than a
  bug — but it is one line to close and it is where "no longer drawn" stops being
  a figure of speech.

So the predicate is the clip **and** the window's own rectangle, and the second
half is the caller's because a region does not know what window it came from.

## Decision

### Close it

`replacePopups` asks, of every popup anchored by name, whether the anchor is
still somewhere a user could look at it. When it is not — no intersection with
the clip above it, or nothing left inside the window, or no region under that id
at all — the popup is closed.

The three candidates, and why the other two lose:

**Pin it to the viewport's edge** is what happens today, arrived at by accident
rather than chosen: `Placement` clamps into the work area and the clamp is the
pin. It keeps the popup visible and on that basis reads as the gentlest of the
three. It is the worst, because it is the only one that makes the toolkit tell a
lie. A menu is a statement about the thing it points at; parked at the top of a
scroller it points at whatever row scrolled up to meet it, and a user reading it
has no way to know it is not about that row. A bug that changes what the user
believes is worse than one that costs them a gesture.

**Hide it and bring it back** keeps the most: the submenu chain, the typeahead
buffer, a `multiple` select's half-finished set. It is also the only one of the
three that can fail *silently*, and it fails in the place a toolkit can least
afford to. A popup holds the keyboard — `Menus` focuses a row on opening, and
`Launcher.topmostKeyboardPopup` routes keys to the topmost popup that wants them
— so an invisible popup is an invisible keyboard target. `Down` moves a selection
nobody can see and `Enter` runs a command nobody chose. Making hiding safe means
dropping the focus on the way out and restoring it on the way back, which is a
focus-restoration problem of its own, and the platform half is not free either: a
hidden popup is an unmapped window, and remapping it is a restack that most window
managers will not give back exactly.

**Close it** costs the user their in-progress interaction, and that is its whole
cost — stated plainly rather than argued away. Three things make it the right
one anyway.

It is the only candidate whose failure the user can see and undo. A closed menu
is gone; the anchor is one scroll back the other way and the menu is one press
after that. Neither of the other two failures is recoverable by a user, because
in neither case can the user tell they are in it.

It is what light dismissal already means, one gesture over. A popup is dismissed
by interacting with the window behind it, and the wheel is the single gesture the
router deliberately lets through to that window while a popup is up. Scrolling the
anchor out of the viewport is not an accident of the input model; it is the user
using the thing underneath. Closing there makes the rule "a popup goes away when
you go back to the window it came from" true in the one case where it was not.

It is honest about what the toolkit knows. The popup exists because a particular
widget was under the pointer; when that widget stops being drawn, the toolkit's
reason for the popup is gone, and it has no second reason to fall back on.
`Host.anchor` has said as much since it was written — *"a rectangle for something
invisible would be a lie a menu would then point at"*. That sentence is a rule
about opening. This makes it a rule about staying open.

### Partly visible is visible

The threshold is an intersection, not a containment. A menu hanging off the last
twenty pixels of a row still points at something the user can see, and a rule that
closed it there would make small scrolls destructive and would put a hair trigger
on the most common gesture. The popup goes when *none* of the anchor survives the
clip.

### The stack above goes too

A submenu is anchored to a rectangle inside the menu it came from — it must be,
because that rectangle is not a node this window painted (ADR-0432) — so it has no
name to re-resolve and would never notice its root going. Left alone it would be a
panel of commands floating over nothing.

So closing a popup for this reason closes every popup opened after it. Open order
is containment order here: a popup opened while another was up is either its
submenu or something standing on it. That is the same sweep `dismissPopups` makes
for a press, restricted to the tail.

`lightDismiss(false)` is not consulted. It says that *input* does not close this
popup — a menu that stays up while its owner is dragged, or one under a test's
control — and an anchor that stopped being drawn is not input. A tooltip outliving
the menu item it was describing is the same orphan by a shorter route.

## Alternatives considered

- **Pin, and mark it.** Keep the popup at the viewport edge and give it a class a
  stylesheet could dim. It keeps the lie and adds a convention nobody would write
  the stylesheet for; and a dimmed menu is still a menu you can click.
- **Hide, and drop the keyboard.** The safe version of hiding. It buys back the
  in-progress interaction at the price of a focus-restoration mechanism, a platform
  remap whose result the window manager decides, and a state — open, holding a
  tree, not on screen — that nothing else in the toolkit has. Worth revisiting if a
  real interaction is ever lost to this; the recorded cost of closing is the thing
  that would justify it.
- **Close on the first frame the anchor is missing from the capture, without the
  clip test.** Simpler, and it fires when a rebuild has not yet painted the anchor
  — the capture is the last painted frame's, so an id genuinely absent from it is
  genuinely not drawn, but this would also make any future culling of off-screen
  subtrees into a menu-closing event. Testing the clip as well is what keeps the
  rule about what the user can see rather than about what the walk happened to
  visit.
- **Closing rectangle-anchored popups too**, by remembering the clip the anchor
  was under at open time. There is nothing to re-resolve: the rectangle was the
  whole of what the caller said, the caller may have computed it from something
  that is not a widget at all — a context menu's anchor is the point the pointer
  was at — and a popup that was never following cannot be discovered to have lost
  what it was following.

## Consequences

- **A menu closes when the heading it hangs off scrolls out of its list**, and so
  does a `popover`, and so does anything else opened by name. It is the first
  behaviour in the toolkit that closes a window because of something a *paint*
  discovered.
- **A `select` list is not covered.** It is anchored to a rectangle reported by
  `Located` rather than to a name, for reasons ADR-0119 gave and ADR-0432
  restates, so a field scrolled out from under an open list still leaves the list
  where the field was. That is now the only widget in the catalog for which the
  original defect survives, which is a better place for it to be than spread
  across three.
- **`HitTest.Region` gained `isVisible()`**, which is `contains` asked of the
  rectangle instead of a point. It answers about the clip only, and says so: the
  window is the caller's to know, and `Launcher.stillOnScreen` is where the two
  halves are put together.
- **An anchor that is merely not painted this frame closes its popup.** That is
  deliberate and it is the same condition `Host.anchor` refuses to open against.
  If the render tree ever learns to cull whole off-screen subtrees from the
  capture, this becomes a false positive, and `isVisible` is where the distinction
  would have to be made.
- **One existing test was asserting the defect.** ADR-0270's
  `followsAScrollingAnchor` scrolls an 80-pixel anchor by 120 and asserts the menu
  travelled the whole way — with the anchor by then entirely above the top of the
  window. That was the following working, and it is the exact picture this ADR
  calls wrong. It now scrolls by 60, which keeps twenty pixels of the anchor drawn
  and keeps the test about what it was about; the limit is
  `PopupAnchorVisibilityTest`'s subject instead. Worth recording because it is the
  only evidence that this decision changes shipped behaviour rather than filling a
  hole.
- **Five tests in `PopupAnchorVisibilityTest` now depend on a viewport that really
  clips.** `PopupLifecycleTest`'s `Scrolled` translates without clipping, which is
  why its anchor could scroll for ever and never leave; the clipping arrangement —
  the clip on the outer box and the translation on the inner one — is the only one
  that behaves like a scroll view, because a clip set on the translating box would
  move with the content it is supposed to be cutting off.
