# ADR-0659: HDR and wide-gamut video is tone-mapped to SDR

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0653 (CPU present interpolates chroma as the GPU does), ADR-0651 (the reference codecs bound directly), ADR-0483 and ADR-0484 (GPU present)

## Context

Phase 5 of `ffmpeg-out.md` asks for HDR: P010, PQ and HLG tone-mapped. A
picture carried its matrix and range and nothing else, so an HDR10 file played
as SDR: PQ codes shown as if they were gamma, BT.2020 primaries shown as
BT.709's. Highlights washed out and colours dulled, on both present paths.

What a picture's colour means is signalled in three places, each in ITU-T
H.273's numbers: the codec (AV1's sequence header, H.264's and HEVC's VUI),
the container (Matroska's `Colour`, MP4's `colr`), and, for the light levels,
metadata (AV1's metadata OBUs, HEVC's SEI as GStreamer's caps carry it,
Matroska's `MasteringMetadata` and `MaxCLL`, MP4's `mdcv` and `clli`). VP9
signals no transfer function at all: only its container can. HEVC's VUI was
not read: `ParameterSets` stopped after the reorder depth.

The window the UI is drawn into is SDR, 8 bits a channel; an HDR display path
through SDL's GPU swapchain is a separate question.

## Decision

**A picture carries a `ColorDescription`, and both present paths map anything
but SDR in BT.709 to SDR in BT.709, by one specification.**

- `codec.ColorDescription`: primaries (BT.709, BT.2020, Display P3, or
  unspecified; BT.601's are drawn as BT.709's), transfer function (SDR, PQ,
  HLG, or unspecified) and the mastering and content peaks, from H.273 codes.
  `TrackParams.Video.colour()` holds the container's word, `VideoFrame.colour()`
  the decoder's, and the video thread completes the frame's from the track's,
  part by part. `VideoPlanes.colour()` hands it to a view. Unspecified is drawn
  as SDR BT.709, which is what an untagged picture always was.
- Read from: dav1d's sequence header and its picture's content light and
  mastering display; Matroska's `Colour`; MP4's `colr` (`nclx`, `nclc`), else
  `vpcC`, and `mdcv`, `clli`; H.264's VUI and HEVC's, which is now read by a
  full HEVC parameter set reader (`bitstream.hevc`, held to FFmpeg's
  `trace_headers` field by field); GStreamer's caps colorimetry through
  `gst_video_*_to_iso`, and its `mastering-display-info` and
  `content-light-level`. libvpx, VideoToolbox and Media Foundation report
  nothing more, and the track fills in.
- **The mapping** (`gpu.render.ToneMapping` for the shader, `picture.hdr.ToneCurve`
  for the CPU, the same four steps): R'G'B' to light by PQ's EOTF, HLG's
  inverse OETF and BT.2100's OOTF at 1000 nits, or BT.1886's gamma 2.4 for SDR;
  into BT.709's primaries through XYZ, below zero clipped; for HDR brighter than
  the target, BT.2390's EETF on the largest channel from the picture's peak
  (mastering, else MaxCLL, else 1000 nits; HLG 1000) down to 203 nits, BT.2408's
  reference white, the other two channels scaled alike so hue holds; then
  divided by 203 and raised to 1/2.4.
- **GPU present**: two more fragment shaders, `yuv2hdr` and `yuv3hdr`, with the
  mapping's uniform block; SDR BT.709 keeps the old shaders, byte for byte.
  The DXIL is signed: the Linux DXC here (1.9.2609) ships `libdxil.so`.
- **CPU present**: libyuv's conversions round to their fixed point first, half
  a percent short at white (1018 of 1023 for a 10-bit white), which PQ's
  highlights magnify into a dozen levels. So `goldberry_media_tone_map`, in
  `libgoldberry-media`'s own C, converts the planes itself in floats with the
  same centred bilinear chroma, and maps them through tables the Java side
  builds once a curve. Native ABI 3. A picture with alpha is drawn as SDR:
  tone-mapping premultiplied colour would darken its edges.

## Alternatives

- **An HDR swapchain** (SDL's `SDL_GPU_SWAPCHAINCOMPOSITION_HDR10_ST2084`).
  The UI would have to be composed in a wide space too, and most windows are
  on SDR displays. Tone-mapping first is what every player does on an SDR
  display; an HDR output path can come later beside it.
- **libyuv's AR30 conversions** for 10-bit, mapped afterwards. Measured: 5 to
  12 levels from the reference on a saturated test card, all of it libyuv's
  rounding. Rejected for the float pass.
- **A 3D LUT** sampled in the shader. Cheaper per pixel, but a texture per
  curve, interpolation error at the gamut edges, and nothing to hold it to but
  itself.
- **Hable's or Reinhard's curve.** BT.2390's EETF is the published reference,
  leaves everything below its knee as it is, and maps the stated peak exactly
  to white.

## Consequences

- An HDR10, HLG or BT.2020 SDR source plays with its highlights and colours
  where they belong on an SDR window. CPU present is within a level of the
  reference; GPU present within three (six on a tone-mapped picture, where
  PQ's curve is steeper) of CPU present, counted where they differ by more
  than 0.004 of white in light, since near black the encoding's slope turns a
  GPU sampler's coarse filter weights into many levels and a fraction of a nit.
- `VideoFrame` and `TrackParams.Video` gained a component each, with their old
  constructors kept; `VideoPlanes` a constructor and an accessor.
- `ParameterSets` now reads HEVC's VUI: its range and matrix go to the
  providers too.
- Three fixtures (`clip-av1-pq.mkv`, `clip-av1-pq.mp4`, `clip-vp9-hlg.webm`), an
  HDR10 HEVC one for the system decoders, and FFmpeg traces of the HEVC and
  H.264 fixtures for the parsers.
