# ADR-0630: A golden admits a few stroke pixels in a hundred thousand

- **Status:** Accepted
- **Date:** 2026-10-11
- **Amends:** ADR-0050 (one reference set, compared with a tolerance)

## Context

Every golden is compared against one reference set, shared by every platform,
with `Tolerance.RASTER`: a pixel may differ by two levels in 256, 2% of the
image may differ at all, and no pixel may stray beyond the two levels. The
last rule rested on Blend2D's coverage being analytic, so that nothing on an
edge flips between CPUs.

The showcase's new shell put two stroked Lucide icons in every screen's top
bar (`panel-left-close` and `arrow-left`). On the macOS arm64 lane, 53 goldens
in `:example` failed: every gallery screen, the shell's two goldens and the
guide's screen pictures. Each failure was the same 2 to 4 pixels at 1x and 10
to 13 at 2x, on the left edge of those two icons, 3 to 4 levels off at 1x and
13 to 14 at 2x. **Every other pixel of those frames was identical** to the
x86-64 goldens, and the Windows and Linux x64 lanes passed.

So the pipelines agree, and the disagreement is in a stroke's outline, which
compiled C++ computes before the fill. The arm64 and x86-64 builds of that code
place a vertex a hair apart, plausibly because the compilers fuse
multiply-adds differently. That was not verified, as there is no arm64 machine
here. A fill cannot do this; a stroke can.

## Decision

**`Tolerance.RASTER` admits one pixel in 100,000 beyond the two levels**
(`new Tolerance(2, 0.02, 0.00001)`). The other two numbers are unchanged.

- A 1200x900 frame may have 10 such pixels, and a 2x screen picture 43 to 72.
  The worst seen was 4 and 13.
- A widget-sized golden, under 100,000 pixels, may have none, which is the old
  rule exactly.
- A missing caret, line or focus ring moves more pixels than that, and a
  changed colour moves thousands, so neither can hide.

Rejected:

- **Per-platform goldens:** three sets to bless and review for every visual
  change, which ADR-0050 already declined.
- **Rebuilding Blend2D with `-ffp-contract=off`:** a guess about the cause
  that cannot be checked without an arm64 machine, and it may not hold if
  Blend2D uses fused instructions on purpose.
- **A looser tolerance for the showcase's tests only:** the next frame-sized
  golden with a stroke in an awkward place would fail the same way.

## Consequences

- The macOS lane's 53 failures pass without re-blessing anything.
- `ToleranceTest` pins both sides: the icons' 4 and 13 pass, 11 in a
  1200x900 frame and a 16px caret fail, and a widget golden admits none.
- The guide's tolerance paragraph gains a sentence at release
  (`docs/snapshot/guide-testing-tolerance.md`).
