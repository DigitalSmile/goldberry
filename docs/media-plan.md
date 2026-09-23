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
| Video is converted to BGRA as it is decoded, presented against the master clock by whoever asks, and painted when a picture falls due; the SDL sink's queue drains smoothly between pulls | 0463 | done |
| `slider` says when a gesture ends (`onCommit`, `commit=`), so a seek bar scrubs while dragged and seeks exactly on release | 0464 | done |
| Network media is read by one `HttpIO` through a read-ahead cache of extents, ICY is stripped inside it, and playback waits for a high water mark measured in demuxed time; the low water mark is empty | 0465 | done |

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
| §5 frame contract | NV12, I420, P010 | and **I010**, 10-bit planar 4:2:0, which dav1d and VP9 profile 2 produce: the common 10-bit case is lent without a copy. The built-in decoder converts any other format (4:2:2, 4:4:4, 12-bit, VP9's RGB) to I420. Planes must be native memory, since swscale reads their addresses | done |
| §3 packet queues | bounded queues the demux thread blocks on | a blocking put deadlocks with two tracks: video's queue full, audio's empty, the audio clock stopped, video waiting for it. `put` never blocks; the demux thread waits only when **every** queue is full, or one has run four times past its bounds (ffplay's rule). Queues are bounded by bytes too, for containers that give no packet durations | done |
| §3 master clock | audio clock stepped by `SDL_GetAudioStreamQueued` | SDL drains in 1024-sample pulls, so the raw clock moves in 21 ms steps. `SdlAudioSink` reports the queue draining smoothly between pulls (ADR-0463) | done |
| §3 master clock | "− device latency" | SDL 3 reports no device latency. Not subtracted; on Bluetooth output pictures lead the sound by up to ~200 ms | open: a per-platform latency query (CoreAudio, WASAPI, PulseAudio) or a user offset |
| §7 S7 | an unsupported file errors | every chosen track is checked before any plays, so the error names every codec without a decoder, video first | done |
| §4 MediaIO table | `IcyIO`, a MediaIO of `HttpIO` + ICY | ICY is a property of an HTTP response (`icy-metaint`), so `HttpIO` strips it itself, before the cache, where offsets are still audio bytes. No `IcyIO` class (ADR-0465) | done |
| §4 buffering | BUFFERING below the low water mark | the low water mark is empty: a track stalls when its decoder runs out of packets with more to come. Above zero it would pause with media in hand, and flicker through BUFFERING after every local seek (ADR-0465) | done |
| §4 `bufferedRanges` | real byte ranges mapped to time, "not an estimate" | the ranges are real; the mapping to time is in proportion to length and duration, exact for a constant bit rate. The container's index would make it exact at a cost in bindings | done |
| §4 opening | not discussed | `avformat_find_stream_info` reads up to its analyse duration before anything plays: about 4.4 s (830 KB) of 48 kHz PCM, all of a short MP3. Over a slow link that is start-up time | open: a smaller `probesize` and `max_analyze_duration` for network sources, which needs `AVDictionary` options on `avformat_open_input` |
| §4 `HttpIO` | reconnect "resuming at the last byte offset" | at the last byte the reader was *handed*. When a connection fails, the JDK's client drops what it had received and not yet handed on, so the resumed Range starts a little earlier than where the server broke off. Nothing is lost or repeated | done |
| swscale | `sws_getContext` returns null for a conversion it cannot do | FFmpeg 8 **asserts** (aborts the process) on a format with no descriptor, such as `AV_PIX_FMT_NONE`. `VideoConverter` checks that both formats have names before asking | done |

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
| Bindings | `AvUtilCalls`, `AvFormatCalls`, `AvCodecCalls`, `SwCalls` as holder records (ADR-0173 idiom), with errors translated to `FfmpegException` | done for phases 1–3: 48 functions. Phase 3 added swscale's context, scale, colour details and coefficients, and the `AVFrame` picture fields (size, `linesize`, colour, duration) and `AVCodecContext.thread_count`. HW decode (phase 5) adds the `av_hwdevice_*` family and `get_format` |
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
| Linux and Windows | the superbuild and the loader on the other three targets | written, untested: no Linux or Windows host was available. Linux uses the shared branch, with no rpath because the loader opens the five in dependency order. Windows was hardened on review: FFmpeg's `configure` runs through MSYS2's `sh` (`GOLDBERRY_SH`), dav1d's `libdav1d.a` is copied to `dav1d.lib` for MSVC's linker, and the toolchain check asks for `sh` on Windows and `nasm` on every x64 host. Verifying it needs the runners, which is CI's work |

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
| SDL audio: 9 `SDL_*AudioStream*` functions (the ninth, `SDL_SetAudioStreamFrequencyRatio`, for the rate in phase 7), `SDL_AudioSpec` and 2 constants in `libgoldberry`, with `natives.sdl.audio.SdlAudioStream` exported to `:media` alone | done (ADR-0462). `SdlAudioSink` is `MediaPlayer`'s default. Tests use SDL's `dummy` driver, so they are silent |
| Clock SPI: audio master clock, monotonic clock, virtual clock | done: the audio clock is the sink's queue, counted in samples, smoothed between SDL's pulls. `MediaClock` is the SPI for the free-running clock a source with no audio uses, and a video after its audio ends. The virtual clocks are `VirtualSink` (ADR-0462) and a hand-moved `MediaClock` |
| State machine `IDLE → OPENING → BUFFERING ⇄ PLAYING ⇄ PAUSED → ENDED`, `ERROR` | done: `PlaybackState`, published as immutable `PlayerStatus` values |
| Pause, seek (coalesced), volume, mute | done: the first sample after a seek is the target's, checked sample-exact (S2 for audio) |
| `audio-player` widget | done: `media.view.AudioPlayer` (`@Markup("audio-player")`, `player=` names the `MediaPlayer`). It has play/pause, elapsed and remaining time, a seek `slider`, mute and volume, `LIVE` in place of the seek bar for an unseekable source, and the error message in `ERROR`. Since phase 3 its controls are the shared `media-controls` bar (`Transport`): the **keys** (Space/K, ←/→, ↑/↓, M, Home) work, and the seek bar **scrubs to keyframes while dragged and seeks exactly on release** through `slider`'s commit hook (ADR-0464). `media.css` carries the `--gb-media-*` tokens. **Goldens:** `audio-player-playing` (one scale) and `audio-player-error` (full scale sweep). The playing one is checked at one scale for a measured reason: at 1.25x every differing pixel is on a slider groove's edges (4 px at 1x, 5 at 1.25x). That is `slider`'s rounding in `:widgets`, and it is still a follow-up there |
| Fixture corpus (≤ 2 s per clip) | done: 14 clips made by `media/src/test/fixtures/make-fixtures.sh` (`brew install ffmpeg`) from synthetic signals, so they carry no third-party licence. **Audio:** FLAC in FLAC, MP4 and Matroska (sample-exact, after a seek too), Opus in MP4, Ogg and WebM, Vorbis in Ogg, MP3, and MP3 with PNG cover art. **Video, ready for phase 3:** VP8 and VP9 in WebM, AV1 in Matroska and MP4. **S7:** H.264 and AAC in MP4, which opens, lists both tracks, and fails with `UNSUPPORTED_CODEC` |

## Showcase: the Media tab

| Item | Status |
|------|--------|
| A fifteenth tab, **Media**, after Web view (no digit key: the first ten keep theirs) | done, then split into **Audio** and **Video** in phase 3 |
| `audio-player` from markup: `media.kdl` names the model's `MediaPlayer` as a named object | done |
| Sources: Opus, Vorbis, MP3 with cover art, FLAC at 24 kHz mono (resampled), a live unseekable stream, a WAV decoded in Java, H.264/AAC (unsupported), bytes that are not media, a missing file, and **Open a file…** through the desktop's dialog. Clips are made by `example/samples/make-media-samples.sh` | done |
| Driven from Java: play, pause, restart, seek to 25/50/75 %, mute, volume | done |
| Status, Tracks (with the cover-art track marked), This build (`MediaCapabilities`) | done |
| A decoder written in Java: `JavaPcmDecoder`, a `DecoderProvider` with a switch | done |
| Golden `gallery-media`, FFmpeg pinned off in the showcase's tests (the Web tab's rule), plus the 16 gallery goldens re-taken for the new tab label | done |
| Native image: the clips are globbed in the manual reachability metadata. FFmpeg itself is not in an image yet | open, with the natives jar's publication |
| Video (phase 3): a `media-player` above the `audio-player`, both over the one player (since split, next row). Two more samples made by the same script from FFmpeg's synthetic sources: a Mandelbrot zoom in VP9 with the arpeggio in Opus (pictures on the audio clock), and the Game of Life in AV1 with no audio (the free-running clock). The Status card names both decoders, and the file dialog offers video | done. Measured live on macos-aarch64: in sync (the picture at 2.96 s after 2.97 s of play), 33 frames a second while a 25 fps clip plays (the pictures and the two position polls), about 1.5 ms each. The gallery golden `gallery-media` is re-taken for the new pane |
| Separate **Audio** and **Video** tabs (sixteen screens): each has its own `MediaPlayer`, its own document (`audio.kdl` with the `audio-player`, `video.kdl` with the `media-player`) and its own samples. Audio keeps the live stream, the failure cases and the Java decoder; Video has VP9 with Opus, AV1 with no sound, and the H.264 error case. One `MediaScreen` with a `Kind` builds both, and every id on it is prefixed with the kind | done. `gallery-audio` and `gallery-video` replace `gallery-media`, and the other gallery goldens are re-taken for the new tab labels |

## Phase 3 — video, software decode, CPU present

**Exit status: met.** S2, S5 and S7 pass against the fixtures, and S8 passes with a
fake H.264/AAC `DecoderProvider`, including its failure mid-stream. 248 tests in
`:media` with FFmpeg required, and `:media:check` green with SpotBugs clean and the
coverage floor raised to 0.87 of lines and 0.75 of branches (88.9% and 77.1%
measured).

Exit: S2, S5 and S7 pass, and S8 passes with a fake video DecoderProvider.

| Item | Status |
|------|--------|
| Video decode thread, frame queue, A/V sync | done (ADR-0463): `VideoWorker` decodes and converts each picture it keeps into a buffer from `FrameQueue` (3 waiting, at most 7 buffers, reused; a handed-out picture is safe until two newer ones are handed out). Presented against the master clock by the view when it paints and by the decode thread while it waits, so a player with no view ends. Late pictures are dropped before conversion. `Playback` now runs one packet queue per track, starts only when every track is ready (the sink opens paused, so the first sample and the first picture leave together), ends when every track has played out, and hands the clock to the free-running one when the audio ends first. `AudioWorker` and `VideoWorker` are the two decode threads |
| swscale → PRGB32 → `BLImage` (CPU present) | done: `VideoConverter`, bit-exact, colour from the frame's tags (untagged is BT.709 from 720 rows, BT.601 below). `FfmpegDecoder` lends I420, NV12, P010 and I010 without a copy, and converts anything else to I420. `VideoPicture` is the public, borrowed result; the widget wraps its buffer as an `Image` with no copy |
| `video-view` (`fit: contain \| cover \| fill`) | done: `media.view.VideoView` (`@Markup("video-view")`, `player=`, `fit=`, and `none`), over `VideoSurface`. It paints when the next picture falls due, not every refresh |
| `media-controls`, `media-player`; keys; `--gb-media-*` tokens | done: `MediaControls` (`@Markup("media-controls")`) and `MediaPlayerView` (`@Markup("media-player")`: the picture, the overlay with the error and the controls, click to play or pause, the controls fading on `.is-pointer-idle` after 2.5 s of a still pointer while playing). Both and `audio-player` share `Transport` and the focusable `media-controls` bar. New tokens `--gb-media-backdrop`, `--gb-media-overlay-background`, `--gb-media-overlay-padding`, `--gb-media-player-min-height` |
| Byte-exact goldens on a virtual clock | done: `PictureGolden` compares decoded pictures pixel for pixel. Eight goldens: VP8, VP9 and AV1 at fixed times on the audio clock (`VirtualSink`), and VP9 10-bit on a hand-moved `MediaClock`. The widgets have tolerance goldens: `media-player-paused`, `media-player-error` (S7) and `video-view-cover` |
| Seek modes and scrubbing (S2) | done: `SeekMode.ACCURATE` shows the picture covering the target, `KEYFRAME` the keyframe landed on. A paused player decodes one picture after each seek, and audio honours a paused seek (it used to replay up to 200 ms of the old position on play) |
| S7 in `media-player` | done: the H.264/AAC fixture shows `no decoder for h264, aac` over the picture |
| S8 with a fake video provider | done: grey I420 pictures and silence from a test provider for `h264` and `aac`, presented unchanged; failing mid-stream, it falls to nothing and errors naming `h264` |
| Frame step (`,` `.`), fullscreen (`F`) | frame step done in phase 7 (below). Fullscreen **blocked**: `:core` has no call that makes a window fullscreen, and the backend SPI would need one first |
| `VideoPlaybackTest` S2 ("scrubbing shows each keyframe…") flaky | done: it failed in about two of three full runs, before phase 6 as after. Traced to a race in the Engine, not the test: a play straight after a paused seek resumed the sink before the audio thread had taken the seek's flush, so the old position's samples were still in it (and a real device would pull them). The sink now starts only when no seek is pending or under way and the audio thread has honoured the latest one (`Playback.sinkCurrent`); the audio thread starts it itself when it catches up. The Serial is published before the queues are flushed. `MediaPlayerTest` holds a seek inside the demuxer to check it, and fails without the fix |
| Device latency in the audio clock | open: see the corrections table |
| Rate (`SDL_SetAudioStreamFrequencyRatio`) | done in phase 7 (below) |

## Phase 6 — network

**Exit status: met.** S3 passes against a local server that drops connections,
stalls, and ignores Range, with every sample of the played WAV checked in order,
and against a fake `MediaIO` that stalls at an exact byte, so PLAYING →
BUFFERING → PLAYING is seen at the moment it should be. S6 passes against the
same server speaking ICY, through the built-in protocol, as an application gets
it. 301 tests in `:media` with FFmpeg required, and 225 in the showcase. One
older test was flaky; its cause, a race in the Engine, is fixed (phase 3's
table). `:media:check`
green with SpotBugs clean and the coverage floor raised to 0.88 of lines and 0.77
of branches (89.5% and 78.9% measured).

Exit: S3 and S6 pass with a fault-injecting fake MediaIO and against a local HTTP
server.

| Item | Status |
|------|--------|
| `HttpIO` | done (ADR-0465): the JDK's `HttpClient` (redirects, the default proxy selector and authenticator, HTTP/2 over TLS, HTTP/1.1 for plain `http:`). Range for seeking, `HttpStatusException` for an error status, `Options` for the read-ahead, cache, stall timeout, reconnects and ICY. A virtual thread fetches. `MediaIOs` opens `http:` and `https:` with it |
| Read-ahead cache | done: `ReadAheadCache`, extents of 64 KB chunks that merge, fetched from the first byte the reader lacks, evicted farthest first. A seek inside it sends no request, which a test counts |
| Reconnect with backoff | done: resumes by Range where the connection broke; a server that ignores Range is fetched again and skipped; a live stream carries on. Stalls are found by the reader, which drops a connection quiet for `stallTimeout`. A `4xx` is final. A read waits no longer than the Source's timeout. `close()` ends a blocked read, and `Playback.close()` now also closes a source that is still opening |
| ICY | done: `IcyStream` strips the metadata and reads `StreamTitle`, values with quotes of their own included, UTF-8 or Latin-1. `nowPlaying` follows the demuxer's position |
| `MediaIO` additions | done: `isLive()`, `buffered()`, `nowPlaying()` with defaults, so every existing protocol is unchanged. `ByteRange` |
| Water marks | done: `PacketQueue.endNanos()`, `bufferedAhead`, `MediaPlayer.Builder.highWaterMark` (1 s). A video-only source stalls too, holding the free-running clock |
| Status | done: `PlayerStatus.bufferedAhead`, `bufferedRanges` (`TimeRange`), `nowPlaying`, `live()`; `MediaInfo.live`. A new title is pushed as a status |
| Widgets | done: `LIVE` only for a live source; an unseekable source that ends shows what remains; the title is `.media-now-playing`, over the controls in `audio-player` and in `media-player`'s overlay, with `--gb-media-now-playing-color` |
| `bufferedRanges` on the seek bar | open: `slider` in `:widgets` has no second range to draw. The data is in the status |
| Fault-injecting fakes | done: `MemoryIO` stalls at a byte, as often as it is moved on, and ends a stalled read on close; `TestHttpServer` drops, stalls, ignores Range, omits the length, answers with a status, and speaks ICY |
| Showcase | done: the Audio tab's live sample says what is playing. Samples over a real HTTP server (a local one, so the showcase stays offline) are open |

## Phase 7 — polish, in part

| Item | Status |
|------|--------|
| Rate | done: `MediaPlayer.setRate` (0.25–4, kept across sources), `PlayerStatus.rate`, `AudioSink.setRate` with a default that plays at 1 only, `SdlAudioSink` over `SDL_SetAudioStreamFrequencyRatio` (a ninth SDL audio export in `libgoldberry`), the free-running clock at a rate, and the sink target scaled by it. Keys `<` `>` step through 0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75 and 2, and `.media-rate` shows it when it is not 1, so no golden changed. Pitch-preserving tempo stays post-v1 |
| Frame step | done: `MediaPlayer.step(n)` and the keys `,` `.`: pause, then an accurate seek to the shown picture plus `n` picture lengths, clamped to the first and last pictures |
| Fullscreen (`F`) | blocked on `:core`: no window fullscreen call exists |
| Track menus, subtitles | open |

## Phases 4–7

| Phase | Scope | Status |
|-------|-------|--------|
| 4 GPU present | plane upload, YUV→RGB shader, 601/709/2020 and range | **blocked** on M4: `:gpu` is empty and `BackendWindow` has no GPU surface yet (ADR-0019 waits for a consumer). GPU present needs both, and designing them is M4's work, not this plan's |
| 5 HW decode | d3d11va, VideoToolbox, VAAPI for VP9/AV1, copy-back, fallback ladder. Switches on `GOLDBERRY_MEDIA_HWACCEL` in the superbuild | open. Copy-back gives NV12, which CPU present takes, so it does not wait for phase 4; its exit criterion ("4K60 on GPU present") does |
| 6 Network | `HttpIO` (Range, read-ahead cache, reconnect, ICY), water marks, live sources | done, below |
| 7 Polish | track menus, subtitles (text formats, external `.srt`/`.vtt`), rate, frame step, fullscreen | in progress: rate and frame step done (below); track menus and subtitles open; fullscreen blocked on `:core` |

## Documents kept in step

| Document | What changes | Status |
|----------|--------------|--------|
| `docs/content-widgets.md` | table row, §8 rewritten from libVLC to FFmpeg, §12 licence row | done |
| `docs/goldberry-media.md` | the corrections above | done |
| `docs/ARCHITECTURE.md` §3.1 and §15 | the second native boundary, and `:media` in the module list | done |
| `docs/goldberry-media.md` §3, §5, §6, §7 | phase 3 as built: presentation, the clock, seek modes, I010, the keys, S7's strictness | done |
| `docs/core-widgets.md` §3 | `slider`'s commit hook (ADR-0464) | done |
| `docs/content-widgets.md` §8, `docs/ARCHITECTURE.md` module table | the four widgets built | done |
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
| 2026-09-23 | Phases 1 and 2 closed, CI excepted. The Windows superbuild hardened on review (MSYS2 `sh`, `dav1d.lib`, `nasm` and `sh` in the toolchain check), untested for want of a host. Paused seeks now clear the sink, and a failed playback closes its sink |
| 2026-09-23 | Phase 3: swscale bindings and the probe's colour and pixel-format constants, `I010`, video from `FfmpegDecoder`, `VideoConverter`, and the Engine rebuilt around one packet queue per track, `AudioWorker`, `VideoWorker`, `FrameQueue` and `MasterClock` with `MediaClock`. `SeekMode`, `VideoPicture`, `untilNextPicture`. S2, S5, S7 and S8 pass: 248 tests with FFmpeg required |
| 2026-09-23 | Phase 3 widgets: `video-view`, `media-controls`, `media-player`, the keys, and `slider`'s commit hook in `:widgets` (ADR-0464). Showcase Media tab plays video. Measured live, the view repainted at display rate (107 frames a second for 25 pictures); pacing it by the next picture, and smoothing SDL's stepped queue, brought that to 33 at about 1.5 ms (ADR-0463) |
| 2026-09-23 | Phase 6: `HttpIO` with the read-ahead cache, reconnects, stalls and ICY; water marks in demuxed time; `bufferedAhead`, `bufferedRanges`, `nowPlaying` and `live` in the status; the title line in the widgets (ADR-0465). S3 and S6 pass. Phase 4 recorded as blocked on M4. The phase 3 S2 test found flaky under load, before and after this phase |
| 2026-09-23 | The phase 3 S2 flake traced to a play after a paused seek resuming the sink before the audio thread dropped the old samples, and fixed in the Engine. 302 tests, three full runs green |
| 2026-09-23 | Phase 7 in part: playback rate (`setRate`, `<` `>`, `SDL_SetAudioStreamFrequencyRatio`) and frame step (`step`, `,` `.`). Fullscreen recorded as blocked on `:core`. 314 tests, three full runs green |
