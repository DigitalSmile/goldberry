# 162. A golden is checked at every scale

Date: 2026-08-20

## Status

Accepted. Closes the gap
[ADR-0157](0157-a-layer-is-blitted-into-its-own-size.md) left open.

## Context

ADR-0157 fixed a layer composited at twice its size on a 2× display and ended
with what it had *not* fixed:

> **The broader gap is not closed** — almost every pixel assertion in this
> repository is at 1×, and this class of bug is invisible there. A golden corpus
> at 2× is the obvious next step and is not built.

The numbers behind that sentence: 39 calls to `GoldenImage.assertMatches` across
21 test classes, producing 106 committed images. **37 of the 39 are at 1.0.** One
is at 1.5 and one at 3.0, both added deliberately by somebody who had just been
bitten.

The class of fault this hides is narrow and entirely mechanical. Two coordinate
spaces meet everywhere in the paint path — logical units, which are what a
stylesheet and a layout speak, and device pixels, which are what a raster is
made of — and the conversion between them is a multiplication by the display
scale. **At a scale of 1 that multiplication is the identity.** So a size
converted twice, a size never converted, an origin computed in the wrong space
and a stroke width that picked the factor up on the way past all produce exactly
the right picture on the machine the golden was generated on, and the wrong one
on the reporter's.

## Decision

**Every golden that matches is drawn again at 2× and 1.5× its own scale, and
asserted to be the same picture. Nothing further is committed.**

`GoldenImage.assertMatches` calls `ScaleInvariance.assertScaleInvariant` once the
image comparison has passed. That renders the same scene into a frame of the same
*logical* size on a larger device — `TestFrames` is described in physical pixels,
so both the buffer and the scale are multiplied and the logical size comes out
unchanged — area-resamples the result back down, and compares.

`ScaleInvariance.assertSamePictureAtEveryScale` is the same check with no golden
behind it, for the tests that read pixels back directly. `ClipTest`,
`TransformPaintTest` and `IconPaintTest` now use it: those are where the
arithmetic actually lives, and none of them had a golden to hang a second scale
off.

### What the comparison is allowed to demand

Not equality, and not `GoldenImage`'s two-level tolerance. Two things differ
legitimately between one scale and another:

- **Edge placement.** Yoga's point scale factor rounds computed edges onto whole
  device pixels, so at 2× an edge can land on a *half* of a logical one. A
  high-contrast border that moves half a pixel differs by over a hundred levels
  in the column it moved out of.
- **Antialiasing.** A glyph rasterized at 2× and averaged down is a different
  approximation of the same coverage integral than one rasterized at 1×. Both are
  right and they are not the same bytes.

Both are *sub-pixel*; the faults being hunted are not. So a pixel may find its
match anywhere in the 3×3 neighbourhood around it — which absorbs half-pixel
movement and resampling blur completely — and what is left has to be small:

| Setting | Value | Why |
|---|---|---|
| Neighbourhood radius | 1 pixel | The differences forgiven are sub-pixel. A radius of 2 would start forgiving a control that *moved*. |
| Channel tolerance | 72 levels | High enough that the honest disagreements — always on glyph edges — stay a handful of pixels rather than a region. Single pixels do go past it (121 is the worst measured); what does the work is the share below. |
| Share allowed past it | 1.2% | The worst honest case measured is 0.332%. |
| Multipliers | 2, 1.5 | A Retina display, and the ordinary fractional Linux case — the one where a raster is rounded *up* to a whole pixel and has to be divided back. |

The search runs in **both directions**. "Every pixel of the reference appears
near where it was" says nothing about something that grew: the reference's ink is
all still present, just with more around it. Ink that appeared where there was
none is only visible looking the other way.

The reference is **what this run drew at 1×**, not the committed PNG. The
question is whether the renderer agrees with itself across scales; comparing
against the file would fold a stale golden into the answer as well.

## Alternatives considered

**Commit `name@2x.png` beside every golden.** The obvious answer. It doubles a
106-image corpus, and — the reason it is wrong rather than merely expensive — a
committed 2× image asserts only *this is what it drew*, which a wrong image
satisfies exactly as well as a right one, forever. Nobody reviewing a pull
request looks at a 2× render of a slider and notices that its thumb is a
half-pixel out. The claim worth making is not "this is the 2× picture" but "the
picture does not depend on the device", and that one can be checked against the
1× image already in the repository.

**Compare with a per-channel tolerance and no neighbourhood search.** Simpler,
and it fails on every scene with a border in it: a half-pixel edge shift is a
delta of over 100 in a whole column of pixels, so the threshold would have to be
loose enough to let a real fault through.

**Assert on ink coverage or a bounding box instead of pixels.** Robust against
antialiasing by construction, and blind to anything that keeps the same total
amount of ink — a control drawn at the right size in the wrong place, which is
half of this family.

**Leave it opt-in, one test at a time.** 21 classes would have had to adopt it and
the ones nobody remembered would be exactly the ones with the bug. Making it part
of what `assertMatches` means closes all 39 call sites at once and makes a new
golden covered on the day it is written.

## Consequences

**A golden costs about 17 ms more.** `:widgets:test --tests '*GoldenTest*'` — 88
images — goes from 2.16 s to 3.64 s in-JVM, a 68% increase on a suite that is
seconds long. It buys three renders where there was one.

**Nothing new was found.** The whole corpus passed at 2× and 1.5× on the first
run, at 2215 tests and no failures. ADR-0157's bug was the one that was there and
it was already fixed; this is the check that says so, and the check that will not
let the next one through. Reporting it any other way would be dishonest about
what a green run means here.

**The thresholds are calibrated to this machine's rasterizer.** Blend2D compiles
its pipelines for the CPU it finds (ADR-0030), so another architecture's
antialiasing differs — the same reason `GoldenImage` has a tolerance at all. The
margin is 3.6× between the worst measured honest case and the limit, and
`-Dgoldberry.golden.scales.report=true` prints what every check measured, so a
runner that starts pressing against it says so in numbers rather than in a
mysterious failure.

**A one-pixel translation of the whole scene is invisible to this.** That is the
neighbourhood search doing its job, and it means the check is not a substitute
for the golden: the golden pins position, this pins invariance, and neither
subsumes the other.

**Turned off with `-Dgoldberry.golden.scales=`.** A golden update run skips it
already, because `assertMatches` returns before the check when it is rewriting.

**Verified by breaking it on purpose.** `ScaleInvarianceTest` reconstructs
ADR-0157's bug — a rectangle sized in physical pixels and drawn in logical ones —
and asserts the check rejects it, and does the same for a border thickened by the
scale, which is the subtle end of the family and only a stroke wide. A harness
whose failure path never runs is a harness that reports success.
