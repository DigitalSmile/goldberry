# ADR-0089: A knob's gesture is a rate

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §7.1, `docs/design-system.md` §3 §3.1, `docs/core-widgets.md` §3

## Context

`knob` is the tenth control and the last of §3's rotary/linear family. On the
face of it a slider bent into a circle: same `min`/`max`/`step`, same keyboard
map, same `bind` and `change`. Every piece of machinery it needs looked like
something [ADR-0079](0079-a-continuous-value-is-placed-by-ratio.md) and
[ADR-0080](0080-a-value-is-measured-along-a-part.md) had already built.

None of it was.

**A slider's value is a position.** The pointer is somewhere along a track, the
fraction it sits at *is* the value, and it is read fresh on every event with no
history at all. That is why `Slider` keeps no state and why the router only ever
had to report *where* a gesture started.

**A knob has no track.** `design-system.md` §3 gives it a rate instead — "value
drag 200px per full range" — so the value is `where it started + how far you have
dragged`. And "where it started" is exactly what nothing could answer. A widget
is an immutable value rebuilt from the model
([ADR-0004](0004-three-tree-retained-declarative-model.md)), so by the second
frame of a drag the value at the press has been overwritten by the value the drag
itself asked for. The widget that sees the move is a different object from the one
that saw the press.

Three more gaps turned up behind it:

- **`Box.Mark`'s arc was a constant.** `ARC` existed for `spinner` and hardcoded
  three quarters of a circle from twelve o'clock. §3 wants 270° with a *sweep
  that is the value*.
- **Pointer events carried no modifiers**, anywhere — not in `PointerEvent`, not
  in the backend SPI, not from SDL. §3 asks for "×0.1 with fine modifier".
- **Nothing had ever handled `Kind.WHEEL`.** The route had been live and tested
  since [ADR-0061](0061-the-events-a-test-cannot-produce-are-pushed.md) and no
  widget consumed it.

## Decision

**The router remembers a third thing about a gesture, and it is not a point.**
`Handles.gestureAnchor()` is asked **once, on the press** — deepest-first along
the chain, so a press that lands on a *part* is anchored by the control that will
handle it — and handed back on every event of that gesture as
`PointerEvent.anchor()`. `NaN` by default and `NaN` outside a gesture, which is
`dragX()`'s convention and is load-bearing: a widget that read "no gesture" as an
anchor of zero would snap a knob to its minimum on every hover.

This is [ADR-0075](0075-a-gestures-origin-is-the-routers.md)'s argument one step
further. The router's implicit capture already spans exactly one gesture
([ADR-0058](0058-a-press-captures-the-pointer.md)), so it is both the only thing
that can know and the thing whose lifetime already matches. It is a `double` and
not an `Object`, because the router must not start holding application values it
cannot reason about, and every gesture that has wanted one has wanted a number.

**The fine modifier is a property of the gesture, not of the event.**
`PointerEvent.gestureModifiers()` is what was held when the button went *down*.
Reading the live modifier instead would rescale travel already covered: press
Shift 100 px into a drag and the value jumps from half a range below where it
started to a twentieth of one, without the pointer moving. Drawn perfectly,
reported nowhere, and it reads as the knob slipping.

**Modifiers are read from the platform, not latched from the last key event.**
`SDL_GetModState` joins the export list — the first new symbol since
[ADR-0086](0086-x11-is-the-linux-default-for-now.md) — and is polled inside the
same pump that produced the event, because SDL's mouse events carry no `mod`
field where its keyboard events do. `int modifiers` is threaded through all four
`BackendEvent` pointer records, both backends, `Window` and `PointerRouter`.

**`Box.Mark` gains `start` and `sweep`**, so `ARC` is the one mark whose geometry
is not fixed by its kind — because it is the one that has to show a number.
`Arc.addTo` was already fully general and already fed by
[ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md)'s cubics, so **no native
symbol was added for the drawing**; a zero sweep draws nothing, which is what a
knob at its minimum wants and needed no test for it.

**Detents are magnetic; `step` is a grid.** `step` puts every value the control
reports onto a grid, from the keyboard and the pointer alike. Detents leave the
knob continuous and pull a drag onto a nearby position. §3 pins the count's
meaning nowhere, so the pull is derived: a detent owns the middle half of the gap
to its neighbour, which leaves the outer half reachable. A pull of a whole half
would make detents a grid and delete the distinction.

**Four nodes, nested rather than stacked** — `knob`, `knob-track`, `knob-arc`,
`knob-dial`. §8's subset has no `position: absolute` and `stack` is M3's, but a
child at `width: 100%; height: 100%` with no padding is exactly its parent's box,
which *is* stacking for as long as nothing has to overlap in two directions at
once.

## Alternatives considered

**Make `Knob` the toolkit's first `Widget.Stateful` and keep the anchor in its
`State`.** No SPI change at all, and the state has exactly the gesture's
lifetime. Rejected because it puts gesture state back on the widget after
[ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md),
ADR-0058 and ADR-0075 spent three records moving it off: the press is the router's, the drag origin is the router's,
and "the value at the press" is the same kind of fact. It would also have made
`knob` structurally unlike every other control in the catalog for a reason a
reader would have to reconstruct.

**Track the drag incrementally — apply the delta since the previous move.** Needs
no anchor, only the previous pointer position. Which the widget also cannot hold,
so it moves the same problem one field along; and it accumulates floating-point
error over a long drag, so a knob dragged to the top and back does not come home.

**Latch the modifiers from the last key event.** No new native symbol, and the
router already sees `keyPressed(key, modifiers, repeat)`. Rejected: a window that
loses focus while Shift is held never sees the key release, so the flag stays down
until the next time Shift is pressed *and* let go — a control that is silently in
fine mode, with nothing on screen to say so.

**Scope the fine modifier out and ship the knob without it.** It is one line of
§3's metrics row, and `core-widgets.md` §3 lists it among the gestures. It is also
the thing that makes a knob usable for a value that matters, which is what knobs
are for.

**Give `Box` a list of marks instead of nesting two arc nodes.** One node, no
parts, no `100%` trick. Rejected because the two rings need two colours and a
`Box` carries one `ComputedStyle` — the argument every part in this catalog rests
on ([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)) — and because
`knob-track` and `knob-arc` are then not selectable, which §11 requires.

**Build the circular drag too.** §3 offers it as "circular-drag optional". It has
to decide what happens when the pointer crosses the 90° gap at the bottom, and
every answer is either a jump or a wrap that depends on which way round the user
went — which needs the accumulated angle, a *second* piece of gesture state, for a
gesture that is nobody's first choice.

## Consequences

**Gesture state has a general home now.** A splitter, a scrollbar thumb, a
text-selection drag and a canvas pan all want "what was it when this started", and
none of them will look like a rotary control. `GestureAnchorTest` is written
against a bare widget in `:core` for that reason.

**Every pointer event is four bytes larger and one SDL call more expensive.**
`SDL_GetModState` is polled per pointer event, which on a 120 Hz trackpad is a
few thousand calls a second into a statically linked function that reads a global.
Not measured, and named here so it can be if a profile ever points at it.

**The export list grew for the first time since ADR-0086**, which means a CI run
across four targets is what says this change works — the machinery that has now
caught the same class of bug three times.

**One control hovers with a rule the others do not have.** `knob:hover` changes
`knob-dial` rather than `knob`, because the control's own box paints nothing.

**The first drawing was wrong and only the golden said so.** The dial was
`knob`'s own `background` and both rings were stroked on the same box, so the
track ran *across* the body — `--gb-border` on `--gb-knob-bg` is about 1.2:1, and
the 270° of travel a user is meant to read was invisible. Every value assertion
passed. `KnobDial` exists because of it, and the knob is in
`controls-on-surface-*` rather than exempt from it.

**A gentle touchpad scroll used to do nothing on a stepped knob.** A touchpad
reports fractions of a line; a stepped knob snaps every value it reports; and
because each wheel event computes from the current value rather than accumulating,
a third of a step rounded straight back — every time. A stepped knob now moves at
**least one step** for any scroll at all. A continuous one still passes the
fraction through, which was always right.

**§3's "modifier for fine adjustment" is scoped to the drag**, because
`design-system.md` §3 attaches the ×0.1 to the value drag specifically and
`core-widgets.md` §3 lists it in one breath with the wheel and the arrows. The
two documents are not quite saying the same thing; the precise one wins, and this
sentence is the record that it was a reading rather than an oversight.

**`Scale` is not wired to `knob`.** §3 gives the dB mapping to `fader`. The range
is there and `Scale` is already a sealed interface of records, so it is an
argument away — deliberately not added because a sibling has one.
