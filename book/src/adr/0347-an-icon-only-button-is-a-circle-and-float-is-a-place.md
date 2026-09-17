# 347. An icon-only button is a circle, and `float` is a place

Date: 2026-09-17

## Status

Accepted. Builds the four `button` options §3 listed as not built after
ADR-0293: `outlined`, `square`, `circle` and `float`.

## Context

§3 gives `button` two shape classes and an outline, and says of the shape that
"a button given an icon and no label is a circle by default — an icon-only
button is a disc everywhere else in the canon, and requiring `class="circle"`
to get the obvious result is the kind of improvisation Principle 3 exists to
prevent; `class="square"` overrides it". It gives `float=#true` as the
floating action button, "a class of *placement*, not of appearance", pinned to
a window corner at the window margin with elevation 1.

Three of the four are stylesheet rules. The default circle is one line of
logic. `float` is the one that needed a design: a widget is a value inside a
tree, and a floating button is *not* in that tree — it is in the window's
overlay layer (ADR-0100), which only a `Host` can reach.

## Decision

**The shapes and the outline are classes; an icon-only button adds `circle`
unless told `square` or `circle`; and `float` is a stateful wrapper that puts
the button in the overlay layer and builds nothing in place.**

- **`button.outlined`** is a transparent fill, a 1px `--gb-border` and the
  text ink, and it composes: `button.outlined.danger` changes the border and
  the ink together, `button.outlined.primary` takes the accent. `button.square`
  is radius 0; `button.circle` is a `full` radius on a box as wide as it is
  tall. `Button#classes()` adds `circle` when the label is empty, there is an
  icon, and neither shape was written.
- **`Floated(button, corner)`** is `Widget.Stateful`. Its state attaches the
  button — with `float` added to its classes — through `Host#overlay` on the
  first build and removes it on unmount; `button float=#true corner=…` inflates
  to one, and `Widget.nothing()` is what stands in the tree. **The handler is
  read at the press**, not at the attach: a lambda is a new object on every
  build, so an equality that included it would take the button down and put
  it back every frame, losing its hover and focus each time. The overlay is
  re-attached only when the word, the icon, the disablement, the attributes or
  the corner change; the press forwards to the latest description's handler.
- **`button.float`'s elevation is an edge**, as on every floating surface in
  the sheet; a shadow here would be the first. The 0.9→1 scale on the way in
  is not built: the overlay layer has no entering state for a transition to
  run from. `book/src/TODO.md` has it.

## Consequences

- `Button.SQUARE`, `CIRCLE`, `OUTLINED` and `FLOAT` name the classes.
- The `button-icon` golden changed: its icon-only button is a disc now, which
  is the rule taking effect on the one picture that had it wrong.
- The base stylesheet's comments may not contain a `#` — `ButtonTest` reads
  one as a colour literal — so `float=true` is spelled without KDL's hash
  there.
- A floating button described in a card is drawn in the window's corner; the
  headless gallery, which has no host, draws it nowhere, so the showcase
  golden shows the card and not the button.
- The showcase's Basic screen has a card with all four.
