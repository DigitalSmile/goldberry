# 434. Every `check` sweeps 1.25, and nothing sweeps 1.75

Date: 2026-09-19

## Status

Accepted. Extends [ADR-0162](0162-a-golden-is-checked-at-every-scale.md), which
built the sweep and chose its two multipliers, and
[ADR-0157](0157-a-layer-is-allocated-in-physical-pixels.md), which is the bug the
sweep exists because of.

## Context

Every one of the 246 committed goldens — 208 in `:widgets`, 21 in `:example`, 11
in `:core`, 6 in `:html` — is drawn again at 2× and 1.5× its own scale and
checked for describing the same picture. `TODO.md` recorded the hole in that:

> What no test at any scale covers is a **fractional scale other than 1.5** —
> 1.25 and 1.75 are ordinary Windows settings and neither is exercised.

`:widgets` holds at both — `./gradlew :widgets:test -Dgoldberry.golden.scales=2,1.5,1.25,1.75`
passes over its 208 images, and so does `:core:test`. **`:example` does not**, and
that turned out to be the interesting part of this decision rather than a
footnote; it is worked through below. So the question was never quite "does it
pass": it was which multipliers belong in every developer's `check`, and what the
one image that fails is actually saying.

### What a multiplier actually buys

Not "a scale somebody uses". What the sweep exercises is **Yoga's rounding**: a
point scale factor rounds every computed edge to a whole device pixel, so what a
multiplier `m` samples is the set of sub-pixel offsets an integer logical
coordinate can land on — `k·m mod 1` over integer `k`. Written out:

| multiplier | as a fraction | offsets visited |
| --- | --- | --- |
| 2 | 2/1 | `{0}` |
| 1.5 | 3/2 | `{0, ½}` |
| 1.25 | 5/4 | `{0, ¼, ½, ¾}` |
| 1.75 | 7/4 | `{0, ¼, ½, ¾}` |

That table is the decision. 2 never lands between pixels at all, which is exactly
why it catches a doubled subtree and catches no rounding. 1.5 adds halves. **1.25
adds quarters, which strictly contain the halves and reach two offsets nothing
else in the list reaches.** And 1.75 is the same four quarters in a different
order — a different denominator would have been a different question, and 7/4 and
5/4 have the same one.

The magnitude argument does not rescue it either. Whatever 1.75 does by being
*large* — a hairline rounding up rather than down, a raster allocated a pixel
wider — is bracketed by 1.5 below it and 2 above it, both already in the list.

### What it costs

The wall-clock A/B that prompted this — `:widgets:test --rerun-tasks` at 3m47s
with four scales — is not a measurement of the sweep. Re-run on this machine it
gave 97 s for two multipliers, 98 s for three, 139 s for four, and **101 s for no
sweep at all**: the whole-invocation number is dominated by compilation and by
whatever else the machine is doing, and the sweep is inside its noise. That is
worth stating plainly, because it is the number somebody will quote.

Measured properly — JUnit executor time, the `*GoldenTest` classes alone, minimum
of four runs:

| multipliers | `:widgets` (187 goldens swept) | `:example` gallery (21 goldens) |
| --- | --- | --- |
| none | 2.4 s | 3.3 s |
| 2 | 3.5 s | 5.9 s |
| 2, 1.5 | 4.4 s | 8.3 s |
| 2, 1.5, 1.25 | 5.3 s | 11.2 s |
| 2, 1.5, 1.25, 1.75 | 6.3 s | 13.5 s |

Dead linear in both, at about **0.95 s per multiplier in `:widgets` and 2.5 s per
multiplier in the gallery** — 5 ms per widget golden and 120 ms per gallery
golden, which is the ratio between a 200-point control and a 1200×1720 screen.
Across the whole repository a multiplier is **about four seconds of `check`**.

### The one image that fails, and what it is telling us

`gallery-canvas` misses at 1.25× by 13,278 pixels of 1,080,000 — **1.229%
against a 1.200% budget.** The first instinct is that a threshold set for two
multipliers is simply too tight for a third, and that is wrong. Measured across
the whole gallery at 1.25×, every other image lands between 0.001% and 0.107%;
the noisiest is `gallery-markdown` at 0.096%. The canvas screen is not near the
edge of the distribution, it is two orders of magnitude outside it, and it always
was:

| multiplier | pixels with no match | worst nearby delta |
| --- | --- | --- |
| 1.5 | 0.605% | 163 |
| 2 | 0.743% | 163 |
| 1.75 | 1.189% | 229 |
| 1.25 | **1.229%** | 229 |

The diff says why. The disagreement is not spread over the screen — it is
concentrated in two kinds of thing, both solid rather than outlined: **the three
QR codes**, and **the decoded PNG drawn at its natural size**. Everything
vector on that wall — the cubics, the arcs, the dashed baseline, the gradient —
shows the ordinary faint edge noise every other golden shows.

That is not a geometry fault, and it is not antialiasing either. It is the
invariance claim being **false**. A QR module is a hard-edged square in a dense
grid; re-render it at 5/4 and a module boundary rounds to the other side of a
device pixel, and a run of modules comes back inverted rather than blurred. The
three-by-three neighbourhood search cannot forgive that, and should not: the
neighbouring pixel is the opposite colour, which is exactly the signature the
search exists to refuse. A raster drawn at its own pixel size is the same story
with a resample on top.

So the honest options are two, and one of them is wrong. Raising the budget to
1.5% would buy this one screen at the cost of loosening the check on 245 images
that meet 1.200% with a factor of ten to spare — and `ScaleInvarianceTest`'s own
header warns about precisely that: "a threshold set one step too generous
produces a suite that runs at three scales and notices nothing."

**The canvas golden is excluded from the sweep instead**, through a new
`GoldenImage.assertMatchesAtOneScale`, and the argument is the one `TODO.md`
already makes about `DamageTest`: a damage rectangle is in physical pixels by
design, so an invariance check there would assert something false. A barcode is
the same, from the picture's side rather than the geometry's.

**The cost is real and is not hidden**: the paths, strokes, gradient and dashed
rule on that wall *are* facts about logical space and are no longer checked to
be. Keeping them would take a sweep over part of an image — a mask, or a
per-region budget — which is a mechanism nothing else in the repository needs
yet, and building it speculatively to save one screen is not this ADR's trade.

### The nightly is not the answer

The obvious split — 1.25 in `check`, 1.75 in the nightly — is refused by
`nightly.yml` itself, in its own header:

> Deliberately NOT here: the golden images, the unit suite and the static
> analysis. Those gate every PR in `linux.yml`, `macos.yml` and `windows.yml`, and
> a check that only runs at night is one nobody associates with the change that
> broke it.

And there *was* a golden job there until the 2026-09-18 review deleted it (B5) —
whose comment described a scale sweep it did not run and could not have, because
`-Pgoldberry.skipNative=true` builds no rasterizer and every golden skips. Adding
a nightly scale sweep three weeks after removing one, for the multiplier that
buys the least, would be putting the worst of the four in the one place nobody
reads.

## Decision

**`ScaleInvariance.DEFAULT_MULTIPLIERS` becomes `2, 1.5, 1.25`. 1.75 goes
nowhere.**

Three multipliers, chosen so that no two share a rounding denominator. The cost
is four seconds on `check` — under 2% of `:widgets:test` and invisible in its
run-to-run variance — for the offset family that 208 widget goldens and 21
gallery screens have never been drawn at, on the display scale more Windows
machines are set to than any other non-100% value.

1.75 is a one-line command when the rounding path itself changes:

```
./gradlew check -Dgoldberry.golden.scales=2,1.5,1.25,1.75
```

The reasoning is not left in prose. `ScaleInvarianceTest` computes the offset sets
and asserts that `offsets(1.25) == offsets(1.75)`, that the quarters contain the
halves, that 2 visits only zero, and that the three defaults are three distinct
families. If a change to how edges are rounded ever makes 1.75 a different
question, that test goes red and this ADR is wrong in the place it is wrong.

## Consequences

- Every golden in the repository but one is now checked at 1.25, and the corpus
  passes unchanged. **No golden image moved**, which is the whole point of the
  sweep being a second question rather than a second set of files (ADR-0162).
- `gallery-canvas` is that one, and is now the only golden here with no scale
  sweep behind it. `GoldenImage.assertMatchesAtOneScale` is how it says so, with
  the measurements and the argument at its call site rather than in a list of
  exclusions somewhere else. It is worth being uncomfortable about: a golden that
  opts out is a golden nothing checks at 2×, which is the blindness ADR-0157 was
  about. The second opinion that screen loses is worth less than the 245 checks a
  looser budget would have cost, and not much less.
- **The natural-size image tile on that screen deserves its own look**, and this
  ADR is not it. It differs wholesale between scales while the stretched and
  cropped tiles beside it differ only at their outlines, which is the shape of a
  pixel size being used where a logical one belongs — ADR-0157's bug — rather
  than of a resample. It may be nothing; nobody has checked; the sweep can no
  longer tell anyone.
- `check` costs about four seconds more. The gallery pays three of them, because
  its images are fifty times the area of a widget's.
- `-Dgoldberry.golden.scales=` still turns the whole thing off, and a
  comma-separated list still replaces the default — the `2,1.5,1.25,1.75` run
  above is the same mechanism, not a new one.
- The four classes of direct pixel assertion `TODO.md` listed are unchanged and
  stay unchanged: `BoxPainterTest` and `TextPaintTest` carry their own scale
  cases, `DamageTest` is excluded because a damage rectangle is in physical
  pixels by design, and `ThreadedPaintTest` is about worker counts. Adding a
  third multiplier does not make any of those four a better idea.
- Taken with [ADR-0435], `check` now runs a three-multiplier display-scale sweep
  over 246 goldens **and** a two-scale text-scale audit over 28 screen layouts,
  and the two do not multiply: the audit runs at display scale 1.0 only, on
  purpose. Combining them would be a third axis over the whole corpus, at roughly
  the cost of the entire gallery again, to ask whether a label fits its box — a
  question that is settled in logical units before a device is chosen.
