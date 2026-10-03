# ADR-0554: A picture of a screen pins what the build can do

- **Status:** Accepted
- **Date:** 2026-10-03
- **Relates to:** `docs/ci-fixes-2026-10-03.md`,
  [ADR-0549](0549-the-showcase-is-the-guide-a-screen-per-chapter-and-a-card-per-section.md),
  [ADR-0050](0050-golden-images-have-a-tolerance.md)

## Context

The showcase's Diagnostics screen has a card, *What this build can do*, with a
yes or no badge for every `Capability`. It read `Goldberry.capabilities()`, which
is the answer of the `libgoldberry` the process loaded. That is the point of the
card in the window, and the wrong thing for a golden image of it.

The golden `gallery-diagnostics.png` was taken on a machine whose library is
built without ibus and udev (the degraded-platform flag), so it photographed
*input method: no* and *device hotplug: no*. CI's Linux library has both, and
macOS and Windows answer differently again. The first snapshot after ADR-0549
failed `GalleryGoldenTest > Diagnostics` on Linux, macOS and Windows with the
same three badges. No retake can fix that: whichever machine takes the golden
breaks it on the others.

The Styling screen's *Following the desktop* card had the same dependency,
reading `SYSTEM_THEME` from the library. It did not fail only because every
build so far had that capability.

The web view and FFmpeg already had this problem and were solved in the test
task: their libraries are pinned away, so the Web and Media screens look the
same on every machine. The native capabilities cannot be solved that way,
because the goldens need `libgoldberry` itself.

## Decision

**The capabilities a screen reports are part of what it is built from.**
`GalleryContext` carries them as a `Set<Capability>`, and so does `Screen`, which
makes the context. The constructors the window uses default to
`Goldberry.capabilities()`, so the running showcase is unchanged. `BuildCapabilities`
and `ThemeCards.Desktop` take the set they are given, and
`BuildCapabilities.ofThisBuild()` is gone.

**`ShowcaseScene` pins the set**: every capability except `WEB_VIEW`. The web
view is off for the reason the Web screen shows it off: the test task pins its
library away. Every picture taken through the scene uses this set: the gallery
goldens, the guide's screen pictures and the chapter tests. The set is
`ShowcaseScene.CAPABILITIES`.

## Consequences

- `gallery-diagnostics.png` shows the pinned set and is the same on every
  runner. It was retaken once, and only it changed.
- `DiagnosticsChapterTest` asserts every badge against the pinned set, and
  `StylingChapterTest` that the desktop card asks the set it was given.
  `GalleryContextTest` covers the default and the copy.
- A screen that adds another machine-dependent reading must take it from the
  context too. Reading the library directly passes on the machine that took the
  golden and fails everywhere else, which is how this was found.
