# 471. AVI is demuxed, and a container with no demuxer is named

Date: 2026-09-24

## Status

Accepted. Follows S7 of `docs/goldberry-media.md` §7 ("an unsupported file
errors, naming what it could not play").

## Context

A user opened an Xvid and AC-3 AVI, the most common shape of a film rip, and
got `not playable media: Invalid data found when processing input`. The file is
not damaged. The build has no AVI demuxer, so FFmpeg's probe had nothing that
recognised it, and `avformat_open_input` failed as it does for random bytes.

The codec policy is unchanged by this: MPEG-4 Part 2 and AC-3 are on the "not
built, by decision" list, so the file cannot play either way. What was wrong is
the answer. An H.264 and AAC MP4 opens, lists its tracks, and says
`no decoder for h264, aac`. An AVI of the same kind said the file was broken.

The same holds for every container the build does not demux, and MPEG-TS is
not demuxed by decision. A transport stream, an FLV, or an ASF file all read as
invalid data.

## Decision

**The AVI demuxer is built.** AVI is a container with no patents of its own,
and FFmpeg's demuxer adds 17 KB to `libavformat`. An AVI now opens, and S7
applies to it as to MP4: an Xvid and AC-3 rip reports
`no decoder for mpeg4, ac3`, and an AVI of codecs the build has (MP3, PCM, VP8)
plays.

**A container with no demuxer is recognised by its first bytes, and named.**
`IoCallbacks` keeps the first kilobyte of the source as FFmpeg reads it, from
position 0 and for live sources too. When `avformat_open_input` fails with
`AVERROR_INVALIDDATA`, `ContainerSniffer` matches that head against a short
list of well-known signatures: MPEG-TS and M2TS (sync bytes a packet apart),
MPEG program and elementary streams, raw H.264, FLV, ASF, RealMedia, MXF, IVF,
AIFF, CAF, AMR, WavPack, Monkey's Audio, Musepack, DSF, ADTS AAC and AC-3. The
error is then `MediaError.UnsupportedContainer`: `no demuxer for MPEG-TS in this
build`.

A signature is reported only when **this build has no demuxer for it**, read
from the loaded libraries (`FfmpegCapabilities.demuxers`). A container the
build does read, and that failed anyway, is damaged, and stays `InvalidData`.
So does a head that matches nothing.

## Alternatives considered

- **Only the better message.** AVI would then say `no demuxer for AVI`. That is
  true, but less useful than naming the codecs, and it hides the AVIs this
  build could play.
- **Build every demuxer, so FFmpeg names the format itself.** MPEG-TS is
  excluded by decision, and the demuxer set is part of the size budget and the
  attack surface. A signature table costs neither.
- **Ask FFmpeg's `av_probe_input_format` with every demuxer compiled in.**
  This is the same as the previous option: the probe only knows demuxers that
  exist.

## Consequences

- `MediaError` gains `UnsupportedContainer(format)`. Code that switches over
  `MediaError` exhaustively needs a case; `MediaErrorTest` holds that switch.
- The superbuild's demuxer list is `matroska,mov,avi,ogg,flac,mp3,wav,srt,webvtt,ass`.
- The showcase's Video file dialog offers `.avi`.
- Test fixtures: `clip-xvid-ac3.avi`, `tone-mp3.avi`, `clip-mpeg2.ts` and
  `clip-flv1.flv`.
