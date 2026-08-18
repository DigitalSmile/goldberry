# 116. A scroll view is a clip, an offset and two extents

Date: 2026-08-18

## Status

Accepted. Builds on [ADR-0114](0114-a-clip-is-a-rectangle-the-painter-carries.md)
(the clip), [ADR-0115](0115-a-wheel-reports-a-fraction-and-a-detent.md) (the
wheel) and [ADR-0068](0068-the-transform-stack-is-java-side.md) (the transform
stack). Answers the geometry question
[ADR-0080](0080-a-value-is-measured-along-a-part.md) and
[ADR-0097](0097-a-selection-that-travels-needs-a-geometry.md) both stopped at.

## Context

`scroll` is `docs/core-widgets.md` §1, and `book/src/TODO.md` named it three
times in three unrelated entries: a menu taller than the work area is clamped
and loses its bottom, a tab strip wider than its window overflows it, and
`select` over a realistic option list cannot be written at all. One missing
widget behind three pieces of blocked work is the definition of what to build
next.

It is also the widget that finally forces a question the toolkit has walked
around twice. A scroll view is arithmetic on **two rectangles** — how much
taller its content is than its viewport — and a widget cannot measure either
one. `build` and `render` both run before Yoga, which is ADR-0080's finding;
ADR-0097 hit the same wall from the other side, wanting the distance between two
segments and finding that "a stylesheet cannot write it, because segments are as
wide as their labels; and a widget cannot compute it, because build/render run
before Yoga".

Both of those ADRs found a way to avoid needing the number. `scroll` cannot: the
clamp *is* the widget.

## Decision

### Three nodes, each of them one idea

```
scroll               the viewport. Clips, takes the wheel and the keys
└── scroll-content   the moving box. Translated by the offset
    └── …            whatever was written inside
```

`scroll` itself is a **composition node**: stateful, styling nothing, holding
the offset. `scroll` as a CSS type is the viewport it builds, for exactly
[ADR-0109](0109-a-tab-arrives-and-departs-on-the-frame-clock.md)'s reason — a
stateful widget that was also styled would put two `scroll` nodes in the cascade,
one inside the other, and every rule would apply to both.

### The offset is a transform, not a layout

The content is moved with `transform: translate`, which costs no layout. §1.7
already refuses to transition width and height because "animating width/height
would run Yoga per frame"; an offset expressed as `top` or as a margin would run
Yoga over the whole subtree on **every wheel notch** to move a box that did not
change size.

It also makes hit testing come out right for free. The painter carries the
accumulated matrix and the router inverts it (ADR-0068), so a row scrolled up by
200px is clicked where it looks, with nothing in the scroll view arranging that.
And it makes the clip correct without a second mechanism: ADR-0114's clip is
intersected in the same walk, so content translated out of the viewport is cut
at the viewport's edge rather than painted over its neighbours.

`flex-shrink: 0` on the content is the whole difference between a scroll view
and a squashed one. Yoga's default shrinks a child that does not fit, so content
in a too-short viewport would be compressed to fit it and there would be no
overflow left to scroll — negotiated away before it was ever measured.

### The two extents arrive on the event

This is the part that is new machinery rather than assembly.

`PointerEvent` and `KeyEvent` each gained `bounds()` and `part()`, both
[`Extent`](../../../core/src/main/java/io/github/digitalsmile/goldberry/input/Extent.java)s
— a width and a height, resolved by the router out of the hit-test snapshot the
last paint left behind. `bounds()` is the handling widget's own box; `part()` is
the box named by the existing `Handles.localPart()`. A scroll view names
`scroll-content`, so it is handed its viewport and its content in the same event
and the clamp is a subtraction.

Three things make this the right shape rather than a special case:

1. **It reuses a vocabulary that already exists.** `localPart()` was built for a
   slider measuring a value along its track (ADR-0080). Nothing new is being
   named; what was missing was the *other* rectangle — a widget that pointed
   `local()` at a part had no way back to itself.
2. **It works for the keyboard**, which is why it is on `KeyEvent` too. `PageDown`
   carries no position and needs both extents exactly as the wheel does. Nothing
   had ever put a size on a key event, and a scroll view that only worked for
   people with a mouse would fail §1's "keyboard (PgUp/PgDn/Home/End/arrows when
   focused)" outright.
3. **It stays one frame behind, and that is honest.** These are measurements of
   the last paint, not predictions. A viewport resized this frame clamps against
   last frame's height for one frame — invisible in practice, and the alternative
   is a widget that computes layout, which is the thing three ADRs have now
   declined to build.

### At the edge it lets go

A wheel or a key is consumed **only when something actually moved**. At the top
of a list a further scroll up is left unconsumed and bubbles, which is §2.4's
"inner scroller consumes until its edge, then chains to the ancestor" — obtained
from the router's ordinary bubble path rather than from anything here knowing an
ancestor exists. `PointerRouter.pointerWheel` now reports whether anything
consumed the event, which `keyPressed` already did.

### What a line is worth is a constant, and that is a gap

The wheel reports lines (ADR-0115) and a viewport moves pixels, so something has
to convert. That number is `ScrollViewport.LINE`, a constant of 20 — three of
which is the conventional notch, which is what the rest of the desktop does.

It **should** be a token. §3 says metrics ship as component-token defaults that
an application may override, and this is plainly one. It is not, because nothing
lets a widget read a resolved custom property: `StyleResolver` computes them for
`var()` substitution and `ComputedStyle` does not carry them, so a
`--gb-scroll-line` would be a number an author could set and no widget could
see. Shipping a token with no reader is worse than shipping the constant, because
the token would silently do nothing. The gap is in `book/src/TODO.md`; closing it
is the same change that would let any widget honour any metric.

## Consequences

Three blocked pieces of work are unblocked: a popup can hold a scroll view, a tab
strip can scroll its overflow, and `select` over a realistic option list is now
ordinary widget work. None of them is done here — this is the viewport they were
waiting on, not their integration.

**What is not built**, and each is in `book/src/TODO.md` with what it waits on:
scrollbars of any kind (§2.4's overlay thumb, its hover-widening, its idle fade,
its drag and its track-click paging), `scrollIntoView`, the "always show scroll
bars" 12px gutter, and `affix`. The scrollbars are the largest and want a second
commit rather than a bigger one; the rest each want a decision first.

Every widget in the catalog now sees `bounds()` on the events it handles, and
most should ignore it. The temptation it creates is real — a control that starts
laying itself out from last frame's measurements is a control that lags its own
content — and the rule that keeps it honest is the one ADR-0080 already wrote:
read geometry to interpret an *input*, never to decide a *size*.

Nested same-axis scrollers are banned in the canon (§2.4) and nothing enforces
the ban. Chaining works, so a nested pair behaves reasonably rather than badly;
what is missing is the diagnostic that would tell an author they wrote something
the design system rules out.
