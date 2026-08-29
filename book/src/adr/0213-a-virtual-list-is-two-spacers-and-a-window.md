# 213. A virtual list is two spacers and a window

Date: 2026-08-29

## Status

Accepted. Delivers §10's committed follow-up, and unblocks `table`.

## Context

`docs/core-widgets.md` §10 says a `list` "renders instantiated rows — fine into
the low thousands" and commits **"virtualization/recycling is the committed v1.x
follow-up (the item-factory API is designed for recycling from day one so it's a
performance upgrade, not an API break)"**. ADR-0212 shipped the item-factory in
that shape and recorded that whether it survived contact was untested, because it
could not be tested until a recycler existed.

`table` is deferred behind the same work (ARCHITECTURE §17), so this is one
mechanism two entries in §10 are waiting on.

The hard part is not deciding *which* rows to build. It is that the answer
depends on geometry a widget cannot compute — where the list was painted, and
what clips it — and every facility that hands geometry to a widget carries the
same warning: **what it triggers must not change what it reports** (ADR-0117 rule
3, ADR-0119's termination rule). A list that built fewer rows would be shorter,
be told a new position, build again, and oscillate at the frame rate.

## Decision

**The window is computed from [Located], and the rows outside it become two
spacers.** `clip.top() - self.top()` is how far into the model the viewport has
reached, because `self` is where the list has been *scrolled to* rather than
where it was laid out. Divide by the row height for the first index, divide the
clip's height for how many fit.

**The spacers are what make it terminate**, and this is the whole of the safety
argument. A spacer's height is `rowCount × rowHeight`, so the column adds up to
the same total however the window moves: the node that was measured **does not
move**, the next frame reports the rectangle that produced this window, and the
second report changes nothing. That is `affix`'s structural answer (ADR-0119)
applied to a different problem — not a rule anyone has to remember, but an
arithmetic identity.

**Two spacers rather than one padded box.** The one above and the one below
answer different questions — how far down the window starts, and how much is left
under it — and a single `padding` could not say both.

**It is opt-in, and it takes a row height rather than a flag.** The height is the
one thing the widget cannot find out: a stylesheet resolves `--gb-list-row-height`
and no widget can read a resolved custom property (a live `TODO.md` entry, and
`ScrollViewport.LINE`'s reason for being a constant). A number nobody states is a
number nobody has. It also makes the feature honest about its precondition —
`index × height` is only a position if every row is that height, so a list with
rows of varying height must not virtualize, and saying the height out loud is
where that becomes obvious.

**Four rows of overscan.** `Located` is last frame's, so a wheel that travels
half a viewport between two frames would show a band of nothing for one of them.
Four rows is 128 logical pixels at the default height — more than a detent moves
— and it costs eight built rows on a model of any size.

**The first frame builds a guess**, because the window is computed from a painted
rectangle and the first frame is what produces one. The spacers make the guess
harmless: the column's total height is right from the first frame however few
rows are in it, so nothing jumps when the second frame corrects the window.

**`Home`, `End` and the typeahead reach rows that are not built.** This is the one
thing virtualization breaks and has to put back. All three move the focus by
*name* through `Host.focus` (ADR-0176), and a name resolves against the element
tree — so a virtual list asked for its last row was asking for a row that does not
exist, and `End` did nothing at all. The move becomes two steps: widen the window,
then focus.

**And the second step is a retry rather than a delay.** The frame loop fires its
timers *after* the platform pump, and whether the repaint a `setState` asked for
was drawn inside that pump or is still queued depends on the pacer — so the first
attempt may reach a tree that has not been rebuilt. `Host.focus` returns whether
it found anything, which is what makes that recoverable rather than a lost
keystroke. Bounded at two attempts, so an id naming no row costs two turns and
stops.

## Alternatives considered

- **Measuring the rows instead of being told a height.** It is what a variable-height
  virtual list needs and it needs a second pass: to know where row 4,000 begins you
  must have measured the 3,999 above it, which is the work being avoided. The usual
  way out is an estimate corrected as rows are measured, which makes the scrollbar
  drift under the reader's thumb. Not worth it for a row height the design system
  fixes anyway.
- **Recycling elements rather than rebuilding them.** §10 says "virtualization/recycling"
  and this is the first half. The reconciler already keys rows by item identity, so a
  window that moves by one row reuses every element but one — which is recycling, done
  by the machinery that was already there rather than by a pool this widget owns.
- **Reading the scroll offset from a `ScrollController`.** It would couple `list` to
  `scroll`, and a list is not always in one — `Located` reports the window's own
  rectangle when nothing clips (ADR-0119), so an unclipped virtual list correctly
  builds a screenful and no more.
- **Turning it on automatically above some row count.** The row height would have to
  be guessed, and a wrong guess is a broken layout rather than a slow one. A list of
  20,000 that is slow is a list somebody will profile; a list of 20,000 whose rows
  overlap is a bug report about drawing.
- **Making the reach synchronous by flushing the tree.** A widget that flushed the
  element tree from a key handler would be rebuilding the tree it is currently being
  dispatched inside.

## Consequences

- **A list of ten thousand builds about twenty rows**, and the tests say so by
  driving real painted frames — the assertions fail in seven places when the row
  height is set back to zero.
- **The focused row can be scrolled out of existence.** Wheel a virtual list far
  from the focus ring and the focused row leaves the window, is unmounted, and the
  router drops it (ADR-0180's rule). Arrow keys are unaffected, because the ring
  asks the viewport to follow it; only pointer-scrolling away and then pressing an
  arrow loses the place. Every recycling list has this unless it pins the focused
  index, and pinning it would keep a row nobody is looking at built for ever.
- **`list-spacer` is a part a stylesheet can select and must not decorate.** A
  border or a background on it would be a band of paint where the model says there
  are rows, which is exactly the illusion the spacer exists to maintain.
- **A row height that disagrees with the stylesheet is a silent layout error.**
  Nothing checks that the number passed matches what the rows measure; the symptom
  is rows that drift out of step with the scrollbar. A `Measured` assertion on the
  first built row could catch it, and is not built.
- **`table` is unblocked**, which was the other half of why this was worth doing
  now.
