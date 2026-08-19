# 153. A rate is counted; a refresh is asked for

Date: 2026-08-19

## Status

Accepted. Removes a reading [ADR-0150](0150-a-hud-reads-itself-against-a-budget.md)
put a budget on, and answers the question that removing it raised: can the real
frame rate come from SDL?

## Context

`frame` was the mean interval between frames, and `fps` is `1000 /` it. Both are
**counted by the loop**, and §1.7 makes that loop idle when nothing asks for a
frame — so the gap between two frames is however long the user did not touch the
window. A rate over that measures the user, not the toolkit: it collapses the
moment they stop clicking and stays low for the next sixty frames, because the
ring is sixty frames long.

ADR-0150 then gave both a budget and a colour. So a window sitting still read
red, permanently, and the showcase looked broken at rest. A diagnostic that cries
wolf while nothing is wrong is worse than no diagnostic, which is
[ADR-0101](0101-a-diagnostic-must-not-be-the-thing-it-measures.md)'s subject from
the other side.

**And SDL has no frame rate.** `SDL_GetCurrentDisplayMode` reports what the
*display* does; nothing anywhere reports what a loop achieved, because that is
not a thing a platform knows — only the loop can count its own frames.

## Decision

**`frame` is gone. `refresh` takes its place, and it is asked for rather than
counted.**

```
60 fps
refresh 60 Hz
paint 2.1 ms
build 0.05 ms
…
```

`SdlVideo.refreshRate` was already bound for the frame pacer; it is promoted to
`BackendWindow.refreshRate()` with a default of **0** for "the platform will not
say", which is a headless backend and a mode SDL cannot describe. `Window` reads
it once a frame — it is cached in the backend and changes only when the window
moves monitors — and it reaches a `hud` through `FrameStats.displayHertz()`.

**Every budget is now a share of one display frame** rather than of a hard-coded
16.7 ms: paint a half, raster a quarter, style and layout an eighth each, build a
sixteenth. So a 120 Hz window judges its paint against 4.2 ms, and the same
2 ms paint that is comfortable at 60 Hz is near its budget at 240. When the
platform will not say, it falls back to 60 Hz — a stated assumption rather than a
hidden one. This closes the gap ADR-0150 left in `book/src/TODO.md`.

**`fps` stays and is never coloured.** It is worth showing — while something is
moving, the loop runs continuously and it is exactly the number to watch — but it
is context rather than a budget. The readings the toolkit is answerable for are
`paint` and the four stages, and those are the same number whether the loop is
busy or idle.

## Consequences

**The showcase stops reading red at rest**, which was the report.

**A reading's name is a CSS class, and the namespace is shared.** The first draft
called this one `display`, and `.display` is already §1.4's largest type rank —
so `60 Hz display` rendered at 28px in the golden. Renamed to `refresh`. Nothing
prevents the next collision; the classes a widget invents and the classes a
design system defines are one namespace, and that is now written down.

**`refresh` reads dashes on the headless backend**, and every golden image of a
`hud` therefore states a rate explicitly. `FrameStats.of` grew an overload for it
rather than a defaulted field, so a test that does not care about the display
still compiles unchanged.

**A 0 Hz answer is not an error.** The pacer already treated it that way — an
unknowable refresh rate means an unpaced loop — and this reads it the same way:
assume nothing, say so, and budget as if 60.
