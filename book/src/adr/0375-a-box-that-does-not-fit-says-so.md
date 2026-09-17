# 375. A box that does not fit says so

Date: 2026-09-17

## Status

Accepted. Closes the open half of `book/src/TODO.md`'s "Nothing has a minimum
size, so overflow is silent".

## Context

`flex-shrink: 0` stops a control being squashed and does not stop it being
clipped: a window narrower than its content overflows, which is CSS's behaviour
and not a bug. The entry's two proposed answers — a scroll view and an ellipsis —
both shipped, and what was left is that **nothing says it happened**. A button
pushed off the right edge of a window looks exactly like a button that was never
built, and the toolkit knew and did not mention it: Yoga has recorded
`hadOverflow` per container since the first day, and nothing read it.

## Decision

**The layout pass asks the root whether its line overran, and when it did, a walk
names what is off the edge — once.**

- `RenderTree.update` calls `YGNodeLayoutGetHadOverflow` on the root after
  `calculateLayout`. That is one foreign call on a frame where everything fits,
  which is nearly every frame.
- `OverflowWatch` (in `paint.tree`, where the tree is) walks only then, and
  reports each child laid out past its container's own edge.
- `paint.overflow` holds the rest: `Overrun` is the value — two names and two
  distances — and `OverflowLog` is the dedupe and the sentence. Keyed by
  *shape*, `container > child`, not by distance, so dragging a window narrower
  says it once rather than once a pixel. The same argument, and the same cap, as
  a dropped declaration (`ComputedStyle.REPORTED`).
- **Not** reported: a container whose `overflow` is not `visible`, because that
  is a `scroll` viewport working; and a child whose `position` is `absolute`,
  because it was placed rather than flowed — a tab's underline, a popover's
  arrow.
- `OverflowLog.reported()` hands the list back, so a test asserts on what was
  said without a logging backend and an application can put it on a HUD.

## Consequences

- The warning names the two boxes and says what the three answers are — a
  `scroll`, an ellipsis, or a `min-width` — and picks none of them, which is the
  author's decision and was the entry's own conclusion.
- On a window that is *already* too small the walk runs every frame. That is the
  deliberate trade: the frame budget's difficult case is a window that fits, and
  the reporting is bounded however often the walk is not.
- A box built by composition has no type, so it is reported as "a box". That is
  the same anonymity that makes it invisible to a type selector.
