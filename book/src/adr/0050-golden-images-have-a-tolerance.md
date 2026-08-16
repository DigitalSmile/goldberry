# ADR-0050: Golden images have a tolerance, and why

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §14; [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0016](0016-verify-the-artifact-and-never-skip-the-check.md), [ADR-0030](0030-pin-blend2d-and-asmjit-by-commit-sha.md), [ADR-0033](0033-assets-are-fetched-and-compiled-not-committed.md), [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)

## Context

Every test in the suite until now asserted about one stage: a token, a specificity
score, a glyph advance, a cascade winner. None of them looks at a pixel. A
stylesheet can parse correctly, cascade correctly, compute correctly and still
paint the wrong thing, and nothing would have said so.

§14 asks for golden-image tests that "run identically in CI on all three OSes".
The design questions are what to compare, how exactly to compare it, and what to
do about the fact that identical is a strong word.

## Decision

**Compare rendered output against committed PNGs, with a tolerance.**

An exact match is the obvious design and it is wrong *across platforms*. Blend2D
compiles its rasterizer pipelines at run time with AsmJit
([ADR-0030](0030-pin-blend2d-and-asmjit-by-commit-sha.md)), specialized to the CPU
it finds: AVX2 on one runner, SSE2 on another, NEON on an Apple Silicon one.
Those pipelines agree about what they draw. They are not required to agree about
the last bit of a blended subpixel. Demanding bit-equality would fail on whichever
architecture the goldens were not generated on, and the obvious fix — three sets of
references — is three things that can rot independently.

So two gates, and **both must pass**:

- **Per-channel tolerance: 2 of 256.** A rounding disagreement between two SIMD
  pipelines lands at one.
- **Area: at most 2% of pixels may differ at all.** Antialiased edges are where
  pipelines disagree, and an edge is a small fraction of a frame.

The second gate is the one that does the work, and it is not obvious that it is
needed until you try to defeat the first. Changing one fill from `#bf616a` to
`#bf616b` — a difference no human would call a different colour — produces a
worst channel delta of **1**, comfortably inside the tolerance, while moving
**29% of the image**. The area gate catches it. Either gate alone is a test that
can be walked past.

**The scenes are driven through CSS, not by building `Box`es.** A golden that
runs stylesheet → cascade → `var()` → `ComputedStyle` → `Box` → Blend2D is one
image that fails if any of six stages breaks. Two of them render the same tree
under `nord-light` and `nord-dark`, which cannot both be right unless custom
properties inherit and the theme layer wins ([ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)).

**PNG is read and written here, in `java.base`.** `ImageIO` would do it in two
lines and lives in `java.desktop`. Goldberry's whole claim is that it does not go
through AWT ([ADR-0003](0003-sdl3-as-the-only-desktop-backend.md)); a harness that
drags AWT in to check that claim would be a strange thing to own. The writer is
8-bit RGBA, unfiltered, one `IDAT` — and since every golden is a file this code
wrote, that is also all the reader has to handle.

**No display is involved.** `Frame` paints into memory
([ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)), so these run in the
existing per-platform `verify` jobs — the ones that already download the shipped
`libgoldberry` and have no C toolchain. No window, no compositor, no Xvfb.

## Alternatives considered

- **Exact match, three sets of goldens.** Rejected above: three references, three
  rot rates, and a contributor on a fourth CPU with no way to run the suite.
- **A perceptual metric (SSIM or similar).** More faithful to "does this look the
  same" and much harder to explain when it fails. Two integers a reader can check
  by hand beat a score they have to trust.
- **Assert on sampled pixels instead of whole images.** That is what the existing
  rendering tests already do, and it is why a golden was still needed: sampling
  asserts what you thought to look at.
- **Store goldens as raw pixels.** Simpler still, and unreadable. The reason to
  commit a PNG is that a reviewer can open the diff in the pull request.

## Consequences

- **`-Dgoldberry.golden.update=true` rewrites every golden.** It is a review step,
  not a fix: the image diff in the pull request is the only thing that says
  whether the change was intended. The flag had to be forwarded explicitly in
  `core/build.gradle`, because a system property otherwise reaches the Gradle
  daemon and stops there — the same trap `example/build.gradle` already documents,
  and it wasted the first attempt at generating these.
- **Failures upload three images**: expected, actual, and a diff that scales the
  delta into magenta so a two-level difference is visible at all. "It differs" is
  not actionable.
- **The goldens are small and few.** Six scenes at up to 240×80. They are meant to
  be readable in a review, not to be a screenshot gallery; a golden nobody looks at
  is a golden that gets `--update`d past.
- **This is the first cross-platform claim the suite can actually check.** If AVX2
  and NEON ever disagree by more than a rounding step, the Linux, macOS and
  Windows legs are where it surfaces — and the tolerances above are the statement
  of how much disagreement is acceptable.
- **Text is in a golden, at 1.5× scale.** Every HiDPI bug hides at 100%, and a
  scale applied twice or not at all shows in glyph positions first. It depends on
  the embedded Inter being pinned by checksum
  ([ADR-0033](0033-assets-are-fetched-and-compiled-not-committed.md)); nothing here
  reads a system font, which is what makes the image reproducible at all.
