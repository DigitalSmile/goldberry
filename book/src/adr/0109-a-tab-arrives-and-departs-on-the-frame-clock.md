# ADR-0109: A tab arrives and departs on the frame clock

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/design-system.md` §1.7, `docs/core-widgets.md` §5,
  extends [ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md),
  applies [ADR-0081](0081-a-perpetual-loop-has-no-state.md)'s shape to something
  that is not a loop

## Context

Three things were wrong with `tabs` as it shipped, and each turned out to be a
different lesson.

1. **A tab added or closed did not appear until the window was resized.**
2. **The `+` was 28 wide and 20 tall**, so the mark drawn to fill it had a long
   arm and a short one.
3. **Nothing animated**, and §1.7's enter/exit lifecycle had been a specification
   with no subject since it was written.

## Decision

### A structural change needs something to subscribe to

The first was not a tab bug. `ShowcaseModel` held its tabs in a plain `List`, and
a plain list is not something a widget can watch: the toolkit rebuilds a subtree
when something it subscribes to changes, and nothing subscribed. Everything else
in that window is a *value* reaching a bound widget, which needs no rebuild — and
this is the first thing in it that changes the **shape** of the tree.

So the list is a `Property<List<String>>` and the pane that builds the strip
subscribes to it, which is exactly what `showProse` and `clicks` already did for
the other two structural changes in the showcase. Replaced rather than mutated,
because a subscriber is subscribed to the *value* and a list changed in place is
the same value.

The resize was a red herring twice over: it made the tabs appear because a resize
re-lays out from a tree that was rebuilt for another reason, and it made the bug
look like a layout problem.

### A mark fills its box, so a box for a mark is square

The `+` and the `×` are painter marks rather than icons
([ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)), and a mark is
drawn to the box it is in. A 28×20 box is a cross with unequal arms. The two
numbers are one number now.

The `margin` that would have spaced it from the last tab is **not** in §8's
subset — the third property this widget has reached for and not found, after
`border-bottom` and `currentColor`. The list's own `gap` does it instead.

### An arrival is a function of the clock, not a transition

Everything else that moves in this catalog moves between two styles the cascade
resolved ([ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md)). Neither
half of this can be:

- **A tab arriving** has no two styles. Its element did not exist last frame, and
  the first frame of a newly built element starts nothing
  ([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)).
- **A tab leaving** is worse. The application has already dropped it from its
  list — that is what `close` asking rather than doing *means*
  ([ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)) — so without
  something holding on there is nothing left to animate.

So it is `spinner`'s shape ([ADR-0081](0081-a-perpetual-loop-has-no-state.md)): a
function of the frame clock, read in `render`, which is the only place a widget is
given one. What a spinner does not need and this does is a **beginning**, and the
first read of the clock is what stamps it.

`Tabs` therefore has state, and it is one thing: which tabs are arriving, which
are leaving, and — for the leaving ones — what they last looked like, because the
application no longer has a description to give.

### Opacity and a translation, and nothing else

§1.7's whitelist is the compositor-cheap set. A tab that animated its own *width*
would run Yoga on every frame of every arrival and reflow the row beside it. So a
tab appears in its final place and fades up into it, which also makes a departure
the same animation backwards.

Under reduced motion there is no animation at all — §1.7 asks for movement to be
removed rather than shortened.

### The phase is passed as functions, because the record is public

`Tab` is exported and `TabPhase` is not, and a public record cannot have a
component of a type nobody outside the module can name — the compiler says so,
which is the module system doing its job. So a tab is handed a `BooleanSupplier`
and a `DoubleUnaryOperator`: *are you animating*, and *how visible are you at this
time*. Reading the second is what starts an arrival and what finishes a departure.

### The model node and the styled node are two nodes

Making `Tabs` stateful and leaving it `Styled` put **two `tabs` nodes in the
cascade**, one inside the other, so every rule in `controls.css` applied twice —
a doubled padding waiting to happen. `Tabs` is a composition node now: it holds
the model, and the `TabStrip` it builds holds the appearance, the CSS type, the
attributes and the focus scope.

## Alternatives considered

- **Animating with a transition anyway,** by building the tab one frame before
  showing it. It is a frame of latency on every arrival, and it does nothing at
  all for departures.
- **Keeping closed tabs in the application's list until the animation ends.** It
  makes every application implement the lifecycle, and makes "which tabs are
  there" a question with two answers.
- **A `Clock` on the state** instead of reading the frame clock in `render`. The
  state would then be on a different clock from the renderer, so a golden image of
  a half-finished arrival — which is how the four tests here work — would be
  impossible.
- **Animating height or width**, which is what a strip that slides tabs open would
  do. Off §1.7's whitelist for the reason the whitelist exists.

## Consequences

- **The frame after an arrival finishes is still painted.** Whether a node animates
  is read *before* it is drawn, and drawing is what advances the phase — so the
  frame that completes an arrival still reports itself as animating and the one
  after it does not. One frame, once, and the test says so rather than hiding it.
- **A departing tab answers nothing.** It is not in the application's list any
  more, so it has no `select` and no `close`: picking it would report a value that
  does not exist.
- **This is the toolkit's first enter/exit animation**, and `toast`, `dialog` and
  `popover` want the same thing. What is here is deliberately a tab's own — a
  shared `TabPhase` promoted to an overlay lifecycle is the next step and should
  wait for its second consumer.
- **Reordering is still not animated**, and would be a different animation: a tab
  that moves has two positions and no geometry to interpolate between them, which
  is ADR-0097's problem again.
