# 309. A sheet of icons reflows, and pays for it

Date: 2026-09-13

## Status

Accepted. Replaces the fixed-column virtualized list
[ADR-0307](0307-the-eleventh-screen-has-no-digit.md) built the Icons screen with,
and records what that trade costs.

## Context

The Icons screen chunked 1544 names into rows of a **fixed seven** and put them in
a virtualized `list`. That was cheap and it was wrong in the one way a sheet of
things can be wrong: it did not follow the window. A wide window left a band of
empty space down the right; a narrow one had its last column clipped, because
seven tiles of 152 do not fit in 720 points and a `row` does not care.

The fix is a reflowing grid, and §8's subset has no `grid` — no `column-count`,
no `repeat(auto-fill, …)`. What it has is
[`masonry`](0196-a-masonry-is-a-layout-that-reads-last-frame.md), which is a count of
equal-width columns and nothing else.

So the question is not "how do I reflow" but **what the reflow costs**, because a
masonry cannot virtualize: placing a card under the shortest column is a decision
about every card, so every card has to exist.

## Decision

**A `masonry` whose column count is as many tiles as fit, inside a `scroll`.**

The count is `floor((width + gap) / (tile + gap))`, over a width the last frame
reported through [Measured](0119-a-widget-may-be-told-where-it-is.md). One frame
of settling, which is `Masonry`'s own arrangement for heights and is invisible:
the default is seven, which is what the window opens at.

Three things fell out of building it that are worth writing down.

### A masonry of equal-height tiles reads across the row

`Masonry`'s own documentation warns that it reads **down** each column rather than
across, and calls that its one real cost. That warning is about cards of
*differing* heights. When every tile is the same height, "the shortest column, and
the emptiest of the equally short" places them across the row — so an alphabetical
sheet reads left to right, which is what a reader scanning for `chevron-right`
expects. The property is asserted, because it is a consequence of a tiebreak
rather than something the widget promises.

### The scroll's content must not grow

`icon-sheet` had `flex-grow: 1`, which is what a box that should fill its viewport
looks like. It is a vertical `scroll`'s **content**, and content told to grow is
exactly as tall as the viewport — so nothing overflowed, no thumb was drawn, and
the sheet simply ran off the bottom of the window. A golden could not tell the
difference: content running past the edge looks the same either way, and the thumb
has faded by the time a picture is taken.

That is why the screen has a driven test as well as two goldens. It asserts the
content is taller than the viewport, which is the difference between scrolling and
overflowing.

### The measurement, which contradicted the first draft of this record

| | elements | opens in | build | style | layout |
|---|---|---|---|---|---|
| the whole sheet | 4709 | 464 ms | 0.0 ms | **3.7 ms** | 0.6 ms |
| after typing `ar` | 1085 | — | 0.0 ms | 0.9 ms | 0.2 ms |
| the wall, for scale | ~220 | — | 0.0 ms | ~0.3 ms | ~0.3 ms |

This record's first draft said the sheet was "expensive to open and ordinary to
scroll". The first half is an understatement and the second half is false. The
style pass is **O(elements) whatever is cached** — ADR-0299's cache stops the
*shaping*, not the walk — so twenty times the wall's elements is roughly twenty
times its style cost. 4.3 ms of build, style and layout is a quarter of a 60 Hz
frame before anything is rasterized.

`FrameBudgetTest` now measures both rows and asserts the whole sheet against a
budget **of its own** — the measurement with room to move. A budget the screen
fails would be a test nobody can leave green; a budget it cannot fail would be no
test at all.

The filtered row is asserted as a **ratio**, and that too is a correction. The
first version of the test asserted it against the wall's 1.0 ms and it failed
under a full build at 1.25 ms: 1085 elements is still five times the wall's, and
the measurement straddles the line depending on what else the machine is doing.
The claim this screen actually makes is that *searching takes most of the cost
back* — a third of the elements, less than half the style — and that is what is
checked.

## Consequences

**The sheet follows the window**, which is the point: seven columns at 1200, four
at 720, and the last column a whole tile at both.

**Opening the Icons tab takes about half a second.** That is a real cost on a real
gesture, and it is the one number here that would stop this being acceptable in an
application rather than a gallery. It is paid on the tab switch, once per visit.

**The search field is the performance story**, not a convenience. Two letters take
the sheet from 4709 elements to 1085 and roughly three quarters of the style cost
with it — and looking for an icon is the only reason to be on this screen. The
screen is defensible *because* of the field, which is an argument worth being
explicit about rather than a happy accident. It does **not** make the sheet as
cheap as a wall of cards, and saying it did is the over-claim this record had to
take back.

**`IconsScreen.columnsFor` and `showcase.css` share two numbers** — the tile width
and the gap. A widget cannot read what a stylesheet resolved for a node it is
about to describe, so the arithmetic is stated twice and the CSS says so. Changing
one without the other gives a sheet that over-fills its row, which the driven test
catches by asserting no tile is narrower than a whole tile.

**Virtualization is gone and could come back** without losing the reflow: a
virtualized `list` whose rows are `columnsFor(width)` tiles wide is the same
picture with the same reflow and a bounded element count. It is not what is built
here, and the reason is that it is not a masonry — it is a hand-rolled grid that
would have to re-derive the row chunking on every resize. If the opening cost
matters more than the simplicity, that is the change to make, and the numbers
above are what would justify it.

## Alternatives considered

**Keep the virtualized list and make its column count dynamic.** Rejected for
this change and named above as the way back. It keeps the cost bounded and gives
up the widget: chunking names into rows by a measured width is a grid written by
hand in the screen, where a masonry is one that already exists and is tested.

**Cap the sheet at the first N matches with a "keep typing" line.** Rejected: the
screen's whole claim is that the bundled set is *browsable*, and a sheet that
shows 300 of 1544 until you guess a word is a worse answer to "which icons are
there" than the website it replaces.

**Drop the caption element and draw the name as a second box on the tile.** It
would take about a third of the elements, which sounds like the fix and is not:
3 ms instead of 4 is the same order of magnitude, and the caption needs its own
resolved style — muted, `caption`-ranked — which is what an element *is*. Rejected
as a change that costs the styling and buys a number that is still over.

**Leave the fixed seven columns and let the last one clip.** Rejected — it is the
defect this record exists to fix, and it is visible in the first narrow golden of
the screen.
