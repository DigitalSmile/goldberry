# goldberry-media: FFmpeg-free engine — plan

Status: **in progress** since 2026-10-24; supersedes the FFmpeg-direct design in
`goldberry-media.md`. The decision is ADR-0651 (parked in `docs/snapshot/adr/`).
The log at the end says what is built and what is next.

## 1. Decision

Replace FFmpeg with direct FFM bindings to the reference codec libraries plus OS-provided hardware decoders. Demuxing, I/O, clocking and A/V sync live in Java. No LGPL/GPL natives, no FFmpeg configure step, ~4–5 MB of natives instead of ~15–25 MB.

Scope unchanged: royalty-free codecs built-in, network I/O on the Java side, hand-written FFM bindings, C/C++ natives only, `Decoder` SPI for everything else.

## 2. Target state

```
MediaView (widget)
   └── Player              Java: state machine, master clock, seek, buffering
        ├── Source          Java: SeekableByteChannel / HTTP range / custom
        ├── Demuxer         Java: Matroska/WebM, MP4/ISOBMFF, Ogg, (ADTS, raw)
        ├── VideoDecoder    SPI → dav1d | libvpx | OS HW | user-provided
        ├── AudioDecoder    SPI → opus | vorbis | flac | minimp3 | OS | user-provided
        ├── FrameSink       GPU: NV12/I420 → RGB shader on SDL_GPU
        │                   CPU: libyuv → BLImage (software backend)
        └── AudioSink       SDL3 SDL_AudioStream (resample + format convert)
```

Two threads per playing stream: demux+video-decode, audio-decode. Audio is the master clock (SDL queue position); video frames are presented on the UI thread via the existing animated-value / virtual-clock machinery so tests stay deterministic.

## 3. Native inventory

| Library  | Codec / role       | License | Lang    | Build  | Replaces in FFmpeg    |
|----------|--------------------|---------|---------|--------|-----------------------|
| dav1d    | AV1 decode         | BSD-2   | C + asm | meson  | libdav1d wrapper      |
| libvpx   | VP9 / VP8 decode   | BSD-3   | C + asm | configure | vp9 / vp8          |
| libopus  | Opus               | BSD-3   | C       | CMake  | libopus wrapper       |
| libogg + libvorbis | Vorbis   | BSD-3   | C       | CMake  | vorbis                |
| libFLAC  | FLAC               | BSD-3   | C       | CMake  | flac                  |
| minimp3  | MP3                | CC0     | C header | —     | mp3float              |
| libyuv   | CSC / scale (CPU path only) | BSD-3 | C++ | CMake | swscale             |

Not shipped: libavformat (Java demuxers), swresample (SDL_AudioStream), libaom (dav1d is better), libtheora (out of scope unless requested).

Build notes for `:natives`:
- dav1d via `ExternalProject_Add` with meson; `-Denable_tools=false -Denable_tests=false`. Needs nasm on x86.
- libvpx: `--disable-examples --disable-tools --disable-unit-tests --disable-vp8-encoder --disable-vp9-encoder --enable-vp9-highbitdepth`. On Windows build in MSYS2 or use the vcpkg port. Decode-only build is ~1.5 MB.
- All libraries built as shared objects with hidden visibility; one `goldberry_media` shim is **not** needed — bind each library's C API directly.
- Alternative to the hand superbuild: a vcpkg manifest with `dav1d libvpx opus libvorbis libflac libyuv`. Decide in Phase 0 after measuring build time on the 4-lane CI matrix.

## 4. Java side

### 4.1 Demuxers (`media.container`)
- `Matroska` — EBML reader, Segment/Tracks/Cluster/SimpleBlock/BlockGroup, Cues for seek, lacing. Covers `.webm` and `.mkv`.
- `Mp4` — ISOBMFF boxes: ftyp/moov/trak/stbl (stts, stsc, stsz, stco/co64, stss, ctts), fragmented MP4 (moof/traf/trun) for streaming, `av01`/`vp09`/`Opus`/`fLaC`/`mp4a` sample entries. Also used for HW path (`avc1`/`hvc1` extradata pass-through).
- `Ogg` — pages, packets, granulepos; Opus/Vorbis/FLAC-in-Ogg.
- `Adts` / raw frame streams for MP3/AAC passthrough to OS decoders.
  Each exposes `Track` metadata + `Packet(pts, dts, duration, keyframe, data)`. Seeking: index-based where present, bisection on keyframes otherwise.

### 4.2 Decoder SPI (`media.spi`)
```java
public interface VideoDecoder extends AutoCloseable {
  static Optional<VideoDecoder> open(VideoTrack t, DecoderHints h);   // via ServiceLoader
  void send(Packet p);                      // may throw NeedMoreOutput
  Optional<VideoFrame> receive();           // NV12/I420/P010 planes or GpuTexture handle
  void flush();
}
```
Same shape for `AudioDecoder` (PCM `MemorySegment` out). Providers are ordered by `DecoderHints.preferHardware` and track codec; built-in providers register with `@Service` on module descriptor, no reflection.

`VideoFrame` carries either CPU planes (zero-copy slices of the decoder's buffer, released on `close()`) or a GPU handle (D3D11 texture / CVPixelBuffer / VA surface) that the backend imports into SDL_GPU.

### 4.3 FFM bindings (`media.native.*`)
Hand-written, one class per library, `Linker.nativeLinker()` + `SymbolLookup.libraryLookup`. Layout agreement verified by the existing jextract layout-check in TESTING.md (jextract used only to generate test fixtures, never shipped code). Critical callbacks: dav1d picture allocator (`Dav1dPicAllocator`) to decode straight into `Arena`-owned buffers; libvpx uses `vpx_codec_set_frame_buffer_functions` for the same.

### 4.4 Sinks
- GPU: upload Y + UV planes as `R8` / `R8G8` textures, fullscreen quad with BT.601/709/2020 matrix selected from track colour metadata; HDR (P010 → PQ/HLG) is Phase 5.
- CPU (Blend2D backend): `libyuv` `I420ToARGB` / `NV12ToARGB` into `BLImage` with `BL_FORMAT_PRGB32`.
- Audio: `SDL_AudioStream` per track; resampling, channel mapping and S16/F32 conversion are SDL's. Volume/mute on the stream gain.

## 5. Hardware / patented codecs (optional providers)

| Platform | API                     | Codecs                   | Output import             |
|----------|-------------------------|--------------------------|---------------------------|
| Windows  | Media Foundation (MFT)  | H.264, HEVC, AV1, VP9, AAC | D3D11 texture → SDL_GPU |
| macOS    | VideoToolbox / AudioToolbox | H.264, HEVC, AV1 (M3+), AAC | CVPixelBuffer (IOSurface) → Metal |
| Linux    | VA-API (libva, MIT)     | H.264, HEVC, AV1, VP9    | DMA-BUF → Vulkan import   |

Each is a separate module (`goldberry-media-win`, `-mac`, `-linux`) so the core stays license-clean and tiny; the platform vendor covers codec royalties.

> **As built (before this plan):** the system decoders already exist, inside `goldberry-media` as `…media.platform` rather than three modules: VideoToolbox and AudioToolbox on macOS, GStreamer on Linux (which reaches VA-API and NVDEC through its own plugins) in place of a VA-API binding, and Media Foundation on Windows, held back behind `-Dgoldberry.media.mediaFoundation=true`. Every one copies back to system memory. What phase 4 has left: zero-copy import into SDL_GPU, Media Foundation on by default, and the `ffmpeg-cli` provider. These providers need bitstream repacking (Annex-B ↔ AVCC) which lives in Java (`media.bitstream`).

Vulkan Video (`VK_KHR_video_decode_{h264,h265,av1,vp9}`) as a single cross-platform HW backend: tracked, not planned for v1 — no macOS, and stateless API means Java must own SPS/PPS parsing and DPB management.

Escape hatch: `goldberry-media-ffmpeg-cli` — a provider that spawns a user-supplied `ffmpeg` binary and reads raw frames over a pipe. Nothing linked, nothing built, nothing shipped.

## 6. Phases

**Phase 0 — spike (1 week)**
- Build dav1d + libopus in `:natives` on all four CI lanes; measure build time and output size.
- Hand-bind `dav1d_open/send_data/get_picture`; decode a WebM AV1 clip demuxed in Java; dump I420 to a golden PNG.
- Go/no-go on vcpkg vs superbuild.

**Phase 1 — core pipeline**
- Matroska demuxer, AV1 + Opus decoders, `Player` state machine, audio master clock, GPU NV12/I420 sink, CPU libyuv sink.
- `MediaView` with play/pause/seek/volume, no UI chrome yet.
- Tests: demuxer goldens on sample files, byte-exact frame goldens per platform, virtual-clock sync tests.

**Phase 2 — codec breadth**
- libvpx (VP9/VP8), Vorbis, FLAC, minimp3. MP4 + Ogg demuxers (incl. fragmented MP4).
- `DecoderHints`, provider ordering, codec-not-found UX.

**Phase 3 — player UX**
- Classic controls (transport, timeline with chapters from Matroska/MP4 metadata, volume, speed, fullscreen, subtitles: WebVTT + SRT, Matroska text tracks), keyboard map, GDS theming, gallery example.
- Buffering model for `Source` over HTTP range requests; adaptive prefetch window.

**Phase 4 — hardware providers**
- Windows MFT (H.264/HEVC/AAC first), then VideoToolbox, then VA-API. Zero-copy GPU import per backend; CPU readback fallback.
- `goldberry-media-ffmpeg-cli` provider.

**Phase 5 — nice-to-have**
- HDR (P010, PQ/HLG tone-map shader), 10-bit AV1/VP9 end-to-end, Vulkan Video provider, Theora if anyone asks.

## 7. Removed / changed in existing docs

- `goldberry-media.md`: FFmpeg-direct sections (libavcodec bindings, custom AVIOContext, swscale/swresample) → replaced by §3–§5 above.
- `content-widgets.md` media section: engine = "reference decoders + OS HW via Decoder SPI".
- `ARCHITECTURE.md` Backend SPI: add optional `gpuVideoImport` capability (D3D11/Metal/Vulkan external texture import) next to `gpuSurface`/`Pty`/camera/audio.
- `:natives` superbuild: drop FFmpeg ExternalProject; add dav1d, libvpx, opus, ogg, vorbis, flac, libyuv.

## 8. Risks

- **libvpx build on Windows**: its configure script needs a POSIX shell. Mitigation: vcpkg port, or prebuilt via CI from an MSYS2 lane.
- **dav1d picture allocator misuse** = silent memory corruption. Mitigation: allocator fuzz test + ASan lane in CI for natives.
- **MP4 edge cases** (edit lists, negative CTS, multi-moov) are where FFmpeg's value sits. Mitigation: test corpus from the WebM/MP4 conformance sets; scope v1 to what the demuxer explicitly supports and fail loudly otherwise.
- **A/V sync drift with SDL audio queue latency**: measure `SDL_GetAudioStreamQueued` and compensate; fall back to a monotonic clock when no audio track.
- **HW provider zero-copy import** differs per backend and SDL_GPU version. Mitigation: readback fallback is always present; zero-copy is an optimisation gate, not a release gate.
## 9. Status log

Kept as the work lands. "Done" means built, tested and green on this machine
(linux-x64); the other three targets are CI's to confirm.

### Phase 0 — spike: done 2026-10-24

- The superbuild (`media/src/main/cmake`) replaces FFmpeg's with **one shared
  library, `libgoldberry-media`**, that links dav1d 1.5.4, libvpx v1.17.0, Opus
  v1.6.1, libogg 1.3.6, libvorbis 1.3.7, FLAC 1.5.0, libyuv (main, 1974) and
  minimp3 statically and exports their own C APIs: 144 symbols, listed in
  `exports/goldberry-media.symbols`, checked post-link by `check_exports.cmake`.
  One library rather than seven because the FFmpeg-era `-goldberry` soname
  suffix existed for a reason: a distribution's libdav1d or libvpx in the same
  process (GStreamer's plugins) must not be mistaken for ours, and one private
  name with everything local settles it. **3.7 MB stripped on linux-x64**,
  against 6.7 MB for FFmpeg's five; the gate is 6 MB. Needs libc and libm only.
  First build here: dav1d from cache, the rest about four minutes.
- The pins moved to `gradle/libs.versions.toml`; FFmpeg's and the LGPL
  machinery (`ffmpeg-sources` classifier, `CorrespondingSource`, the
  source-archive tasks) are gone from the build. The natives classifier is
  `natives-<target>`.
- `dev.goldberry.media.ffm`: `MediaNatives` loads and checks the library (ABI
  number, layout file, every struct, every constant), `NativeStructs` declares
  22 upstream structs by hand with both the LP64 and the LLP64 shapes, and
  `…ffm.calls` binds 75 functions in the `:natives` holder idiom. The layout
  probe `media_layout.c` writes `media-layout.properties` beside the library;
  its linux-x64 output is committed as a test fixture.
- `dev.goldberry.media.container`: the `Container` interface the engine will
  drive, `ByteReader` over `MediaIO`, EBML, and the **Matroska/WebM demuxer**
  (SimpleBlock and BlockGroup, the three lacings, unknown sizes, header
  stripping and zlib, BlockAdditions alpha, image attachments, Cues). Held to
  `ffprobe -show_packets` of eight fixtures packet for packet: stream, pts,
  size, keyframe, and duration where the container states one.
- `dev.goldberry.media.decoder.av1`: **dav1d behind the Decoder SPI**. All 25
  pictures of `clip-av1.mkv` match FFmpeg's `framemd5` of the same clip byte for
  byte. The go/no-go of §6 Phase 0 is go: the superbuild, not vcpkg; it needs
  no package manager on the runners and the same recipe on all four targets.

### Phase 1 — core pipeline: in progress

- [x] Opus decoder (`decoder.opus`): libopus, the `OpusHead` record, pre-skip,
      gain, the multistream decoder for a channel mapping; held to the sine
      and to Matroska's `DiscardPadding` through the packet duration
- [x] `engine.Decoders`: resolution over `Container` with the built-in
      providers after the application's
- [x] CPU conversion over libyuv (`picture.YuvConverter`), replacing swscale:
      the six matrices by `ColorMatrix` × range, I420/NV12/I010/P010/I420A,
      premultiplied through libyuv's attenuate; 16-bit strides in samples.
      libyuv's chroma upsampling was nearest-neighbour where swscale's was
      bilinear, so every BGRA golden of a decoded clip was retaken (and again
      once it interpolated; see below).
- [x] Audio conversion in Java (`audio.PcmConverter`), replacing swresample:
      every sample format to interleaved f32, the usual downmix, a linear
      resampler kept as the fallback for a sink that answered another rate.
- [x] `Playback`, both workers, `MediaProbe`, `MediaCapabilities`,
      `VideoAnimation` and `MediaPlayer` over `Container` and `engine.Decoders`;
      a probe needs no native library now. `HardwareDecoding` stays as an API
      and the built-in decoders ignore it.
- [x] `media.ffi` deleted, with its tests and benchmarks; `FfmpegRequirement` →
      `MediaNativesRequirement`; the Java WAV container and PCM decoder replace
      what the test suite had from FFmpeg
- [x] CI: `media.yml` builds the new library on the four lanes (the Linux
      ones in the manylinux_2_28 container, Windows with MinGW-w64 from MSYS2's
      UCRT64) and tests `:media:check` against the artifact on plain runners;
      `publish.yml` attaches `natives-<target>`. **Written, not yet run on the
      runners.**
- [x] The video goldens retaken for libyuv (eleven decoded pictures, three
      widget goldens, the two guide pictures of `media-player`); the showcase
      says "the media natives" where it said FFmpeg, and refuses a source
      without them with the loader's reason

### Open after 2026-10-24

- [x] `LoopingPlaybackTest`'s seam with audio landed 2.5 ms late. Neither the
      pass length nor Opus was wrong: Matroska's millisecond stamps (-7 ms for
      a 6.5 ms codec delay, 14 ms for 13.5) placed each frame up to a
      millisecond off the last, and the sample-counted clock jumped by it. A
      frame within 2 ms of the last one's end now follows it
      (`engine.AudioPlacement`, ADR-0652 parked).
- [x] MP3's encoder delay is dropped, without an SPI carrier: the reader times
      the first packet at minus the LAME delay plus 529, and the Engine drops
      everything before zero, which it claimed to and did not (an MP4 edit
      list's AAC priming played too). One second of tone is 48 000 samples
      end to end. ADR-0652.
- [x] A native image built and ran (GraalVM 25.4, linux-x64), and it found a
      bug: the metadata registered `opus_decoder_ctl`'s variadic shapes as
      plain calls, so the image died at the first load of the library.
      `ffm.DowncallShape` records the variadic position and the metadata
      spells `firstVariadicArg`; the image loads the library and binds every
      holder. The image also grants `--enable-native-access` to
      `dev.goldberry.media` now. Not covered by CI: the showcase workflow's
      image walks no media screen and carries no media natives.
- [x] `goldberry-media.md` describes this engine (§2–§10 rewritten; §11 kept
      as history); the GPU siting doc says libyuv where it said swscale.
- [x] `:media:gpuTest` was red: `VideoOnGpuTest` holds GPU present to CPU
      present's goldens, and libyuv repeated each chroma sample over its 2×2
      block where the shader interpolates (146–174 levels, PSNR 24.7 dB).
      libyuv moved from its 2021 `stable` branch to main (version 1974) for
      the bilinear `…MatrixFilter` conversions, and is built with
      `LIBYUV_UNLIMITED_DATA`: the shared reference showed its default caps
      the blue gain of limited-range BT.709 and BT.2020 at 2.0, 12 levels
      short on a saturated blue. CPU and GPU now agree within 2 levels (3 for
      10-bit), PSNR 54.9 dB; native ABI 2. Both are held to one Java reference
      (`picture.YuvReference`), and a mismatch is put down to the misreading
      that would draw it: swapped planes, wrong matrix, range or siting
      (`picture.YuvDiagnosis`). ADR-0653 parked.
- [x] FFmpeg-era comments swept from `:media`'s sources and tests;
      `BuiltInDecoders` and `MediaCapabilities` say PCM decodes without the
      library.
- [ ] `media.yml` and `publish.yml` have not been through CI; the Windows lane
      (MinGW, `-static`, the `.def` export) is the one most likely to need a
      round.
- [x] `HardwareDecoding` was stored and never read: no provider could see it.
      It is in every `DecoderRequest` now (`hardware`, `softwareOnly()`), and
      the system decoders honour `OFF`: GStreamer passes over the decoders
      whose class says `Hardware` (on this machine `nvh264dec`, for
      `avdec_h264`), VideoToolbox asks for its software decoder, and Media
      Foundation's synchronous transforms were software already. This is the
      plan's `DecoderHints.preferHardware`, under the name the API already
      had. ADR-0654 parked. VideoToolbox's half is written, and the Mac lane is
      its first run.
- [ ] `README.md` still names the `ffmpeg-<platform>` classifier: it describes
      the release, so the change is parked in `docs/snapshot/README.md`.

### Phase 2 — codec breadth: in progress

- [x] libvpx (`decoder.vpx`): VP9 and VP8, 8- and 10-bit, 4:2:2/4:4:4
      subsampled to 4:2:0 through libyuv, the WebM alpha stream in a second
      context; held to FFmpeg's `framemd5` on eight clips. Found on the way:
      libyuv's 16-bit functions take strides in samples, not bytes.
- [x] FLAC (`decoder.flac`): libFLAC's stream decoder through three upcalls,
      every sample of the test tones exact; a bare STREAMINFO or no record at
      all is wrapped into a stream header.
- [x] Vorbis (`decoder.vorbis`) and the Ogg container (`container.ogg`): pages,
      packets across pages, Opus/Vorbis/FLAC mappings, per-packet timing from
      the granules, bisection seeks; held to `ffprobe` on three fixtures
- [x] minimp3 (`decoder.mp3`) and the raw MP3 container (`container.mp3`):
      ID3v2 with `APIC` as cover art, Xing/Info/VBRI and the LAME tag, CBR and
      TOC seeks. The encoder delay is not dropped yet (no SPI carrier for a
      "skip my first N samples"); the padding is, through the packet duration
- [x] MP4 (`container.mp4`): the sample tables, edit lists' common shapes,
      fragments, `dOps` → `OpusHead`, `dfLa`, `avcC`/`hvcC`, `covr`; held to
      `ffprobe` on six fixtures
- [x] Bare FLAC (`container.flac`): frames found by sync and CRC-8, PICTURE
      blocks, SEEKTABLE or an estimate that backs off
- [x] WAV (`container.wav`) and raw PCM in Java (`decoder.pcm`)
