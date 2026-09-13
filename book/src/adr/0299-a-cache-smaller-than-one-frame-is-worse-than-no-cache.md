# 299. A cache smaller than one frame is worse than no cache

Date: 2026-09-13

## Status

Accepted. Makes [ParagraphCache]'s capacity a starting point rather than a ceiling,
and fixes the benchmark that should have caught the defect and was measuring a
screen that no longer exists.

## Context

`markdown-view` and `html-view` build **one `text` widget per word**
([ADR-0295](0295-a-document-is-a-value-and-a-paragraph-is-a-row-of-words.md),
[ADR-0298](0298-html-is-a-document-and-not-an-engine.md)), which both records say
out loud costs "roughly one widget per word" and treat as a widget-count problem
to revisit if anything needs it.

It was not a widget-count problem. It was a **cache** problem, and it was 11× on
the frame path:

| screen | elements | style pass, settled | paragraphs shaped, settled |
|---|---|---|---|
| Basic (a wall of cards) | 219 | 1.0 ms | 0 |
| Markdown | 866 | **9.5 ms** | **287** |
| HTML | 860 | **6.2 ms** | **313** |

Two hundred and eighty-seven paragraphs shaped on a frame where **nothing had
changed** — no build, no style resolution, no invalidation, on a tree the previous
frame had already drawn. Each one is 56 µs of HarfBuzz
([ADR-0037](0037-the-text-cache-and-what-it-is-for.md)) to arrive at glyphs the
cache had held moments earlier.

The cause is arithmetic rather than a subtle interaction:

> `ParagraphCache.DEFAULT_CAPACITY` is **256** — "a screenful of distinct strings,
> roughly". A page of six hundred words asks for six hundred distinct paragraphs
> per frame. With least-recently-used eviction, each lookup evicts the entry the
> walk is about to reach, so the hit rate on the excess is not *lower*, it is
> **zero** — and the cache pays for the eviction on top of the shaping.

The comment was true when it was written: the workload it was sized for is a
window of controls, where a screenful of distinct strings is forty. A document is
a different shape of tree, and it arrived two hundred records later.

**Nothing caught it**, and that is the second half of this record.
`FrameBudgetTest` exists precisely to catch "the showcase spent a month painting
at 10–15 ms with nothing moving" (ADR-0142) — and every measurement in it named
the screen `"controls"`, which stopped being a screen when the gallery was
reorganised into questions rather than widget families (ADR-0222). `pickScreen`
set a property no tab matched, so the budgets were measured against **a window
with no screen selected**. A benchmark measuring the wrong tree is the exact
failure that file's own comment describes, about `FrameBenchmark` measuring a
synthetic 15-node tree.

## Decision

### The cache sizes itself to the frame it is drawing

`DEFAULT_CAPACITY` stays 256 as a *starting point*, and the cache grows to fit the
working set it is actually asked for, bounded by `MAX_CAPACITY = 8192`:

- **During a frame**, in the eviction hook. If this frame has already asked for as
  many paragraphs as the cache holds, then the least-recently-used entry is by
  definition one *this frame* touched — so the cache doubles rather than discard
  work that is about to be asked for again. This is what makes the **first** frame
  of a long document the cheap one rather than the one after it.
- **At the end of a frame**, in `frame()`, which the renderer calls once per
  render: capacity rises to a quarter more than the frame asked for, so a document
  that gains a word does not re-tune.

It **does not shrink**. A window that showed a long document once can show it
again, and giving the memory back would cost the next visit the shaping it just
paid for; the whole cache is thrown away with the renderer anyway.

The ceiling is a real bound rather than a gesture: 8192 entries of six `int[]`s
the length of a word is under two megabytes. A frame whose working set is larger
thrashes exactly as every frame used to — which is the honest failure mode, and
the alternative is a cache that grows until something else runs out.

### The measured result

Same machine, same trees, after the change:

| screen | style pass, settled | paragraphs shaped, settled |
|---|---|---|
| Markdown | 9.5 → **0.8 ms** | 287 → **0** |
| HTML | 6.2 → **0.6 ms** | 313 → **0** |

A scrolling frame — a wheel event, a flush and a layout — is 1.1–2.3 ms with
**nothing rebuilt and nothing re-resolved**, so what remains of a scroll is layout
and raster.

**Culling was considered and measured away.** The obvious next move is to skip
boxes outside the viewport's clip, and the numbers say it would buy nothing here:
the raster of the 860-box document screen (8.0 ms at 1280×900, one thread, no
damage) is the same as the 219-box wall's (8.3 ms), because rasterization is
pixel-bound and Blend2D already rejects a clipped box cheaply.

### The guard is a count, not a stopwatch

`FrameBudgetTest` now measures screens that exist — and `treeFor` **refuses a name
the gallery does not have**, so this cannot silently recur — and it gained the
assertion that would have caught this in the first place:

> Render an unchanged tree twice; the second render shapes **no** paragraphs.

A count rather than a duration, so it holds on any machine and says exactly what
is wrong when it fails. `WidgetRenderer.paragraphs()` is public for it, which is
also what a diagnostic wants: "this window's text working set is 692, the cache
holds 865".

## Consequences

**Documents are usable.** This is the whole point: an eleven-times-cheaper style
pass on the screens a reader spends time in, with no change to what is drawn.

**Every application gets it**, not only the content views. Any tree with more
distinct strings than 256 — a long table, a big tree view, a list of file names —
was paying the same toll silently.

**Memory grows with the document**, up to the ceiling, and a renderer that has
shown a big page keeps that capacity for its lifetime. `capacity()` and
`highWaterMark()` say so when somebody asks.

**The identity that the retained render tree relies on is restored.** ADR-0069's
measure callbacks are kept when a paragraph is the *same instance* frame to frame;
under thrashing they were new objects every frame, so the layout pass was paying
for rebinding as well.

**`FrameBudgetTest` was measuring nothing for some months**, and the fix is a
guard rather than a correction: naming a screen that is not in the gallery now
throws. That is the general lesson of this record — the benchmark and the thing it
benchmarks drifted apart silently, which is the same failure as a cache and its
workload drifting apart silently.

## Alternatives considered

**Raise `DEFAULT_CAPACITY` to a bigger constant.** Rejected: it trades one wrong
number for another. 2048 is wasteful for a window of controls and still too small
for a long article, and nothing in the constant says which workload it was chosen
for.

**Let the application configure it.** Rejected as the *only* answer: the renderer
is built by the launcher, so an ordinary application has no reach to the knob —
and a toolkit that needs tuning to draw a document at a sensible speed has the
default wrong.

**Make the views build fewer paragraphs** — one `text` per unmarked run rather than
per word. Rejected here, and it is not free: a coalesced run wraps *inside* its own
box, so a paragraph holding one would have a different leading from the one below
it and a mark in the middle of a line would break the wrapping. It is also
unnecessary now that the cache fits: the shaping is paid once.

**Cull boxes outside the clip.** Measured and rejected — see above. Worth
revisiting when something is bound by draw calls rather than pixels.
