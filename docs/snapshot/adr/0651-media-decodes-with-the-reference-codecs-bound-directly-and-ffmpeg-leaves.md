# ADR-0651: Media decodes with the reference codecs bound directly, and FFmpeg leaves

- **Status:** Accepted
- **Date:** 2026-10-24
- **Supersedes:** ADR-0460 (media is FFmpeg driven from Java, not libVLC), in its choice of engine; what it decided against libVLC stands

## Context

`goldberry-media` was built on FFmpeg: five LGPL shared libraries, bound by
hand, doing the demuxing, the decoding, the resampling and the colour
conversion, with the threads, the clock and the I/O in Java. Two things wore
it down.

The licence. FFmpeg is LGPL-2.1, and every other native Goldberry ships is Zlib,
MIT or BSD under an Apache project. Keeping FFmpeg honest meant five
replaceable shared objects with a build suffix of their own, a
`ffmpeg-sources` classifier carrying 170 MB of source beside every snapshot, a
publishing rule that refused a binary without it, a notice that explained
relinking, and a build-logic test for each of those. The section-4 obligation
followed the module everywhere, and it is the one thing in the repository
whose licence a downstream has to read before shipping.

The build. FFmpeg's configure runs under a POSIX shell with make, pkg-config,
nasm and meson for dav1d, on four runners, one of them Windows with MSVC under
MSYS2, where coreutils' `link.exe` shadows the linker and pkg-config splits
paths at a drive letter. The Media workflow was the one that failed for reasons
unrelated to the change being tested, and the FFmpeg build took the longest of
anything in CI.

What was used of FFmpeg was small: the Matroska, MP4 and Ogg demuxers, the VP8,
VP9, AV1 (dav1d), Opus, Vorbis, FLAC and MP3 decoders, `swr_convert` to
interleaved float and `sws_scale` to BGRA. The H.264, HEVC and AAC paths were
never FFmpeg's: they are the platform decoders behind the Decoder SPI
(ADR-0493).

## Decision

**FFmpeg leaves `goldberry-media`.** The engine becomes:

- **Demuxing in Java.** Matroska and WebM first, then MP4, Ogg and the raw
  audio streams, under `dev.goldberry.media.container`, driven through one
  `Container` interface. Each demuxer is held to `ffprobe -show_packets` of the
  test clips, packet for packet.
- **The reference decoders, bound directly.** dav1d, libvpx, libopus,
  libvorbis, libFLAC and minimp3, through FFM, each behind the existing Decoder
  SPI as a built-in provider that ranks after an application's own. The
  software decoders are bit-exact by specification; the AV1 decoder is held
  to FFmpeg's `framemd5` of the same clips.
- **One native library, `libgoldberry-media`.** The six decoders and libyuv
  linked statically into one shared object per target, exporting their own C
  APIs under a single explicit list, everything else local. Shipped as
  `goldberry-media`'s `natives-<target>` classifier.
- **Audio through SDL.** The sink is opened at the track's own rate and channel
  count; SDL's stream resamples to the device. What remains of `swresample` is
  a sample-format conversion to interleaved float, in Java.
- **Pictures through libyuv on the CPU**, and the GPU shader that already
  existed. Byte-exactness per platform is kept as a golden; where libyuv's SIMD
  rounds differently per architecture the goldens name the architecture, as
  ADR-0649 named the vector level.

Every upstream is BSD-2, BSD-3 or CC0. Nothing in the module is LGPL any more,
and the corresponding-source machinery goes with it.

### Why one library and not seven

The plan proposed building each upstream as its own shared object and binding
it by name. The FFmpeg era had already found the problem with that: a process
that also holds a distribution's copy of the same library, as every GStreamer
plugin path does, resolves a `DT_NEEDED` or a `dlopen` against whichever soname
loaded first, and the `-goldberry` build suffix existed to keep the two apart.
One library with one private name, and every upstream symbol local but the
exported list, settles it without a suffix on every upstream's build. It is
also one file to unpack, one layout probe, one size gate and one export check.

### Why the superbuild, not vcpkg

The §6 Phase 0 question. The superbuild needs CMake, Ninja, meson, make and
nasm, which the Media workflow already installed for FFmpeg; vcpkg would add a
bootstrap and a binary cache on four runners to save a `configure` that takes a
minute. The first build on this machine took four minutes with dav1d cached and
produced a 3.7 MB library against FFmpeg's 6.7 MB.

### Windows builds with MinGW-w64

libvpx's configure wants a POSIX shell and a GNU toolchain. Rather than drive
MSVC from under MSYS2 again, the Windows leg builds the whole library with
MSYS2's UCRT64 GCC, links its runtime support statically, and depends on the
universal CRT alone. FFM does not care which compiler built a DLL, and one
toolchain makes the Windows recipe read like the Linux one.

### What stays

The Decoder SPI, `MediaIO`, the Engine's threads and clock, the platform
decoders, the widgets, the probe's vocabulary (`CodecId`, `TrackParams`) and
the frame contract (`VideoFrame`, `AudioFrame`) are unchanged. FFmpeg's names
for codecs and formats stay as the vocabulary, because every application that
read a track's `codecName` was written in them.

Rejected:

- **Keeping FFmpeg and fixing the CI.** The licence obligations stay, and the
  build stays the slowest and the most fragile in the matrix.
- **GStreamer or the platform decoders for everything.** Neither is on every
  machine with the royalty-free codecs, and the published artifact has to play
  WebM without asking the user to install anything.
- **A Java decoder for every codec.** AV1 and VP9 in Java would be years of
  work for a tenth of dav1d's speed; the reference decoders are small, BSD and
  bit-exact.

## Consequences

- The build-logic classes `CorrespondingSource` and `UpstreamSource`, their
  tests, the `ffmpeg-sources` classifier and the FFmpeg notice are deleted.
  `gradle/libs.versions.toml` pins eight upstreams for the module instead of
  two.
- `dev.goldberry.media.ffi` is deleted, with its tests and benchmarks, and
  `FfmpegRequirement` is `MediaNativesRequirement`.
- Containers FFmpeg read and this engine does not — AVI, MPEG-TS, FLV, ASF —
  are refused by name as `UnsupportedContainer`. The test clips for ADR-0471
  keep that promise.
- The native ABI of `libgoldberry-media` is its own number, `goldberry_media_abi()`,
  separate from libgoldberry's.
- WAV and raw PCM, which FFmpeg demuxed and decoded for the test suite and the
  showcase's Java-decoder card, are a Java container and a Java decoder now;
  probing any source needs no native library at all.
- `HardwareDecoding` stays in the API and the built-in decoders ignore it:
  FFmpeg's hwaccels were the only reader, and the hardware path is the system
  providers' (`docs/ffmpeg-out.md` §5 is where it widens).
- A player's status names the decoder that plays — `dav1d`, `libvpx`, `opus`,
  `vorbis`, `flac`, `minimp3`, `pcm` — where it said `ffmpeg`.
- The guide's media page, `goldberry-media.md`, `content-widgets.md` and
  `ARCHITECTURE.md` §4 change as `docs/ffmpeg-out.md` §7 lists; the guide
  pieces are parked in `docs/snapshot/` until the release.
