# ADR-0069: The render tree is retained, and reconciled against a box tree

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/ARCHITECTURE.md` §5, §11; supersedes the "for now" in
  [ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md); completes
  [ADR-0004](0004-three-tree-retained-declarative-model.md); acts on the
  measurements in [ADR-0037](0037-what-the-text-path-costs.md); follows the
  method of [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md)

## Context

[ADR-0004](0004-three-tree-retained-declarative-model.md) specified three trees.
Two were built. The third — "one render object per visual node, owns a `YGNode`,
a `ComputedStyle`, and the paint logic" — was deliberately not, and
[ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md) said why, and what it
would take to change:

> The box tree is rebuilt every frame, and that is the cost to reclaim. No render
> object survives a frame, so nothing knows what changed. **That is the argument
> for building them, and it should be made with a measurement.**

So the measurement came first. `FrameBenchmark` renders a tree the shape of the
showcase — a bar, a sidebar, wrapped prose, seven measured leaves — through the
whole pipeline, at 960×640 on linux-x64.

| | median |
|---|---|
| **frame CPU before rasterization** | **354 µs** |
| render (cascade + boxes) | 199 µs |
| layout + walk (Yoga tree built, laid out, freed) | 190 µs |

**"Before rasterization" is not a hedge, it is what changed.** Blend2D's own
raster cost is identical either way, and at 960×640 on one thread it is about
310 µs — larger than everything above it. Folding it into the headline would
dilute the thing this record is about by a constant nobody touched. The
whole-frame figures with it included are in the consequences.

Two things were being thrown away and rebuilt every frame, and both had already
been measured in isolation by [ADR-0037](0037-what-the-text-path-costs.md)
without anything acting on the numbers:

- **Shaping.** `Widgets.Text.render` called `Paragraph.of` per frame — 56 µs
  against 0.05 µs for a cache hit. `ParagraphCache` had been built for exactly
  this and `status.md` recorded it as having **no consumer**.
- **The measure callbacks.** A `MeasureCallback` is a confined `Arena` and a
  `MethodHandle` bound into native code: 11 µs to create, against 0.3 µs to call
  through. ADR-0037 called it "the largest cost of text in a layout pass".

## Decision

### 1. The render tree is retained, and it is reconciled against a `Box` tree

`RenderObject` owns a `YGNode` and holds the `Box` last applied to it.
`RenderTree` owns the root, the `YogaConfig`, and the walk. An application holds
one for the life of a window.

The input to reconciliation is **still the per-frame `Box` tree that widgets
describe**. That is the part worth defending, because the obvious alternative —
have `Paints` mutate a render object directly — was rejected:

- A `Box` is immutable, and an immutable tree is the ideal thing to diff. There
  is no question of when it was last read or whether someone else is holding it.
- It keeps a widget's job "describe yourself", which is the whole of
  [ADR-0004](0004-three-tree-retained-declarative-model.md)'s programming model.
  Not one widget changed for this.
- The box tree is already what every golden image and every rendering test runs
  through, so the retained path can be asserted to produce **identical layout**
  to the throwaway one, which is the first test in `RenderTreeTest`.

So ADR-0053's box tree was not a detour. It turned out to be the diff input.

### 2. Every Yoga setter is guarded, and the guards are the point

**Yoga dirties a node when a style is *set on it*, not when the value differs.**
A retained tree that re-applied every style every frame would dirty every node
every frame, Yoga's layout cache would never hit once, and it would cost exactly
what throwing the tree away costs — plus the memory management of keeping it.

So `RenderObject.apply` compares each property against the box already applied
and calls the setter only on a difference. That single decision is what turns
"the tree is kept" into "the layout is skipped":

| layout + walk | median |
|---|---|
| throwaway tree | **190 µs** |
| retained, nothing changed | **9.1 µs** |
| retained, **a fresh box tree every frame** | **7.2 µs** |

The third row is the one that matters and it is the one that could have gone
wrong. A real application rebuilds its boxes constantly; if the guards compared
by identity rather than by value they would never fire, and this row would look
like the first. It looks like the second, which says the values compare equal and
Yoga does nothing. **20×**, and the CPU a frame spends before rasterizing goes
from 354 µs to 148 µs.

### 3. `ParagraphCache` gets its consumer, and identity is load-bearing

`Paints.Context` grew a `paragraph(style, text)` method and the two widgets that
shaped their own text now go through it. ADR-0053 said that interface existed to
be widened; this is the widening.

It saves the 56 µs, and it does something less obvious that the retained tree
depends on: **the paragraph that comes back is the same instance as last
frame's**. `RenderObject` compares by identity to decide whether the measure
callback it has is still the right one. An equal-but-distinct paragraph would
rebind an upcall stub per text node per frame, which is the other 11 µs — so the
cache is not an optimisation layered on top of retention, it is a precondition
for it.

### 4. One layout pass, two readers

`Window` code was calling `BoxPainter.paint(frame, boxes)` and then
`HitTest.capture(frame, boxes)` — **two complete Yoga trees built and laid out
per frame**, one to paint and one to find out where it had painted. `HitTest`
gained a `capture(RenderTree)` overload that reads the pass `update` already ran.
The showcase now does one.

This was not in any of the measurements above, because the benchmark measured a
single path at a time. It is a straight halving of the layout cost of a real
window, and nobody had noticed it.

## The bug retention introduced, which is the honest part of this record

`RenderTreeTest` caught a wrong layout on the first run: a paragraph replaced
with much longer text reported a **one-line height**, laid out over six lines of
prose, with no error anywhere.

**Yoga does not dirty a node when its measure function is replaced.** It dirties
on a style change, and text is not a style — from Yoga's point of view nothing
about the node changed, so it reused the height cached for the *previous*
paragraph. The fix is one call to `YGNodeMarkDirty`, and `YogaNode.markDirty`'s
own documentation had described this exact situation since ADR-0029: "Yoga marks
a node dirty by itself whenever a style changes, but it cannot know that the
*text* changed."

It could not happen while the tree was thrown away, because a node built this
frame has no cached measurement to reuse. It is the first bug in this repository
that exists **only because state is now kept**, and it will not be the last —
which is the standing cost of this record and is why the equivalence test
("a retained tree lays out identically to a thrown-away one", asserted again on
the tenth frame) is the first test in the file rather than an afterthought.

## Consequences

- **The CPU a frame spends before rasterizing falls from 354 µs to 148 µs**, and
  the layout half of it from 190 µs to 7 µs. With rasterization included and
  Blend2D pinned to one thread, a whole frame goes from about 490 µs to 320 µs —
  the difference is the same, and painting is now most of what is left. Against
  the 16.67 ms budget none of it was a problem at 960×640; this buys headroom for
  4K and for trees far larger than seven text nodes, which is where the throwaway
  path was going to become one.
- **A caution about the benchmark itself.** Blend2D's threaded context *queues*
  its work and only blocks when the frame ends, so a timing loop around `paint`
  on one measures submitting a frame rather than drawing it — 6.8 µs for a
  960×640 window, which is not a number to believe. The whole-frame rows are
  taken with the worker count pinned to zero for that reason. This is
  [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md)'s lesson arriving for
  the second time, from a different direction.
- **`BoxPainter.paint` still works and is still what the goldens use.** It builds
  a throwaway `RenderTree`, uses it once and closes it — one implementation, two
  lifetimes, rather than two implementations. Having two was what ADR-0053
  rejected, and it would have left the golden images testing a path applications
  do not take.
- **Render-object identity now exists**, which is what
  [ADR-0068](0068-the-transform-stack-is-java-side.md) said layer promotion was
  waiting for. A promoted node needs somewhere to keep its cached raster between
  frames and there was nowhere; now there is. Damage tracking wants the same
  thing plus `YGNodeGetHasNewLayout`, which the wrapper already exposes and
  nothing yet reads.
- **A display-scale change rebuilds the whole tree.** Yoga rounds every computed
  edge onto the config's pixel grid, a config change does not dirty anything, and
  there is no call that means "re-round". Dragging a window to a monitor with a
  different scale therefore costs one throwaway frame, which is the right price.
- **A `RenderTree` must be closed**, and before the fonts it draws with: a render
  object holds a measure callback that closes over a paragraph, and a paragraph
  over a font. Closing them the other way round leaves a native stub pointing at
  a freed face.
- **Reconciliation matches children by position**, checked against owner identity
  and measured-leaf-ness. That is enough because the **element tree has already
  done the keyed diff** ([ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)):
  by the time a box tree exists the order is stable. A mismatch costs a rebuilt
  subtree, never a wrong result.

### What this does not do

- **Nothing is cached at the paint level.** The retained tree skips *layout*; it
  still walks every node and issues every Blend2D call every frame. Damage
  tracking and layer promotion are what would change that, and they now have
  the identity they were missing.
- ~~**The cascade still runs per node per frame.**~~ It did, at 135 µs of the
  148 µs left — the largest remaining term by a wide margin, and the next thing
  to measure rather than layout. Taken immediately afterwards in
  [ADR-0070](0070-the-cascade-resolves-invalidated-nodes.md).
- **`ParagraphCache` is per renderer and bounded at 256 entries.** A window
  showing more distinct strings than that thrashes it, and thrashing costs both
  the shaping *and* a rebound measure callback. Nothing measures the hit rate in
  an application yet.
