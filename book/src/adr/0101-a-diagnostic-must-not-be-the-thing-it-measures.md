# ADR-0101: A diagnostic must not be the thing it measures

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §7, `docs/design-system.md` §1.7,
  needs [ADR-0100](0100-a-window-has-a-layer-above-its-application.md), extends
  [ADR-0028](0028-the-start-up-timeline.md) and
  [ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md)

## Context

The toolkit measures its own frames already and has since
[ADR-0028](0028-the-start-up-timeline.md): `Window.paint` takes five `nanoTime`
readings and logs where a frame went — buffer, begin, draw, end, present. Every
one of them is behind `LOG.isTraceEnabled()`, which is the right gate for a log
line and the wrong one for a number somebody wants to *watch*: switching TRACE on
to see a frame rate means measuring a loop that is now also writing a line per
frame to a file.

What was wanted is a `hud` — §7's frame-rate readout, floating over the window it
is reporting on, and the first widget in the catalog that is about the toolkit
rather than about the application.

Two things had to be decided, and the second is the one worth a record.

## Decision

### The statistics are a live object on the window, read down the render context

`FrameStats` is an **interface**; `FrameRing` is the fixed-size ring of the last
60 frames behind it, written once per painted frame by `Window.paint` from two
ungated `nanoTime` readings. Two calls against a frame is not a cost worth an
`if`, and a rate that exists only at TRACE is not a rate anyone can use.

It reaches a widget on `Paints.Context`, beside the frame clock and the
reduced-motion flag, because it is the same kind of fact: something true of the
frame being rendered rather than of the node rendering. `Paints.Context` was
described as an interface "precisely so that it can grow"
([ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md)) and this is the
third thing to grow onto it. The alternative — a widget holding the stats it was
built with — makes `hud` unwritable from a document, because the inflater has no
window to ask.

The interface is what makes it testable: `FrameStats.of(60, 16.7, 2.1, 4200)` is
a rate somebody chose, so a golden image of a HUD is a golden image and not a
race against the machine that ran it. This is `Clock.virtual()`'s argument
([ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md)) applied to a second
kind of time.

### The HUD reports the loop and never drives it

**A `hud` does not call `repaint`.** It draws the numbers as of the frame it is
being drawn in, and when the loop goes quiet they stop moving with it.

The alternative is not a small convenience. `docs/design-system.md` §1.7 ends
with "the frame loop is fully idle when no animation is active", and the toolkit
holds itself to it: the launcher asks for another frame only while something is
animating. A HUD that asked for a frame so it could show a fresh number would
make that sentence false for every window with one in the corner — and would
report a steady 60 fps whatever the application was doing, because the frames
being counted would be the ones the HUD requested. It would be measuring itself.

So a HUD tells you about frames you were already getting. During a resize, a drag
or a transition — which is when there is anything to watch — the loop is running
and the numbers move. On an idle window they freeze, and that is the honest
answer to "what rate is a loop drawing nothing achieving".

It is self-correcting rather than stale: the window is a frame count, so an idle
second sits *inside* it, and the first frame after that second carries it into
the mean. The rate falls the moment there is anything to fall in front of.

### Nothing measured reads as dashes, not as zero

`— fps`, not `0 fps`, when there is no frame loop over the tree — in a unit test,
or a render into a `Layer`. **A zero is a measurement.** A loop that genuinely
stopped dead does read `0 fps`, and being able to tell those two apart at a
glance is the whole reason for the distinction.

### The readings are parts, and the numbers are formatted in the root locale

Each reading is a `hud-reading` part ([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md))
with its own class, so a stylesheet can dim the units or colour a paint time that
has run out of budget; one paragraph with separators in it could carry none of
that.

`Locale.ROOT`, always. `docs/core-widgets.md` §5 says a locale-formatted number
produced inside the toolkit makes a golden image that cannot be reproduced on
another machine — a rule written for `statistic`, whose numbers are the
application's and which therefore takes a string. A HUD's numbers are the
toolkit's own, so it formats them itself, the one way that is the same
everywhere. The test that pins this sets the default locale to Germany and
asserts `16.7 ms`, because `16,7 ms` is what a CI runner in Berlin would
otherwise have produced.

### A bare `hud` shows two numbers, not three

`60 fps` and `paint 2.1 ms`. The frame interval is the third reading and is off
by default because it is `1000 / fps`: a HUD showing both would spend a third of
its width restating its first number. `hud readings="fps frame paint"` asks for
it, for whoever is thinking in budgets rather than in rates.

The pair that *is* shown is the pair that answers different questions. The
interval is the display's and says nothing about headroom; the paint time is the
toolkit's half of it. 2 ms inside a 16.7 ms frame is idle hardware, and 15 ms
inside the same frame is one resize away from dropping every other one.

## Alternatives considered

- **A rate since start-up.** Trivial to compute and useless to read: it tells you
  about the resize you finished a minute ago, and it stops moving, so a HUD
  showing it looks broken.
- **A time window rather than a frame window** — "the frames of the last second".
  It has to be pruned, which means the answer changes when nobody asked it
  anything, and a HUD reading it twice in one frame could get two numbers. A
  fixed count is a mean over a fixed sample and is computed on demand from data
  nothing but `record` touches.
- **Sampling on a timer at 2 Hz** so the digits do not flicker. That is a second
  clock, it needs a frame to display its result, and needing a frame is the thing
  this record refuses. The ring's 60-frame mean is already the smoothing.
- **Reading the statistics through `BuildContext.findAncestor(WindowRoot.class)`**
  rather than off the render context. It works, and it makes the window's own
  node a carrier for anything a subtree might want to know — which is a service
  locator growing in the widget tree. The frame clock settled this shape already:
  facts about the frame travel with the frame.
- **A HUD outside the catalog**, as a debug flag the launcher honours
  (`-Dgoldberry.hud=true`). Tempting, and wrong in the module graph: the launcher
  is `:core` and the catalog is `:widgets`, so `:core` would have to ship the one
  widget it deliberately stopped shipping ([ADR-0092](0092-a-primitive-is-a-widget-like-any-other.md)).
  An application writes `host.overlay(new Hud(), Corner.BOTTOM_END)`, or `hud` in
  a document, and both are one line.

## Consequences

- **`Window.paint` takes two `nanoTime` readings on every frame**, where before
  it took none unless TRACE was on. The other five are still gated.
- **The showcase toggles a HUD on `Ctrl+F`**, off by default — which is also what
  keeps it out of the golden images, since a frame rate is exactly the kind of
  machine-dependent number §14's image corpus must not contain.
- **`--gb-hud-bg` is the only background token that does not invert with the
  theme.** A HUD lies over colours the toolkit does not know, so it carries its
  own contrast: a dark plate with a hairline edge on both themes. Without the
  edge it disappears into a dark window, which is what the first pass did.
- **Nothing yet reports a dropped frame.** The ring records what was painted, so
  a frame the platform refused after it was painted is in the mean and a frame
  the loop never got to is not. A HUD that said "3 late" would need the pacer's
  view as well as the painter's, and the pacer is the `sdl3` backend's.
- **`paintMillis` is the painter's wall time**, which on a multi-threaded Blend2D
  context includes waiting for its workers at `end` and excludes the platform's
  own upload in `present`. That is the same split the TRACE line has always
  reported, and the same caveat applies to reading it.
