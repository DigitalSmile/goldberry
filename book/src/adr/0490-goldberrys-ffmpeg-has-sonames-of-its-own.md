# 490. Goldberry's FFmpeg has sonames of its own

Date: 2026-09-28

## Status

Accepted. Changes the file names of
[ADR-0460](0460-media-is-ffmpeg-driven-from-java-not-libvlc.md)'s natives, and
what `docs/goldberry-media.md` §2 asks of a user who replaces them.

## Context

The five FFmpeg libraries were named as FFmpeg names them: `libavcodec.so.62`
on Linux, `libavcodec.62.dylib` on macOS, `avcodec-62.dll` on Windows. The
loader opens them in dependency order and relies on the dynamic linker to
satisfy each one's dependencies from those already loaded. That comment in
`FfmpegLibrary` states it outright: glibc matches an already-loaded soname,
and Windows an already-loaded module name.

A distribution's FFmpeg of the same major answers to the same names. Ubuntu
26.04's FFmpeg 8 is `libavcodec.so.62` too. Any process that loads both gets
one of them twice:

- **Ours first**, the case the GStreamer providers
  ([ADR-0489](0489-linux-and-windows-platform-decoders-are-gstreamer-and-media-foundation.md))
  found. GStreamer's `libgstlibav.so` needs `libavcodec.so.62`, and glibc
  satisfied it with ours, which decodes VP8, VP9 and AV1 and nothing else. So
  `avdec_h264` could not be created. It was created when GStreamer loaded
  first, and not when our FFmpeg had loaded first; one run of each showed it.
  A web view plays media through GStreamer too, so a Goldberry application
  with the web view and `:media` was exposed.
- **The system's first.** Our `libavformat` then links against the system's
  `libavcodec`: another build, other options, and a layout the probe never
  measured.

## Decision

**FFmpeg is configured with `--build-suffix=-goldberry`.** Every library's
file name, soname and dependencies carry the suffix: `libavcodec-goldberry.so.62`,
`libavcodec-goldberry.62.dylib`, `avcodec-goldberry-62.dll`. No other FFmpeg
has these names, so neither can stand in for the other.

- **Two copies of the suffix,** each checked against the other.
  `GOLDBERRY_FFMPEG_BUILD_SUFFIX` in the superbuild sets the configure line.
  `FfmpegPlatform.BUILD_SUFFIX` is the one the loader looks for.
  `FfmpegSuperbuildTest` fails if they differ, if the suffix is empty, or if
  the configure line stops using it.
- **`package.cmake` names the libraries with the suffix.** It also empties its
  output directory of libraries first: the natives jar packs the whole
  directory, and the libraries named before the suffix were still there.
- **The NOTICE tells a user who replaces the libraries** (the LGPL's relinking
  promise) to configure theirs with the same suffix. The configure line it
  quotes includes it.

## Consequences

- Checked on linux-x64. Every soname and `NEEDED` entry carries the suffix, and
  the unsuffixed files are gone from the output. The size is unchanged at
  6708 KB. With our FFmpeg loaded first, GStreamer's `avdec_h264` and
  `avdec_h265` now decode in the same process, which ADR-0489's tests depend
  on.
- `:media:check` with FFmpeg required passes: 473 tests.
  `FfmpegPlatformTest` names the suffixed files on all three systems.
- macOS and Windows take the same configure option. Their names follow
  FFmpeg's own rules for `--build-suffix` and were not built here. The macOS
  install names become `@loader_path/libavcodec-goldberry.62.dylib`.
- A replacement FFmpeg built without the suffix is refused as missing. The
  error names the file the loader looked for, so it says what to build.
