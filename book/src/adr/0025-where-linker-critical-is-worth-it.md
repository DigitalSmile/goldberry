# ADR-0025: Where `Linker.Option.critical` is worth it

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.1, §5, [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0024](0024-a-repaint-must-wake-the-loop.md)

## Context

`Linker.Option.critical(boolean allowHeapAccess)` tells the linker that a native
function is short-running, does not block, and does not call back into Java. The
JVM may then run the call **without transitioning the thread out of Java state**,
which removes the per-call transition cost. With `allowHeapAccess = true` it also
permits passing an on-heap `MemorySegment` straight to native code, where the
default would refuse it — the JVM pins the array for the duration instead of
staging a native copy.

The obvious place to reach for it is the frame path, and the obvious hope is that
it removes a copy. Both deserve measuring rather than assuming, because the option
is not free: a critical call keeps the thread in Java state, so **the garbage
collector cannot reach a safepoint until it returns**. A long or blocking call
under `critical` stalls every thread in the VM for its duration.

## Decision

**Not in the SDL frame path.** Measured, on linux-x64:

- A round trip through a bound SDL function costs **~6.3 ns** once warm
  (1,000,000 calls of `SDL_GetVersion` through its binding, including decoding a
  record from the result).
- A frame at 1920×1080 makes about ten downcalls: two size queries, a scale query,
  `SDL_GetWindowSurface`, `SDL_UpdateWindowSurfaceRects`, and a handful of event
  polls.

That is roughly **60 ns of call overhead in a frame that takes 5,000,000 ns** —
about one part in eighty thousand. `critical` could remove some fraction of that
60 ns. There is nothing there to win.

**And it removes no copy.** After ADR-0024 the toolkit paints directly into the
buffer SDL lends it, so no pixels cross the boundary at all. The copy that
remains is inside `SDL_UpdateWindowSurfaceRects`, moving the surface into the
compositor's buffer — native code copying native memory, which no linker option
can reach. `allowHeapAccess` would matter only if a *heap* segment were being
passed as a pointer, and none is: the event buffer and the damage rectangles are
native arena allocations, and the pixels are SDL's own memory.

**The rule for when it is considered:** the function must be short, non-blocking,
and free of upcalls, and the call site must be hot enough for a few nanoseconds to
matter. In the current bindings the calls that are hot enough are not short, and
the calls that are short are not hot:

| Call | Short? | Hot? | Verdict |
|---|---|---|---|
| `SDL_WaitEventTimeout` | **no** — blocks up to a second | once per pump | never; it would stall the VM |
| `SDL_UpdateWindowSurfaceRects` | no — 3–6 ms, talks to the compositor | once per frame | never |
| `SDL_GetWindowSurface` | no — 72 ms when it allocates a new surface | once per frame | never |
| `SDL_GetWindowSizeInPixels`, `SDL_GetWindowDisplayScale` | yes | ~3 per frame | allowed, and worth ~20 ns |

**Where it will pay off is M1.** Text shaping and paint call into HarfBuzz and
Blend2D per glyph and per path — thousands of short, pure calls per frame, which
is exactly the shape `critical` exists for. `allowHeapAccess = true` is worth
revisiting there too, so a Java glyph array can be handed over without staging it
into native memory first. That is a decision to make with a benchmark in front of
it, which is why this record exists: to say what the benchmark has to show.

## Alternatives considered

**Apply it to the safe getters anyway.** It is three lines and the calls do
qualify. It also buys ~20 ns per frame, adds an option to every reader's mental
model of those bindings, and establishes a habit of reaching for `critical`
without measuring — which is how it eventually gets applied to something that
blocks.

**Apply it to `SDL_UpdateWindowSurfaceRects`, the expensive one.** This is the
tempting mistake and the reason for the table above. The call is expensive
*because* it does real work and talks to the compositor; under `critical` the
whole VM would be unable to collect garbage for the 3–6 ms it takes, every frame.
The cost being high is precisely what disqualifies it.

**Use `SDL_Renderer` with a streaming texture instead of the window surface.** A
different way at the same problem — it would move the upload onto the GPU and make
the copy someone else's. It is a real option for later and has nothing to do with
linker options.

## Consequences

The bindings stay plain, and the frame path's cost stays where the measurements
say it is: about 60% in SDL's upload to the compositor, about 35% in Goldberry's
own writes to the frame buffer, and under 5% in everything else including every
FFM crossing.

The rule is written down, so the next person to have this idea — reasonably — can
check it against a table instead of re-deriving it.

The benchmark behind the 6.3 ns figure was a throwaway. If `critical` is
revisited for the M1 text path it needs a real one, tracked over time (§14 already
asks for upcall and shaping micro-benchmarks); a number measured once and quoted
forever is how a decision outlives the facts that justified it.
