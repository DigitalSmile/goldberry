# ADR-0729: A box that did not change is not damage, and many damaged boxes are merged

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** the render tree's damage tracking, `RenderObject.sameAppearance`

## Context

Found with jdb on a downstream board on 2026-11-12: with one spinning 16 px
icon on screen, every frame repainted the whole window. Three things combined.

- `IconPaint.render` drew an icon through a lambda minted on every render, and
  `RenderObject.sameAppearance` compares painters by identity, the only thing a
  lambda can be compared by. While anything animates the tree is rendered every
  frame, so every `icon` on screen was "changed" every frame wherever it was.
  `sparkline`, `image`, the documents' picture, `color-plane`, `color-ramp` and
  `qr-code` did the same.
- `Icon` had no `equals`, so a painter that held its icon would still not have
  matched one built from a second `Icon.find` of the same name.
- `RenderTree.damage` merged only rectangles that overlapped, and past
  `MAX_DAMAGE_RECTS` (8) it gave up and damaged the whole frame.

A window with more than eight icons repainted all of itself on every frame
anything moved: 240–260% CPU in the demo at 120 Hz, about 80% under the board's
30 Hz cap.

## Decision

1. **A painter that draws a value is a value.** Each of the seven is a record
   holding what it draws: `IconPainter(Icon, argb)`, `SparklinePainter`
   (the series, fill and marker without its attributes, and the colour),
   `PlanePainter`, `RampPainter`, `ModulesPainter` (the cached matrix),
   `ImagePainter` and `PicturePainter` (an `Image` is immutable and compared by
   identity, so an animation's next frame, a new image, is still a change). The
   comparison in `sameAppearance` stays as it is: `Objects.equals` on the
   painter, which a record answers by value and a lambda by identity.
2. **`Icon` compares by value**: name, size, stroke width and outline. The
   outline is compared because `Icon.of` takes any name; the hash is the name
   and the size.
3. **Past the limit, merge the nearest.** `DamageMerge.reduce` joins, step by
   step, the pair whose bounding rectangle adds the least area neither covered,
   folds in whatever the join now overlaps, and stops at `MAX_DAMAGE_RECTS`.
   Only past 256 rectangles, where the pairwise search costs more than it saves
   and the frame has changed nearly everywhere, is the whole frame damaged.
4. **Painters that read live data stay lambdas.** The microphone views read
   their source at paint time, so a fresh painter every frame is the correct
   answer there, and `VideoSurface` says the same about its pictures.

## Consequences

- One spinner repaints itself; the icons around it are not damage. A dozen
  small changes scattered down a column are a few strips, not the window.
- `Icon` instances that were distinct map keys before are equal now when they
  describe the same icon.
- A widget that wants the same is written the same way: hold the painter, or
  make it a record of what it draws. A lambda is still safe, and still damage on
  every render.
- `DamageTest`'s "scattered changes fall back to the whole frame" is now
  "are merged into a few rectangles", and asserts every change is still covered.
