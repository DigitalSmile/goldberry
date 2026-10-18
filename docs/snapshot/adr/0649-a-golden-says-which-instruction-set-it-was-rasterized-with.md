# ADR-0649: A golden says which instruction set it was rasterized with

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0050](0050-golden-images-have-a-tolerance.md), [ADR-0162](0162-a-golden-is-checked-at-every-scale.md)

## Context

Two `TODO.md` entries waited on hardware nobody here has: *The scale-invariance
thresholds are calibrated on one CPU* and *The rounded corners and the transforms
have not been rasterized on AVX-512*. Blend2D compiles its pipelines for the CPU
it finds. The golden suites are green on MSVC and on Apple Silicon's NEON path in
CI. This machine is a Haswell, which has AVX2 and no AVX-512.

Both entries said the answer would come from "the next CI run". But no run said
which instruction set it rasterized with. A green run on an AVX-512 runner was
indistinguishable from a green run on AVX2, and a red one was not attributable
to the rasterizer's path at all.

## Decision

**Every golden failure, and every line of the scale report, names the
architecture and vector level it was rasterized on** (`SimdPath`). Examples are
`x86-64, AVX2`, `x86-64, AVX-512` and `aarch64, NEON`. On x86 the level is
HotSpot's `UseAVX`, the highest level the CPU and the operating system both
enable, which is the same question the rasterizer's dispatch asks. It needs
nothing from the native library, so no export and no ABI change. On ARM it is
NEON, which every 64-bit core has.

With that, both entries are answered by the suite and not by an entry:

- A runner with AVX-512 that disagrees beyond the tolerance turns a golden red,
  and the failure says `AVX-512`. The tolerance (ADR-0050) and the scale
  thresholds (ADR-0162) are what decide it, as they decide every other path.
- A run made with `-Dgoldberry.golden.scales.report=true` prints the level on
  every line, which is how a runner pressing against the limit says so in
  numbers.
- The transform half's other claim, that `BLMatrix2D` is six doubles in the
  order `matrix()` writes them, is checked by the layout probe on every target
  already.

Rejected:

- **Binding Blend2D's `BLRuntimeSystemInfo`** to read the features the
  rasterizer itself detected. It would answer more exactly, at the cost of a
  native ABI bump on every platform. The JVM's reading names the same level.
- **Running the suite under an AVX-512 emulator here.** Intel's SDE does that,
  under a licence the user would have to accept. It is still open as a way to
  answer the question once by hand.

## Consequences

- `SimdPathTest` holds the level names and that a HotSpot JVM on x86 reports
  one.
- Whether any CI runner has AVX-512 is still unknown. The first red golden or
  report line from one will say so.
