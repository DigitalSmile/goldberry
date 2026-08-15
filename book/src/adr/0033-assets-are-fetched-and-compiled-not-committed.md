# ADR-0033: Assets are fetched and compiled, not committed

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §6.1, §6.2, §6.3, §15, [ADR-0015](0015-licensing-and-third-party-disclosure.md), [ADR-0029](0029-yogas-node-api-and-who-owns-a-node.md), [ADR-0030](0030-pin-blend2d-and-asmjit-by-commit-sha.md), [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md), [ADR-0032](0032-shaping-is-utf16-in-glyphs-out.md)

## Context

Three things came due at once.

**The toolkit had no font.** `docs/ARCHITECTURE.md` §6.1 says Inter and JetBrains
Mono ship inside the jar, and §6.2 adds OpenMoji for the emoji slot. None of them
were in the repository, which is why [ADR-0032](0032-shaping-is-utf16-in-glyphs-out.md)
had to record that shaping itself was untested: the HarfBuzz binding could be
exercised against an empty face, but ligatures, kerning and real glyph ids need
real outlines.

**Four licence placeholders were release blockers.** ADR-0015 requires the
verbatim upstream text for everything bundled, and `licenses/inter.txt` and its
three siblings were notes saying so rather than licences.

**Yoga and Blend2D had never met.** Both were bound and independently tested —
layout in ADR-0029, painting in ADR-0031 — and nothing put the output of one into
the other. A join that has never run is a join that does not work.

## Decision

**A `:assets` subproject, in Java.** The first attempt at this was fifty lines of
Groovy in `core/build.gradle`, and it was the wrong shape by the time it handled
its third SVG element: converting 1544 icons from seven shape primitives into
path data is real logic, and real logic belongs somewhere it can be unit tested
rather than somewhere it can only be run. `:assets` is a build-time tool with an
ordinary `main`, no `module-info`, and 23 tests. It is not published, so §15's
artifact list is unchanged.

**Assets are pinned by version *and* SHA-256.** The same argument
[ADR-0030](0030-pin-blend2d-and-asmjit-by-commit-sha.md) makes for the native
upstreams applies harder here: GitHub permits a release asset to be replaced, and
a font that changed underneath us would change how every application renders,
with no version number moving to say so. The manifest lives in Java rather than
`gradle/libs.versions.toml` because a version catalog has nowhere to put a
checksum, an archive layout, or the list of entries worth extracting — and
splitting those across two files is how they drift apart.

**Fetched at build time, cached outside `build/`.** The archives total 90 MB and
four files are wanted from them. They are cached under `.gradle/` so a `clean`
does not mean downloading them again, and they cannot go stale because the
checksum is what decides a cache hit.

**Icons are compiled, not shipped.** Lucide's 1544 SVGs become one table of path
data, `name<TAB>path` per line, parsed lazily on first use. Shipping SVGs would
put an XML parser on the path that draws a checkbox. §6.3 anticipates a "compact
binary path table"; this is the same idea in the form that can be read, diffed
and grepped, and turning it binary is worth doing when something measures the
parse — today nothing draws an icon at all.

**An unconvertible icon fails the build.** Lucide is uniform by construction —
24×24, 2px round strokes, no transforms, fills or groups — so every shape can
become path data. Anything else is refused rather than skipped, because an icon
that silently lost a piece is far harder to notice than a build that stops.

**Licences are vendored by a separate, manual task.** `vendorLicences` writes the
verbatim upstream texts into `licenses/` and is run by hand and committed. It is
deliberately not part of `build`: ADR-0015 wants those texts in the repository so
it is self-describing, and a generated file inside a tracked directory that
nobody committed is worse than no file at all.

**`Box` and `BoxPainter` join the two engines.** A `Box` is an immutable
flexbox-styled rectangle with children; `BoxPainter` builds a Yoga tree from one,
sets the config's point scale factor **from the frame's display scale**, lays out
at the frame's logical size, and walks the result accumulating absolute positions
into Blend2D fills.

That scale factor is the piece worth naming. Yoga rounds computed positions to a
pixel grid, and the grid is the config's. Left at 1, every edge in a 1.5× window
lands on a whole *logical* pixel — one and a half physical ones — so half the
edges fall mid-pixel and the compositor smears them. This is the first code that
sets it, and therefore the first code for which ADR-0019's fractional-DPI claim
is a mechanism rather than an intention.

## Alternatives considered

**Commit the fonts and icons.** A hermetic build, no network, no checksums to
maintain. It also puts three megabytes of binary into the history permanently,
makes every upstream bump a diff nobody can review, and means the repository
carries redistributable assets whose provenance is a commit message. The build
already requires network for the native superbuild, so this changes no
constraint that was not already there.

**Keep the asset logic in the build script.** Fewer moving parts, no extra
subproject. It also means the SVG conversion — the one piece with arithmetic in
it — can only be checked by running the whole build and looking at the output.
The 23 tests in `:assets` exist because that was not good enough.

**Put the asset versions in `libs.versions.toml`.** Consistent with the native
pins, and it was the first attempt. A catalog entry is a version string and
nothing else, so the checksum, the archive layout and the extraction list would
have lived somewhere else — which is exactly the split that lets a version bump
land without its checksum.

**Ship the SVGs and parse at runtime.** No build-time compiler and no format to
maintain. It also ships an XML parser dependency and puts it on the path that
draws a checkbox, for icons that never change after the build.

**Bundle OpenMoji's colour build.** §6.2 wants COLRv0 available. It is 2.5 MB
against 1.4, and nothing can draw layered outlines yet — so it would be weight
in every jar for a feature that does not exist.

**Make `Box` the widget model.** It is one small step from here, and it would be
inventing the three-tree design a second time. ADR-0004 is still open; `Box` is
deliberately a join between two engines and not a proposal about widgets.

**Retain the Yoga tree across frames.** `BoxPainter` builds and frees a tree per
paint, which is the wrong shape for a real toolkit: layout should be incremental
and only dirty subtrees recomputed. Retaining it is the render tree's job, and
the render tree is blocked on ADR-0004. Building it per frame is honest about
being a seam rather than an engine.

## Consequences

**Shaping is tested with real outlines.** The gap ADR-0032 recorded is closed:
Inter produces real glyph ids rather than `.notdef`, a proportional face and a
monospace one demonstrably differ, doubling the scale doubles the advance, and
emoji resolve through OpenMoji. That last one is the measure function's input, so
the paragraph work now has ground to stand on.

**Four of the ten licence placeholders are now verbatim upstream texts** — Inter
and JetBrains Mono's OFL, OpenMoji's CC BY-SA, Lucide's ISC — and they ship in
every jar under `META-INF/licenses/`. The six native ones remain, so
`checkLicenses -Pgoldberry.releaseCheck=true` still fails, but the release
blocker is smaller by the assets.

**`goldberry-core` is now about 3 MB.** 859 KiB of Inter, 296 of JetBrains Mono,
1382 of OpenMoji, and 220 for 1544 compiled icons. That is the trade §6.1 makes
deliberately: deterministic rendering everywhere in exchange for jar size.

**A build with no network cannot produce a usable `goldberry-core`.** The archives
cache after the first fetch, so this bites once per checkout — but it bites, and
a jar assembled without the asset step produces a toolkit that cannot render
text. `BundledAssets` says so by name rather than throwing a
`NullPointerException` from inside a paint pass.

**The showcase lays out through Yoga.** It draws a top bar, a quarter-width
sidebar and a body, in logical coordinates, at whatever scale the display runs —
which is a better demonstration than the two hand-placed rectangles it had, and
it means the join is exercised by something other than its own tests.

**Nothing draws an icon or a glyph yet.** The icons are compiled and reachable;
turning path data into a `BLPath` needs Blend2D's path API bound, and drawing a
`GlyphRun` needs `bl_font_*` and `bl_context_fill_glyph_run_*`. Both are the next
piece of work, and both now have their inputs sitting in the jar.
