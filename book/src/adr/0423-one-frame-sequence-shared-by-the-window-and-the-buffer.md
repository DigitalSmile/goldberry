# 423. One frame sequence, shared by the window and the buffer

Date: 2026-09-19

## Status

Accepted. Closes `book/src/TODO.md`'s "The frame sequence exists twice", under
*Rendering without a window*. Takes the refactor
[ADR-0284](0284-a-picture-with-no-window-under-it.md) listed under *Alternatives
considered* as "the right answer on paper" and did not take during a feature.

## Context

ADR-0284 shipped `Offscreen` by writing down the sequence a window runs:

```
prepare → flush → render → update → capture the regions → advance the clock
→ prepare → flush → render → update → capture the regions
→ prepare → flush → render → update → paint
```

and recorded, in as many words, that every step in it is there because leaving it
out produced a picture that was wrong in a way nobody would notice for weeks. It
also recorded that this was now the **second** copy of that list rather than the
third, and that what held the two together was the golden suites: every golden
image goes through `Offscreen`, so a divergence moves a picture.

That safety net is real and it is also the wrong shape. It is a *detector*, and
what it detected last time cost nine committed images showing an arrangement no
window ever drew — the old harness had never called `ElementTree.flush()`, and
ADR-0284's own evidence is that removing only that one call reproduces all nine
old goldens byte for byte. A net that catches the fall after the fact is what you
build when you cannot stop the fall. Two copies of an order-sensitive list is a
fall you can stop.

The reason it was not stopped in ADR-0284 is worth stating plainly, because it is
the reason to be careful now: `Launcher.paint` is forty lines and only about half
of them are the sequence. Damage, the frame ring, the HUD's four stage timings,
the model sweep, the popup re-placement and the animation re-request are woven
through it, and a refactor that pulled the sequence out badly would pull one of
those with it.

## Decision

**`io.github.digitalsmile.goldberry.frame.FrameSequence` owns the order, and the
two callers own everything that differs.** Both `Launcher.paint` and
`Offscreen.render` now go through it.

```java
var stages = sequence.layOut(frame, renderer(), beganAt);
// ... the launcher's damage pass, frame ring and HUD, none of which the
// sequence knows about ...
regions = sequence.captureRegions(frame, router);
```

Two methods, and the split between them is not arbitrary — it is where the order
is load-bearing:

- **`layOut`** runs prepare, flush, render, update. Four steps, each in front of
  the next for a reason with an ADR behind it: the resolver before the build
  (ADR-0254), the flush before the styling (ADR-0052, and the nine goldens), one
  layout pass read by two later readers (ADR-0069). This is the method that
  matters. It is now impossible to run these four in the wrong order, or to leave
  one out, from anywhere in the toolkit.
- **`captureRegions`** sets the window bounds and then hands the capture to the
  router, in that order, because a `Located` widget is told what clips it and
  "nothing clips me" has to resolve to a real rectangle (ADR-0119).

### The paint step is deliberately not in it

The entry named six steps and this extracts five. The sixth — draw — differs
between the two callers *by design*: a window paints the damaged rectangles
because the backend promises last frame's pixels are still there (ADR-0072), and
a buffer has no last frame to promise anything about, so it paints in full. Both
sides are one call with no ordering constraint around them.

So it stays out, and the rule the exclusion illustrates is the one worth keeping:
**extraction buys safety exactly where order is load-bearing.** Wrapping
`render.paint(frame)` in a shared method that took a flag would move a two-way
branch about window backends into the one class that should not know what a window
is, in exchange for nothing.

### What reality did not match

Three things, and the first is the interesting one.

**The three sequences are not one sequence.** The entry says the two "run the same
steps in the same order", and at the level of `layOut` they do. At the level of a
whole frame they do not, and no amount of extraction makes them:

| | lay out | paint | capture |
|---|---|---|---|
| a window's frame | ✓ | ✓ | ✓ |
| `Offscreen`'s measuring pass | ✓ | — | ✓ |
| `Offscreen`'s drawing pass | ✓ | ✓ | — |

A window captures *after* painting, because what the pointer is tested against
must be the frame the user can see (ADR-0054). A measuring pass captures without
painting at all — that is what makes the two extra passes cheap. And the drawing
pass paints without capturing, because the render is about to be unmounted and
there is nobody left to tell.

A single "run a frame" method would therefore have had to grow two booleans to
serve three callers, and the thing it was protecting — the order *within*
`layOut` — would have been just as protected without them. The unit that is
genuinely shared is smaller than the entry assumed, and it is the whole of the
part that had a bug in it.

**`Launcher` called `renderer()` twice per frame** — once for `prepare` and once
for `render` — where `Offscreen` resolved it once. Both are correct, because
`renderer()` is idempotent after the first call clears `stylesDirty`, but the
sequence takes a renderer as an argument and so the launcher now calls it once.
Passed in rather than held, because a theme swap builds a new renderer (ADR-0067)
and a sequence that cached one would paint last theme's colours.

**`Offscreen` set the router's window bounds once, before its first pass**; a
window sets them every frame. Folding that into `captureRegions` means the
offscreen render now sets them three times instead of one. Identical in effect —
nothing reads them between the passes, and the frame does not change size inside a
render — and one fewer thing for the two callers to do differently.

## Consequences

- **Every golden image still matches.** `:core:test` and `:widgets:test` ran green
  with no image re-blessed and none touched, which is the entry's own stated
  safety net and therefore the only acceptable result for this refactor. That is
  the claim to check first if anything here is ever revisited: the net is still
  hanging, it simply is no longer the only thing holding the two callers together.
- **The HUD's numbers are unchanged, and that took a second overload.**
  `layOut(frame, renderer)` times itself; `layOut(frame, renderer, beganAt)` is
  told when the frame started. The launcher's build budget begins *before* the
  model sweep that precedes the build, so a sequence that timed itself would have
  quietly moved the sweep out of a number that appears on screen. A refactor is
  allowed to be behaviour-preserving about pixels and careless about measurements
  only if nobody is reading the measurements, and `hud` is.
- **`Stages` is a record with the four timestamps and three differences.** The
  timings are taken on every frame rather than behind a flag, which is ADR-0146's
  existing judgement: the stages are what a `hud` shows, so a number from a frame
  that happened to be traced would be a different frame's.
- **`captureRegions` refuses a sequence that has never laid out.** Not
  pedantry — a capture of an unlaid tree is a list of rectangles at the origin, and
  nothing downstream treats that as an error. A menu would simply open in the
  corner of the window, once, for one user.
- **The package is not exported.** `frame` names the element tree, the cascade, the
  render tree, the paint pipeline and the hit test, so it cannot live inside any
  one of them without pointing that package at the other four; and it is a seam
  between two callers in `:core` rather than a promise to an application, which is
  what `Offscreen` is for. An application that found and called it would be
  assembling a frame loop by hand.
- **`FrameSequenceTest` is the first test in this repository of the order itself.**
  Everything before it asserted on what a caller *produced* — a window's goldens,
  an offscreen render's pixels — which is how a missing `flush` survived long
  enough to be committed nine times. Each case now names the step that would go
  missing and the symptom: `flushesBeforeItStyles` is ADR-0284's exact bug,
  `preparesBeforeItBuilds` is ADR-0254's, `setsTheWindowBoundsWithTheRegions` is
  ADR-0119's.
- **`Launcher.paint` lost fourteen lines and none of its comments.** The reasons
  each step is in front of the next one moved to where the steps now are, which is
  the only way this refactor is not a loss: those comments are the record of what
  each step was protecting against, and a step without its reason is a step
  somebody reorders.

## Alternatives considered

- **One method that runs a whole frame, with a listener for the stage timings.**
  The design that enforces the most, and it cannot serve three callers with three
  different orders (see the table above) without booleans that describe windows.
  The order it would have protected is the order `layOut` already protects.
- **Leave it, and trust the goldens.** ADR-0284's position, and it was defensible
  while `Offscreen` was new. It stops being defensible once the entry naming the
  duplication is being closed: the net's own last catch cost nine wrong images and
  an ADR to explain them.
- **Put `FrameSequence` in `paint.tree`, beside `RenderTree`.** It would be the
  fifth package it depends on deciding to own it. `widget` has the same problem
  from the other end.
- **Export the package.** There is one thing an application wants from this, and it
  is called `Offscreen`.
- **Extract the launcher's whole paint method and give `Offscreen` the parts it
  needs.** This is the refactor ADR-0284 was afraid of, and rightly: the damage
  pass, the frame ring, the popup re-placement and the animation re-request are a
  window's behaviour, and a shared object holding them would be a window with the
  window taken out.
