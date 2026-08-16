# ADR-0037: What the text path costs

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §6, [ADR-0004](0004-three-tree-retained-declarative-model.md), [ADR-0017](0017-proving-the-struct-by-value-upcall.md), [ADR-0028](0028-the-start-up-timeline.md), [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md), [ADR-0036](0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)

## Context

M1's exit criteria name two things that are not features: *paragraph cache* and
*upcall benchmarks green*. Both are claims about cost, and neither could be
settled by argument. `docs/ARCHITECTURE.md` §6 calls the paragraph cache "the hot
path"; [ADR-0017](0017-proving-the-struct-by-value-upcall.md) proved the `YGSize`
upcall *works* and never said what it costs; and
[ADR-0031](0031-blend2d-and-the-borrowed-buffer.md) established the habit that
settles this sort of question — get the number before optimising anything,
because the obvious candidate is usually not where the time goes.

Building a cache first would have been building it blind.

## Decision

**Benchmarks are a tagged JUnit task that prints and does not assert.**
`@Tag("benchmark")`, excluded from `check`, run by `./gradlew benchmark`. Nothing
asserts a timing: a threshold that passes on a workstation and fails on a shared
CI runner teaches nobody anything, and the number is the deliverable. This is
what [ADR-0028](0028-the-start-up-timeline.md) and
[ADR-0031](0031-blend2d-and-the-borrowed-buffer.md) already did informally; it is
now a task.

**The paragraph cache holds shaped paragraphs, keyed by `(font, text)`,
least-recently-used.** It caches *shaping*, because shaping is the only part of
the text path large enough to be worth caching. §6 specifies (text, resolved text
style, width bucket): today a `Font` is the resolved style, and the width bucket
belongs to `Paragraph`'s own memo because shaping does not depend on width.

The font is compared by **identity**. Two `Font`s over the same face at the same
size agree about everything measurable today and are separate native objects;
sharing on the strength of that would stop being true the first time either gets
a font feature or a variation axis set on it.

## The numbers

Measured on linux-x64 (a VirtualBox VM), Inter at 14 points, a paragraph of about
seventy words wrapping to five lines. Medians over 20 000 iterations after 2 000
warm-up.

| | median | |
|---|---|---|
| wrapping, memo hit | **0.02 µs** | free |
| `ParagraphCache` hit | **0.05 µs** | a map lookup |
| the `YGSize` upcall crossing | **~0.3 µs** | Java called from C, struct by value |
| wrapping, memo miss | **4.8 µs** | breaking five lines |
| **creating one upcall stub** | **11.0 µs** | `MeasureCallback.of` + close |
| **shaping** | **56 µs** | `Font.shape`, what the cache avoids |
| `Paragraph.of` | 61 µs | shaping, plus building the prefix sums |
| loading a `Font` | 650 µs | two face parses (ADR-0034) |

And a whole layout pass over the same tree:

| | median |
|---|---|
| layout pass, no text | 12.5 µs |
| layout pass, one paragraph | 40.4 µs |

## What the numbers say

**The upcall is cheap, and that settles a question ADR-0017 left open.** A crossing
costs about a third of a microsecond — a sixteenth of a wrap, a two-hundredth of a
shaping. The fiddliest thing the toolkit asks of FFM is also one of the cheapest
things in the text path. Yoga may call it as often as it likes.

**Wrapping is free when memoised and cheap when not.** 0.02 µs against 4.8 µs, and
the miss only happens at a width not seen since the last one. The one-entry memo
in [ADR-0036](0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md) is
enough; a bigger cache there would be optimising something already at 20 ns.

**Shaping is the only thing worth caching, and the cache is 1200× cheaper than
it.** 56 µs against 0.05 µs. This is what justifies `ParagraphCache` existing at
all — and equally, what says a cache of *layouts* would be pointless.

**The biggest cost of text in a layout pass is not text.** Adding one paragraph
takes a pass from 12.5 µs to 40.4 µs, and of those 28 µs, **11 µs is creating an
upcall stub** — an `Arena` and a `MethodHandle` bound into native code — with the
rest being two or three memo-miss wraps. `BoxPainter` builds a fresh Yoga tree per
frame and therefore a fresh stub per text box per frame, which it says of itself
is "the wrong shape for a real toolkit and the right shape for a join that exists
to be exercised". Now there is a number on it. The retained render tree
([ADR-0004](0004-three-tree-retained-declarative-model.md)) removes it by keeping
the node, and this is the first measurement that makes that a performance argument
rather than only a design one.

**Painting now dominates a frame, which reverses ADR-0031.** Over 119 consecutive
frames at 960×640 with the wrapped paragraph on screen:

| | median | p95 | max |
|---|---|---|---|
| acquiring the buffer | 0.18 ms | 0.40 ms | 7.76 ms |
| **painting** | **5.10 ms** | 10.65 ms | 19.18 ms |
| presenting | 1.92 ms | 4.10 ms | 6.96 ms |
| total | 7.86 ms | 14.18 ms | 23.22 ms |

Three frames of 119 exceeded the 16.67 ms a 60 fps budget allows.

[ADR-0031](0031-blend2d-and-the-borrowed-buffer.md) measured painting at ~1.3 ms
and present at ~10 ms and concluded that present dominated by an order of
magnitude. Text moved paint to 5.1 ms — glyph rasterization is real work — and
paint is now 2.7× present.

**But only half of that reversal is text.** ADR-0031 measured under **Wayland**;
these frames are **X11**, because the showcase's Wayland run had just taken the
compositor down with it and X11 was the safe way to measure. Most of Wayland's
~10 ms present was waiting on the compositor, and X11 does not wait the same way.
So: the paint increase from 1.3 ms to 5.1 ms is attributable to text; the present
decrease from ~10 ms to 1.9 ms is attributable to the driver, and nothing here
should be read as having made present faster. **The like-for-like Wayland
measurement is still owed.**

## Alternatives considered

**JMH.** The right tool, and it means a new plugin, a new subproject, a forked-JVM
harness and a build dependency, to measure a handful of operations whose costs
differ by three orders of magnitude. `System.nanoTime` around a warmed loop
distinguishes 0.02 µs from 56 µs perfectly well. JMH earns its keep when the
answer is within noise of the question, and none of these are.

**Assert the timings in CI.** Tempting — "upcall benchmarks green" sounds like a
passing test. It would be a test that fails when the runner is busy, gets a
tolerance widened until it cannot fail, and then means nothing.

**Cache layouts as well as shapings.** Wrapping is 20 ns on a memo hit. There is
nothing there to save.

**Cache by font equality rather than identity.** Would let two equivalent `Font`s
share, and would silently share between two fonts that stopped being equivalent.

## Consequences

**M1's benchmark and cache criteria are met.** `./gradlew benchmark` produces the
table above, and `ParagraphCache` is built, tested and justified by a measurement
rather than by §6 saying so.

**The cache has no consumer yet, and that is stated rather than hidden.** Nothing
rebuilds a widget tree, so nothing currently re-shapes the same text. It exists
because the number says it will be needed the moment something does, and because
building it after the render tree would mean discovering the 56 µs then.

**Two follow-ups now have numbers attached.** The retained node saves 11 µs per
text box per frame; Blend2D's `thread_count` is worth revisiting now that paint is
the largest term in a frame rather than the smallest — ADR-0031 explicitly parked
it as "only matters if paint ever becomes the bottleneck", and on these numbers it
has.

**M1's remaining criterion is the 60 fps claim itself.** These frames come from
one machine, and it is a VirtualBox VM with software rasterization on X11. The
milestone asks for Linux, macOS and Windows. What can be said
from here is that a 960×640 frame with a wrapped paragraph fits in the budget with
a factor of two in hand on the median and not on the p95, and that the next thing
to measure is Wayland on real hardware.
