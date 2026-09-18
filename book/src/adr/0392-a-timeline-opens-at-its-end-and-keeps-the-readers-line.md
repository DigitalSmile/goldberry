# 392. A timeline opens at its end and keeps the reader's line

Date: 2026-09-18

## Status

Accepted, closing `docs/gaps.md` G48.

## Context

A chat timeline wants three things of a viewport and `scroll` did none of them.
It should open on the newest message rather than on the oldest. It should stay
on the newest while the reader is already there, and leave them alone when they
are not. And when older messages are paged in *above*, everything the reader is
looking at should stay exactly where it is instead of jumping down by the height
that arrived.

`ScrollController.scrollBy` answers none of the three, and the reason is the same
each time: they are all **layout facts**. Where the content ends is not known
when the build that added a row runs; "am I at the end" is a comparison between
two rectangles neither of which exists yet; and a scroll issued after the fact is
a visible jump rather than a viewport that never moved. §1 already gives `scroll`
"scroll position is retained state surviving rebuilds", and this is that promise
one step longer — retained against the *content* changing, not only against the
widget being re-described.

The third is the hard one, and it is hard for a precise reason. **Content getting
taller and content arriving above are the same number.** Twelve lines added to
the top of a log and twelve added to the bottom both make the content 240px
taller, and the first must move the offset by 240 while the second must move
nothing. A naive "the content grew, so shift" implementation passes a test that
only prepends and drags the reader down every time anything is appended, which is
the failure this whole record is arranged around.

Nothing in the toolkit could tell them apart. `Extent` on an event, `Measured`
and `Located` all report a property of *one* frame; the difference between the
two is a property of a pair. So it had to be built.

## Decision

**Three attributes' worth of behaviour, on one new geometry facility.**

### `Anchored`, the fourth geometry facility

`io.github.digitalsmile.goldberry.input.handler.Anchored` is told, once a frame,
**how far the content slid under a widget** — the one thing `Measured` and
`Located` cannot say because it is a difference between two frames.

`PointerRouter.notifyAnchored` is a third walk beside `notifyMeasured` and
`notifyLocated`, and it is the right home for the same reason those are: the
router is the one place that holds the painted rectangles, the one place that
holds per-element identity across frames, and the one call every window makes
once per frame.

- It picks, inside the part the widget names, **the deepest node in document
  order that begins at or after the viewport's leading corner** — the reader's
  first whole line. A node straddling the corner is not it, but it is descended
  into, which is what makes one rule cover `scroll { row … row … }` and the far
  commoner `scroll { column { row … } }` alike.
- Both axes at once, so the router needs to know nothing about which way the
  viewport scrolls: children of a vertical one agree about the left edge and
  children of a horizontal one agree about the top.
- It remembers where that node sits **inside the part, in layout coordinates**.
  A scroll is a transform on the part, so subtracting the part's own origin
  cancels it exactly: the remembered number moves when something inside the part
  changed and not when the viewport did. That is the whole trick, and it is why
  "grew at the bottom" reports nothing at all rather than reporting something
  small.
- It keeps that node while it keeps moving and **re-picks on the first quiet
  frame**. Re-picking during a shift would measure the correction that is about
  to land and count the insertion twice.
- `anchorPart()` is **nullable**, and that is the switch. Finding an anchor walks
  a subtree per frame per widget that asked; a viewport nobody asked to preserve
  returns null and is skipped before anything is walked.

**Identity is the reconciler's, and a key is what it is made of.** Children
matched by position are not the same node when a list is prepended to — element 0
simply describes a different message — so nothing moved and nothing is reported.
That is honest rather than a limitation: `Widget#key()` has asked for keys on
list items since it was written, and this is the first widget that cannot work
without them.

### `anchor="end"`, and what "at the end" is worth

`ScrollStick` holds one boolean per axis: *is this viewport at the end*. It is
written whenever the offset moves **on purpose** — `moveTo` and `scrollBy`, which
between them are the wheel, the keys, a drag, a track click and every
`scrollIntoView` — and read when a measurement arrives.

A flag and not a recomputation, because by the time a message has arrived the
extents have already changed and `offset == overflow` is false for exactly the
viewport that was at the end a moment ago.

**Half a logical pixel** is how near the end counts as at it
(`ScrollStick.TOLERANCE`). The same figure `ScrollController.Position` has used
since it was written, below a device pixel at every scale this toolkit renders
at, and there because an offset is a `double` arrived at by adding wheel
fractions and clamping — asking a dragged-to-the-bottom viewport for exact
equality with an overflow computed from float extents loses the stick roughly
whenever the arithmetic feels like it.

It is deliberately *small*, and there is no hysteresis and no grace period. The
frame after the user scrolls one pixel up the stick is off and stays off until
they come back down, because a pixel up is a decision and a tolerance wide enough
to be forgiving is wide enough to drag somebody back down while they are reading.

Opening at the end is not a separate case. `initState` turns the flag on before
the first layout — a standing instruction rather than a position — so the first
measurement lands at the end by the same line of code that keeps it there, and
the two cannot disagree about the frame in between.

### `preserveOnPrepend`, and the three states

The component on the record is a `@Nullable Boolean`: unset, true, false. The
default is the anchor's, so collapsing "unset" into either boolean would make
`.anchor(END).preserveOnPrepend(false)` and `.preserveOnPrepend(false).anchor(END)`
mean different things. `preservesOnPrepend()` resolves it. `KdlNode.flagProperty`
is the markup-layer half — `booleanProperty` folds absent into `false`, which is
right for every attribute whose default is a constant and wrong for this one.

The correction is applied by `ScrollState.shiftBy`, which is deliberately **not**
`scrollBy`: a glide would draw a 240ms slide every time a line was logged
(ADR-0363), and waking the bars would say the user had scrolled when they had
not. Nothing moved; only the offset did.

An `END` viewport that is at the end skips the shift entirely, because keeping to
the end has already put it at the new end and that is the same number. A `START`
viewport that merely happens to be scrolled to its bottom is a different thing
and takes the shift like any other.

### The axes, the directions and the glide

`END` means the **far end along the scrolling direction**, not "the right". The
offset is measured from the content's start edge and the layout direction decides
which edge that is, so under a right-to-left document the same anchor puts the
newest item where that language's reader ends up, with no case for it anywhere in
this code. `BOTH` sticks on both axes.

The glide is untouched. A programmatic scroll still glides and still sets the
offset to its target immediately, so `settle()` reads the target and a
`scrollIntoView` onto the last row is how a timeline is deliberately caught up
with.

### It is one frame late

The heights of rows that have just been inserted do not exist until a frame has
been laid out, so the correction lands on the frame *after* the insertion. That
is `Measured`'s bargain unchanged — a thumb has been one frame behind since
ADR-0117 — and at frame rate it is not a jump anybody sees. It is not the jump
G48 complains about either: that one is an application scroll, a frame late *and*
animated over a quarter of a second.

## Consequences

- `scroll anchor="end" preserve-on-prepend=#true` is writable in markup, and the
  Navigation screen's new console card is the demonstration: **Log a line**
  follows the end when you are on it and leaves you alone when you are not, and
  **Load older** drops twelve lines above the viewport without the words you are
  reading moving.
- `ScrollTimelineTest` states the three behaviours as three cases and adds the
  fourth — the one that regresses — as its own: *a message arriving while you are
  reading history does not move you at all*. Its rows are a whole number of pixels
  tall on purpose. Yoga snaps each node's position to the pixel grid, so the
  anchor is preserved exactly whatever the heights are and a row either side of it
  can land a rounded pixel from where it was; whole rows take the grid out of the
  assertions and leave the arithmetic.
- `Anchored` is public and general. "Where has this container's content moved to"
  is the same question a virtualized list asks when its estimated row heights are
  replaced by measured ones, and nothing else in the toolkit could ask it.
- Every existing `scroll` is unchanged: `START` is the default, it preserves
  nothing by default, and a viewport that asks for neither returns a null anchor
  part and costs the router one reference comparison a frame.
- The gallery's Navigation golden is re-blessed for one more card.
