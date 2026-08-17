# ADR-0081 — A perpetual loop has no state

*Accepted, 2026-08-17. Extends
[ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md).*

## Context

`progress` and `spinner` are the seventh and eighth controls, and they are the
first two whose motion **is not a transition**.

Everything that has moved so far moved between two styles the cascade resolved: a
button's hover colour, a switch's thumb, a check mark's scale. ADR-0067 built
that — a per-node overlay, interpolated on the frame clock, driven by CSS
`transition` declarations — and it covers every state change in the catalog.

An indeterminate progress bar has no two states. Neither does a spinner. §3.1
asks for "sweep loop 1.2s `linear`" and "rotation 900ms `linear` loop", and §8's
CSS subset has no `@keyframes` and is not going to grow one: a loop is not a
declaration about a state, and every mechanism in the engine is built around
diffing one style against another.

§1.7 does name a mechanism for this:

> **Explicit = the `Animation` API.** `AnimationController`
> (forward/reverse/repeat/stagger) on the same frame clock — used internally by
> indeterminate progress, spinner, toast reflow, and available to apps for
> `canvas` work.

So the expected shape of this change was: build `AnimationController`, give each
of the two controls one, start it when the element mounts, stop it when the
element unmounts.

## Decision

### No controller. A loop that never ends is a function of the clock

```java
static double phaseAt(double now) {
    var phase = (now % SWEEP_PERIOD) / SWEEP_PERIOD;
    return phase < 0 ? phase + 1 : phase;
}
```

That is the whole mechanism. There is nothing to start, nothing to stop, nothing
to dispose, and nothing that can leak — and the widgets stay what every other
widget in the catalog is, a value with no state on it.

The argument is ADR-0073's, for the third time. A roving focus position was
*derived* from `:checked` rather than stored, because a second copy of a fact the
tree already holds disagrees with it the first time something changes without
telling the thing that cached it. An effective `disabled` was derived by walking
ancestors for the same reason (ADR-0077). Here the fact is the time, the tree
already holds it — the renderer reads the clock once per frame — and a
controller would be a second copy of it, per element, each one remembering when
its own element happened to mount.

**And the stored version has a visible symptom that the derived version cannot
have.** Two spinners in one window, mounted a frame apart, are permanently out of
phase: two rings turning at the same speed and never at the same angle. It looks
wrong and it does not look broken, which is the worst kind of defect — nobody
files it, and nobody finds the cause when they do. Derived from the clock, being
in step is not something anyone has to arrange. `progress-sweeping.png` is two
bars in one frame at the same position, and it is a picture that only passes for
the derived version.

### `AnimationController` is therefore not built

§1.7 names it and this change does not add it, deliberately. Its remaining
subjects are the ones with a **lifecycle** — "toast reflow", and the
`opening → open → closing → removed` sequence §1.7 gives every overlay — where
there really is a start, an end, an interruption to reverse from, and a state to
hold. None of those widgets exist; they are M3.

Building it now, for two callers that do not need it, would be inventing an API
against no requirement and then shaping the requirement to fit it. Principle 3's
rule, and ADR-0074's `density-regular.css` refused for the same reason: the
absence of a thing is a design position, and this one is on the record so that
whoever builds the controller for `toast` builds it for `toast`'s problem.

### A widget reads the frame's time, and says it wants another frame

Two additions, both small:

- `Paints.Context.nowMillis()` — the time the renderer read **once** for this
  frame, so two spinners see the same number rather than two calls to
  `System.nanoTime` a few microseconds apart. `reducedMotion()` joins it, because
  a widget that animates itself has no declaration for the renderer to collapse.
- `Paints.isAnimating()`, default false — §1.7's idle frame loop stops the frame
  after the last transition settles, and a spinner has no transition to settle.
  Without it the loop would paint a spinner once and go to sleep in front of it.

`isAnimating()` is a **property of the description**: a bar is indeterminate
because it was built that way, and one that has been given a value stops asking.
Nothing is started or stopped here either.

### The sweep is a transform, and it stays inside its track

The bar moves by `transform: translate(…%)`, never by width or margin. Animating
either of those would run Yoga on **every frame of a loop that never ends**,
which is exactly the cost §1.7's whitelist is a closed enum to refuse.

The percentage is a proportion of the **moving box** — CSS's rule, and here it is
the convenient one: the bar's own width is the natural unit for its travel. That
is the same rule that made `translate` *unable* to place a slider's thumb
([ADR-0079](0079-a-continuous-value-is-placed-by-ratio.md)). Two controls, one
rule, opposite conclusions, and the difference is only that one of them has a
thumb sharing its track.

**The bar reverses at the ends rather than running off them**, which is a
divergence from the usual drawing and is forced: the off-one-end-and-in-at-the-
other version depends on `overflow: hidden`, and nothing in this toolkit clips a
box. A bar that ran past its track would be drawn across whatever is beside it,
and the wrap from one end to the other — which clipping is what hides — would be
a visible jump once every 1.2 seconds. A bar that turns has no wrap to hide.
Linear each way, so the only thing that happens at the turn is that the direction
changes.

### A spinner is a mark, and the arc is three cubics

The obvious implementation is an icon and it is wrong twice: an `Icon` owns
native memory and a widget is a value rebuilt every frame — the argument
`Button`'s *borrowed* icon makes — and it would put the toolkit's own spinner
behind an asset the application has to register.

So it is a `Box.Mark`, like a tick and a dot, and the arc behind it is built from
cubics through the already-exported `bl_path_cubic_to`. **No symbol was added to
the export list**, which is ADR-0064's rule and the fifth time it has held. `Arc`
is the general form of what `RoundRect` does at fixed angles: quarters, because
KAPPA is the answer for 90° and a single cubic over 270° is visibly not a circle.

Three quarters rather than a whole ring, because a spinning circle is a circle:
the gap is the entire reason the rotation can be seen. `spinner-turning.png` and
`spinner-half-turn.png` are the same three spinners 450 ms apart, which is what
says both that the gap moved and that the three of them are in step.

### Reduced motion stops the movement rather than slowing it

§3.1 gives both controls the same answer — "reduced-motion: opacity pulse" — so
both stop moving. A slower sweep is still a sweep.

What ships is the *stopping*, and not the pulse: a pulse is a loop between two
opacities, and §8 has no `@keyframes` to write one with. A reduced-motion user
gets a bar holding still across a third of its track — a control that says
"working" rather than an empty groove — and that is recorded here as a divergence
rather than presented as compliance.

## Consequences

Two controls, one new part (`progress-fill`), one new mark kind (`ARC`), one new
file in `:core` (`Arc`), and two methods on interfaces that every existing
implementation ignores.

`Paints.Context` gained two abstract methods rather than two defaults, so every
hand-written implementation had to be updated — there is one, in the test
fixtures. A default `nowMillis()` returning zero would be a stopped clock nobody
notices they inherited, which is worse than a compile error.

**A golden image containing a spinner needs a virtual clock**, and until now no
golden needed one unless it was a picture of a transition. Every scene in the
repository was deterministic under the system clock because nothing in it moved
on its own; `controls-on-surface-*` now contains a control that draws itself from
the frame time, and under a wall clock it is a different ring on every run. It
duly was — 84 pixels apart, on the first regeneration after the spinner joined
that scene. ADR-0067's argument for the virtual clock was about photographing a
moment; this is the same clock answering a different question, which is whether
an image is reproducible at all.

**What this does not do:**

- There is no `AnimationController`, so an application cannot drive its own
  animation imperatively yet. It has `Clock` and its own `onPaint`, which is what
  the two controls here use.
- The reduced-motion **pulse** is absent, as above.
- `progress` has no `:disabled` and no label. §3 gives it neither, and a
  progress bar is not interactive, so `:disabled` would mean "this progress is
  unavailable", which is not a state anything has asked for.
- Neither control carries semantics yet — §3 says "Semantics: progressbar" and
  "decorative unless labeled" — because the semantics tree
  (`ARCHITECTURE.md` §13) does not exist for any control.
- A window containing a spinner **never idles**, which is not a regression but is
  worth stating plainly: §1.7's "the frame loop is fully idle when no animation
  is active" now has a control that keeps one active for as long as it is
  mounted. That is the cost of something on screen that moves, and the showcase
  demonstrates it by having one.
