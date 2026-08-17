# ADR-0075: A gesture's origin is the router's, and a drag asks for a state

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/core-widgets.md` §3; `docs/design-system.md` §1.2, §3, §3.1;
  extends [ADR-0058](0058-a-press-captures-the-pointer.md); applies
  [ADR-0063](0063-data-flows-down-events-flow-up.md); third instance of
  [ADR-0065](0065-a-part-is-styleable-and-not-constructible.md); uses
  [ADR-0068](0068-the-transform-stack-is-java-side.md)

## Context

`toggle` is the fifth control, and it is next because of the half that is *not*
like `checkbox`. `docs/core-widgets.md` §3 asks for "switch; **drag** or
click/Space", and everything shipped so far responds to a click, a key or a focus
change — all single events. A drag is a **sequence**, and the toolkit had no way
to describe one.

The obstacle is not the pointer plumbing, which ADR-0058 already settled: a press
takes an implicit capture, so every move until the release reaches the pressed
node wherever the pointer goes. What is missing is *where the gesture started*.
A widget cannot remember it — a widget is a value, rebuilt every frame, and the
`Toggle` instance that sees the release is a different object from the one that
saw the press. There is nowhere on it for an origin to live.

## Decision

### The router reports the origin, because it is the only thing that can

`PointerEvent.dragX()` / `dragY()`: how far the pointer has travelled since the
button went down.

The router records the press position and hands it to every event it dispatches
while the button is held. This is the argument already written twice — on Tab
([ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md)) and on arrow
keys ([ADR-0073](0073-a-composite-is-one-tab-stop.md)) — reaching a third case:
**the router owns what the widget cannot see.** It is also the component whose
lifetime already matches, since the interval a drag offset is defined over is
exactly the interval the implicit capture spans.

The alternative was making `Toggle` a `Widget.Stateful` so its `State` could hold
the origin. That is a real mechanism and it is the wrong one here: it would put a
`State` object, an extra element and a rebuild lifecycle behind every switch in
order to remember two floats that the router already has, and `slider`, `knob`,
`split-pane` and a scrollbar would each need their own copy of it. The test is
therefore written against a bare widget in `:core` rather than against `toggle`,
because the next four users will look nothing like a switch.

### It is `NaN` and not zero when no button is held

Zero is a **real answer** — it is what a press with no movement gives — so a
widget reading zero cannot tell "did not move" from "no gesture in progress".

`NaN` can carry that distinction, and it carries it in a way that does not need a
guard: `Math.abs(Float.NaN) >= 8` is `false`, so an event with no origin reads as
*not a drag* through the arithmetic itself. A widget that forgets to check gets
the safe answer rather than a wrong one, which is not true of zero — with zero,
forgetting to check makes every stray event look like a press that did not move.

The `PRESSED` event itself reports a **zero** drag rather than `NaN`: the origin
is recorded before the dispatch, so a handler that reads `dragX()` on every
pointer event does not have to special-case the first one.

`ENTERED` and `EXITED` carry no origin even mid-drag. They are hover events
derived from where the pointer *is* rather than steps in a gesture, and the
router raises them whether a button is down or not.

### A drag asks for a state; a click asks for the other one

One comparison against **half the thumb's travel**:

- moved ≥ 8px — the user dragged, and the value they asked for is the direction:
  right is on, left is off, however far past the track they went.
- moved < 8px — the user clicked, so the value flips.

Eight is `travel / 2` from §3's "travel 16" rather than a number chosen by feel:
it is the point at which a thumb dragged from either end has passed the middle,
so the value asked for is the one the thumb is nearer to.

The distinction matters and is the thing a naive implementation gets wrong.
**Dragging right on a switch that is already on asks for on**, not for off. A
drag is a request for a *particular* state, which is why the handler is a
`Consumer<Boolean>` and not a `Runnable` — the second valued action in the
toolkit after `radio-group`'s. `Space`, which has no direction, is the one place
this widget reads its own value.

Through markup the value crosses as a **`String`**, through the one valued shape
`Actions` already has. A second shape would have to be a `Consumer<Boolean>`, and
erasure makes `bind(name, Consumer<String>)` and `bind(name, Consumer<Boolean>)`
ambiguous for every implicitly typed lambda — so it would cost an awkwardly named
method or a bespoke interface. ADR-0073 already wrote the rule this follows: the
value crosses as the string a document would have written, and an application
that wants another type parses it in Java, where a bad value is a bug it can see.
`slider` and `knob` arrive at the same door.

### There is no cancel gesture, and that is deliberate

Every other control here acts on `CLICKED` — a press and a release on the same
node — because dragging off a button and letting go is how a user cancels
(ADR-0058). `toggle` acts on `RELEASED`, and is the only control that does.

For a switch, dragging **is** the interaction. A drag that ends far from the
control is still a drag in that direction, which is how every platform switch
behaves. Acting on the click instead would mean a drag that left the track did
nothing, and acting on both would fire twice for one gesture.

### `toggle-track` and `toggle-thumb` are the fifth and sixth parts

The track is ADR-0065's argument a third time and it holds unchanged: two
surfaces a theme must style separately, and one `ComputedStyle` carries one
background.

The thumb is ADR-0073's argument — **two things must move independently, and the
unit of independent movement is a cascade node.** §3.1 asks for "thumb
`translate` base; track color base", and a `transform` applies down its whole
subtree, so a thumb drawn onto the track would slide the track with it. This is
the same trap the check mark hit, arriving from the other direction.

Where the thumb travels to is the **stylesheet's** decision:
`toggle-track:checked toggle-thumb { transform: translate(16px) }`. Nothing in
`ToggleThumb` knows that it moves, so a theme can change the travel, or stop it,
without a Java change. The pill needed no new drawing code either —
`border-radius: 10px` on a 20px box is §3's `full`, through the four cubics
[ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md) already ships, so **no
native symbol was added**.

The four numbers in §3's row are one arithmetic statement: `2 + 16 + 16 + 2 = 36`
across and `2 + 16 + 2 = 20` down. The padding is 2 because that is what makes the
travel 16, and `ToggleTest.metricsAddUp` asserts the leftover rather than the
padding, so changing the track width fails the test that says why.

### The thumb's colour animates, which §3.1 does not list

§3.1 says "thumb `translate` base; track color base (**same clock — they arrive
together**)" and elsewhere "anything not listed does not animate". The thumb's
*background* is not listed, and it is animated anyway.

It has to be. The thumb is a different colour on the two tracks — see below — so
a thumb whose colour snapped would arrive before the thumb did, breaking the one
thing that row actually states. Listing it is what makes §3.1 self-consistent
here, and it costs nothing: `background-color` is already on §1.7's whitelist and
already running on this duration.

### Two thumb colours, and neither may be the window's

The dark theme's off pill is `nord2` and its on pill is `nord8` — a dark grey and
a light frost blue. **No single colour clears §1.2 against both**, so a thumb that
tried would be legible in one state and lost in the other. Two tokens, and the
light theme mirrors it in the opposite direction.

A thumb has **two** constraints where the checkbox's mark has one. It must read
against its own pill, *and* it must differ from the window: only 2px of pill
separates it from whatever is behind the control, where a mark is surrounded by
its fill on every side. The first attempt took the checkbox's
`--gb-checkbox-mark-checked` value, `nord0`, which is also `--gb-bg` — so the
checked thumb read as **a hole punched through the switch** rather than a disc
sitting in it. It is `nord3` now, and the light theme's is `#ffffff` rather than
`nord6` for the same reason from the other end.

That is the third time this exact defect has been found — `--gb-checkbox-bg` was
`--gb-surface` (ADR-0073), and now this — and all three times it was **the golden
image and not a test**. A colour that equals another colour is a passing
assertion.

The light theme's off switch therefore has a *dark* thumb, which is not what iOS
looks like. §1.2 decides it: a white thumb on `nord5` is 1.4:1 and cannot be
seen, and looking conventional is not one of the principles.

### `--gb-button-height` and friends

§3's preamble asks for metrics as "component-token defaults (`--gb-button-height`
etc.); app stylesheets may override component tokens, never structure".
[ADR-0074](0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md) shipped
one `--gb-control-height` for §1.3's density, which is what §1.3 asks for and not
what §3 does.

Both, in two levels: `--gb-button-height: var(--gb-control-height)`. A density
moves all of them at once, and an application can still pin one control without
writing `button { height: … }` — which is overriding *structure*, the thing that
sentence rules out.

## Consequences

- `toggle` ships: a record, a node, a CSS type, `bind` + a valued `change`, the
  drag, `Space`, `:disabled`, the shared focus ring, and four golden images.
  **Five of thirteen controls.**
- **The drag mechanism is `:core`'s, not the toggle's.** `slider`, `knob`,
  `split-pane` and a scrollbar get a gesture origin by reading one accessor, and
  `DragOriginTest` is written against a bare widget so it stays that way.
- The showcase's switch is bound to the **same property** as one of its
  checkboxes, so dragging the switch moves the checkbox's tick. Two controls on
  one value is ADR-0063 made visible: neither owns the state and both are showing
  what the property says.
- **Open: the thumb does not follow the pointer during the drag.** It slides to
  its new position when the gesture ends rather than tracking the finger, so a
  drag reads as a switch-with-a-threshold rather than as a thing being pushed.
  §3.1's "slider/knob: drag 1:1, no animation" is the row that says what tracking
  looks like, and `toggle` has no such row — but a real switch does track. Doing
  it needs the thumb's position to come from the pointer rather than from
  `:checked`, which means an animated value the *widget* supplies, and there is
  no route for that today. `slider` will have to build one.
- **Open: `--gb-toggle-height` and the other three component tokens have no
  test that they are honoured individually.** `DensityTest` asserts every control
  moves with the density, which passes whether the indirection exists or not.
  Worth an assertion when the first application actually overrides one.
- **Open: the toggle does not shrink with a compact density**, and that is read
  off §3 rather than decided here: the rows that have a compact value carry it in
  parentheses and the `toggle` row does not. The 32-tall *row* around the pill
  does shrink, because that is `--gb-toggle-height`. Whether a 28-tall row with a
  20-tall pill in it is what §1.3 intends is a question for whoever writes the
  compact screenshots.
