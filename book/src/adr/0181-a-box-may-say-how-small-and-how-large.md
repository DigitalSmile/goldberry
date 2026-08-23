# 181. A box may say how small and how large

Date: 2026-08-23

## Status

Accepted. Adds `min-width`, `max-width`, `min-height` and `max-height` to §8's
CSS subset, and gives `dialog` the two numbers §2 has always asked it for.

## Context

`docs/core-widgets.md` §2 asks a dialog for "min width 320, max 80% of the
window". Neither was expressible. §8's subset had no such property, `Box` had no
field for one, and `ComputedStyle` had nowhere to put one — so `controls.css`
carried a paragraph explaining that the scrim's 24px of padding was a *de-facto*
maximum and that the minimum was simply missing: a dialog with three words in it
was three words wide.

It was never only about dialogs. `book/src/TODO.md` listed it as "a gap in the
style engine rather than a decision about dialogs" and named the consumers
waiting. By the time this was built there were five:

- **`dialog`** — §2's min 320 / max 80%, above.
- **`toast`** — `controls.css` says outright that 360 "is a **width** rather than
  a maximum because the subset has no `max-width` (see `dialog`)".
- **`tooltip`** — "has no maximum width of its own", which is what makes a long
  one a single unreadable line.
- **`popover`** — `minimumWidth` is a Java argument on `Host.popup` doing what a
  declaration should do.
- **`text-area`** — max rows.

Three widgets writing a *width* where they meant a *maximum* is the shape of a
missing property rather than three independent choices.

## Decision

### One value, not four components

`Limits` — `minWidth`, `maxWidth`, `minHeight`, `maxHeight` — beside `Insets` in
`natives.yoga`, which is where the vocabulary `css` and `layout` share already
lives ([ADR-0172](0172-a-package-is-a-role-and-the-module-is-the-fence.md)).

`Insets`' reason applies: the four are only meaningful together, and `Box` and
`ComputedStyle` would each have grown four components where they now grow one —
across 45 positional reconstructions between them, every one of which is a place
to put an argument in the wrong slot.

There is a second reason `Insets` does not have. These four are the **same
question asked four ways**, and a caller that handled three of them has a bug
nobody would find: a dialog with a minimum width and no maximum reads as working
right up until somebody writes a long sentence in one. One value makes handling
three of four impossible.

### Undefined, not zero

"No limit" is `StyleLength.UNDEFINED` on every edge, which is Yoga's own default
rather than a convention layered over it. It has to be: a **minimum** of zero is
a real declaration that constrains nothing, but a **maximum** of zero is a box
that may not exist. Spelling "no limit" and "a limit of none" the same way would
make the second unsayable.

### The scrim lost its horizontal padding, and that is the whole trick

§2 wants "80% of the window", and `max-width: 80%` resolves against the
containing block. A dialog's containing block is the scrim, which fills the
window — so the percentage means what §2 says **only if the scrim has no padding
across it**. With the 24px it had, the maximum would have been 80% of *the window
less 48px*, which in a narrow window squeezes a dialog below the width §2 allows
it. Measured, not reasoned about: in the golden's 424px window the dialog came
out at 330 where §2 permits 339.

So `dialog-scrim` is `padding: 24px 0` now. The padding across was the de-facto
maximum; there is a real one, and the two would be fighting. Down the page it
stays — a tall dialog still has to be kept off the top and bottom edges, and no
maximum on the height is doing that job.

This is the argument for putting the number in CSS rather than in Java. "80% of
the window" needs nothing to measure a window: a percentage and a containing
block that happens to be one.

## Consequences

- **The dialog goldens all changed, and the change is §2 being applied.** The
  dialog in them wanted 85% of the window and is now capped at 80%, so its
  message wraps to two lines. That is what a maximum does, and the picture is the
  first evidence the property is real.
- **A guard keeps an unlimited box cheap.** `RenderObject.apply` skips all four
  setters when neither frame had a limit — nearly every node — so a box that
  never mentions a minimum costs one comparison rather than four foreign calls.
  A guard that skipped a setter Yoga needed would give a correct first frame and
  a wrong second one, which is the failure `staysIdenticalAcrossFrames` exists
  for; the same question is asked of these, in both directions, because a
  one-way guard would leave a limit behind after its declaration went.
- **Of the four consumers waiting, only one wanted converting.** Looked at one at
  a time rather than swept:
  - **`tooltip` gets a maximum**, and it is the case the property was most needed
    for. §2's metrics row gives a tooltip a padding, a radius and two delays and
    no width at all, so 320 is a judgement of `Toaster.DEFAULT_MAXIMUM`'s kind:
    without one, a sentence of help text is a ribbon across the window that is
    harder to read than no tooltip.
  - **`toast` keeps its width.** The note in `controls.css` gave two reasons and
    only the first expired — the second is a design argument that still holds:
    giving every toast the same 360 is what makes a stack of three read as a
    stack, and a maximum would size each to its own string, which is the ragged
    pile the note was arguing against.
  - **`popover`'s `minimumWidth` cannot become a declaration.** It is
    `field.size().width()` — what the control it drops from turned out to be —
    and no stylesheet can know a runtime measurement
    ([ADR-0145](0145-a-dropdown-is-as-wide-as-what-it-drops-from.md) says so).
    It was listed as waiting on this and never was.
  - **`text-area`'s max rows is built**, and is a row *count* rather than a
    length: it grows between `rows` and `max-rows` and scrolls past that. Also
    never waiting on this.
- **`Box` and `ComputedStyle` are 24 and 23 components wide**, and the one failure
  mode that follows is an argument in the wrong slot — a record that compiles,
  runs, and is wrong in a way no golden obviously shows. `RecordWitherTest` closes
  it: every wither is asked to set its component to the value it already holds,
  and must give back an equal record. That is complete — a wither that writes its
  argument into the wrong slot, reads the wrong component into a slot, or passes
  one component twice all fail it — and it needs no value factory and nothing per
  component, so a component added tomorrow is covered the moment its wither
  exists. Verified by planting a `width`/`height` swap the compiler cannot see:
  the test named the wither.

  **A test rather than a refactor**, deliberately. The structural answer is to
  group components into sub-records until no argument list is long enough to get
  wrong — which is what `Insets` and `Limits` already do for their four apiece —
  and doing it to the rest would turn `box.width()` into `box.layout().width()`
  across the whole toolkit for a benefit this catches completely and immediately.
