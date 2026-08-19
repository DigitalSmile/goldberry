# 151. A frame can say what it did

Date: 2026-08-19

## Status

Accepted. The counters that two performance investigations each had to invent
from scratch, kept this time.

## Context

`hud`'s stage breakdown ([ADR-0146](0146-a-hud-shows-where-the-frame-went.md))
says *which* part of a frame is expensive. Twice now the answer to *why* has been
a count rather than a duration — the style cache missing on every element
([ADR-0142](0142-a-style-handed-down-keeps-its-identity.md)), and a click
invalidating the whole tree
([ADR-0149](0149-a-state-invalidates-what-it-can-reach.md)) — and both times
finding it meant compiling a counter into the renderer, running a purpose-built
probe, and taking it out again.

## Decision

**`-Dgoldberry.trace.frames=true` logs one line per frame that did something.**

```
frame 268 | build 0.010 style 8.636 layout 1.115 raster 1.304 ms
  | elements 125, built 0, resolved 3, invalidated 1
  | cascade 5.133 identity 0.956 motion 0.176 boxes 1.467
  | text 17 cached, 6 shaped | damage 3
```

The counts are what the timings cannot say. `resolved 3` beside `style 8.6` is a
cascade that is slow per element rather than running too often; `built 125` is a
rebuild; `subtree walks: column:ACTIVE -> 61` names the node and the state that
threw a subtree away; `6 shaped` is text being re-shaped that a cache should have
held.

`style` is broken down further into the four things the render walk does —
**cascade**, **identity** (`restyle` and the value comparison that keeps a style's
instance), **motion**, and **boxes** (the widgets' own `render`, paragraph
measurement included). That split is what separated "the cascade is slow" from
"the cascade is running too often", which are different bugs with the same
symptom.

**Quiet frames are skipped** unless `=all`, because an idle loop at 60 fps
otherwise writes a line a frame saying nothing happened, and the frames worth
reading are the ones next to the click.

**`-Dgoldberry.trace.input=true`** is separate and loud: one line per node per
pseudo-class the pointer or the keyboard sets. It is the other half of the same
question — that one says what a frame cost, this says what asked for it.

## Consequences

**Free when off.** Every counter sits behind one `static final boolean` read from
a system property, which the JIT folds away entirely. Nothing allocates on a
traced frame either, except the map naming subtree walks — and that is only
touched when a subtree is actually thrown away.

**A system property and not a log level**, for
[ADR-0101](0101-a-diagnostic-must-not-be-the-thing-it-measures.md)'s reason: an
`isTraceEnabled()` per element per frame would be a diagnostic measuring itself.

**The timings inside `style` are taken with `nanoTime` per element**, so a traced
frame is slower than an untraced one by four timestamps a node. That is fine for
finding a 10× problem and useless for finding a 10% one, and it is the honest
limit of a counter you can leave in the shipping build.
