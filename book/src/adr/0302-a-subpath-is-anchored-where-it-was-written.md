# 302. A subpath is anchored where it was written

Date: 2026-09-13

## Status

Accepted. Fixes a third of the bundled icon set, which had been drawing off the
viewBox since `:assets` compiled its first table
([ADR-0033](0033-assets-are-fetched-and-compiled-not-committed.md)).

## Context

`IconCompiler` reduces every Lucide SVG to a single run of path data, because
shipping 1544 XML documents would put a parser on the path that draws a
checkbox. Each `<path>`, `<line>`, `<circle>` and the rest becomes a subpath, and
the subpaths are joined with a space. Its own comment said why that was safe:

> Every shape in the document becomes a subpath and they are concatenated in
> document order. That is safe because each one begins with a moveto —
> concatenating path data is only ever wrong when a fragment continues from
> wherever the previous one ended.

Both halves of that are true. The conclusion does not follow, because **a moveto
may be relative**, and SVG has a rule that makes a relative one look absolute:

> If a relative `moveto` (`m`) appears as the first element of the path, then it
> is treated as a pair of absolute coordinates.
>
> — SVG 1.1 §8.3.2, and unchanged in SVG 2 §9.3.3

"Of the path" means of the `d` attribute it is written in. Lucide writes its
icons as several `<path>` elements and lets each one open with `m`, which inside
its own element is measured from the origin exactly as `M` would be. Joined
behind another subpath, the same three characters mean something else entirely.

`a-arrow-down` is the whole bug in one line. It compiled to:

```
M3.5 13h6 m2 16 4.5-9 4.5 9 M18 7v9 m14 12 4 4 4-4
```

The first subpath leaves the pen at (9.5, 13). The `m2 16` that follows was
written to mean "start at (2, 16)" and was read as "start 2 right and 16 down
from here" — (11.5, 29), which is off the bottom of a 24×24 viewBox. The arrow
under the A had the same treatment and landed somewhere else again.

**481 of the 1544 icons** had at least one such subpath after the first. They did
not fail to draw; they drew the wrong shape, somewhere else, at an offset that
depended on where the previous shape happened to end — which is why nothing
caught it. An icon set is checked by looking at it, and a third of a sheet of
24-pixel glyphs being subtly wrong reads as a rendering artefact.

A further 84 open with `m` and have no second subpath, where the rule really does
make it absolute and the geometry was always right. The table text changes for
those too — 565 lines in all — and nothing drawn changes.

There is a second trap inside the first, and it is the reason this record exists
rather than a one-character fix. `m2 16 4.5-9 4.5 9` is a moveto followed by
**two implicit `lineto`s**, and SVG says an implicit repeat inherits the case of
the command that opened it: relative after `m`, absolute after `M`. Rewriting
the letter alone moves the pen correctly and then draws the rest of the subpath
to two absolute points nobody meant. That is an icon that is wrong in a
*different* way, and worse than the original, because it looks deliberate.

## Decision

**Every subpath is anchored absolutely before it is joined, and the result is
checked rather than assumed.**

`SvgPathData.absoluteStart` rewrites an opening `m x y` to `M x y`, and moves any
trailing coordinate pairs of that command to an explicit `l` so they keep the
meaning they were written with. Everything after that is passed through byte for
byte: the numbers are upstream's, and re-emitting them would lose precision for
nothing — the same reason `IconCompiler` copies a `d` attribute instead of
parsing and printing it.

It is applied to **every** part, including the first, where it is a no-op in
effect: the pen starts at the origin, so `m` and `M` agree there. Applying it
uniformly is what makes "every subpath of the table opens with an absolute
moveto" a property of the table rather than a property of its first line.

`IconCompiler` then refuses a compiled subpath that still does not open with `M`.
That cannot happen for the seven elements it converts, which is the point: the
check is for the eighth, whenever somebody adds one.

The two classes live in a new `io.github.digitalsmile.goldberry.assets.svg`
package with `SvgShapes`, because reading SVG's grammar and converting SVG's
basic shapes are the same subject and `PrepareAssets` is not.

## Consequences

**481 icons change shape**, and 84 more change only in the table. Any golden
image containing one of the 481 has to be re-recorded — it was recording the bug.
As it happens the repository's goldens are unaffected: the showcase binds
`palette` and `plus`, and the widgets that draw their own icons use `check`,
`square`, `type` and `layout-dashboard`, none of which is in the 481. That is
luck rather than design, and the next golden that shows a different icon will
need re-recording.

**The table is a build output**, so there is nothing to migrate: the next
`prepareAssets` produces the corrected one. An application pinned to an older
jar keeps the old table, which is the same thing as keeping the old bug.

**`SvgPath` in `:core` is unchanged and stays that way.** It treats a leading
relative moveto as relative — correct for it, because it starts at the origin, so
the two readings agree on the only input where it matters. Teaching it the
first-element rule would be teaching it a rule about a *document* it never sees.

**The check in `IconCompiler` will refuse an icon set that is not Lucide-shaped**
rather than emitting something almost right, which is the same bargain the
element allow-list already makes.

## Alternatives considered

**Fix it in `SvgPath`, in `:core`.** Rejected, and it is the tempting one: the
reader could treat the first moveto of the data as absolute. That fixes nothing
— by the time the reader sees the string, the subpaths have already been joined
and there is exactly one "first" moveto, which was already right. The
information about where one `<path>` ended and the next began is lost at
concatenation, so the correction has to happen before it.

**Join with an explicit `M 0 0` between parts, or emit `Z` before each.**
Rejected: `M 0 0` adds a stray point to the path that a round line cap will
paint, and `Z` closes subpaths Lucide deliberately leaves open, turning every
open stroke into a triangle.

**Emit a fully parsed, normalised path — absolute commands throughout.**
Rejected: it makes the compiler an SVG path *renderer*, doubles the size of the
table in decimal digits, and rounds every coordinate. `:core` already has the
reader that does this properly, at draw time, from the numbers upstream wrote.

**Re-order the shapes so a relative one never follows another.** Rejected as
nonsense on inspection — it changes paint order, and paint order is the only
thing that makes an overlapping icon read correctly.
