# ADR-0653: CPU present interpolates chroma as the GPU does

- **Status:** Accepted
- **Date:** 2026-10-24
- **Relates to:** ADR-0651 (media decodes with the reference codecs bound directly, and FFmpeg leaves), ADR-0483 and ADR-0484 (GPU present)

## Context

Video stores chroma once per 2×2 block of luma, and a present path has to fill
in the other three pixels. GPU present samples the chroma planes with a linear
sampler, each sample at the centre of its block: every pixel takes nine
sixteenths of the nearest sample and the rest from its neighbours. swscale did
the same on the CPU, so the two paths drew the same picture, and
`VideoOnGpuTest` held GPU present to CPU present's goldens within 2 levels.

When libyuv replaced swscale (ADR-0651), the pinned libyuv was its `stable`
branch, from April 2021, which has no interpolating conversion: each chroma
sample was repeated over its block. The paths parted at every chroma edge, by
146 to 174 levels on the test clips (PSNR 24.7 dB), and the GPU lane went red.
Natural video has few hard chroma edges, so the difference is seldom visible,
but a picture should not depend on which backend draws it, and the goldens
should hold what a user sees on either.

## Decision

**CPU present interpolates chroma bilinearly from centred samples**, as GPU
present does:

- libyuv is pinned to main (version 1974) for its `…MatrixFilter` conversions
  with `kFilterBilinear`, whose kernel is 9:3:3:1 with the edges repeated: the
  sampler's own. NV12 has no filtered conversion, so its chroma is split into
  two planes (`SplitUVPlane`) and converted as I420.
- libyuv is built with `LIBYUV_UNLIMITED_DATA`. By default it caps the blue
  coefficient at 2.0 so its SIMD can multiply in signed bytes, and the exact
  values of limited-range BT.709 (2.112) and BT.2020 (2.142) are cut short: a
  saturated blue lands up to 12 levels below the GPU's. The option multiplies
  unsigned, and is the configuration libyuv's own tests run.
- The unused conversions and the scaler leave the export list, and the native
  ABI of `libgoldberry-media` is 2. `kFilterBilinear` is read from the layout
  file like every other upstream constant.

**Both present paths are held to one Java reference**, `YuvReference` in
`:media`'s tests: the matrix and range a picture is tagged with, chroma
blended from centred samples. A picture off by more than rounding is put down,
in the failure message, to the misreading that would draw it — Cb and Cr
swapped, red and blue swapped, the wrong matrix or range, chroma repeated or
sited left — by drawing each of them and naming the closest (`YuvDiagnosis`).

## Alternatives

- **Loosening the parity test** to what repeating against interpolating can
  explain. Cheaper, and the CPU path's picture would have stayed visibly
  blockier at chroma edges than the GPU's, and the goldens would describe a
  picture no GPU user sees.
- **Making the GPU repeat chroma** to match. A worse picture for the path most
  users take, to fit the one fewer do.
- **Upsampling the chroma to 4:4:4 with `ScalePlane` first**, on the old pin.
  A second pass and a 4:4:4 buffer per picture, for what one call does now.

## Consequences

- CPU and GPU present agree within 2 levels a channel on every fixture (3 for
  10-bit, whose libyuv path truncates its last shift), PSNR 54.9 dB where it
  was 24.7.
- Every decoded golden, the three widget goldens and both `media-player`
  guide pictures are retaken.
- With a GPU view attached nothing changes in cost: the player hands the
  planes over and libyuv does not run. On the CPU path the filtered
  conversion costs a little more than the repeating one.
- A future libyuv pin moves with main, not `stable`; the layout probe and the
  reference tests are what an update is checked against.
