# 250. A stack is one child in flow and the rest over it

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0100](0100-a-window-has-a-layer-above-its-application.md), whose last
blocker [ADR-0244](0244-a-child-may-say-where-it-sits.md) removed.

## Context

`docs/core-widgets.md` §1 has asked for one since the beginning:

> **`stack`** — z-order layering; children positioned by alignment or absolute
> insets. Basis for badges-over-things and custom overlays.

The entry tracking it said the mechanism was never in doubt — `WindowRoot`
already lays an overlay out with `position: absolute` and Yoga insets — and named
one thing missing: the **alignment** half, which needed `align-self`. That
arrived in ADR-0244, and the entry's own last sentence became "what `stack` still
wants is `stack`".

## Decision

**The first child stays in flow; every child after it is `position: absolute`.**

That is the whole of the widget. It is nine lines, and each of the two halves is
answering a question the other could not:

- **Something has to give the stack a size.** A box whose children are all out of
  flow is a box of nothing, so the first child is left alone and the stack takes
  its size. This is also what the name implies — the thing, and then what goes on
  top of it — and it makes wrapping an existing widget in a `stack` a change that
  cannot move it.
- **An overlay must not resize what it sits on.** Taking the rest out of flow is
  what stops a badge widening the avatar under it.

### Nothing here positions anything, and that is the point

§1 asks for children "positioned by alignment or absolute insets", and `stack`
implements neither. Both already worked:

- An absolute child with **no inset** is placed by its container's `align-items`
  and `justify-content`, and by its own `align-self`. So a badge reaches a corner
  with two declarations and no arithmetic.
- An absolute child **with** an inset goes exactly where it says.

This is the case `ComputedStyle.INITIAL` has been describing since before
anything could reach it. Its comment on the inset field reads: *"an inset of zero
pins a node to its container's edge, and 'no inset at all' is what a node that
never mentions one must get. Yoga spells that `undefined`, and the difference
only shows on an absolute node — where zero would stretch it and undefined leaves
it where the alignment put it."* `stack` is the widget that finally shows it.

**Z-order is document order**, which is the painter's existing rule for siblings
rather than anything this widget arranges.

## Alternatives considered

- **Every child absolute.** The stack then has no intrinsic size and collapses to
  nothing, so every use would need an explicit width and height — which is the
  opposite of "badges over things", where the thing already knows how big it is.
- **A `layer=` or `z=` attribute.** Z-order is document order and the painter
  already works that way; a second spelling of "which is on top" is two things to
  keep in step. `elevated` remains the way to lift a box out of order (ADR-0069),
  and a stack does not use it.
- **An `align=` attribute on the stack**, so a document could write
  `stack align="top-end"`. It is a second vocabulary for `align-items` and
  `justify-content`, in a toolkit whose whole argument is that layout is the
  stylesheet's.
- **Positioning the overlays in `render`.** It needs the boxes' sizes, which do
  not exist until Yoga has run, and it would put a layout engine inside a widget
  that already has one underneath it.

## Consequences

- **The catalog is 52 widgets**, and §1's `core` group is complete but for
  `image`.
- **Ten tests, six of which fail against a `stack` that does not position
  anything** — asserted against **Yoga's own output**, because every claim a
  stack makes is a claim about where boxes ended up.
- **The tests wrap the stack in a row that does not stretch it**, and that is
  load-bearing rather than tidy: the root box is always laid out at the frame's
  size, so a stack tested as the root is 300 wide whatever its children do and
  every size assertion passes for the wrong reason. That was found by writing the
  assertions first and watching four of them fail at 300.
- **The markup path is tested through the real catalog**, not by constructing the
  record — `stack` is in §1's `core` list, so what is under test is the
  registration.
- **No stylesheet rule ships for it.** A `stack` sets no colour, no padding and
  no gap, and where its overlays land is the application's to declare — which is
  `row`'s and `column`'s arrangement exactly.
