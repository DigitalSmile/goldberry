# ADR-0661: A user-installed ffmpeg is the last rung for the patent-pool codecs

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0472 (the platform decoders bind the system frameworks), ADR-0489 (GStreamer and Media Foundation), ADR-0654 (hardware decoding reaches the decoders), ADR-0651 (the reference codecs bound directly)

## Context

`ffmpeg-out.md` §5 left one escape hatch for the codecs the published natives
deliberately do not build: a provider that runs an `ffmpeg` the user installed
and reads raw frames back over a pipe, with nothing linked, built or shipped.
The system decoders cover H.264, HEVC, AAC, AC-3 and E-AC-3 where the system
has them; a Linux machine without gst-libav, Windows while Media Foundation is
held back, and MPEG-2, MPEG-4 Part 2, ProRes or DTS anywhere had nothing.

A program at the other end of two pipes needs a framing both ways. Raw
elementary streams cannot carry a configuration record or a time, and raw
frames out of `ffmpeg` carry no time either.

## Decision

**`dev.goldberry.media.ffmpegcli.FfmpegCliProvider`, exported, registered with
`ServiceLoader` and off unless `-Dgoldberry.media.ffmpeg` names a program** (or
an application hands one to `FfmpegCliProvider.of(Path)`), as Media
Foundation is off without its property.

- **What it claims:** a codec that is not `royaltyFree()` and that Matroska
  names (H.264, HEVC, MPEG-2, MPEG-4 Part 2, AAC, AC-3, E-AC-3), and a few
  `UNKNOWN` Matroska tracks by their `CodecID` (ProRes, Theora, FFV1, MPEG-1,
  MP1/MP2, DTS, TrueHD, MLP, ALAC, TTA, WavPack), each only where the
  program's `-codecs` listing, asked once per program, flags it `D`. Never a
  royalty-free codec: the built-in decoders are tested, and preferred. Its
  priority is −100, below the system decoders' 0.
- **Matroska both ways.** The track goes in as a Matroska stream written by a
  new `container.matroska.MatroskaWriter` (unknown-size Segment and Clusters,
  a microsecond `TimestampScale`, the request's extradata as `CodecPrivate`),
  shifted by 1000 s so that priming before zero survives Matroska's unsigned
  Cluster times. It comes back as Matroska, read by `MatroskaContainer` over
  the pipe: NV12 as `V_UNCOMPRESSED` (now read, as `rawvideo`, with its
  `UncompressedFourCC`), P010 for a track whose pixel format has more than
  8 bits as a `V_MS/VFW/FOURCC` track (`-allow_raw_vfw 1`, since Matroska
  names no 10-bit raw layout; the bytes are known by their size), sound as
  `A_PCM/FLOAT/IEEE`. The command line:
  `ffmpeg -hide_banner -nostdin -nostats -loglevel error [-hwaccel auto]
  -probesize 32 -analyzeduration 0 -fpsprobesize 0 -f matroska -i pipe:0
  -map 0:0 -copyts -map_metadata -1 -map_chapters -1
  (-fps_mode passthrough -c:v rawvideo -pix_fmt nv12|p010le [-allow_raw_vfw 1]
  | -c:a pcm_f32le) -cluster_size_limit 1 -flush_packets 1 -f matroska pipe:1`.
  `-hwaccel auto` unless the request is `softwareOnly()`.
- **Times are put back.** `ffmpeg`'s Matroska output is stamped in
  milliseconds whatever goes in, so each frame takes the exact time of the
  packet that went in within a millisecond of it, and its own time otherwise.
- **Colour** from the output track's `Colour` element (now read:
  `MatroskaContainer.colour`), else the stream's parameter sets, else the
  built-in default.
- **Threads and failure.** A writer, a reader and an error-output thread per
  run, platform daemons (a virtual thread blocked on a pipe holds its carrier
  anyway). Queues of 8 packets in and 4 pictures or 16 runs of samples out;
  `send` refuses when the first is full and `receive` waits. A flush kills
  the program, and the next packet starts another. A status other than 0, an
  output that cannot be read, or an end with nothing decoded is thrown with
  the last lines of the error output, and the Engine walks its ladder.

## Alternatives

- **Raw streams both ways** (`-f h264 pipe:0`, `-f rawvideo pipe:1`). No times
  out at all, no configuration records in, and a parser per codec: Annex B
  for H.264 and HEVC, ADTS for AAC.
- **NUT or MPEG-TS out.** `ffmpeg` writes both with raw video, but there is no
  Java reader for either, and Matroska's is here and tested.
- **Probing as `ffmpeg` does by default.** It held every picture of a
  one-second clip until the end: twenty frames for a frame rate, and the
  muxer's 32 KB Clusters over a pipe. Off: the header describes the track.
- **One `ffmpeg` kept across a seek**, told of the discontinuity. The CLI has
  no flush: killing it is the only seek it knows. A seek costs a start, tens
  of milliseconds, more with a hardware decoder's.

## Consequences

- With `-Dgoldberry.media.ffmpeg=/usr/bin/ffmpeg` an application plays H.264,
  HEVC, AAC, AC-3 and E-AC-3 on any desktop that has `ffmpeg`, and the codecs
  above besides. The fixtures' pictures match `ffmpeg`'s `framemd5` byte for
  byte, NV12 and P010, with and without hardware, and their times exactly.
- The provider adds no native code and no metadata to the native image; it
  needs `ProcessBuilder` and a program on the user's machine.
- About twelve packets go in before the first H.264 picture comes out (frame
  threads and reordering); a seek restarts the program.
- Its tests skip where no `ffmpeg` is on the `PATH` or named by the property,
  and never fail for want of one: no workflow installs it, and the provider is
  the user's to turn on.
- Found on the way, and left open: the Engine's audio thread walks the ladder
  for a failure on a packet but not for one while it drains at the end. A
  short audio track whose every packet was sent before `ffmpeg` died fails
  the playback rather than moving on; the video thread falls back there and
  decodes the tail again from its keyframe.
