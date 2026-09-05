# 228. A phase is asked whether it is still running

Date: 2026-08-30

## Status

Accepted. Closes two `TODO.md` entries opened by
[ADR-0175](0175-a-banner-says-its-kind-twice.md) — one a defect, one recorded as
a harmless cost that turned out to be avoidable.

## Context

`docs/design-system.md` §1.7 promises that "the frame loop is fully idle when no
animation is active". It was false for any window with a `carousel` on it at all,
and for any window with an open `collapse`.

Both widgets handed their moving part a **function of the clock** and decided at
*build* time whether there was an animation:

```java
showing ? this::visibility : null      // collapse
this::visibility                       // carousel — never null
```

and both parts then answered `isAnimating()` with `visibility != null`. So the
question the frame loop asks — *are you still moving?* — was answered with *were
you built in a state where you could move?* A `collapse` reported an animation
for as long as it was open; a `carousel` reported one from its first frame and
never stopped. Only a rebuild could take either back out of the loop, and an open
section is exactly the thing nothing rebuilds.

`message` did not have the bug, and the difference is one word: it hands over the
**`Phase` itself**. A phase settles itself on the frame that finishes it, so
asking it is asking the only object that knows. A `DoubleUnaryOperator` closing
over the same phase cannot say whether it has finished, because a function of the
clock has no state to report.

A second entry sat beside this one and had been written off:

> **Every clock-driven arrival costs one wasted frame.** The renderer asks
> whether a node is animating *before* it draws it, so the frame that finishes an
> arrival still reports one more […] Harmless and worth writing down.

Harmless, and not necessary. A phase learns it has finished by being **read**,
and the only place a widget is handed the frame clock is `render`. Asked
afterwards, the answer is current.

## Decision

**Hand the `Phase`, not a function of it.** `CollapseBody` and
`CarouselViewport` take a `Phase` and answer `phase.isRunning()`, which is what
`MessageBox` already did. `CollapseState.visibility` and
`CarouselState.visibility` are deleted; there is nothing left for them to wrap.

**Reduced motion ends the phase rather than drawing past it.** Both parts now
call `phase.skip()` when the frame says motion is reduced, so a reader who asked
not to be animated at also stops paying for frames spent standing still —
`MessageBox`'s behaviour, applied to the two widgets that were guessing.

**The node that *carries* a phase answers for it too.** `CollapseSection` and
`CarouselView` hold the phase and hand it down; they now override `isAnimating`
as well. The renderer ORs over the tree, so this changes no behaviour — what it
buys is that `AnimationSweepTest`'s rule (ADR-0226) stays sharp, with no
exception for "it hands it to a child" that nothing could check.

**`CollapseSection` guards on `open`**, and the guard is the bug in miniature: a
section shut half way through its arrival keeps an `ENTERING` phase that nothing
will ever read again, so nothing will ever settle it. A shut section has no body
and animates nothing whatever its phase remembers.

**The renderer asks `isAnimating` after `render` rather than before.** One line
moved, and it is worth one frame of every animation in the toolkit — every
arrival, every departure, every scrollbar fade, on every widget.

## Alternatives considered

- **A `BooleanSupplier` beside the `DoubleUnaryOperator`**, which is what `Tab`
  does and which works. It is two closures where one object will do, and it
  leaves the same trap set for the next widget: nothing about a pair of lambdas
  says they have to agree.
- **Fixing only the `null` check** — `showing && phase.isRunning()` computed at
  build time. It is the same mistake with a longer expression: the answer is
  still frozen at the moment of the build.
- **Leaving the wasted frame.** It is one frame per animation, which is genuinely
  small, and it is also a `repaint()` per animation on a battery. The entry called
  it harmless because it looked structural; it was one line.
- **Asking `isAnimating` both before and after.** Belt and braces, and it would
  reinstate the wasted frame it was meant to remove.

## Consequences

- **A behaviour change nobody can see and every laptop can feel.** A window with
  a carousel on it went from repainting at the display's refresh rate for ever to
  repainting when something moves.
- **`TabMotionTest` lost an assertion and gained a better one.** It documented the
  wasted frame — "one more frame before the loop sleeps, and the reason is worth
  knowing" — and now asserts that the finishing frame is the last one.
- **`CarouselTest`'s `animating()` asserted the bug.** It said a fresh carousel
  reports an animation, which was true and wrong. That is the shape of this whole
  entry: the test was written against the implementation rather than against
  §1.7.
- **A new `IdleLoopTest`** in `:widgets`, asserting on the **renderer** rather
  than on a part, because the renderer is what the frame loop asks. Six cases: a
  shut section, an opening one, one shut mid-arrival, an untouched carousel, a
  moving one, and the no-wasted-frame claim on its own.
- **Two legitimate reasons the loop stays awake are now visible in that test** and
  worth writing down: a CSS transition starts on the frame that *observes* the
  changed style, so a test must draw once before advancing its clock; and opening
  a `collapse` puts `.open` on it, whose chevron rotates under a transition of its
  own.
- **The `AnimationSweepTest` rule fired on this change**, naming `CarouselView`
  and `CollapseSection` the moment they gained a `Phase` component. That is the
  sweep from ADR-0226 doing its job on the first real change after it landed.
