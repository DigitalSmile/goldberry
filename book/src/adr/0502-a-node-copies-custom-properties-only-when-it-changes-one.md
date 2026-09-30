# 502. A node copies custom properties only when it changes one

Date: 2026-09-30

## Status

Accepted. Answers the TODO.md entry "`customPropertiesFor` still walks to the
root", which cited [ADR-0070](0070-the-cascade-resolves-invalidated-nodes.md).
Keeps [ADR-0152](0152-the-cascade-looks-at-rules-that-could-match.md)'s cache
and changes only what a node does on a miss. Measured first, per
[ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md).

## Context

`StyleResolver.customPropertiesFor` gets a node's parent's custom properties by
recursing to the root, and then adds the node's own. The TODO entry read that
as a full cascade at every ancestor, O(depth × rules) per node, paid on a first
frame and on every invalidated subtree. It proposed computing each node from
its parent's resolved map instead.

That was already the algorithm. ADR-0152 caches each node's map against its
parent's map by identity, and the renderer resolves top down, so by the time a
node asks, every ancestor's entry is valid. The walk costs one cache probe per
ancestor, and none of them cascades. What the measurement found instead was a
cost with nothing to do with depth.

`DeepTreeStyleBenchmark` (`:widgets`, tagged `benchmark`) builds a chain of
`panel > column > (text, button, next level)` to element depth 51, 101 and 201,
styled by `Controls.stylesheets(Theme.NORD_DARK)` plus one hover rule on the
middle panel. Every node is styled. The Nord theme puts 146 `--gb-*` properties
on `:root`, and `controls.css` declares 35 more, 33 of them on `:root` too.
Temporary `System.nanoTime` probes inside `resolve` split one first-frame pass
(every styled node resolved against a resolver it has no cache entry for):

| Depth (nodes) | Pass | Cascade | Custom properties | of which: copy and compare | Substitution |
| --- | --- | --- | --- | --- | --- |
| 51 (103) | 1288 µs | 348 (27%) | 815 (63%) | 799 | 109 (8%) |
| 101 (203) | 2822 µs | 990 (35%) | 1567 (56%) | 1510 | 241 (9%) |
| 201 (403) | 6867 µs | 3063 (45%) | 3271 (48%) | 3064 | 502 (7%) |

The walk triggered **no** ancestor cascades at any depth. With every level a
cache hit, the walk alone costs 7, 34 and 97 µs a pass, 0.5–1.5% of the pass. It
does grow with depth squared, but it is small. The term that mattered was the
line after the cache check: `new LinkedHashMap<>(inherited)`, a put for each of
the node's own custom properties, and `own.equals(inherited)`. That is roughly
7.5 µs a node, the same at every depth. It copies and compares 180 entries to
learn, at almost every node, that the node declares none.

## Decision

**A node builds a map only when it changes a property.** Its own cascade's
`--*` winners are compared with the inherited values first. When none differs,
it hands down the parent's map itself, which is the instance ADR-0152's cache
already keyed on. When one does, it copies the parent's map, puts the changes,
and freezes the result with `Map.copyOf`. The result is the same as before in
every case. Before, the node kept `inherited` exactly when the copy equalled
it, and that is exactly when every own value equals the inherited one. Token
equality includes source position, so "equal" in practice means the same rule
matched both parent and child, `group { --b: … }` for example.

The walk and the cache are unchanged. So are `var()` substitution at use time,
the raw storage of a custom property's tokens, and the invalidation paths in
`Element`. `CustomPropertiesCacheTest` (`:core`, `widget` package) checks the
cached path through real `Element`s against an uncached stand-in that
re-cascades every ancestor on every ask. The cases are an override at depth, a
`var()` in an inherited property that resolves differently below each of two
overrides, a middle ancestor's hover that changes its own map, one that reaches
a descendant's custom property only through `.mid:hover .deep`, and a deep node
asked before anything above it is cached. The tests pass on the old algorithm
as well, which is the point of them.

Measured as a paired A/B in one JVM, with the old merge behind a temporary
switch, alternating ten passes of each for forty rounds after five warm-up
rounds. The figures are medians over five runs at depths 101 and 201 and three
runs at depth 51:

| Depth | First-frame resolve, before → after | First `render()` | `render()` after the middle panel's hover |
| --- | --- | --- | --- |
| 51 | 1337 → 505 µs (−62%) | 1765 → 836 µs (−53%) | 1000 → 497 µs (−50%) |
| 101 | 2651 → 1168 µs (−56%) | 3383 → 1805 µs (−47%) | 1949 → 1130 µs (−42%) |
| 201 | 7796 → 4098 µs (−47%) | 10035 → 5786 µs (−42%) | 5938 → 3823 µs (−36%) |

After the change, the probes put the merge at about 60 µs a pass at depth 101,
down from 1510. That is 0.3 µs a node.

## Consequences

- **A first frame's style resolution roughly halves** at any depth, and so does
  a subtree re-resolving after an invalidation. The saving is per node, not per
  level, so a shallow wide window gets the same share as a deep one.
- **The walk stays**, at one identity probe per ancestor per node. At depth 201
  it is 1.5% of a first frame. Removing it would mean trusting a parent's entry
  without checking the chain above it, which is the check that makes ADR-0070's
  inheritance invalidate itself. That is not worth 97 µs.
- **The real depth term is selector matching.** The cascade's cost per node
  grows from 3.4 µs at depth 51 to 7.6 µs at depth 201, because a descendant
  combinator that fails walks every ancestor. That is O(depth × rules) per
  node, the shape the TODO entry feared, in `SelectorMatcher` rather than here.
  Browsers answer it with an ancestor Bloom filter. It is recorded rather than
  scheduled, because nothing shipped nests 200 deep and at the showcase's depth
  the cost is a few microseconds a node.
- **The benchmark is kept** as `DeepTreeStyleBenchmark` and listed in
  `docs/testing.md` §1.5. It prints the first-frame pass, custom properties
  alone, the warm walk, a first `render()`, the hover and an unchanged frame at
  each depth. It asserts nothing.

**Machine caveat.** The measurements ran on the 8-core development machine
while other worktrees' Gradle builds were running. The load average was about 3
for the probe breakdown and 7–15 during the A/B runs. Absolute numbers from
separate runs moved by up to 2× under that load, and that is why the
before/after figures come from interleaved passes inside one JVM rather than
from two runs. The ratios held within ±5 points across runs. The absolute
microseconds should not be quoted as this machine's idle figures.
