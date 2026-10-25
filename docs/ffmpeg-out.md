# goldberry-media: FFmpeg-free engine — plan

Status: **in progress** since 2026-10-24; supersedes the FFmpeg-direct design in
`goldberry-media.md`. The decision is ADR-0651 (parked in `docs/snapshot/adr/`).
Phases 0 to 3 are built and green on linux-x64; the first CI run of the new
workflows and phase 4 are what is left. The log at the end says what is built
and what is next.

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

Not shipped: libavformat (Java demuxers), swresample (SDL_AudioStream), libaom (dav1d is better). libtheora, out of scope unless requested, was requested on 2026-10-25 (ADR-0660).

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
- ~~`Adts` / raw frame streams for MP3/AAC passthrough to OS decoders.~~ MP3's
  raw stream is `container.mp3`. A bare ADTS `.aac` is not read: AAC reaches the
  system decoders inside MP4 and Matroska, which is where it is met, and the line
  was dropped on 2026-10-24 rather than scheduled.
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
- HDR (P010, PQ/HLG tone-map shader), 10-bit AV1/VP9 end-to-end, Vulkan Video provider, Theora if anyone asks. (All four taken up on 2026-10-25; see §9.)

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

### Phase 1 — core pipeline: done 2026-10-24 (linux-x64; the CI run is open below)

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
- [x] `README.md` still names the `ffmpeg-<platform>` classifier: it describes
      the release, so the change is parked in `docs/snapshot/README.md`, and
      moves in with the release.

### Phase 2 — codec breadth: done 2026-10-24

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
- [x] `DecoderHints`, provider ordering and the codec-not-found UX, which the
      plan lists here, are the API the engine already had under other names:
      `HardwareDecoding` in every `DecoderRequest` (ADR-0654), `engine.Decoders`
      asking the application's providers before the built-in ones, and
      `MediaError.UnsupportedCodec` ("no decoder for h264, aac"), which the
      player reports as a status and the showcase shows.

### Phase 3 — player UX: done 2026-10-24

What the plan asks for was built before it, but for two pieces: the four
widgets, the keyboard map, track menus, SubRip/WebVTT/ASS/`mov_text` subtitles,
the theme tokens, the showcase screens and the HTTP cache all exist (see
`goldberry-media.md` §4 and §6).

- [x] Chapters (ADR-0655 parked): `Chapter` and `MediaInfo.chapters()`, read
      from Matroska `Chapters`, an MP4 chapter track or `chpl`, ID3v2 `CHAP`
      frames, and the `CHAPTERnnn` comments of Ogg and FLAC, settled once by
      `chapter.ChapterList`. `MediaPlayer.nextChapter()`/`previousChapter()`,
      `Page Up`/`Page Down` in the transport, notches on the seek bar through a
      new `Slider.marks` (`slider-mark`, `--gb-slider-mark-bg`), and the title
      of the chapter playing as `.media-chapter`. Five fixtures
      (`tone-chapters.*`); FFmpeg writes the Ogg one a second late and the FLAC
      one not at all, so two scripts beside `make-fixtures.sh` put them right.
- [x] The prefetch window (ADR-0657 parked): `HttpIO`'s read-ahead was a fixed
      8 MB, three seconds of a 20 Mbit/s film. The Engine now asks
      for 30 s at the source's mean bitrate once it opens
      (`MediaIO.readAhead`, `engine.PrefetchWindow`), and `HttpIO` keeps it
      between its configured read-ahead and its cache's room.

### §8's risks: the sanitizer lane and the robustness tests

- [x] Robustness tests (ADR-0656 parked): `media.robustness` feeds every
      container damaged files (bit flips, overwrites, size fields, truncation
      at every header length, noise; through short-chunk, unknown-size and
      unseekable sources) and every built-in decoder damaged packets and
      configurations, seeded, 8 rounds in `test` and 48 in the lane; "frames,
      or a `MediaException`, and the reading ends". What they found, each
      with a regression test: EBML sizes added up in an `int`; sizes read from
      a file trusted with allocations (`ByteReader` now refuses a count past a
      known end before allocating); an unknown-size element where a sized one
      is required threw `IllegalStateException`; Matroska pixel sizes,
      channels and depths wider than an `int` went negative; MP4 `stsz` with
      one size had an unbounded count. Read, not fuzzed: libFLAC's 8- and
      12-bit samples were handed over unshifted, 256 and 16 times too quiet.
- [x] The sanitizer lane: `GOLDBERRY_MEDIA_SANITIZE=address[,undefined]` builds
      every upstream with the sanitizers (`:media:nativesBuildAsan`, its own
      deps and output directories; the release build is unchanged), and
      `:media:sanitizerTest` runs the robustness, decoder and `ffm` tests
      against it under `LD_PRELOAD` libasan. Green here, ASan silent; a planted
      overflow through minimp3 was caught, so the lane sees. UBSan found two
      things upstream (libvorbis `sharedbook.c`, libFLAC `lpc.c`), suppressed
      in `media/src/test/sanitizer/ubsan.supp`. The `sanitizer` job of
      `media.yml` is **written, not yet run**, and gates publishing as every
      Media job does.
- [x] The 8K cap (ADR-0658 parked, 2026-10-25): dav1d's `frame_size_limit`
      and libvpx's `--size-limit` were unset, so a damaged header could ask for
      65536×65536. `codec.PictureLimit`: 8192 a side, 8192 × 4352 pixels (the
      largest AV1 and VP9 level 6 picture), held by dav1d before it allocates,
      by libvpx's build before it allocates, by the player from a stated size,
      and by the video thread for every provider's picture. Four fixtures past
      it, one per refusing layer.

### Next

1. Push, and run `media.yml` (with its new `sanitizer` job) and `publish.yml`
   on the four runners; the Windows lane is the likeliest to need a round.
2. Phase 4: zero-copy import of the system decoders' surfaces (Linux's
   DMA-BUF first), Media Foundation on by default once its lane is green,
   and the `ffmpeg-cli` provider.

### Phases 4 and 5: the plan of 2026-10-25

Decided with the user on 2026-10-25:

- **No SDL patch, so no zero-copy import.** SDL 3.4.18's GPU API wraps no
  outside texture on any backend; upstream's PR #14157 (D3D12 and Metal only,
  open, contested) is the place for it. Instead one CPU copy goes: GPU present
  stages a picture's planes into the device's upload memory on the video
  thread, and the render thread records the upload without copying. Copy-back
  stays the system decoders' contract.
- **Vulkan Video, all four codecs** (H.264, HEVC, VP9, AV1), on a Vulkan
  device of its own, copied back like the system decoders; the parameter sets,
  slice headers and the picture buffer kept in Java. This machine's GTX 1660 Ti
  decodes H.264, HEVC and VP9; AV1 is held to parsing tests only here.
- **Theora** is built: libtheora in `libgoldberry-media`, Ogg and Matroska.
- **CI runs from master** (changed 2026-10-25: no work branch): the work is
  committed to master and the user pushes it, which runs `snapshot.yml` and,
  through `publish.yml`, every platform lane and `media.yml`; Media Foundation
  goes on by default once its lane is green.

The items, each "done" when built, tested and green here:

- [x] HDR (ADR-0659 parked, 2026-10-25): `codec.ColorDescription` (H.273
      primaries and transfer function, mastering and content peaks) on
      `TrackParams.Video`, `VideoFrame` and `VideoPlanes`, read from dav1d's
      sequence header and metadata OBUs, Matroska `Colour`, MP4
      `colr`/`vpcC`/`mdcv`/`clli`, H.264's VUI, HEVC's VUI through a new full
      parameter set reader (`bitstream.hevc`, held to FFmpeg's `trace_headers`),
      and GStreamer's caps; completed from the track by the video thread.
      Tone-mapped to SDR BT.709 (BT.2390 EETF to 203 nits, hue kept) by
      `yuv2hdr`/`yuv3hdr` on the GPU and by `goldberry_media_tone_map`, a float
      pass in the library's own C, on the CPU: libyuv's AR30 was measured half a
      percent short at white, a dozen levels after PQ. Native ABI 3. CPU within
      a level of the reference, GPU within six of CPU where visible in light
      (NVIDIA and lavapipe). The DXIL of the two new shaders is signed: this
      machine's DXC 1.9.2609 has `libdxil.so`.
- [x] 10-bit end to end: was already so for I010 (dav1d, VP9 profile 2) and
      P010 (GStreamer's NVDEC, VideoToolbox), through both present paths
      (`VideoPlaybackTest.tenBitPlanes`, `VideoOnGpuTest` parity); 10-bit AV1
      now has a fixture of its own (`clip-av1-pq.mkv`) with a golden and GPU
      parity, and the HDR10 HEVC fixture checks GStreamer's 10-bit output.
- [x] The `ffmpeg-cli` provider (ADR-0661 parked, 2026-10-25): `ffmpegcli.FfmpegCliProvider`,
      off unless `-Dgoldberry.media.ffmpeg` names a program or the application
      calls `FfmpegCliProvider.of`. It claims the patent-pool codecs Matroska
      names, and twelve `UNKNOWN` Matroska `CodecID`s, wherever `ffmpeg -codecs`
      flags them `D`, at priority −100, below the system decoders. The track
      goes in as Matroska (`container.matroska.MatroskaWriter`, µs timestamps,
      shifted 1000 s), and frames come back as Matroska that `MatroskaContainer`
      reads over the pipe: NV12 `V_UNCOMPRESSED`, P010 `V_MS/VFW/FOURCC`, f32
      PCM. Times are put back from the packets that went in, colour comes from
      the output `Colour`, and a seek restarts the program. All six video
      fixtures match `framemd5` byte for byte, with and without hardware; 39
      tests, which skip without an `ffmpeg`. The audio thread's missing fallback
      while it drains at the end, which this found, is fixed in the Engine.
- [x] Theora (ADR-0660 parked, 2026-10-25): libtheora 1.2.0's decoder compiled
      into the library (11 exports, +48 KB, 3.8 MB), `decoder.theora` behind
      `libtheora`, the Ogg mapping (granules as FFmpeg reads them, 3.2.0 too;
      seeks land on a keyframe) and Matroska's `V_THEORA`; four fixtures held to
      `framemd5` and `ffprobe`; native ABI 3.
- [x] Staged planes (ADR-0662 parked, 2026-10-25): the video thread copies
      a picture's planes into the view's upload memory (`VideoLayer.staging()`,
      a pool of mapped `UploadBuffer`s cycled on the device's thread), and the
      UI thread records the upload with no copy. `PlaneStore`/`PlaneBuffer`
      in the SPI, `Attachment.setPlaneStore`, `PictureRenderer.planeStore()`.
      4K on the UI thread: NV12 2.6 → 0.5 ms, P010 5.1 → 0.5 ms
      (`:gpu:videoLayerProbe`, NVIDIA). GPU tests green on NVIDIA and
      lavapipe; staged pictures draw within 2 levels of CPU present.
- [x] Vulkan Video (ADR-0663, parked, 2026-10-25): `platform.vulkan.VulkanVideoProvider`,
      priority −10 (below the system decoders, above `ffmpeg-cli`), off under
      `HardwareDecoding.OFF`; `-Dgoldberry.media.vulkanVideo=off|auto|all`,
      `auto` claiming H.264 and HEVC. FFM bindings generated from the system's
      Vulkan 1.4.341 headers (`tools/media/generate_vulkan.py`), one
      `VideoDevice` a process, one `VideoSession` a stream, decode → semaphore →
      copy-back.
  - [x] H.264 (`platform.vulkan.h264`): POC types 0–2, sliding window, MMCO
        1–6, `frame_num` gaps, output by reorder depth; progressive 8-bit
        4:2:0. All four fixtures match `framemd5` on the GTX 1660 Ti, with crop,
        Matroska, a seek and a flush after the end.
  - [x] HEVC (`platform.vulkan.hevc`): VPS/SPS/PPS as `StdVideoH265*`, POC,
        the reference picture set (short- and long-term, a missing picture as
        no slot), RASL pictures skipped after a CRA that starts afresh, output
        by reorder depth and latency, Main and Main 10 (P010). Three new
        256×144 fixtures (NVIDIA decodes HEVC from 144×144): closed GOP, open
        GOP, Main 10; all match `framemd5`, and a seek onto a CRA is exact.
  - [x] VP9 (`bitstream.vp9`, `platform.vulkan.vp9`): the uncompressed header
        and superframe index, held to `trace_headers` on five clips and a
        hand-made IVF (show-existing, intra-only, error-resilient, tile rows,
        ±63 deltas); eight reference slots on nine session slots, hidden
        frames decoded and not shown, `show_existing_frame` read back with no
        decode, a keyframe of another size makes the session again; profiles 0
        and 2. Two new 256×144 fixtures (NVIDIA decodes VP9 from 128×128):
        both match `framemd5`, and so do a seek and a show-existing frame put
        into the stream. Only under `-Dgoldberry.media.vulkanVideo=all`.
  - [x] AV1 (`bitstream.av1`, `platform.vulkan.av1`): OBUs, the sequence
        header (from `av1C` too), frame headers with every derived value
        (`set_frame_refs`, `load_previous`, skip mode, global motion), tile
        groups and the reference update (§7.20/§7.21), held to `trace_headers`
        on seven fixtures, five of them new (tools, superres, error-resilient,
        a still, timing). The sequence header as session parameters, each
        frame's header and tile offsets in its temporal unit, eight reference
        slots on nine, a shown key frame loaded everywhere. Film grain and
        superres are left to dav1d (not claimed, or refused). **Not decoded
        here**: the GTX 1660 Ti has no AV1; the picture info is checked
        against four streams without a device, and `clip-av1-256.mkv` waits for
        a GPU that decodes AV1.
  - [x] The provider registered (`module-info`, `META-INF/services`), the
        Vulkan downcalls in the native image's foreign metadata.
- [ ] Media Foundation on by default (after its CI lane)
- [ ] `media.yml`, `publish.yml` and the sanitizer job green on the runners

### The first CI run: Snapshot #72 on b83be6c4 (2026-10-25)

The whole of phases 0 to 5 reached the runners in one push. The sanitizer job
went green; the library failed to build on all four targets, each for a reason
of its own, so the `:media` job never ran:

- [x] **linux-x64**: `undefined reference to 'dlsym'` from dav1d's
      `dav1d_open` (it looks up `__pthread_get_minstack`). The manylinux_2_28
      container's glibc 2.28 keeps `dlsym` in libdl, which merged into libc
      only in 2.34, so a newer host links without it. The library links
      `${CMAKE_DL_LIBS}`.
- [x] **linux-aarch64** (`udot`, dot product) and **macos-aarch64** (i8mm):
      libyuv's `*_neon64.cc` kernels use extensions the baseline `-march`
      refuses. libyuv's own CMake builds those four files alone with
      `-march=armv8.2-a+dotprod+i8mm` and picks a kernel at run time from the
      CPU's features; the superbuild does the same now. Checked by configuring
      as aarch64 here (the flag lands on the four files and nothing else);
      there is no arm64 compiler on this machine, so the runners compile it
      first.
- [x] **windows-x64**: the DLL linked, and `check_exports.cmake` found none of
      its 160 symbols. Binutils 2.39 and later print the export name table as
      `[   0] +base[   1]  0000 name` under a column header, where the parser
      expected `[   0] name`. It reads the last word of each line of the name
      pointer table now, and takes `-DDUMP=` (a saved listing) in place of
      running the tool, which `nativebuild.ExportCheckTest` uses to hold it to
      ELF, Mach-O, both binutils layouts and CRLF output.
- [x] **Qodana #76** on the same commit: 40 new High findings, all in the
      phase 4–5 code. Packets that own their `Arena` carry Qodana's
      `resource` beside Error Prone's `MustBeClosedChecker`; `VideoSession`,
      `UploadBuffer` and `FfmpegProcess` join the profile's owned types; the
      Matroska zlib `Inflater` is a try-with-resources (AutoCloseable since
      JDK 25); conditions that could not change (FLAC's doubled bound,
      Matroska's denominator, `AsynchronousCloseException` under
      `ClosedChannelException`, a null `MemorySegment`, `ToneCurve`'s
      unreachable `UNSPECIFIED`) are gone, AV1's `read_global_param` assigns
      each width once, and the rest are boxing and naming.
- [x] Its 80 `unused` notes: spec constants nothing reads, parser entry
      points and accessors with no caller, and ten bindings never called
      (dav1d's version, frame delay and `data_wrap`, the libraries' version
      strings, `vpx_codec_error`, `opus_packet_get_nb_samples`, FLAC's reset)
      are deleted; the export list keeps them, as it keeps the rest of the
      upstream APIs. `SampleFormat.ffmpegSuffix()` goes (in the parked
      breaking changes); `UploadBuffer.size()` and
      `MediaCapabilities.canDemux` are public API and now tested. Four `.orig`
      leftovers committed with b83be6c4 are deleted.
- [x] **macos-aarch64, `:gpu:gpuTest`**: `GpuApiTest$Uploads.fromUploadMemory`
      (row 1 wrong) and `VideoLayerTest.stagedPlanes`. SDL 3.4's
      `METAL_UploadToTexture` passes `BytesPerRow(destination->w)` whatever
      `pixels_per_row` says (its download honours it), so padded rows shear
      on Metal. `SdlGpuDevice.uploadsPaddedRows()` (false on Metal, mirrored
      by `GpuDevice`) makes a padded upload record a row at a time, and the
      staging packs its rows there, so a picture is still one copy a plane.
      ADR-0662 (parked) says so; `StagedPlanesLayoutTest` holds the layout
      both ways. The row-at-a-time path was run here on Vulkan with the
      answer forced false.
- [x] **Snapshot #73 on af34b92f**: the library built on all four targets,
      the sanitizer job and Qodana #77 were green, and macOS's `:gpu:gpuTest`
      passed with the row-at-a-time uploads. The `:media` job failed on all four
      before any test ran: `nativesJar<host>` packaged the superbuild's install
      directory, which `-Pgoldberry.skipNative` leaves empty, while the tests
      read the runner's build from `-Pgoldberry.media.libdir`. The host's jar
      now packages the library the tests load. And `testNativesJar` had run no
      test since `NativesJarTest` left with FFmpeg: `ffm.MediaNativesJarTest`
      loads the library from the jar, decodes, and finds the notices.
- [ ] The next run: the `:media` job on four targets is the first time the
      tests meet each target's library.
