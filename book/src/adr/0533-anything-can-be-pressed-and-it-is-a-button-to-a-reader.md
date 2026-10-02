# ADR-0533: Anything can be pressed, and it is a button to a reader

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0232](0232-modality-is-one-flag-and-not-a-scrim.md),
  [ADR-0260](0260-a-name-is-an-attribute-every-widget-has.md),
  [ADR-0327](0327-a-hover-is-a-node-property-not-a-menus.md),
  `docs/goldberry-gaps.md` entry 20, `docs/gaps.md` G56 and G50

## Context

Two applications asked for the same thing. Deploy Orc wrote its own
`Pressable` for release rows, environment lanes, wizard picks and connection
chips: click, `Enter` and `Space`, and focus. Tessera wanted a picture in a chat
timeline to open the viewer when pressed (G56). It tried a one-row `ListView`,
which drew out of flow, and a label-less `Button`, which is refused because it
has nothing to read out. It shipped a `Button` labelled "Open" in the corner of
every picture.

G56 offered three shapes: a `Pressable(onPress, child)` container,
`onPress` on `ImageView`, and `Attributes.onPress` beside `onPointerEnter` and
`onPointerExit`.

## Decision

**A `pressable` container, in `dev.goldberry.widgets.controls.pressable`. It is
a button in every way but its box, and it needs a name.**

- `Pressable(name, onPress, disabled, content, attributes)`, with
  `new Pressable(name, onPress, Widget... content)` for short. In markup it is
  `pressable name="…" press="…" { … }`, and `disabled=` disables it.
- The behaviour is `Button`'s, line for line. It is focusable unless disabled.
  A click activates it, not a release. `Space` and `Enter` activate it, without
  repeats and without modifiers. `isDisabled` refuses activation, and the
  router's disabled walk covers its content.
- **A key that bubbles up from something focused inside it is not a press.**
  Only a key whose target is the pressable itself activates it, so `Enter` in a
  field inside a pressable row does not also press the row. A click is
  different. It bubbles from whatever was hit inside, which is the point, and a
  control inside that consumes it keeps it.
- Role `BUTTON`. The name is a required component, refused when blank, because
  the content is a picture or a row of texts and there is no label to work one
  out from. That is the rule `Button` states as "nothing to read out".
  `Attributes.name` wins over it when set, as it does everywhere. Markup's
  `name=` is the component and is not kept twice.
- `controls.css` gives it a column layout, the pointer cursor, the focus ring
  and the disabled fade, as rules of its own, and nothing else. There is no
  padding, no surface and no hover wash.

**The router is not changed.** `:hover`, `:active` and `:focus-visible` already
mark the whole chain under the pointer, `elementAt` already enforces modality
(ADR-0232), and dispatch already bubbles a click to the nearest `Handles`. So
the container gets all of it by being a focusable `Handles` node. `PointerRouter.hook`
stays as ADR-0327 left it.

## Alternatives considered

- **`Attributes.onPress` through `hook`.** It is the smallest change, and it
  is the wrong one. A hook is a synthetic event delivered to one element, and
  it consumes nothing. A press must be a Tab stop, take `Space` and `Enter`,
  carry a role and a name, and match `:focus-visible`. None of that can be
  attached to an arbitrary node by an attribute. A focusable widget can do all
  of it, and the router already treats one as a control.
- **`onPress` on `ImageView`.** It solves one widget. The release rows and the
  lanes are not pictures.
- **Deriving the name from the content's text.** It would announce "2.4.0
  Released on Tuesday" for a row whose action is "Open release 2.4". The
  application knows what the press does. The text inside does not say.

## How G50 follows

G50 asks for `Attributes.draggable(payload)` and `dropTarget(accepts, onDrop)`.
Those are router state: a gesture that leaves one element and is answered by
another, with a ghost, `:drag-over` and `Esc`. `hook` is the place where a node's
attribute meets a router-derived event, and it is untouched here. So a drag
attribute can be read in the same `switch` without first undoing a press
there. A `pressable` that is also `draggable` needs the router to tell a click
from a drag before it synthesizes `CLICKED`. That belongs to the router's
gesture handling, and nothing in this widget has to change for it.

## Consequences

- Tessera's picture loses the "Open" chrome. Deploy Orc's `Pressable` can go.
- G56 is closed in `docs/gaps.md`.
- There are now two ways to press something, and the rule between them is
  short. A `button` has a label and a look. A `pressable` wraps something that
  already has a look.
