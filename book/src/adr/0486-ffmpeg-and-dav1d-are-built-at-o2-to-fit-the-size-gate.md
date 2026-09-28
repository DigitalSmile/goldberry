# 486. FFmpeg and dav1d are built at -O2, to fit the size gate

Date: 2026-09-27

## Status

Accepted. Closes the linux-x64 half of `docs/media-plan.md` phase 1's "Linux and
Windows" row: the media superbuild is now built and measured on a Linux host.
It amends the build of
[ADR-0460](0460-media-is-ffmpeg-driven-from-java-not-libvlc.md)'s superbuild and
leaves the configure line of `docs/goldberry-media.md` §2 unchanged.

## Context

The size gate of `docs/goldberry-media.md` §2 was set and first passed on
macos-aarch64: 4989 KB against a 6 MB target and a 7 MB failure. The first
linux-x64 build, with the same pins and the same configure line, failed it:

| library | linux-x64, configure's `-O3` |
|---|---|
| libavutil | 1002 KB |
| libswresample | 174 KB |
| libswscale | 2026 KB |
| libavcodec, dav1d inside | 5163 KB |
| libavformat | 612 KB |
| **total** | **8980 KB** |

The gate's message blames something the configure line did not mean to enable.
Nothing was. `config.h` enabled exactly the demuxers, decoders, parsers and
filters the line lists, with hardware decode off (ADR-0470). The growth was
spread over FFmpeg's C code: swscale's `output.c` and `swscale_unscaled.c`
templates, VP9's `inter_pred` and reconstruction, and the tx transforms in
avutil. dav1d was about 2 MB of the 5163 KB, about 870 KB of it C and the
rest x86 assembly, SSE2 up to AVX-512, that arm64 does not have.

FFmpeg's `configure` compiles at `-O3`, and meson's `release` buildtype does
the same for dav1d. At `-O3`, GCC 15 unrolls and vectorises those templates far
more than Apple's clang did on the Mac. Each change below was measured by
rebuilding the same pins and stripping the result the way `package.cmake` does:

| build | total |
|---|---|
| `-O3`, as configure chooses | 8976 KB |
| FFmpeg `--optflags=-O2` | 6952 KB |
| … and dav1d `-Doptimization=2` | **6696 KB** |
| … and `--enable-lto` | 6608 KB |

The build through Gradle then gave 6708 KB. The last 12 KB is the install
prefix and the configure line, which end up as strings in the libraries.

## Decision

**FFmpeg is configured with `--optflags=-O2`, and dav1d with
`-Doptimization=2`**, on every target. dav1d keeps `--buildtype=release`,
because meson ties `NDEBUG` and `trim_dsp` to the buildtype, not to the
optimisation level. Both were checked in the result: `-DNDEBUG` on the compile
lines and `TRIM_DSP_FUNCTIONS 1` in `config.h`.

The flag is part of `GOLDBERRY_FFMPEG_CONFIGURE`, so `ffmpeg-NOTICE.txt` quotes
it with the rest of the configure line, as the LGPL's relinking promise needs.
`FfmpegSuperbuildTest` reads both flags out of `CMakeLists.txt`, and fails on
every machine if they are removed. The gate itself fails only on a machine that
builds FFmpeg, and only after several minutes.

### Speed

Most of FFmpeg's decoding runs in hand-written assembly, which `-O` does not
touch. To check that `-O2` costs no speed, a C harness decoded 3 s clips of 4K60
through a custom `AVIOContext`, as `MediaIO` does, against both builds. It
converted each picture with `sws_scale` the way `VideoConverter` does, and ran
each case three times on an 8-core linux-x64 with GCC 15.2:

| case | `-O3` | `-O2` |
|---|---|---|
| VP9 8-bit, one thread, decode | 60.4–60.7 fps | 60.7–62.1 fps |
| AV1 10-bit (dav1d), one thread, decode | 54.4–56.4 fps | 56.2–57.6 fps |
| VP9 8-bit → BGRA (CPU present) | 5.68–6.08 ms a picture | 5.71–5.78 ms |
| VP9 10-bit → BGRA | 14.5–17.1 ms | 13.8–14.5 ms |
| AV1 10-bit → BGRA | 13.0–13.3 ms | 13.1–13.6 ms |
| VP9 10-bit → P010 | 8.1–8.4 ms | 9.9–10.2 ms |
| Opus, decode | 31.8–32.6 k packets/s | 30.5–31.6 k packets/s |

Only the conversion from I010 to P010 is slower. It is swscale's C repacking
of planar 10-bit into semi-planar 10-bit, which GCC vectorises only at `-O3`.
Nothing on the playback path does that conversion. `FfmpegDecoder` lends I010
without a copy, the GPU uploads I010 planes, and hardware decode copies back
P010 straight from the device (ADR-0470, ADR-0483).

### On the other targets

- **Windows:** FFmpeg's MSVC toolchain already compiles at `-O2`, since `cl`
  has no `-O3`. The flag changes nothing there, and dav1d's `-O2` is the only
  difference.
- **macOS:** the flag applies there too. One set of flags for every target
  matters more here than 20 KB on the Mac. The macOS figure in
  `docs/media-plan.md` is from `-O3` until the Mac or CI measures it again. It
  can only shrink.

## Consequences

- linux-x64 builds 6708 KB, under the 7 MB gate but over the 6 MB target. The
  target remains a goal, and the gate is what fails a build. What is left over
  the target on x64 is mostly dav1d's assembly. `meson_options.txt` has no option
  to drop AVX-512, only `enable_asm`, which would drop all of it.
- `-Pgoldberry.media.required=true` `:media:check` against the linux-x64 build:
  460 tests, none failing. The 10 skipped are the VAAPI tests, and hardware
  decode is off on Linux (ADR-0470).
- LTO was measured and not kept: 88 KB for a slower build, and one more
  toolchain requirement on every runner.
- A JVM warning seen in this run, "You have loaded library libavutil.so.60 which
  might have disabled stack guard", is not about the shipped libraries. All five
  have a non-executable `GNU_STACK` and load without it.
  `FfmpegLibrariesTest` writes a text file under that name to test a failed
  load. HotSpot reads the ELF stack note before `dlopen`, finds none in text,
  and warns.
- linux-aarch64 has still not been built. Its dav1d assembly is the arm64 kind,
  so it should land nearer macOS than linux-x64.
