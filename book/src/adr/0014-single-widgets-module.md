# ADR-0014: One `widgets` module, charts included

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §11, §14, §15

## Context

The original module layout had five modules: `:natives`, `:core`, `:charts`,
`:gpu`, and `:gallery`. Two of those were thin for different reasons.

`:charts` was to hold five widgets — `sparkline`, `line-chart`, `bar-chart`,
`area-chart`, `donut-chart` — all built on the `canvas` primitive and the theme
palette, and §11 describes the set as "deliberately small — not a plotting
library". A separate published artifact, module descriptor, and version for five
canvas-based widgets with no dependencies of their own is more packaging than the
content justifies.

`:gallery` was a showcase application that, per §14, doubles as the visual
regression corpus. But its content is one screen per widget — which means it
tracks the widget catalog exactly, and keeping the catalog and the screens that
exercise it in separate modules guarantees they drift.

## Decision

Merge `:charts` and `:gallery` into a single `:widgets` module, published as
`goldberry-widgets`. It holds the widget catalog above the `core` primitives —
controls, containers, menus, and the chart widgets — together with the showcase
screens that serve as the golden-image corpus.

`:widgets` depends on `:core` only. The gallery's former dependency on `:gpu` is
deliberately dropped: a published widget library must not drag SDL_GPU and its
driver surface into every consumer. The `canvas3d` showcase belongs in `:gpu`.

The module layout is now four: `:natives`, `:core`, `:widgets`, `:gpu`.

## Alternatives considered

- **Keep `:charts` separate** (the §15 layout). Rejected: consumers who do not
  want charts save a few canvas-based classes with no transitive dependencies,
  which is not worth an artifact in the publishing matrix.
- **Merge `:charts` into `:core`.** Rejected: `:core` is the primitives, style,
  layout, text, paint, and backend SPI. Charts are ordinary widgets built on
  `canvas` like any other, and putting them in `:core` would blur what `:core` is.
- **Keep `:gallery` as a separate non-published module.** Rejected for the drift
  reason above, and because a showcase in its own module tends to lag the catalog
  rather than gate it.

## Consequences

- Consumers of `goldberry-widgets` get the chart widgets whether they use them or
  not. The cost is small and bounded — they are canvas-based, pull in no native
  code beyond what `:core` already requires, and are a deliberately limited set.
- **The visual regression corpus now lives beside the widgets it covers.** §14's
  intent — a widget with no screen has no pixel coverage — becomes enforceable in
  one module rather than across two, which is the main reason for the merge.
- One fewer artifact to publish, version, and document.
- The showcase ships inside a published library rather than as a separate
  application. If that becomes unwanted — because the screens grow large, or
  because shipping demo code to consumers grates — the seam to split it back out
  is a source set, not a rearchitecture.
- `docs/ARCHITECTURE.md` §11 and §15 are corrected: `goldberry-charts` becomes
  `goldberry-widgets`, and `:gallery` is gone from the module list.
