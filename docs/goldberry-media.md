# goldberry-media

Embedded audio/video playback for Goldberry: `audio-player`, `video-view`, `media-controls`, `media-player` widgets over an FFmpeg-direct engine. Optional module; `goldberry-core` and the core superbuild are unaffected.

Status: engine decided (FFmpeg-direct, supersedes libVLC). Two scope decisions: **royalty-free codecs only**, and **no FFmpeg network layer** (all I/O in Java). Rationale in §11.

## 1. Glossary

| Term | Meaning |
|---|---|
| **Engine** | The non-visual `MediaPlayer`: threads, queues, clock, state. No widget dependency. |
| **Source** | A URL or path plus open options (headers, timeouts). |
| **MediaIO** | Java SPI (`read`, `seek`, `size`, `close`) that supplies every byte FFmpeg reads, through a custom `AVIOContext`. Implementations: `FileIO`, `HttpIO`, `IcyIO`. |
| **Decoder** | Java SPI (`send`, `receive`, `flush`, `close`) the decode threads talk to. Supplied by a **DecoderProvider**; the built-in FFmpeg provider is the default and lowest-priority one. |
| **Extended natives** | A user-built FFmpeg of the pinned major with extra decoders/demuxers, loaded via `goldberry.media.libdir`. Never published by Goldberry. |
| **Read-ahead cache** | `HttpIO`'s byte-range cache; source of the buffered ranges shown on the seek slider. |
| **Track** | One selectable audio, video or subtitle stream of a Source. |
| **Packet queue** | Bounded per-Track queue of demuxed `AVPacket`s. |
| **Frame queue** | Small (3–4) queue of decoded video frames with pts. |
| **Serial** | Integer bumped on every seek; packets/frames with a stale Serial are discarded. |
| **Master clock** | The time base video is presented against. Audio clock when an audio Track plays, else monotonic; virtual in tests. |
| **Copy-back** | Transfer of a HW-decoded surface to system memory as NV12/P010. |
| **Present path** | How a frame reaches the screen: **GPU present** (plane upload + shader) or **CPU present** (swscale → `BLImage`). |
| **Fallback ladder** | Ordered degradation: HW decode → software decode; GPU present → CPU present. |
| **Live Source** | Source with no duration and no seeking (Icecast-style radio, endless HTTP streams). |
| **Water marks** | Low/high buffered-duration thresholds that drive BUFFERING ↔ PLAYING. |

## 2. Natives

FFmpeg (one pinned major, shared libraries) + dav1d. Built by the media superbuild via `ExternalProject_Add`; Windows builds run configure under msys2 (MSVC or mingw toolchain); dav1d builds with meson + nasm.

Libraries: `avformat`, `avcodec`, `avutil`, `swresample`, `swscale`. Not built: `avfilter`, `avdevice`, `postproc`, programs, docs, **network, all protocols**.

```
--disable-everything --disable-programs --disable-doc --disable-network
--disable-avdevice --disable-avfilter --disable-postproc
--enable-shared --disable-static
--enable-demuxer=matroska,mov,ogg,flac,mp3,wav,srt,webvtt,ass
--enable-decoder=vp8,vp9,av1,libdav1d,opus,vorbis,flac,mp3,
                 pcm_s16le,pcm_s24le,pcm_f32le,subrip,ass,webvtt,mov_text
--enable-parser=vp8,vp9,av1,opus,vorbis,flac,mpegaudio
--enable-bsf=vp9_superframe,vp9_superframe_split,av1_frame_merge
--enable-libdav1d
```

Codec policy: royalty-free or patent-expired only. **Not built, by decision:** H.264, HEVC, AAC, AC-3/E-AC-3, MPEG-2/4, MPEG-TS demuxer. The `mov` demuxer stays because MP4 legitimately carries AV1/VP9/Opus/FLAC; an MP4 with H.264/AAC opens, and the Engine reports `UNSUPPORTED_CODEC` naming the codec.

| Platform | HW decode |
|---|---|
| Windows | `--enable-d3d11va`, hwaccels `{vp9,av1}_d3d11va2` |
| macOS | `--enable-videotoolbox`, hwaccels `{vp9,av1}_videotoolbox` |
| Linux | `--enable-vaapi`, hwaccels `{vp8,vp9,av1}_vaapi` |

FFmpeg's native `av1` decoder is hwaccel-only; dav1d is the software AV1 path and, given how recent AV1 hardware is, the common one.

No TLS library on any platform: HTTPS is the JDK's (§4).

Size budget per platform (stripped): avcodec 1.5–2.5, avformat ~0.6, avutil ~0.7, swscale ~0.6, swresample ~0.2, dav1d 1.5–2. **Target ≤ 6 MB**, CI fails the build above 7 MB.

Packaging: natives ship as `goldberry-ffmpeg-natives` classifiers (`windows-x64`, `macos-arm64`, `macos-x64`, `linux-x64`, `linux-arm64`), separate from `goldberry-media`. Each jar carries the LGPL text, dav1d's notice, and a `NOTICE` with the source tag and configure line. System property `goldberry.media.libdir` overrides the extracted libraries (LGPL replaceability, and the hook for Extended natives, §5).

Startup check: `avformat_version()` / `avcodec_version()` / `avutil_version()` majors must equal the pinned majors, else the Engine refuses to load with a message naming expected and found versions (no struct access happens before this check).

Superbuild option `-DGOLDBERRY_FFMPEG_EXTRA="decoder=...;demuxer=...;parser=...;bsf=...;hwaccel=..."` appends to the configure line, so Extended natives are built with the same scripts, toolchain and sonames as the published ones. Default empty; CI for published artifacts asserts it is empty.

Bindings: jextract over the five headers, pinned to the FFmpeg major; covered by the layout-agreement checks in TESTING.md. Prefer `av_opt_set*` and accessor functions over direct struct field access where they exist.

## 3. Engine

Platform threads (virtual threads would pin in native calls). One `Arena` per Engine; frame buffers are pooled `MemorySegment`s.

| Thread | Work |
|---|---|
| Demux | `av_read_frame` → Packet queues of selected Tracks. All bytes arrive through MediaIO: `avio_alloc_context` with `read_packet` and `seek` (incl. `AVSEEK_SIZE`) upcalls. Owns seek: `avformat_seek_file`, flush queues, bump Serial. Abort is Java-side: closing the MediaIO makes the pending upcall return `AVERROR_EXIT`. |
| Audio decode | `Decoder.send/receive` → swresample to interleaved f32 at device rate → `SDL_PutAudioStreamData`. |
| Video decode | `Decoder.send/receive`. In the built-in FFmpeg provider: `get_format` upcall selects the HW pixel format; HW frames go through Copy-back (`av_hwframe_transfer_data` → NV12, P010 for 10-bit) → Frame queue. |

**Codec resolution.** The Engine holds no codec whitelist. Per Track: ask DecoderProviders in priority order (`supports(codec, params)`), the built-in FFmpeg provider last, which answers from `avcodec_find_decoder` at runtime. No provider → `UNSUPPORTED_CODEC`. `MediaCapabilities` exposes the resolved decoder/demuxer set (`av_codec_iterate`, `av_demuxer_iterate` + providers) for apps and diagnostics.

**Master clock.** Audio: pts of last queued sample − `SDL_GetAudioStreamQueued` duration − device latency. No audio Track: monotonic. Tests: virtual clock via the Clock SPI.

**Presentation.** On each Goldberry frame tick the `video-view` takes the newest frame with pts ≤ Master clock and drops older ones.
- GPU present: upload Y and UV planes, YUV→RGB in a shader; matrix (BT.601/709/2020) and range come from the frame's colorspace fields.
- CPU present: swscale → PRGB32 → `BLImage`. Used by the CPU/headless backend and by all golden tests.

**Fallback ladder.** `hw-decode: auto|off`. HW device creation fails → software. Copy-back fails mid-stream → reopen codec in software, resume from last keyframe. GPU canvas unavailable → CPU present.

**State.** `IDLE → OPENING → BUFFERING ⇄ PLAYING ⇄ PAUSED → ENDED`, `ERROR` from any state. Observable properties: `position`, `duration`, `bufferedAhead`, `volume`, `muted`, `rate`, `tracks`, `selectedTracks`, `videoSize`, `isLive`, `isSeekable`, `bufferedRanges`, `nowPlaying` (ICY), `error` (incl. `UNSUPPORTED_CODEC` with codec name).

**Seeking.** Requests are coalesced (only the latest runs). During slider drag: keyframe seek, show the keyframe. On release: accurate seek (decode-and-discard to target pts). Paused: frame step forward.

**Rate.** v1 uses `SDL_SetAudioStreamFrequencyRatio` (pitch shifts with rate). Pitch-preserving tempo is post-v1.

## 4. I/O and network streaming

FFmpeg performs no I/O of its own. Every Source resolves to a MediaIO:

| MediaIO | Backing | Notes |
|---|---|---|
| `FileIO` | `FileChannel` | Same path as network, one code path to test. |
| `HttpIO` | `java.net.http.HttpClient` | Range requests for seek; Read-ahead cache; reconnect with backoff resuming at the last byte offset; headers, auth, proxy, HTTP/2 and TLS from the JDK. Servers without Range support → `isSeekable = false`. |
| `IcyIO` | `HttpIO` + ICY | Strips interleaved ICY metadata, publishes `nowPlaying`; Live Source. |

- Buffering: BUFFERING below the low Water mark, PLAYING resumes at the high Water mark. `bufferedRanges` comes from the Read-ahead cache (real byte ranges mapped to time), not an estimate.
- WebM over HTTP: the demuxer seeks to the Cues at the tail on open; `HttpIO` serves that as one extra Range request and caches it.
- Out of scope by decision: RTSP/RTP (no free-codec content; would need a Java RTSP client).
- Post-v1: HLS/DASH with playlist/manifest handling in Java feeding segments through MediaIO. Because segment selection is ours, ABR becomes possible. Free-codec HLS/DASH content is rare today, hence not v1.

## 5. Extensibility: bring your own codec

Goldberry publishes only royalty-free natives and a neutral interface. Anything patented is brought, built and answered for by the app that wants it. Two levels:

**Level 1: Extended natives (no code).** Build FFmpeg with `GOLDBERRY_FFMPEG_EXTRA` (e.g. `decoder=h264,hevc,aac;demuxer=mpegts;parser=h264,hevc,aac;bsf=h264_mp4toannexb,hevc_mp4toannexb,aac_adtstoasc;hwaccel=h264_d3d11va2,hevc_d3d11va2`), ship the result, point `goldberry.media.libdir` at it. Codec resolution (§3) picks the new decoders up automatically, HW decode and the Fallback ladder included. README states: never `--enable-gpl` (app becomes GPL) or `--enable-nonfree` (not redistributable; fdk-aac is the usual trap); patent licensing is the distributor's responsibility.

**Level 2: Decoder SPI (Java).** Discovered via `ServiceLoader`, consulted before the built-in provider.

```java
public interface DecoderProvider {
    int priority();                                         // higher wins; built-in = 0
    boolean supports(CodecId codec, TrackParams params);    // profile, size, bit depth, channels
    Decoder open(TrackParams params, MemorySegment extradata);
}
public interface Decoder extends AutoCloseable {
    void send(Packet packet);        // null = drain
    boolean receive(Frame out);      // false = need more input
    void flush();                    // on seek; frames carry the Serial of their packet
}
```

Frame contract: video as NV12 / I420 / P010 planes in `MemorySegment`s with pts, colorspace and range; audio as f32 (planar or interleaved) with pts, rate, layout. A provider that decodes on the GPU performs its own Copy-back; a provider failing in `open` or mid-stream drops the Engine to the next provider (Fallback ladder extended: provider → next provider → built-in).

Container reach: `mov` and `matroska` deliver whole frames plus container extradata (avcC/hvcC/esds), so H.264/HEVC/AAC in MP4/MKV reach a provider with the published natives untouched. MPEG-TS and raw elementary streams need parsers, i.e. Level 1.

Intended providers (none shipped in v1): OS decoders in an optional `goldberry-media-platform` module (Media Foundation, VideoToolbox/AudioToolbox, VAAPI; the OS vendor holds the licences); a Cisco OpenH264 provider fetching Cisco's binary on first use (terms and profile support to be verified); commercial SDKs licensed by an app vendor.

I/O has the same shape already: a custom `MediaIO` registered for a URL scheme adds a protocol without touching natives.

## 6. Widgets

| Widget | Content |
|---|---|
| `video-view` | Surface only. `fit: contain \| cover \| fill`. |
| `media-controls` | Composed from core widgets: play/pause, seek slider (buffered range, hover-time tooltip), elapsed/remaining labels, volume button + fader popover, mute, rate menu, audio/subtitle Track menus, loop, fullscreen. Auto-hides on pointer idle. |
| `media-player` | `video-view` + `media-controls` overlay + subtitle overlay. |
| `audio-player` | Compact `media-controls`; optional cover art from the attached-picture stream; `nowPlaying` line for Live Sources. |

Keys: Space play/pause, ←/→ ±5 s, ↑/↓ volume, M mute, F fullscreen, `,` `.` frame step when paused.

Subtitles: text formats decode to ASS events → tags stripped → drawn by Goldberry's text stack as an overlay. External `.srt` / `.vtt`. Bitmap subtitles (PGS/DVB): post-v1.

Java + KDL + CSS parity as for all widgets. Component tokens `--gb-media-*`. Icons from Lucide.

## 7. Key scenarios

**S1. Local file, GPU present, HW decode.** App sets a Source on the Engine → OPENING; the Source resolves to a `FileIO`; Demux thread reads through MediaIO, probes Tracks, selects default audio/video Tracks, fills Packet queues → BUFFERING until the high Water mark → PLAYING. Video decode thread negotiates a HW pixel format via `get_format`; each surface goes through Copy-back to NV12 into the Frame queue. Audio decode thread feeds SDL; the audio clock becomes Master clock. `video-view` presents via GPU present on each frame tick.

**S2. Scrub and release.** User drags the seek slider: coalesced keyframe seeks, each bumps the Serial; stale packets and frames are dropped; the keyframe is shown while PAUSED-for-seek. On release, one accurate seek decodes and discards to the target pts, then PLAYING resumes against the Master clock.

**S3. Progressive HTTP over a flaky link.** Source resolves to `HttpIO`. The connection drops; the Read-ahead cache drains, Packet queues fall below the low Water mark → BUFFERING. `HttpIO` reconnects with backoff and resumes by Range at the last byte offset; queues refill past the high Water mark → PLAYING. The seek slider shows `bufferedRanges` from the Read-ahead cache; a seek inside a cached range issues no request, a seek outside it opens a new Range under a new Serial.

**S4. Fallback ladder.** HW device creation fails on OPENING → software decode, no state change visible to the app. Copy-back fails mid-stream → codec reopened in software from the last keyframe. GPU canvas absent (headless backend) → CPU present.

**S5. Deterministic golden test.** `hw-decode: off`, CPU present, virtual Master clock. Test advances the clock to fixed pts values and asserts byte-exact `BLImage` output (software H.264/HEVC/VP9/AV1 decode is bit-exact by specification).

**S6. Live Source (internet radio).** Source resolves to `IcyIO` → `isLive = true`, `isSeekable = false`; `audio-player` hides the seek slider, shows a LIVE badge and the `nowPlaying` line from ICY metadata; after the initial BUFFERING the Water marks only trigger on network stalls.

**S7. Unsupported codec.** User opens an MP4 with H.264/AAC. `mov` demuxer lists the Tracks; no decoder exists for them → ERROR with `UNSUPPORTED_CODEC(h264, aac)`; `media-player` shows the codec names in its error state.

**S8. Bring your own codec.** An app adds a DecoderProvider for H.264/AAC on the classpath. User opens the MP4 from S7: `mov` lists the Tracks; Codec resolution finds the provider (`supports` → true), opens a Decoder per Track with the container extradata; decode threads run `send/receive` exactly as with the built-in provider; frames enter the Frame queue, Master clock and Present path unchanged. The provider fails mid-stream → Fallback ladder tries the next provider, finds none → ERROR `UNSUPPORTED_CODEC`. With Extended natives instead, the same file plays through the built-in provider with HW decode.

## 8. Phases and exit criteria

1. **Natives + MediaIO.** FFmpeg + dav1d on all platforms in CI, jextract bindings, layout checks, size gate; custom `AVIOContext` over `FileIO`. Exit: Java probes a file through MediaIO and lists Tracks.
2. **Audio player + Decoder SPI.** Decoder/DecoderProvider interfaces, Codec resolution, built-in FFmpeg provider as the only implementation; demux + audio decode + SDL audio + Master clock; pause/seek/volume; `audio-player`. Exit: mp3/flac/opus/vorbis from file with seeking; a test-only fake DecoderProvider (sine generator) is selected over the built-in one by priority.
3. **Video, software + CPU present.** Video decode thread, A/V sync, `video-view`, `media-controls`. Exit: S2, S5 and S7 pass; S8 passes with a fake video DecoderProvider and, in a non-published CI lane, with Extended natives.
4. **GPU present.** Plane upload + shader, colorspace handling. Exit: visual parity with CPU present within tolerance on 601/709 content.
5. **HW decode.** d3d11va / VideoToolbox / VAAPI for VP9/AV1 with Copy-back and the Fallback ladder. Exit: 4K60 VP9 without dropped frames on GPU present; S4 passes with injected failures.
6. **Network.** `HttpIO` (Range, Read-ahead cache, reconnect), `IcyIO`, Water marks, Live Source. Exit: S3 and S6 pass with a fault-injecting fake MediaIO and against a local HTTP server.
7. **Tracks, subtitles, rate, fullscreen, polish.**

Post-v1: HLS/DASH in Java (with ABR), `goldberry-media-platform` DecoderProviders over OS decoders (Media Foundation / VideoToolbox / VAAPI), zero-copy GPU interop, HDR tone mapping, bitmap subtitles, pitch-preserving rate, playlists/gapless.

## 9. Testing

Per TESTING.md. Additions: a small CC-licensed fixture corpus (one clip per container/codec pair, ≤ 2 s each); fault-injecting fake MediaIO (stalls, drops, short reads, no-Range) for S3/S6; layout-agreement checks for every accessed FFmpeg struct; HW decode lane is smoke-only (non-deterministic surfaces), all goldens run on the software + CPU present path.

## 10. Risks

| Risk | Mitigation |
|---|---|
| Free-codec-only excludes most mainstream content (H.264/AAC MP4, public HLS, IP cameras) | Explicit scope; clear `UNSUPPORTED_CODEC` errors; both BYO levels available from v1 (§5); OS-decoder providers post-v1. |
| Upcall cost on the I/O path | FFmpeg reads in 32 KB+ blocks, so upcall frequency is low; `HttpIO` serves from the Read-ahead cache without blocking when possible. Benchmarked in phase 1. |
| Own HTTP streaming logic (Range, reconnect, cache) | Small, pure Java, fully testable with a fake MediaIO; no native network code at all. |
| Extended natives built against a different FFmpeg major or with altered struct layout | Startup version check (§2); `GOLDBERRY_FFMPEG_EXTRA` keeps builds on the same scripts; unsupported configurations fail loudly before any struct access. |
| Decoder SPI adds an indirection on the hot path | One interface call per packet/frame, negligible against decode cost; frames stay in native memory (`MemorySegment`), no copies introduced by the SPI. |
| FFmpeg struct ABI changes across majors | Pin the major; upgrade deliberately; layout checks fail fast. |
| Licensing | LGPL-2.1+ on every platform, dynamic linking only; never `--enable-gpl` / `--enable-nonfree` / `--enable-version3`. dav1d BSD-2. No patent-pool codecs shipped. |
| GraalVM native-image | Three upcalls (`read_packet`, `seek`, `get_format`) need their function descriptors registered in reachability metadata. |
| Windows build complexity (msys2 configure) | Isolated in the media superbuild; artifacts cached; core superbuild untouched. |

## 11. Decision record: FFmpeg-direct over libVLC

libVLC 3.0.x was the earlier choice (stable C API, vmem → `BLImage`, vlcj precedent). Reversed because:

- **Size.** Pruned libVLC is ~32–56 MB per platform (avcodec plugin alone ~20 MB, monolithic); FFmpeg-direct with a restricted decoder list is ≤ 6 MB.
- **Build.** VLC 3.x is autotools + ~100 contribs, mingw-only on Windows, no official Linux binaries. Not superbuild material.
- **Duplication.** VLC's subtitle renderer bundles its own FreeType/HarfBuzz; its TLS bundles gnutls. FFmpeg-direct reuses Goldberry's text stack and the OS TLS.
- **Determinism.** VLC owns its clocks and threads; FFmpeg-direct runs under Goldberry's Clock SPI, enabling byte-exact goldens.
- **GPU path.** vmem forces CPU frames; the zero-copy API exists only in unreleased libVLC 4.

Cost accepted: the Engine (§3) is ours to write and maintain.

Two further scope decisions:

- **Royalty-free codecs only.** Removes patent-pool exposure for Goldberry and for apps that bundle the natives, removes the need for `full`/`free` build flavours, and shrinks avcodec. Cost: mainstream H.264/HEVC/AAC content does not play out of the box. Mitigated by §5: the Decoder SPI and Extended natives exist from v1, so the restriction is a default, not a ceiling, and the responsibility for patented codecs sits with whoever adds them.
- **No FFmpeg network layer.** All I/O through MediaIO in Java. Removes every TLS dependency (schannel / SecureTransport / mbedTLS) and with it the LGPL-3 build on Linux; gives real `bufferedRanges`, JDK proxy/auth/HTTP2, Java-side abort, and network tests without a server. Cost: RTSP is out; HLS/DASH must be written in Java (post-v1), which in return permits ABR.
