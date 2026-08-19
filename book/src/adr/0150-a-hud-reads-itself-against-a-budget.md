# 150. A HUD reads itself against a budget

Date: 2026-08-19

## Status

Accepted. Finishes [ADR-0146](0146-a-hud-shows-where-the-frame-went.md), which
put seven numbers on a plate and left the reader to know what they meant.

## Context

Three things were wrong with the breakdown as it shipped, and all three are about
a number that is correct and unreadable.

- **It said nothing about what the numbers were.** Every reading is a mean over
  the ring's sixty frames; `paint 2.1 ms` reads as "this frame" and is not. A
  reader with the wrong model sees a spike as a plateau on the way in and a
  plateau as a spike on the way out.
- **There was one total and there are two.** `paint` is the toolkit's share of an
  interval; `frame` is the interval. Four stages under a 2 ms paint inside a 16.7
  ms frame is idle hardware, and the same four under a 2 ms paint inside a 40 ms
  frame is something outside this toolkit — and the plate could not tell them
  apart.
- **Seven numbers in a row is a wall.** A reader scanning for the one that has
  gone wrong was scanning along a line of text, in the direction a line of text
  already uses.

## Decision

**A column, a caption, both totals, and a colour when a number is in trouble.**

```
60 fps
frame 16.7 ms
paint 2.1 ms
build 0.05 ms
style 0.29 ms
layout 0.11 ms
raster 1.34 ms
per frame · mean of last 60
```

Every reading carries a **budget** and reports one of three levels — `ok`,
`near`, `over` — as a CSS class, at three quarters of the budget and past it. The
budgets are shares of a 60 Hz frame: 16.7 for the interval, 8 for the toolkit's
paint, 4 for the raster, 2 each for style and layout, 1 for build. They are
judgements, and they live on the reading rather than in a token because a token
would invite an application to move the line instead of the number.

Two readings are judged differently, and both would otherwise cry wolf. The
**rate** is a floor, not a ceiling — more is better — and the **frame interval**
is a target to sit *at*: a vsynced loop is supposed to measure 16.7, and a healthy
window reading amber teaches a reader to ignore the colour.

**The colour comes from a class, and the class comes from the frame.** §10's whole
mechanism is that a colour is a token, so the widget says which of three states it
is in and `controls.css` says what that looks like — `--gb-warning` and
`--gb-danger`, which §1.2 admits "only with semantic meaning" and this is one.

That needed a hook: `Styled.classes(FrameStats)`. The cascade reads a node's
classes **before** that node's `render` runs, and the frame statistics only arrive
in `render`; a widget is a value described once and drawn many times, so it cannot
hold the answer in between either. It is the same shape as the pseudo-classes the
renderer already mirrors from `isChecked()` and `isDisabled()` — a fact the widget
knows and the element has to carry — with the frame added, because that is what
this fact is about.

## Consequences

**A widget can now class itself by the frame, and almost none should.** Anything
derivable from the widget belongs in `classes()`, which costs nothing per frame.
This is for a value that is genuinely a property of the loop, and there is one.

**A changed frame class invalidates that node's style and no other.** A rule
reading it through a descendant combinator would be a stylesheet colouring one
node by another node's frame timings, which is not something anybody should be
able to write (ADR-0149 is the machinery that makes "this node only" cheap).

**The HUD is a column for two readings as well as for seven**, and the default
plate is taller than it was. A diagnostic that changed shape with its contents
would be one whose position in the corner moved as the numbers arrived.

**The budgets are one machine's opinion of a 60 Hz display.** A 120 Hz window has
half the interval and every one of these is wrong by a factor of two — which is
in `book/src/TODO.md`, because reading the display's refresh rate is a backend
question and not this widget's.
