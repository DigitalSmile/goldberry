# goldberry-media — implementation plan

Working notes for building `docs/goldberry-media.md` as the `:media` module. That
document is the design; this one tracks the work. One ADR per decision, in
`book/src/adr/`.

Started 2026-09-23. Pins: **FFmpeg `n8.1.3`** (avformat 62, avcodec 62, avutil
60, swresample 6, swscale 9) and **dav1d `1.5.4`**, both in
`gradle/libs.versions.toml`.

Status legend: **done** means code, tests and docs have landed. **in progress**
means it is being built now. **open** means not started. **blocked** names what
it waits for. **answered** means decided not to build, with the reason.

## Decisions taken to start

| Decision | ADR | Status |
|----------|-----|--------|
| FFmpeg driven from Java replaces libVLC as the engine (`goldberry-media.md` §11) | 0460 | done |
| `:media` binds its own libraries: the second module allowed to hold a `MemorySegment`, and the only one whose libraries are not `libgoldberry` | 0461 | done |
| No preview features. The published jar must load on any JDK ≥ 25 without `--enable-preview` | — | done |
| Not published yet. `:media` joins `PublishedModules` when its natives jar exists on all four targets | — | open |

## Corrections to the design, found while building

The design document is kept in step with these. Each one is written into
`goldberry-media.md` where it applies.

| Where | What the design said | What is true | Status |
|-------|----------------------|--------------|--------|
| §2 configure line | `--disable-postproc` | libpostproc was removed in FFmpeg 8.0, and the option now fails configure as unknown | done |
| §2 struct table | `AVIOContext`: no fields touched | `buffer` is touched once. `avio_alloc_context` may replace the buffer it was given, so the buffer to free is the context's own field | done |
| §2 packaging | classifiers `macos-x64`, `linux-arm64` | the project's matrix is `linux-x64`, `linux-aarch64`, `windows-x64`, `macos-aarch64` (ADR-0041). Media uses the same four names | done |
| §2 constants | not discussed | `AVERROR(EAGAIN)` is −35 on macOS and −11 on Linux and Windows, so it is read from the probe output and never hard-coded | done |
| §2 dav1d | listed as its own library in the size budget | linked statically into `avcodec`. There is one fewer file to load and nothing changes in the licence (BSD-2) | done |

## Local toolchain

The media superbuild needs more than `:natives` does. Nothing here runs in
`./gradlew build`. The superbuild runs only when asked:
`./gradlew :media:ffmpegBuild`.

| Host | Install |
|------|---------|
| macOS | `brew install meson pkg-config nasm` (`nasm` is only used by x86 builds, but meson's dav1d configure looks for it) |
| Debian/Ubuntu | `sudo apt install meson ninja-build nasm pkg-config make` |
| Fedora | `sudo dnf install meson ninja-build nasm pkgconf-pkg-config make` |
| Windows | MSYS2: `pacman -S make diffutils pkgconf mingw-w64-x86_64-meson mingw-w64-x86_64-nasm`, then run from a Developer Command Prompt with MSYS2's `usr/bin` on `PATH` (untested) |

## Phase 1 — natives, bindings, MediaIO

Exit: Java probes a file through MediaIO and lists its Tracks on all platforms,
with the layout test green.

| Item | What it asks for | Status |
|------|------------------|--------|
| Module skeleton | `:media` in `settings.gradle`, `media/build.gradle`, `module-info`, package docs | done |
| Catalog pins | `ffmpeg`, `dav1d` in `[versions]`; the superbuild reads them the way `:natives` does (ADR-0035) | done |
| Layout probe | `ffmpeg_layout.c` prints sizes, offsets, majors and constants as properties | done |
| Java layouts | hand-declared `StructLayout`s for the nine structs, with unread fields as padding | done |
| Layout check | `FfmpegLayoutCheck` compares Java with the probe output. It runs in a unit test against the committed fixture, and at start-up against the packaged file | done |
| Library loading | `FfmpegLibraries`: `goldberry.media.libdir`, then the classifier jar. Loads in dependency order, checks majors before any struct access, then checks layouts | done |
| Bindings | `AvUtilCalls`, `AvFormatCalls`, `AvCodecCalls`, `SwCalls` as holder records (ADR-0173 idiom), with errors translated to `FfmpegException` | in progress. The 18 functions probing needs are done. The rest of the ~60 arrive with the phase that calls them |
| MediaIO SPI | `MediaIO`, `MediaIOProvider` (ServiceLoader, by URI scheme), `Source`, `FileIO` | done |
| Custom `AVIOContext` | `read_packet` and `seek` upcalls, one stub pair per context (ADR-0017), no exception crosses into C, `AVSEEK_SIZE`, abort by closing | done |
| Codec vocabulary | `CodecId` (Goldberry's own, mapped by FFmpeg codec *name* rather than number), `MediaType`, sealed `TrackParams` | done |
| Probe | `MediaProbe.probe(Source)` → `MediaInfo` (duration, tracks) | done |
| Media superbuild | `media/src/main/cmake`: dav1d (meson, static) → FFmpeg (configure, shared) → probe → strip → size gate at 7 MB | done on macos-aarch64: `./gradlew :media:ffmpegBuild` takes about a minute on a warm cache and gives 4.9 MB (avcodec 2.5 MB with dav1d inside). Install names are `@loader_path`, and the only other libraries loaded are system frameworks. The other three targets are in the Linux and Windows row |
| Gradle tasks | `:media:ffmpegConfigure`, `:media:ffmpegBuild`. Tests read the install directory when it exists | done |
| Native tests | layout check and probe against the built libraries. They skip without natives, and `-Pgoldberry.media.required=true` makes them fail instead | done. 108 tests green against the superbuild's output on macos-aarch64, found with no `libdir` override, with `-Pgoldberry.media.required=true` |
| Natives jar | `goldberry-ffmpeg-natives-<classifier>` with the licences and an `ffmpeg-NOTICE.txt` holding the tag and configure line | done: `:media:ffmpegNativesJar<Target>`, and `:media:testNativesJar` loads FFmpeg from the jar with no `libdir` set. It runs in its own JVM, is part of `check`, and skips where FFmpeg is not built. `licenses/ffmpeg.txt` and `licenses/dav1d.txt` are vendored verbatim and listed in `THIRD-PARTY-NOTICES.md`. Publishing waits for all four targets |
| CI | the media superbuild on the four runners, cached, with the size gate | in progress: `.github/workflows/media.yml` builds and runs `:media:check` with FFmpeg required on macos-aarch64 and linux-x64. It has not run yet: it runs when pushed. Windows and linux-aarch64 join later |
| Upcall benchmark | `read_packet` cost against a 32 KB block (§10) | done: `:media:benchmark`. 532 ns per 32 KB block through the stub against 462 ns for the same copy with no crossing, so about 70 ns per block for a Java→C→Java round trip (61 GB/s). FFmpeg pays only the C→Java half. The risk in §10 is closed |
| Native-image metadata | the upcall shapes and the descriptors `FfmpegDowncalls` records, in `META-INF/native-image` (ADR-0339's generator, for this module) | done: `:media:foreignMetadata` generates it into the jar, with the natives jar's resources as a glob. No native image has been built against it yet |
| Coverage floor | a `jacocoTestCoverageVerification` rule, measured with FFmpeg loaded, as `:html` has | done: lines 0.84 and branches 0.72, against 85.9% and 74.4% measured. It applies only where FFmpeg is built |
| Linux and Windows | the superbuild and the loader on the other three targets. The Windows branch of `CMakeLists.txt` is written and untested | open |

## Phase 2 — audio player and Decoder SPI

**Exit status: met.** The fake DecoderProvider is chosen over the built-in one by
priority, and falls back mid-stream. MP3, FLAC, Opus and Vorbis play from a file
with seeking, against committed clips. An accurate seek is sample-exact where the
container counts in samples, and within half a millisecond in Matroska, which
counts in milliseconds.

Refinements to §5, kept in `goldberry-media.md` when phase 2 closes:

- `supports` and `open` take one `DecoderRequest` (codec, name, params,
  extradata, time base) rather than separate arguments. The SPI can then grow
  without breaking a provider.
- An `AudioFrame` may carry any of the twelve `SampleFormat`s, not only f32.
  The engine's one resampling pass converts every one of them, so the built-in
  decoder lends FFmpeg's buffers without a copy, and no provider converts
  anything itself.
- `receive` answers a sealed `Received` (`Decoded`, `NeedsInput`, `Ended`)
  rather than filling an out-parameter.
- Serial stays with the engine. It flushes the decoder on a seek, so every frame
  after a flush belongs to the new Serial, and a provider never sees one.
- The built-in decoder is not a `DecoderProvider`. It opens from the stream's
  own `AVCodecParameters` (block alignment, bits per coded sample), which a
  request does not carry. It still ranks last.
- `AVDISCARD_ALL` is a hint: the generic demux path returns discarded packets.
  `Demuxer.read` filters them as well.

Exit: mp3, flac, opus and vorbis play from a file with seeking, and a test-only fake
DecoderProvider (a sine generator) is chosen over the built-in one by priority.

| Item | Status |
|------|--------|
| `Decoder`, `DecoderProvider`, `DecoderRequest`, `Packet`, `Frame` (`AudioFrame`, `VideoFrame`), `Received`, `Rational`, `SampleFormat`, `PixelFormat` (SPI, `ServiceLoader`) | done |
| Codec resolution by priority, built-in FFmpeg decoder last, fallback ladder by `skip`; `UNSUPPORTED_CODEC` | done: `Decoders.open`. A provider that fails to open is passed over. An A-law WAV is the S7 fixture |
| `MediaCapabilities` (`av_codec_iterate`, `av_demuxer_iterate`, providers) | done. Read from the loaded build. The test checks that the free codecs are there and the patent-pool ones are not |
| Demux thread, packet queues, Serial | done: `Playback`'s demux thread and a duration-bounded `PacketQueue` with flush markers. A seek wakes a blocked producer, and seeks are coalesced |
| Audio decode thread, swresample to interleaved f32 | done: decode on the track's own thread (the SPI's one-thread promise), one conversion to the sink's format, accurate-seek trimming to the sample, backpressure at 200 ms queued |
| SDL audio: 8 `SDL_*AudioStream*` functions, `SDL_AudioSpec` and 2 constants in `libgoldberry`, with `natives.sdl.audio.SdlAudioStream` exported to `:media` alone | done (ADR-0462). `SdlAudioSink` is `MediaPlayer`'s default. Tests use SDL's `dummy` driver, so they are silent |
| Clock SPI: audio master clock, monotonic clock, virtual clock | in progress: the audio clock is the sink's queue, counted in samples. The virtual clock for audio is `VirtualSink` (ADR-0462). The monotonic clock arrives with video, for sources with no audio track |
| State machine `IDLE → OPENING → BUFFERING ⇄ PLAYING ⇄ PAUSED → ENDED`, `ERROR` | done: `PlaybackState`, published as immutable `PlayerStatus` values |
| Pause, seek (coalesced), volume, mute | done: the first sample after a seek is the target's, checked sample-exact (S2 for audio) |
| `audio-player` widget | done: `media.view.AudioPlayer` (`@Markup("audio-player")`, `player=` names the `MediaPlayer`). It has play/pause, elapsed and remaining time, a seek `slider`, mute and volume, `LIVE` in place of the seek bar for an unseekable source, and the error message in `ERROR`. It rebuilds on pushed status and reads the position every 250 ms while playing. `media.css` carries the `--gb-media-*` tokens. Still open: **keys** (Space, ←/→, ↑/↓, M) come with `media-controls` in phase 3. **Seek on release**: `slider` has no release hook, so each drag step is an accurate seek, which the Engine coalesces. §3's keyframe-while-dragging needs a release hook in `:widgets`. **Goldens:** `audio-player-playing` (one scale) and `audio-player-error` (full scale sweep). The playing one is checked at one scale for a measured reason: at 1.25x every differing pixel is on a slider groove's edges (4 px at 1x, 5 at 1.25x). That is `slider`'s rounding in `:widgets`, and it is a follow-up there |
| Fixture corpus (≤ 2 s per clip) | done: 14 clips made by `media/src/test/fixtures/make-fixtures.sh` (`brew install ffmpeg`) from synthetic signals, so they carry no third-party licence. **Audio:** FLAC in FLAC, MP4 and Matroska (sample-exact, after a seek too), Opus in MP4, Ogg and WebM, Vorbis in Ogg, MP3, and MP3 with PNG cover art. **Video, ready for phase 3:** VP8 and VP9 in WebM, AV1 in Matroska and MP4. **S7:** H.264 and AAC in MP4, which opens, lists both tracks, and fails with `UNSUPPORTED_CODEC` |

## Showcase: the Media tab

| Item | Status |
|------|--------|
| A fifteenth tab, **Media**, after Web view (no digit key: the first ten keep theirs) | done |
| `audio-player` from markup: `media.kdl` names the model's `MediaPlayer` as a named object | done |
| Sources: Opus, Vorbis, MP3 with cover art, FLAC at 24 kHz mono (resampled), a live unseekable stream, a WAV decoded in Java, H.264/AAC (unsupported), bytes that are not media, a missing file, and **Open a file…** through the desktop's dialog. Clips are made by `example/samples/make-media-samples.sh` | done |
| Driven from Java: play, pause, restart, seek to 25/50/75 %, mute, volume | done |
| Status, Tracks (with the cover-art track marked), This build (`MediaCapabilities`) | done |
| A decoder written in Java: `JavaPcmDecoder`, a `DecoderProvider` with a switch | done |
| Golden `gallery-media`, FFmpeg pinned off in the showcase's tests (the Web tab's rule), plus the 16 gallery goldens re-taken for the new tab label | done |
| Native image: the clips are globbed in the manual reachability metadata. FFmpeg itself is not in an image yet | open, with the natives jar's publication |

## Phase 3 — video, software decode, CPU present

Exit: S2, S5 and S7 pass, and S8 passes with a fake video DecoderProvider.

| Item | Status |
|------|--------|
| Video decode thread, frame queue, A/V sync | open |
| swscale → PRGB32 → `BLImage` (CPU present) | open |
| `video-view` (`fit: contain \| cover \| fill`) | open |
| `media-controls`, `media-player`; keys; `--gb-media-*` tokens | open |
| Byte-exact goldens on a virtual clock | open |

## Phases 4–7

| Phase | Scope | Status |
|-------|-------|--------|
| 4 GPU present | plane upload, YUV→RGB shader, 601/709/2020 and range | open |
| 5 HW decode | d3d11va, VideoToolbox, VAAPI for VP9/AV1, copy-back, fallback ladder. Switches on `GOLDBERRY_MEDIA_HWACCEL` in the superbuild | open |
| 6 Network | `HttpIO` (Range, read-ahead cache, reconnect), `IcyIO`, water marks, live sources | open |
| 7 Polish | track menus, subtitles (text formats, external `.srt`/`.vtt`), rate, fullscreen | open |

## Documents kept in step

| Document | What changes | Status |
|----------|--------------|--------|
| `docs/content-widgets.md` | table row, §8 rewritten from libVLC to FFmpeg, §12 licence row | done |
| `docs/goldberry-media.md` | the corrections above | done |
| `docs/ARCHITECTURE.md` §3.1 and §15 | the second native boundary, and `:media` in the module list | done |
| `THIRD-PARTY-NOTICES.md`, `licenses/` | FFmpeg (LGPL-2.1+) and dav1d (BSD-2), when the natives jar ships | open |

## Log

| Date | What happened |
|------|---------------|
| 2026-09-23 | Plan written. ADR-0460 and ADR-0461. `:media` module with the I/O SPI, the codec vocabulary, bindings, layout probe and check, custom `AVIOContext` and `MediaProbe`. Superbuild written. Verified against a scratch FFmpeg `n8.1.3` (no dav1d) on macos-aarch64: the probe's output from the installed headers matched the committed fixture byte for byte, and all 108 tests pass |
| 2026-09-23 | Local toolchain installed. First full superbuild on macos-aarch64: FFmpeg `n8.1.3` + dav1d `1.5.4`, 4989 KB, under the 6 MB target. The probe's output matches the committed fixture. `:media:check` green with FFmpeg required |
| 2026-09-23 | Phase 1 local items closed: natives jar and the jar-loading test, vendored licences, native-image metadata, upcall benchmark (70 ns per 32 KB crossing), coverage floor. CI workflow written. Phase 2 begins |
| 2026-09-23 | Phase 2 started: Decoder SPI, `Demuxer`, built-in `FfmpegDecoder`, `Resampler`, `Decoders` resolution and fallback, `MediaCapabilities`. 25 more FFmpeg functions and two more structs (`AVCodec`, `AVInputFormat`) in the layout check. 129 tests green with FFmpeg required |
| 2026-09-23 | Engine for audio: `MediaPlayer`, `Playback` (demux and audio threads), `PacketQueue` with Serial, `AudioSink` with `VirtualSink` for tests and `SdlAudioSink` for the desktop (ADR-0462). `:natives` exports SDL audio streams to `:media`. 152 tests green, one of them through real SDL output on the dummy driver. SpotBugs clean |
| 2026-09-23 | Encoded fixtures: FLAC in FLAC, FLAC in MP4, Opus in MP4, through the whole Engine. 159 tests green |
| 2026-09-23 | `audio-player` widget in `media.view`, in the generated widget catalog. 169 tests green with FFmpeg required, and `:media:check` passes with the coverage floor. Phase 2 is done except the MP3 and Vorbis fixtures |
| 2026-09-23 | Fixtures from a full FFmpeg: 14 clips, all audio codecs and containers, and video for phase 3. Phase 2's exit is met. Showcase **Media** tab with every source, control and state. 196 media tests and 222 showcase tests green. A headless run of the showcase on the Media tab loads FFmpeg and paints cleanly |
