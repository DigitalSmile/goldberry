# 425. What a render may keep, and the thread it may keep it on

Date: 2026-09-19

## Status

Accepted. Closes **two** entries in `book/src/TODO.md` under *Rendering without a
window* — "No reuse and no cache" and "Nothing renders off the UI thread" —
because they turned out to be one decision. The argument that they are one is the
first section below; it was not obvious in advance and the brief for this work
asked for the two to be separated if they stayed separate.

Completes [ADR-0284](0284-a-picture-with-no-window-under-it.md), whose
consequences recorded both halves as open: "A font book is opened and closed per
render unless one is given" and "No `Offscreen` reuse across calls … It also means
the builder is not a cache".

## Context

The two entries, in their own words.

**Reuse.** *"Each render builds a fresh element tree and unmounts it, so rendering
the same document twice does the work twice. A font book can be handed in and kept;
nothing else can."*

**Threads.** *"A render touches no window and no backend, so a server thread is
probably fine — 'probably' is why it is written here rather than in the javadoc.
What would have to be checked first is the shaping cache and Blend2D's own worker
pool."*

### Why they are one decision

The reuse entry is right that a font book can be handed in and nothing else can,
and it is wrong about which part of that is expensive. Per render, `Offscreen`
currently throws away:

- a `Fonts` — memory-mapped faces, 681 µs to parse Inter (ADR-0044);
- a `StyleResolver` — the parsed sheets indexed by selector, which ADR-0070
  measured as the largest term in a frame and ADR-0142 as the one that had stopped
  being cached;
- a `ParagraphCache` — 56 µs per distinct paragraph, twelve times a wrap and two
  hundred times the Yoga crossing (ADR-0037).

The last two belong to a `WidgetRenderer` and cannot be handed in, because the
constructor does not take one. So the reusable unit is **a renderer over a book**.

And that is exactly the object that cannot be shared between threads. Auditing the
render path for the second entry produced a list of fifteen classes that hold a
`Thread owner` field and refuse a foreign caller — `ParagraphCache`, `RenderTree`,
`YogaConfig`, `YogaNode`, `BlendContext`, `ShapedFont`, `BlendFont`, `BlendPath`,
`BlendImage` and the rest — and the two things a renderer keeps are on it. So:

> **What a render may reuse is exactly what it may not share, and the unit of both
> is one object.**

One object, one decision, one ADR. Writing them separately would have produced two
records that each had to describe the other's object to say anything true.

### What the audit actually found

The entry named two suspects. Both were cleaner than expected, and the real
blocker was neither.

**The shaping cache: already correct, and already fail-fast.** `Offscreen`'s own
javadoc claimed "the shaping cache is per renderer", and it is — a `ParagraphCache`
created in the `WidgetRenderer` constructor, on the calling thread. It also carries
`private final Thread owner = Thread.currentThread()` and checks it on every
`paragraph`, `frame`, `size` and `clear`. Not a hazard.

**Blend2D's worker pool: process-wide, and the fallback was already written.** The
pool lives inside `libgoldberry` and has no Java handle anywhere in this
repository. Per-context state is per-instance with a confined `Arena` and an owner
check (`BlendContext`), and the number of workers a frame asks for is a pure
function of surface size and a system property read once at class init
(`PaintThreads`). N concurrent renders therefore contend for one pool — and
`BlendContext.begin` already falls back to a synchronous context when it cannot get
workers, logs it, and reports `threadCount() == 0`. ADR-0042 anticipated "a thread
pool at its limit, or a process that has run out"; it did not anticipate *us* being
the other tenant, and the handling is the same either way. Slower, non-deterministic
in how many workers each render gets, and **identical in output**, which is the
thing a preview cares about.

Also checked and clean: Yoga holds no global config, node pool or measure registry;
every lazily-initialized native global uses the class-holder idiom, so first-call
init is serialized by the JVM rather than raced; the native library extracts to a
uniquely-named temp directory once, from inside class init; there is exactly one
non-final static field in the whole toolkit (`GoldberryRuntime.instance`, guarded
by `static synchronized`) and the offscreen path never reaches it, so no window and
no SDL are involved. The static one-shot warning sets on the path — `OverflowLog`,
`ComputedStyle`'s dropped declarations, `KeyframeTrack`'s unknown names — are all
`ConcurrentHashMap`-backed and memory-safe.

**The blocker was `Fonts`**, and the shape of the problem is the point. Its class
comment has said "confined to the thread that created it" since ADR-0044 and
**nothing checked it** — unlike every other confined object on the path. Its
`faces` and `fonts` caches are plain `LinkedHashMap`s written through a
get-then-put in `fontOf`, so two threads in it are an unsynchronized map mutation:
a lost entry, a native face opened twice, or a corrupted table. The eventual
symptom would not have named the book — it would have been a `requireOwner` from
inside HarfBuzz's or Blend2D's wrapper, on some later frame, naming a font.

And the configuration that reaches it is the one this toolkit's own javadoc
recommended: *"A server rendering many previews should hand over one `Fonts` and
keep it."* Correct for serial renders on one thread. Followed from a worker pool,
which is what "a server rendering many previews" means, it was the single worst
thing a caller could do.

## Decision

Three parts, and the second is the one that matters most.

### 1. `Studio` — the reusable unit, named and closeable

```java
try (var studio = Studio.of(Controls.stylesheets(Theme.NORD_DARK))) {
    for (var document : documents) {
        write(studio.picture(1200, 630).render(new Card(document)).encodePng());
    }
}
```

A studio owns one `Fonts` and one `WidgetRenderer` — and therefore one cascade
index and one shaping cache — and hands out `Offscreen` builders wired to them.
`Studio.of` opens its own book; `Studio.over` borrows the caller's and does not
close it.

**It is not a result cache, and this is deliberate.** Two renders of the same
document still build, style, lay out and rasterize from scratch. A preview is a
picture of program state, and the only honest cache key for one is the caller's —
`Offscreen` guessing at it would be a correctness bug with a performance
justification. What is kept is the machinery, which is where the repeated cost
actually was.

The evidence is a count, not a stopwatch: `StudioTest.keepsTheShapingCache` renders
a document, reads `ParagraphCache.misses()`, renders it again, and asserts the
number did not move. ADR-0299 put that counter there for exactly this kind of
claim, and a timing assertion about the same thing would be a flaky test.

### 2. The promise, written without "probably", and the enforcement under it

**`Offscreen.render` may be called on any thread, and several may run at once.**
That is now in the javadoc on `Offscreen`, and `OffscreenThreadTest` is what makes
it a promise rather than a hope:
`rendersConcurrently` puts eight threads inside the render at the same moment
behind a latch and asserts every one of the eight produced a picture **pixel for
pixel identical** to a render done alone.

Pixels rather than "it did not throw", because the failure mode of sharing
something that should not be shared is a wrong picture long before it is a crash: a
paragraph shaped against a half-written cache is a paragraph, and it draws.

**The limit is sharing, not the thread**, and the three shareable-looking objects
now say so by throwing:

- `Fonts` gained the `owner` check every other confined object on the path already
  had, on both use and `close`. Checked *before* the closed flag goes up, so a
  refused close leaves the owner's book intact rather than half-closed with its maps
  cleared.
- `Studio` carries the same check, with a message that distinguishes the two
  things a caller might have got wrong: *"rendering off the UI thread is supported,
  sharing one studio between threads is not"*.
- `Font`, handed in through `Offscreen.font(Font)`, was already confined by the
  HarfBuzz and Blend2D objects under it.

**A pool of four workers wants four studios.** That is the whole rule, it costs
four font books, and it buys four renders that cannot interfere.

### 3. The javadoc advice that was wrong is gone

`Offscreen`'s "What it costs" section no longer tells a server to hand over one
`Fonts` and keep it. It names `Studio`, states the thread rule, and records that
Blend2D's pool is shared so concurrent renders may paint synchronously — slower,
same pixels.

## Consequences

- **Reuse is invisible in the output, and that is asserted.**
  `StudioTest.matchesAPlainRender` compares every pixel of a render from a *warm*
  studio against a plain `Offscreen` render of the same scene. If keeping the
  cascade index or the shaping cache ever changed one pixel, every golden in this
  repository would move the day the harness started using a studio.
- **A studio's renderer is shared sequentially, and its per-frame state is reset by
  every render** — the frame's time, whether anything is animating, which element
  is being styled. `keepsRendersIndependent` renders two different documents and
  then the first one again, because "the second document left nothing behind" is the
  claim a shared renderer has to earn.
- **Setting stylesheets or fonts on a studio's builder gives the kept renderer up,
  rather than ignoring them.** A renderer *is* its cascade, so one built over other
  sheets would resolve other styles. Silently preferring the studio's would be the
  worst of the three options and the easiest to write.
- **`Fonts` now throws where it used to corrupt.** This is a behaviour change to a
  published class, and it is a refusal in a case that was already broken — every
  path that worked before still works, because every object below `Fonts` would
  have refused the same caller a moment later. The whole suite is the evidence: it
  passes unchanged.
- **Blend2D's pool is shared and this is recorded rather than fixed.** N concurrent
  renders × up to four workers each contend, and the losers rasterize
  synchronously. Fixing it properly means bounding the pool or bounding the
  concurrency, and neither is `Offscreen`'s to decide — a server that cares sizes
  its own worker pool, which is the same knob.
- **Concurrent renders suppress each other's one-shot diagnostics.**
  `OverflowLog.REPORTED` and friends are process-wide by design, so "the first
  overrun of each shape is said out loud once" (ADR-0375) is once per *process* and
  not once per render. Memory-safe, and it means any future test asserting "warned
  exactly once" becomes order-dependent the day it is run beside a concurrent
  render. Written down here because that test does not exist yet and the next person
  to write one deserves to know.
- **JUnit still runs every suite serially.** There is no parallel execution
  configured anywhere in this build, so `OffscreenThreadTest` is the only place in
  the repository where two renders are ever in flight at once. That is a narrow net
  under a broad promise, and it is the honest state of it.
- **`Studio` does not serve a `Filmstrip`'s renderer**
  ([ADR-0424](0424-a-tree-mounted-once-and-photographed-repeatedly.md)). A renderer
  holds one clock; a strip drives its own for its lifetime. A strip from a studio
  shares the book and builds its own cascade index, which is what it costs and is
  cheaper than the alternative of a strip that moves the clock under every still
  picture beside it.

## Alternatives considered

- **Two ADRs, one per entry.** The brief allowed it and the audit removed the
  reason: the object that makes reuse possible is the object that must not be
  shared. Two records would each have had to describe the other's decision.
- **A result cache keyed on the widget.** A `Widget` is a description and often a
  record, so it has an `equals`; that is precisely what makes this tempting and
  wrong. A `Card(document)` whose document is mutable, or which closes over a
  model, is equal to a stale one. A cache that is right only for the callers who
  did not need it is a trap with a hit-rate graph.
- **Make `Offscreen` itself hold the renderer across `render` calls.** No new type,
  and it needs a `close()` — a kept book has to be released — so `Offscreen` would
  become `AutoCloseable` and every existing one-shot caller would start getting a
  resource-leak warning for doing the simple thing correctly. Reuse needs a
  lifetime; a builder should not have one.
- **A per-thread static cache of renderers.** ADR-0044 already answered this for
  fonts, and the answer has not changed: a per-thread cache of native memory has no
  hook that would ever free it. A pool whose threads outlive the work would hold
  every face it ever opened.
- **Make `Fonts` thread-safe instead of thread-checked.** Synchronizing two
  `LinkedHashMap`s is the easy half. The hard half is that what it vends is
  confined: a `Font` is a `ShapedFont` and a `BlendFont`, both of which refuse a
  foreign thread from inside the native wrapper. A book that handed out a font
  safely to a thread that could not use it would have moved the exception one frame
  later and made it harder to read.
- **A `@ThreadConfined` annotation instead of a runtime check.** Documentation with
  better spelling. `Fonts` has carried the sentence since ADR-0044 and the
  recommendation in `Offscreen` contradicted it anyway.
- **Say nothing in the javadoc and leave the entry open.** The audit is done and
  the answer is favourable; leaving "probably" in a TODO after checking would be
  keeping a question we know the answer to.
