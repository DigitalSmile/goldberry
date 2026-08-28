# 211. A popup asks the desktop where the pointer is

Date: 2026-08-27

## Status

Accepted. Fixes a dropdown whose rows cannot be chosen on macOS.

## Context

A press on a popup's row arrived where it should and the release did not, so no
click was ever synthesized: on macOS every menu, dropdown and suggestion panel
could be opened and hovered and not *chosen*. The pointer's own half of the
toolkit is correct — `PointerRouter` requires the release to land on the element
that took the press, which is what makes a drag off a control cancel it
(ADR-0054) — and it was being handed a release from somewhere else entirely.

**SDL promises coordinates in the target window's space and does not deliver
them for a popup here.** `Cocoa_SendMouseButtonClicks` rewrites a mouse event's
coordinates into the target window's space only when the event's `NSWindow` is
*not* the key window; a mouse-**up** is delivered to the key window, and a
`NOT_FOCUSABLE` popup can never be one — which it is by ADR-0189, so that every
popup stops holding the keyboard. So the press arrives in the popup's space and
the release in the **owner's**, both attributed to the popup, because the window
id comes from `mouse->focus` and the coordinates come from `mouse->x/y`.

Worse than a fixed offset: the owner-space value is **stale**. Nothing updates
`mouse->x/y` while the pointer is over the popup, so every release reports where
the pointer was before the popup opened. A release the router looks for inside a
40-pixel row is reported at the coordinates of whatever the user clicked last.

The two ADRs that own the neighbouring decisions could not have caught it.
ADR-0189 made the popup unfocusable for a reason that stands, and ADR-0103 gives
a popup its own tree and its own router, so the wrong coordinates were routed
confidently against the right tree.

## Decision

**A pointer event's coordinates are reconciled against the window they were
attributed to, in the backend, before the event leaves it.** This is the layer
that already knows both numbers and the last one that can tell them apart: above
it a `BackendEvent` is a fact.

**The bounds check is the detector.** A coordinate inside the window it was
delivered to is taken as given — every event on every other platform, and most
of them here. One that falls outside is a coordinate whose *space* is in doubt,
and only then is anything asked.

**The desktop is the answer.** `SDL_GetGlobalMouseState` minus the window's own
desktop origin settles the question without asking the platform what it thinks
the event belongs to. It is the one reading of the pointer that does not depend
on the attribution that is itself in doubt.

**A popup's desktop origin is its owner's position plus the offset it was asked
for.** `SDL_GetWindowPosition` on a popup reports the display's coordinates on
some drivers and the parent's on others, which `Sdl3Popup` already records; its
owner is an ordinary window and answers reliably, and the requested offset means
the same thing everywhere. Two readings that are not in doubt, rather than one
that is.

**Every window is reconciled, not only popups.** A top-level window's
coordinates are already inside its own bounds, so the branch never fires for
one — and a rule that named popups would be a rule that stops being checked the
day something else needs it.

**A window that will not say where it is keeps its coordinates.** There is
nothing better to offer, and a guess would be worse than the platform's.

## Alternatives considered

- **Make the popup focusable on macOS.** It would let SDL rewrite the
  coordinates, and it would give back exactly the bug ADR-0189 fixed — a menu
  left open over another application's window after its owner hid.
- **Offset by the popup's own origin unconditionally**, without a bounds check.
  It assumes every event is in the owner's space, which is false for the press;
  the press and the release genuinely arrive in *different* spaces, so a
  correction applied to both breaks the half that was right.
- **Track the pointer in the owner's window and use its last position.** That is
  what SDL is already doing and what is stale — the owner sees no motion while
  the pointer is over the popup, which is the whole problem.
- **Correct it in `PointerRouter`.** The router would have to know which windows
  are popups and which platform it is on, both of which are the backend's to
  know; ADR-0103's whole point is that the router sees one tree in one space.
- **Do it only on macOS**, behind a platform check. The bounds check is already
  the sharper question — it asks whether the coordinates are wrong rather than
  whether the platform is one that gets them wrong — and it costs nothing where
  they are right.

## Consequences

- **A pointer event may cost one more native call**, and only after failing its
  bounds check: a `SDL_GetGlobalMouseState` and two floats out of a confined
  arena. Never on the ordinary path, where the coordinates are inside the window
  and nothing is asked.
- **`Sdl.globalPointer()` is a second polled reading beside `modifierState()`**,
  taken at translation time for the same reason — inside the pump that produced
  the event is the closest to "when it happened" this layer can get (ADR-0089).
- **A drag off a control still cancels the click.** The desktop reading agrees
  that the pointer is outside the window; only the magnitude changes.
- **The reconciliation is tested and the attribution is not.** `SdlEventBuffer`
  gained `writeMouseMotion` and `writeMouseButton` for `writeWheel`'s reason — a
  test cannot move a pointer — so all three branches run under the dummy driver
  against the real translate: inside the window is untouched, the far edge counts
  as inside, and outside is replaced by the desktop reading. What no test here
  reaches is SDL deciding which window an event belongs to, which is a property
  of a real `NSWindow`; the dummy driver refuses popups outright (`Sdl3PopupTest`),
  so the bug itself is reproduced by running the showcase on a Mac.
