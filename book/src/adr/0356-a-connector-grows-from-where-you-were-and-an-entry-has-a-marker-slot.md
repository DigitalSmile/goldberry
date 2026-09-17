# 356. A connector grows from where you were, and an entry has a marker slot

Date: 2026-09-17

## Status

Accepted. Closes two of the three items ADR-0344 and ADR-0345 left in
`book/src/TODO.md`, and answers the third, ADR-0176's "`isModal` has one
consumer", without building it.

## Context

After the catalog's last four widgets were built, `book/src/TODO.md` listed three
small things under "What the last four widgets left behind" and §4:

1. **A step's connector filled by colour, not by `scaleX`.** §3.1 asks for the
   fill to grow along the line. ADR-0344 said the subset has no
   `transform-origin`, so a `scaleX` would grow from the middle.
2. **A `badge` could not be a timeline's marker.** §10 lists "dot, icon or
   `badge`". ADR-0345 built the first two and said a widget marker needs a slot
   markup can name.
3. **`isModal` has one consumer**, `dialog`. That entry named a `wizard` step and
   a `sheet` as plausible second consumers.

The first reason was wrong. `transform-origin` has parsed, cascaded and painted
since ADR-0068, with `TransformTest` covering its keywords and
`TransformPaintTest` its device-pixel resolution. The sentence in ADR-0344 was
copied from an older note and never checked.

## Decision

**The connector is a track with a fill in it, and the fill is scaled about its
start edge. An entry takes a widget marker from a `marker` child. A wizard is
not modal.**

### The connector

- `step-connector` keeps its size and its track colour, and gains one child
  part, `step-connector-fill`, that covers it. The fill is present in every
  state. A node built already done has no previous style to move from and would
  snap, which is `check-mark`'s reason (ADR-0067).
- `controls.css` gives the fill `transform: scaleX(0)` about `left center`, and
  `scaleX(1)` under `step-connector.done`, with `transition: transform` on
  `--gb-motion-base`. A vertical list uses `scaleY` about `center top`, so the
  line grows down.
- The fill carries no class. `step-connector.done step-connector-fill` reaches
  it, so the one word the list writes is written once.
- Settled, the pixels are what the colour version drew, so both `steps` goldens
  and both `wizard` goldens are unchanged. What differs is the frames between
  the two states.

### The marker slot

- `EntryMarker` is `@Markup("marker")` and holds exactly one widget. It is a
  description, the way `page` is to `wizard` and `tab` to `tabs`: `Entry.inflate`
  lifts its content out of the children and everything else stays the body.
- Named rather than inferred. "The first `badge` in the body" would move a badge
  that a document wrote as content onto the axis. Two `marker` children, or a
  `marker` with zero or two widgets, are refused.
- `Entry` gains a `marker` component and `withMarker(Widget)`. The seven-argument
  constructor is kept. A widget marker wins over an icon, and the pending ring
  never holds one.
- `TimelineMarker` becomes a bare holder with the class `widget`. It takes the
  widget's size and paints nothing of its own, so a badge draws as a badge, in
  its own colours.
- **The rail keeps its 20px.** A wider marker overhangs it evenly, and a short
  badge's overhang lands in the body's 8px of left padding. Widening the rail
  per entry would move the axis for one event. A timeline whose markers are all
  wide widens `timeline-rail` in its own stylesheet.
- The marker names nothing. The entry is still announced by its label and time,
  and §10's "the connecting line is a drawing" covers the axis.

### `isModal`

A wizard is not its second consumer. §6 makes the wizard's content area a
**focus-scope**: Next moves focus to the new page, and nothing is trapped. A
wizard is written inline in a window, and `isModal` traps the keyboard *and*,
since ADR-0232, the pointer, so an inline wizard declaring it would stop the
rest of the window taking input. A wizard shown inside a `dialog` is already
trapped by the dialog. `sheet` is not built. The entry stays open and now names
`sheet` alone.

## Consequences

- ADR-0344's and ADR-0345's "not built" lines are corrected in `TODO.md`,
  `docs/core-widgets.md` §6 and §10, and `controls.css`.
- The box tree under every step connector gains one node. A list of N steps
  paints N−1 more boxes, each a 2px line.
- `marker` is a markup name. It is only meaningful inside `entry`, and elsewhere
  it inflates to a description that draws nothing, which is `page`'s behaviour
  outside a `wizard`.
- The showcase's `Chronicle` gives Rivendell an `IX` badge marker, and
  `gallery-collections` is 1040 tall so the card is photographed whole.
- `timeline-badges-dark.png` is a new golden: a success `v2`, a one-digit `3`
  that is exactly the rail's width, and a dot after them.
