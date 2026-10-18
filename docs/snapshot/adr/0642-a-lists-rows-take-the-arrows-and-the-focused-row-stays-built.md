# ADR-0642: A list's rows take the arrows, and the focused row stays built

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0212](0212-a-list-owns-the-models-a-tree-borrowed.md), [ADR-0213](0213-a-virtual-list-is-two-spacers-and-a-window.md)

## Context

`TODO.md` carried *A virtual list can have its focused row scrolled out of
existence*. A wheel far from the focus ring carries the focused row out of the
window, the row is unmounted, and the router lets go of it (ADR-0180), so the
next arrow key goes nowhere. The entry called it what every recycling list
has, said pinning the focused index would keep a row nobody is looking at
built for ever, and claimed arrow keys were unaffected "because the ring asks
the viewport to follow it".

A probe through the real router, with a 10,000-row list in a `scroll`, found
the last claim untrue, and worse than the entry:

- **Nothing asks the viewport to follow the focus.** No reveal runs when the
  keyboard moves.
- **The arrows never reached the list.** `Up` and `Down` were the list's scope's
  to rove, and roving runs only after the focused chain declines a key. The
  `scroll` is in that chain and takes both arrows to scroll itself. So the
  arrows scrolled the rows past a focus that never moved, and after about ten
  presses the focused row left the window and the focus was dropped. That holds
  for a list that builds every row as well, minus the dropping.
- **Roving could not have worked on a virtual list anyway.** The scope roves
  between the rows that are built, so the row after the window's last was out
  of reach, and `Down` on it wrapped to whatever row the window began with.
- **`End` built every row.** A reach widened the window to the row it wanted, so
  `End` from the top of 10,000 rows built all of them for a frame.

## Decision

**The rows take `Up` and `Down`, as they already took `Home`, `End` and the
typeahead, and step by index.** `ListRow` consumes both and asks the list for
the row before or after it, wrapping at the ends as the scope's roving did.
The list reaches that row the way `Home` does (building it if it has to) and
scrolls the enclosing `scroll` by the least that shows it. The scroll is
moved with a nudge rather than a reveal, because a reveal glides and a held key
would restart the glide on every repeat. A list that builds every row reads
the row's painted rectangle from `Host.anchor`. A virtual one computes it from
the pitch or the measured tops. The scope stays, so the list is still one Tab
stop.

**The focused row is pinned.** The list hears its rows' focus changes and
remembers the focused identity. While the keyboard is on a row, that row is
built however far the window moves from it: on its own, between spacers
(`BuiltRuns`), not by widening the window. The cost the entry feared is one
row, and it lasts only as long as the focus does. Focus leaving the list
releases the pin at the next build.

**A reach pins the row it is building, too.** `End` builds the last row and
nothing between, and the viewport moves to it at once, since where a row is
does not wait for it to be built. A measured list's reach already jumped its
window, and keeps doing so.

Rejected:

- **Making `scroll` decline the arrows inside a composite.** It would mend the
  scope's roving and leave both of its other faults: the window's edge, and no
  reveal.
- **A general focus-follows reveal in the router.** It would be right for every
  composite in a viewport, and it cannot reach a virtual list's unbuilt rows.
  It is a larger change than this entry needs, and nothing else has asked.
- **Stopping at the ends instead of wrapping.** It is the more common desktop
  behaviour, but it would change what a list that builds every row has always
  done, for a reason unrelated to this entry.

## Consequences

- With focus on a list row inside a `scroll`, the arrows move the focus and the
  viewport follows. They no longer scroll the viewport on their own. The wheel,
  the scrollbar and `Page Up`/`Page Down` still do.
- `table` composes a `ListView` and gets all of this for free.
- `MeasuredRow` forwards the row's focus changes, which it must now that the
  list listens for them.
- `ListFocusedRowTest` holds it through the real router and a painted frame:
  stepping past the bottom, wrapping to row 9,999, a wheel away and back, `End`
  building one row, a measured list, and a list that builds every row.
  `BuiltRunsTest` holds the run arithmetic.
- `tree` roves its rows through its scope, so inside a `scroll` the viewport
  takes its arrows the same way. It is not virtual, so it never loses a row. It
  was not part of the entry, was not probed, and is left as it was.
