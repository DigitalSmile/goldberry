# 420. `Measured`'s third rule is checked by a fixed point

Date: 2026-09-19

## Status

Accepted. Enforces the rule stated but not checked by
[ADR-0117](0117-a-widget-may-be-told-what-it-measured.md), whose own consequence —
"every widget in the catalog can now ask for geometry, and almost none should" —
had nothing behind it.

## Context

ADR-0117 gave every widget a way to be told what size it came out as, and hung
three rules on it. The third is the one that matters:

> **What it triggers must not change what it reports.** A widget that resized
> itself from this would be told a new size, resize again, and never settle. The
> one implementation obeys it by construction: a scrollbar is absolutely
> positioned, so nothing it draws can change the rectangle it was measured
> against.

"The one implementation" is now **eleven**, in five packages, written by whoever
needed one. `MasonryCell`, `ToastBox`, `SplitPaneView`, `ImageBox`, `ImageFigure`,
`ScrollViewport`, `TextField`, `TextAreaBox`, `TableHead`, `TourCard`, `IconSheet`.
Obeying a rule by construction is the strongest kind of safe and the least
transferable: the scroll view's absolute positioning protects the scroll view and
tells the twelfth widget nothing.

Nothing checked. The failure has no moment to be caught at — a widget that breaks
rule 3 throws nothing, logs nothing, and draws nothing wrong. It asks for one more
frame, forever. On a desktop that is a warm fan; in a test suite that renders one
frame it is a pass. `MasonryTest.itConverges` is the only test in the repository
that ever looked, and it looks at one widget by hand.

### The runtime check, and why not

The obvious shape is a counter in `PointerRouter`: it already computes
`Measurement.sameAs` per element per frame, so counting consecutive changes for
the same element is nearly free, and past some threshold you have a widget that
cannot settle.

It does not survive contact with a window edge. During a resize drag every
`Measured` consumer is legitimately notified with a genuinely different size, every
frame, for as long as the user holds the mouse down — hundreds of frames, no
oscillation, nothing wrong. To tell that from a real loop the router would have to
know whether the *input* changed, which means re-deriving the causality the check
exists to discover. ADR-0394's verdict applies before a line is written: a check a
user can trigger by dragging a window edge is a check that gets turned off, and it
would fire on the arrangement nothing is wrong with.

There is a deeper reason. Non-termination is a property of a **sequence** of
frames. It cannot be seen from inside one, and the router only ever has one.

## Decision

**A test harness that drives a widget tree to a fixed point, and a test that runs
every `Measured` consumer this module can build through it.**

`Settled` runs the real loop — `tree.flush()`, render, `RenderTree.update`,
`router.updateRegions(HitTest.capture(render))` — and keeps running it until two
consecutive frames lay out identically. That last step is the one that makes it
work at all: `Measured` is delivered by the **router**, from the regions a
laid-out frame produced, so a harness that stopped at `render` would drive
nothing and pass everything.

Three details are load-bearing.

**The signature is layout rectangles only.** A transform is paint and cannot feed
back into layout, so a sweeping progress bar and a turning spinner are still, and
the harness is not fooled into calling an animation an oscillation. The clock is
virtual and never advanced for the same reason.

**A repeat that is not the previous frame is reported as a cycle, with its
period.** "Frame 5 is frame 3 again" is a different finding from "still moving
after twelve frames", and the first is the one that names the bug. Both messages
carry the first few boxes that differed, because a diff of a whole tree is
unreadable and three lines of one names the widget.

**The limit is twelve where the worst honest case is two.** Deliberately loose: the
failure this catches is a tree that never settles, and a limit set near the
observed maximum turns a widget gaining one legitimate frame into a red test with
a misleading name.

## Consequences

**Six consumers are covered, and the numbers are these.** The count is distinct
layouts before one repeats — `1` means the second frame laid out identically to
the first.

| consumer | layouts | what it does with the geometry |
| --- | --- | --- |
| `masonry` | 2 | moves cards between equal-width columns |
| `scroll` | 2 | draws bars, absolutely positioned |
| `split-pane` | 2 | sets the first pane's size from its own |
| `table` | 1 | banks a header width as a drag anchor |
| `text-area` | 1 | wraps text at its measured width |
| `text-input` | 1 | places a caret; never calls `setState` |

**Four of those six pass without demonstrating anything, and the record has to say
so.** A `1` means the consumer's feedback changed no layout rectangle — which is
the property wanted, and is also exactly what a tree with no consumer in it looks
like. So `Settled.consumers()` counts the `Measured` widgets actually placed, and
the helper asserts it is non-zero before trusting the layout count. The guard is
not theoretical: it is what caught the seventh case.

**`toast` is not covered, and the attempt is why the guard exists.** A `Toaster`
builds its plates into the host's overlay layer rather than into its own subtree
(ADR-0177), so a windowless harness lays out a `toaster` with nothing in it:
`settle()` returned 1 and `consumers()` returned 0. Without the guard that would
have been a seventh green row in the table above, asserting nothing about a widget
whose `measured` never ran. `ToastTest` feeds `measured(...)` by hand for the same
underlying reason.

**`tour`, `image` and `IconSheet` are not covered either.** `TourCard` needs a
`Host` and an anchored element; `ImageBox` needs a decoded image, and its rule-3
argument is the most delicate in the catalog — it sets its own *height* from its
own measured *width*, safe only in the branch where the width is a percentage and
therefore the parent's; `IconSheet` lives in `:example` and is out of this module's
reach. `IconSheet` is the one worth moving: its two consumers (`IconsScreen` and
`EmojiScreen`) are the only ones that guard by **quantising** rather than by an
epsilon — a width becomes a column count — and that is the pattern a twelfth
widget is most likely to copy.

**The negative control is the test that makes the other six mean anything.** A
`Plank` that reads its own width and picks a different one is exactly what rule 3
forbids; the harness catches it as a period-2 cycle and the assertion checks the
message says so. A suite of fixed-point tests that has never seen a failure is a
suite that might not be able to.

**This catches oscillation, not slowness and not wrongness.** A consumer that
settles on a value that is simply incorrect passes. A consumer that takes four
frames passes today and fails when somebody tightens the expected count, which is
why the counts are asserted exactly rather than bounded — `assertEquals(2, …)`
fails when masonry starts needing three, and that is the regression worth having.

**Every consumer's own epsilon is still load-bearing and is not what is being
tested.** The 0.5px guards in `MasonryState`, `SplitPaneState`, `ImagePaint`,
`TextAreaState` and `TourState` are what keep a sub-pixel disagreement between two
layout passes from requesting a frame forever; `TableHeaderCell` has an exact `!=`
and gets away with it because its rebuild is layout-neutral. This harness would
catch the loop if one of those were removed — which is the point — but it asserts
nothing about the epsilon itself.

**There is still no shared frame-pump fixture.** Nineteen test classes carry a
private four-line `Harness.frame()`, and `Settled` is the twentieth thing that
knows how to run a frame rather than a replacement for the other nineteen. It
lives in `:widgets`'s test tree rather than `:core`'s `testFixtures` because every
consumer worth driving is a widget and moving it would be a build change for one
caller. If a second module ever needs it, that is the moment.
