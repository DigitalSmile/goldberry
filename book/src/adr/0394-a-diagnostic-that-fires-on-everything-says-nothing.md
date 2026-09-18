# 394. A diagnostic that fires on everything says nothing

Date: 2026-09-18

## Status

Accepted. Re-scopes the overflow watch of
[ADR-0375](0375-a-box-that-does-not-fit-says-so.md) and the nested-viewport
notice of [ADR-0251](0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md), and moves
the resize and popup chatter of the window layer down a level.

## Context

Running the showcase and reading the log is how an application author finds out
what the toolkit thinks of their layout. What they actually got was a wall:

```
WARN  OverflowLog - `card#links-card` overruns `masonry-cell` by 1.0 tall — …
WARN  OverflowLog - a box overruns `icon-tile-name` by 1.0 wide and 2.0 tall — …
WARN  ScrollState - a vertical `scroll` is inside another one; §2.4 rules that out …
```

None of these is wrong about the geometry. All three are wrong about whether
anybody needed to be told.

## Decision

### The overflow watch reports what a reader could see

The numbers first, because they are the argument. Instrumenting `Overrun.between`
and running `:example:test` and `:widgets:test` produced **688** reports. Sorted
by how far the child overran:

| overrun | reports |
|---|---|
| ≤ 0.5 px | 10 |
| ≤ 1 px | 252 |
| ≤ 2 px | 384 |
| ≤ 4 px | 3 |
| ≤ 16 px | 22 |
| > 16 px | 17 |

Ninety-two per cent of them are two pixels or less, and the cliff between 2 and
4 is nearly total. A distribution shaped like that is not a codebase with 650
layout defects in it; it is a diagnostic measuring something other than what it
meant to. Three separate causes, each now exempted in `Overrun.between` with the
case that found it:

- **A container with no size.** `slider-tick` sits in a `0 × 0` box — an anchor
  for placed children rather than a box anything could fit inside. Every child
  overruns it by its own whole size. 22 reports.
- **A child that starts outside.** `slider-thumb` is a 16 px square centred
  across a 4 px groove: `box=16.0x16.0@(100.0,-6.0)`. Flow never produces a
  negative offset — a flowed child begins at its container's content origin — so
  a child at `-6` was *put* there, which is the "placed rather than flowed"
  exemption `OverflowWatch` already made for an absolute box, arriving through
  insets instead. Together with the above, 41 reports.
- **A pixel or two.** Layout is rounded onto the device pixel grid, and a line
  box may be shorter than the face's natural leading. CSS allows `line-height`
  tighter than the text in it and text overhanging its line box is the ordinary
  consequence — the same phenomenon
  [ADR-0393](0393-an-emoji-is-routed-by-the-text-and-drawn-in-layers.md)
  measured for emoji, where OpenMoji ascends 0.7 px above Inter's box. The
  tolerance goes from a tenth of a pixel to **two**.

688 reports become **23**, and the 23 are real: a `masonry` 585 px taller than
the screen it is on, a `badge` 16 px outside its panel, a `button#reset` 64 px
past the end of its row.

Two pixels is a number and numbers in a diagnostic deserve suspicion, so it is
worth saying what it is not. It is not tuned to make the showcase quiet — the
showcase still reports 23 things. It is the point where the measured
distribution stops being arithmetic, and it is well under what this watch exists
to catch, which is a control pushed off the edge of a window.

### The nested-viewport notice was firing on its own advice

It said: *"Give the inner box a size and let the outer one scroll."*

It was firing on `scroll.tall-list` in the showcase's Collections screen — a
virtualized list given `height: 256px; flex-grow: 0; flex-shrink: 0`, inside the
gallery's own viewport. That is the recommendation, followed exactly. It is also
what every chat window, console and settings page with a log box is, and the
engine's behaviour there is defined rather than accidental: the inner one takes
the wheel until it reaches its edge.

What §2.4 is actually about is an inner viewport with **no size of its own** on
the scrolling axis, which grows to its content and leaves the wheel ambiguous.
Telling those two apart needs the inner box's resolved height, and the check runs
in `build`, before the cascade has resolved anything.

So the message stays, drops to `debug`, and stops saying §2.4 "rules that out"
in favour of "discourages" — because on the evidence of the toolkit's own
showcase it does not rule this out, it rules out the sizeless case. A `WARN`
claims something is broken; nothing is. The sharper rule is recorded as needing
a layout-time signal that does not exist yet rather than guessed at now.

### Resize and popup chatter is `trace`

Both are per-interaction rather than per-event-of-interest. A resize drag is one
`window resized` line and one `allocating a frame buffer` line per pointer
motion; a popup opening and closing says so every time a `select` is touched.
Neither is something an author reads on purpose, and both drown what is. They
are `trace` now, which is where a per-frame fact belongs.

Two deliberate exceptions, because "all of them" would have been wrong:

- **`drawing during a resize failed`** stays a `WARN`. It carries a stack trace
  and only fires when something threw.
- **`walking the window's size a pixel a frame`** stays `INFO`. It is printed
  once at start-up and only when `--resize=WxH` asked for it; silencing the
  confirmation of a flag the author passed is not quieting, it is hiding.

## Consequences

**An author who reads the log now sees 23 things instead of 688**, and each of
them is a box a reader could actually see in the wrong place.

**Between two and four pixels is now silent.** An overrun in that band is not
reported at all, and three of the 688 were there. If one of them ever turns out
to matter, the answer is not a smaller tolerance — the cliff would come back with
it — but a diagnostic that knows about line boxes.

**The nested-viewport notice is off by default.** An author who nests two
viewports without giving the inner one a size gets a working, slightly confusing
scroller and no message unless they turn `debug` on. That is the cost of not
crying wolf on the arrangement the message recommends, and it is the right way
round: the failure mode is mild and the false positive was constant.

**`OverflowLog.reported()` is unchanged as an API.** An application that reads it
on a frame gets the same list the log would have printed, which is still the
sanctioned way to assert on this in a test.
