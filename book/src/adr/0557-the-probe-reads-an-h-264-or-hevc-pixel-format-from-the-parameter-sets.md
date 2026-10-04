# ADR-0557: The probe reads an H.264 or HEVC pixel format from the parameter sets

- **Status:** Accepted
- **Date:** 2026-10-04
- **Relates to:** the Gwent clone's Goldberry issue GB-002,
  [ADR-0472](0472-the-platform-decoders-bind-the-system-frameworks.md)

## Context

`TrackParams.Video.pixelFormat()` is `AVCodecParameters.format`, which FFmpeg
fills in only by parsing or decoding the stream. The published natives build
neither an H.264 nor an HEVC decoder, and no parser for either, by the codec
policy in `goldberry-media.md`. So an H.264 track in MP4 reported no pixel format,
where `ffprobe` says `yuv420p`. Building the `h264` parser would have answered it,
but it is code for a patent-pool codec in the published natives, which the
policy rules out.

The platform decoders already read what they need from the track's decoder
configuration record (`avcC`, `hvcC`): `ParameterSets` parses the first SPS for
the bit depth and the chroma format.

## Decision

**The probe names the format from the SPS when FFmpeg names none.**
`ParameterSets.Shape.pixelFormat()` spells it as FFmpeg does: `gray`, `yuv420p`,
`yuv422p` or `yuv444p`, with `<depth>le` after it for 9, 10, 12, 14 and 16 bits.
`Demuxer` asks it only for H.264 and HEVC, and only when FFmpeg has no name.

A full-range stream is named as a limited one: FFmpeg's `yuvj` names are
deprecated, and the range is a property of each frame.

**The bitstream package moves out of `platform`**, to `dev.goldberry.media.bitstream`
(still not exported). It is pure Java with no system code in it, and `ffi`
should not depend on `platform`.

## Consequences

- `clip-h264-aac.mp4` reports `yuv420p`, as `ffprobe` does. The HEVC fixtures'
  records read `yuv420p` and `yuv420p10le`.
- A codec the build neither decodes nor parses, other than these two, still
  reports no pixel format. The `pixelFormat` documentation says so.
- No native code changes, and nothing patented is added to the natives.
