# 154. A reading is a range

Date: 2026-08-19

## Status

Accepted. Finishes the `hud` line that
[ADR-0146](0146-a-hud-shows-where-the-frame-went.md) started and
[ADR-0150](0150-a-hud-reads-itself-against-a-budget.md) coloured.

## Context

Every reading was one number: the mean over the ring's sixty frames. A mean is
the right thing for a budget to judge and the wrong thing to read a frame by,
because it hides the shape of the cost — and the shape is usually the question.
Two windows both averaging 2 ms of paint are different animals if one never
leaves 1.9–2.1 and the other ranges 0.2–14: the second is a spike being averaged
away, and nothing on the plate could say so.

Three smaller things were wrong with the surrounding text. `per frame` did not
say what unit the numbers were in. `mean of last 60` described the arithmetic
rather than the reading. And `this hud included`, added one commit earlier for
honesty, spent a whole line on a caveat that is true of every diagnostic ever
drawn into the thing it measures.

## Decision

**Each duration reading is `min / mean / max`.**

```
60 fps
refresh 60 Hz
paint 1.3 / 2.1 / 5.0 ms
build 0.00 / 0.05 / 0.15 ms
style 0.15 / 0.29 / 1.16 ms
layout 0.04 / 0.11 / 0.22 ms
raster 0.94 / 1.34 / 2.55 ms
ms/frame · min / mean / max · last 60
```

`FrameStats` grows a `Span(min, mean, max)` and a span per stage. The default is
a **flat** span built from the existing mean, so a source that keeps no window —
a test's fixed numbers — reports its one number three times rather than inventing
a spread it never measured. `FrameRing` computes all three in one pass over at
most sixty `long`s, which is what keeps it safe to ask inside a `build` that runs
every frame.

**The mean is in the middle**, where the eye lands and where the budget is
judged. The colour still comes from the mean: a budget is about what a frame
costs habitually, and colouring by the max would paint every window red for one
slow frame in sixty.

**The caption says the unit and the shape** — `ms/frame · min / mean / max · last
60` — because the rows say neither. `this hud included` is gone: it is true, it is
true of every such diagnostic, and it is in ADR-0152 where a reader who wants it
can find it.

## Consequences

**`value()` and `text()` read the same span**, and this is not a tidying. The
first draft left the level reading `styleMillis()` while the row printed
`style()`, and the over-budget golden came out with `style 4.80 / 9.60 / 38.40 ms`
in the quiet colour — nine milliseconds over an eighth of a frame, drawn as
though it were fine. A colour that can disagree with the number beside it is
worse than no colour.

**The plate is wider.** Three numbers and a label is about 24 characters, against
nine before. A HUD is content-sized and pinned to a corner, so it costs screen
rather than layout — and a diagnostic that has to be legible at a glance is worth
the corner.

**A golden of a HUD now needs a `FrameStats` with a spread**, because a flat one
draws the same number three times and proves nothing about the row. The one in
`HudGoldenTest` is written out rather than built from `FrameStats.of`, which is
the honest cost of the default being flat.

**`min` is frequently `0.00`**, and that is a real reading rather than a rounding
artefact: a frame in which no widget rebuilt spends no measurable time building,
and a window with sixty of them in its ring has a genuine floor of zero.
