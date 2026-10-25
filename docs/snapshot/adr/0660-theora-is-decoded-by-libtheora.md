# ADR-0660: Theora is decoded by libtheora

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0651 (the reference codecs bound directly), ADR-0658 (the largest picture decoded is 8K), ADR-0656 (damaged media is fuzzed)

## Context

The plan that replaced FFmpeg left Theora out "unless requested" (its
phase 5). It has been: `.ogv` is still what a lot of archived and
educational video is, Wikimedia Commons among them, and an Ogg file of Theora
and Vorbis opened as a track of `UNKNOWN` beside a Vorbis track that plays.
Theora is royalty-free (On2 gave up its VP3 patents to Xiph in 2002), so it is
inside the codec policy. The Ogg reader already named a `\x80theora` stream
and handed none of its packets over; Matroska's `V_THEORA` was unknown.

Three things about the format shape the work. Its granule position is not a
count: it is the number of the last keyframe shifted up by a per-stream amount,
with the frames since it below, and bitstream 3.2.0 counted frames from zero
where 3.2.1 counts from one. Its pictures are coded bottom up, with the
displayed region's offset measured from the bottom of a frame whose sides are
multiples of 16. And it has 4:2:2 and 4:4:4 as well as 4:2:0.

## Decision

**libtheora 1.2.0 (BSD-3) decodes Theora, inside `libgoldberry-media`**, beside
the other reference decoders, and its own API is exported: `th_info_init`,
`th_info_clear`, `th_comment_init`, `th_comment_clear`, `th_decode_headerin`,
`th_decode_alloc`, `th_setup_free`, `th_decode_packetin`,
`th_decode_ycbcr_out`, `th_decode_free` and `th_version_string`. The native ABI
is 3.

- **The build compiles libtheoradec's sources directly** (FetchContent at the
  `v1.2.0` tag, as libyuv is), not through its autotools build: a clone needs
  autoconf, automake and libtool to bootstrap, three more tools on every
  runner, for eleven C files. The x86-64 MMX/SSE2 paths are GCC inline
  assembly and are built; on arm64 its assembly needs a Perl conversion, so the
  C paths are built there. The encoder and the pre-1.0 `theora_` API are not.
  About 48 KB of the 6 MB gate on linux-x64.
- **The decoder** (`decoder.theora`, provider `libtheora`) takes the three
  headers Xiph-laced, the record Matroska's `CodecPrivate` holds and the Ogg
  reader laces, as for Vorbis. It hands over the picture region, not the coded
  frame: libtheora answers top-down planes at a positive stride with the
  region's offset counted from the top, so the crop is a slice. 4:2:0 is lent;
  4:2:2 and 4:4:4 are subsampled to I420 by libyuv. The colour is BT.601,
  limited range: Theora's two colour spaces differ in primaries only. An empty
  packet (`TH_DUPFRAME`) repeats the last picture at its own time.
  `PictureLimit` is held on the coded size before `th_decode_alloc`.
- **The Ogg mapping** times a Theora stream in frames (time base
  `fps_den/fps_num`) and reads granule positions as FFmpeg's `oggparsetheora.c`
  does, the 3.2.0 offset included, so the packet list is `ffprobe`'s; a frame
  is a keyframe when its second bit is clear. A seek follows the Theora stream
  when one is read: the page the target lands on names the keyframe its
  frames refer back to, a second bisection finds the page before it, and the
  frames ahead of the keyframe are passed over.
- **Matroska** maps `V_THEORA`, with the pixel format and, without a
  `DefaultDuration`, the frame rate read from the laced headers.

## Alternatives

- **libtheora's own autotools build as an ExternalProject**, as the other Xiph
  libraries' CMake builds are. Rejected for the tools it needs and the
  MinGW leg it would complicate; its sources list is short and stable.
- **FFmpeg's `vp3` decoder**, which is bit-exact with libtheora. It is LGPL and
  the reason for this engine was to have none.
- **Leaving Theora to an application's provider.** Nothing would provide it:
  no system decoder on macOS or Windows decodes Theora.

## Consequences

- `.ogv` files play, with their Vorbis or Opus, and Theora in Matroska does
  too. Every picture of the fixtures matches FFmpeg's `framemd5` byte for byte,
  and every packet `ffprobe`'s list.
- A library of ABI 2 is refused by these bindings; the natives are rebuilt.
- arm64 decodes Theora on libtheora's C paths, slower than the x86 ones and
  still far inside real time for the sizes Theora is met at.
- Four fixtures (`clip-theora.ogv`, `clip-theora.mkv`, `clip-theora-444.ogv`,
  `clip-theora-422.ogv`) join the container, decoder, playback and fuzz tests.
