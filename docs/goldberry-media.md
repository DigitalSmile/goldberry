# goldberry-media

Embedded audio/video playback for Goldberry: `audio-player`, `video-view`, `media-controls`, `media-player` widgets over an FFmpeg-direct engine. Optional module; `goldberry-core` and the core superbuild are unaffected.

Status: engine decided (FFmpeg-direct, supersedes libVLC). Two scope decisions: **royalty-free codecs only**, and **no FFmpeg network layer** (all I/O in Java). Rationale in §11.

## 1. Glossary

| Term | Meaning |
|---|---|
| **Engine** | The non-visual `MediaPlayer`: threads, queues, clock, state. No widget dependency. |
| **Source** | A URL or path plus open options (headers, timeouts). |
| **MediaIO** | Java SPI (`read`, `seek`, `size`, `close`, and `isLive`, `buffered`, `nowPlaying` with defaults) that supplies every byte FFmpeg reads, through a custom `AVIOContext`. Implementations: `FileIO`, `HttpIO` (which strips ICY metadata itself). |
| **Decoder** | Java SPI (`send`, `receive`, `flush`, `close`) the decode threads talk to. Supplied by a **DecoderProvider**; the built-in FFmpeg provider is the default and lowest-priority one. |
| **Read-ahead cache** | `HttpIO`'s byte-range cache; source of the buffered ranges shown on the seek slider. |
| **Track** | One selectable audio, video or subtitle stream of a Source. |
| **Packet queue** | Bounded per-Track queue of demuxed `AVPacket`s. |
| **Frame queue** | Small queue (3) of pictures already converted for presentation, with pts, over a pool of at most seven reusable buffers. |
| **Serial** | Integer bumped on every seek; packets/frames with a stale Serial are discarded. |
| **Master clock** | The time base video is presented against. Audio clock when an audio Track plays, else a free-running clock over the `MediaClock` (monotonic by default); virtual in tests. |
| **Copy-back** | Transfer of a HW-decoded surface to system memory as NV12/P010. |
| **Present path** | How a frame reaches the screen: **GPU present** (plane upload + shader) or **CPU present** (swscale → `BLImage`). |
| **Fallback ladder** | Ordered degradation: HW decode → software decode; GPU present → CPU present. |
| **Live Source** | Source with no duration and no seeking (Icecast-style radio, endless HTTP streams). |
| **Water marks** | Buffered-duration thresholds that drive BUFFERING ↔ PLAYING, measured in demuxed time ahead of the clock. The high one is `highWaterMark` (1 s by default); the low one is empty (ADR-0465). |

## 2. Natives

FFmpeg (one pinned major, shared libraries) + dav1d. Pinned in `gradle/libs.versions.toml`: FFmpeg `n8.1.3` (avformat 62, avcodec 62, avutil 60, swresample 6, swscale 9) and dav1d `1.5.4`. Progress is tracked in `media-plan.md`. Built by the media superbuild via `ExternalProject_Add`; Windows builds run configure under msys2 (MSVC or mingw toolchain); dav1d builds with meson + nasm.

Libraries: `avformat`, `avcodec`, `avutil`, `swresample`, `swscale`. Not built: `avfilter`, `avdevice`, programs, docs, **network, all protocols**. (`postproc` no longer exists: FFmpeg 8.0 removed it, and `--disable-postproc` now fails configure.)

```
--disable-everything --disable-programs --disable-doc --disable-network
--disable-avdevice --disable-avfilter --disable-autodetect
--enable-shared --disable-static
--enable-demuxer=matroska,mov,avi,ogg,flac,mp3,wav,srt,webvtt,ass
--enable-decoder=vp8,vp9,av1,libdav1d,opus,vorbis,flac,mp3,
                 pcm_s16le,pcm_s24le,pcm_f32le,subrip,ass,webvtt,mov_text
--enable-parser=vp8,vp9,av1,opus,vorbis,flac,mpegaudio
--enable-bsf=vp9_superframe,vp9_superframe_split,av1_frame_merge
--enable-libdav1d
```

Codec policy: royalty-free or patent-expired only. **Not built, by decision:** H.264, HEVC, AAC, AC-3/E-AC-3, MPEG-2/4, MPEG-TS demuxer. The `mov` demuxer stays because MP4 legitimately carries AV1/VP9/Opus/FLAC; an MP4 with H.264/AAC opens, and the Engine reports `UNSUPPORTED_CODEC` naming the codec. The `avi` demuxer is built for the same reason (ADR-0471): an Xvid and AC-3 rip opens and reports `no decoder for mpeg4, ac3`. A container with no demuxer in the build (MPEG-TS, FLV, ASF, …) is recognised from its first bytes and reported as `UnsupportedContainer`, not as invalid data.

| Platform | HW decode |
|---|---|
| Windows | `--enable-d3d11va`, hwaccels `{vp9,av1}_d3d11va2` |
| macOS | `--enable-videotoolbox`, hwaccels `{vp9,av1}_videotoolbox` |
| Linux | `--enable-vaapi`, hwaccels `{vp8,vp9,av1}_vaapi` |

FFmpeg's native `av1` decoder is hwaccel-only; dav1d is the software AV1 path and, given how recent AV1 hardware is, the common one.

As built (ADR-0470): on by default on macOS and Windows, where the hwaccel needs only the OS. Off by default on Linux, because VAAPI makes `libavutil` link `libva`, and a machine without it could not load FFmpeg at all; `-Pgoldberry.media.hwaccel=true` builds it. The hardware rung chooses its own decoder, the first with a `HW_DEVICE_CTX` configuration for the device type, which for AV1 is FFmpeg's `av1` and not `libdav1d`.

No TLS library on any platform: HTTPS is the JDK's (§4).

Size budget per platform (stripped): avcodec 1.5–2.5, avformat ~0.6, avutil ~0.7, swscale ~0.6, swresample ~0.2, dav1d 1.5–2 (linked statically into avcodec, so one fewer file to load). **Target ≤ 6 MB**, CI fails the build above 7 MB. Both are built at `-O2` rather than `-O3`: at `-O3`, GCC made linux-x64 8.8 MB (ADR-0486). There dav1d's x86 assembly alone is about 1.1 MB, and the build is 6.6 MB.

Packaging: natives ship as `goldberry-media`'s `ffmpeg-<target>` classifiers (`ffmpeg-linux-x64`, `ffmpeg-linux-aarch64`, `ffmpeg-windows-x64`, `ffmpeg-macos-aarch64`: the project's four targets, ADR-0041), jars separate from `goldberry-media`'s own that an application gets only by naming them, as `goldberry-natives`' classifiers are (ADR-0495). Each jar carries the LGPL text, dav1d's notice, and a `NOTICE` with the source tag and configure line. The libraries carry FFmpeg's `--build-suffix=-goldberry` (`libavcodec-goldberry.so.62`), so that a distribution's FFmpeg of the same major, which GStreamer and a web view load, is never mistaken for them, nor they for it (ADR-0490). System property `goldberry.media.libdir` overrides the extracted libraries. It exists for LGPL replaceability only; it is not an extension mechanism, and a library set that fails the startup check or the layout check is rejected.

Startup check: the `*_version()` majors of all five libraries must equal the pinned majors, else the Engine refuses to load with a message naming expected and found versions (no struct access happens before this check).

### Bindings (hand-written, no jextract)

Package `goldberry.media.ffi`, one final class per library (`AvFormat`, `AvCodec`, `AvUtil`, `SwResample`, `SwScale`): `MethodHandle`s from `Linker.nativeLinker().downcallHandle(...)`, resolved lazily from a `SymbolLookup.libraryLookup` over the extracted libraries, each with a hand-written `FunctionDescriptor`. Expected surface is ~60 functions; every function is a one-line static wrapper with Java-typed parameters, and errors (`AVERROR_*`) are translated to a `FfmpegException` at the wrapper.

Struct access is the risk, so it is minimised. Rule: use accessor functions (`av_opt_get/set`, `avcodec_parameters_*`, `av_packet_*`, `av_frame_*`, `av_hwdevice_*`) wherever FFmpeg provides them; touch a struct field directly only where no accessor exists. Each touched struct gets a small view class (`AvFrameView`, `AvPacketView`, ...) whose `VarHandle`s are built from a hand-declared `StructLayout`, with fields the Engine never reads declared as padding.

| Struct | Fields touched directly |
|---|---|
| `AVFormatContext` | `nb_streams`, `streams`, `pb`, `duration`, `flags` |
| `AVStream` | `index`, `codecpar`, `time_base`, `duration`, `disposition`, `discard`, `attached_pic` |
| `AVCodecParameters` | `codec_type`, `codec_id`, `extradata`, `extradata_size`, `width`, `height`, `format`, `profile`, `level`, `bit_rate`, `sample_rate`, `ch_layout`, `color_*`, `chroma_location` |
| `AVCodecContext` | `opaque`, `get_format`, `hw_device_ctx`, `pix_fmt`, `sample_fmt`, `pkt_timebase`, `thread_count`, `flags`, `codec_id` |
| `AVCodecHWConfig` | `pix_fmt`, `methods`, `device_type`: what `avcodec_get_hw_config` lists, with no accessor (phase 5) |
| `AVPacket` | `data`, `size`, `pts`, `dts`, `duration`, `stream_index`, `flags` |
| `AVFrame` | `data[]`, `linesize[]`, `width`, `height`, `nb_samples`, `format`, `pts`, `pkt_dts`, `duration`, `flags`, `hw_frames_ctx`, `color_*`, `chroma_location`, `sample_rate`, `ch_layout` |
| `AVIOContext` | `buffer` only, to free it: `avio_alloc_context` may replace the buffer it was handed, so the one to free is the context's own |
| `AVChannelLayout` | `order`, `nb_channels`, `u.mask` |
| `AVRational` | `num`, `den` (by value) |

Layout agreement: the media superbuild compiles a 40-line C program per platform that prints `sizeof` and `offsetof` for every field in the table above as a properties file; that file is packaged into the natives jar, and a test in `goldberry-media` (plus the same check at Engine startup, once, before any struct access) compares it with the Java layouts. A mismatch fails the build and refuses to load at runtime. Layouts are shared across the four 64-bit targets (FFmpeg's public structs use fixed-width integers and pointers), but the check runs per platform regardless.

Upcalls (three, `Linker.upcallStub` with an `Arena` owned by the Engine): `read_packet` and `seek` for MediaIO, `get_format` for HW pixel-format negotiation. Their descriptors are the only entries needed in GraalVM reachability metadata.

## 3. Engine

Platform threads (virtual threads would pin in native calls). One `Arena` per Engine; frame buffers are pooled `MemorySegment`s.

| Thread | Work |
|---|---|
| Demux | `av_read_frame` → Packet queues of selected Tracks. All bytes arrive through MediaIO: `avio_alloc_context` with `read_packet` and `seek` (incl. `AVSEEK_SIZE`) upcalls. Owns seek: `avformat_seek_file`, flush queues, bump Serial. Abort is Java-side: closing the MediaIO makes the pending upcall return `AVERROR_EXIT`. |
| Audio decode | `Decoder.send/receive` → swresample to interleaved f32 at device rate → an `AudioSink`, which on the desktop is an SDL audio stream (ADR-0462). With no device to open, the SDL sink plays into a `SilentAudioSink`, in wall time, and logs one warning per process: the source plays without sound instead of failing (ADR-0487). |
| Video decode | `Decoder.send/receive`. In the built-in FFmpeg provider: `get_format` upcall selects the HW pixel format; HW frames go through Copy-back (`av_hwframe_transfer_data` → NV12, P010 for 10-bit) → Frame queue. |

**Codec resolution.** The Engine holds no codec whitelist. Per Track: ask DecoderProviders in priority order (`supports(codec, params)`), the built-in FFmpeg provider last, which answers from `avcodec_find_decoder` at runtime. No provider → `UNSUPPORTED_CODEC`. `MediaCapabilities` exposes the resolved decoder/demuxer set (`av_codec_iterate`, `av_demuxer_iterate` + providers) for apps and diagnostics.

**Master clock.** Audio: pts of last queued sample − the sink's queued duration (`SDL_GetAudioStreamQueued` on the desktop). Kept in samples, so it adds up exactly. The SDL sink reports its queue draining smoothly between the device's 1024-sample pulls, so the clock moves continuously rather than in 21 ms steps (ADR-0463); since the pulls come unevenly, the drain is a line at the stream's rate steered toward where the pulls say the device is, within 10%, which never jumps (ADR-0485), and the Engine writes to the sink and records the end it wrote to under the lock the clock reads both under. A jump of a pull passes over a picture at 60 fps. No audio Track, or the rest of a video whose audio has ended: a free-running clock over `MediaClock`, the Clock SPI, which is `System.nanoTime` unless a test hands in its own. Tests: `VirtualSink` for audio, a hand-moved `MediaClock` otherwise. **Device latency is subtracted (ADR-0474)**: the clock is what is *heard*, not what has left the queue. The sink reports `AudioSink.latencyNanos()`: for SDL, its own buffers counted in typical pulls, the median of the last fifteen (three on macOS, where SDL's CoreAudio backend keeps three AudioQueue buffers), plus the operating system's latency from an `OutputLatency` provider, asked about the default device at most once a second. `goldberry-media-platform` reads CoreAudio (device latency, safety offset, IO buffer and stream latency); a Bluetooth headset measured 264 ms. `MediaPlayer.setAudioDelay` corrects by hand, either way. The clock never reads before the last seek's target, and once the queue empties at the end it runs on through the tail in wall time, so a track ends when its last sample is heard.

**Presentation.** The `video-view` shows the newest picture with pts ≤ Master clock, and the ones it passed are dropped. As built (ADR-0463): the video decode thread converts each picture it keeps as it decodes it, so the queue holds pictures ready to blit and the borrowed frame is done with before the decoder's next call; whoever presents moves the queue on, the view when it paints and the decode thread while it waits for room, so a player with no view still reaches its end; and the view paints when the next picture falls due (`MediaPlayer.untilNextPicture()`), not on every display refresh. A picture late by a whole frame is dropped before conversion, unless it is the first since a seek or no packet waits after it.
- GPU present: upload Y and UV planes, YUV→RGB in a shader; matrix (BT.601/709/2020) and range come from the frame's colorspace fields. As built (ADR-0483, ADR-0484): `video-view` shows its pictures through `:gpu`'s video layer when `:gpu` is on the module path (`requires static`), over the rectangle `Fit` gives the picture. The frame queue holds the decoded planes while every view attached to the player draws them, and BGRA otherwise. Chroma is sampled linearly and centred, as CPU present's swscale conversion sites it, so the two paths agree within one level on every fixture.
- CPU present: swscale → PRGB32 → `BLImage`. Used by the CPU/headless backend and by all golden tests. Converted with `SWS_BITEXACT | SWS_ACCURATE_RND` at the decoded size, so the bytes are the same on every CPU; the blit scales.

**Fallback ladder.** `hw-decode: auto|off`. HW device creation fails → software. Copy-back fails mid-stream → reopen codec in software, resume from last keyframe. GPU canvas unavailable → CPU present: as built, a paint whose frame cannot show a GPU layer draws the picture on the CPU, and the view asks the player for converted pictures, which a seek to the position brings back (ADR-0484).

As built (ADR-0470): `MediaPlayer.Builder.hardwareDecoding(HardwareDecoding.AUTO | OFF)`, `AUTO` by default. The ladder is *providers → FFmpeg on the device → FFmpeg in software*. Every hardware frame is copied back (NV12, P010 for 10-bit) and presented as a software one. On the device, any failure of send, receive, copy-back or the final drain is thrown for the ladder rather than as a playback error. The video thread opens the next rung, drops the queued packets, and makes an accurate seek to the position, or to the last seek's target when no picture has shown since, so a device that cannot decode the codec (AV1 on an M1) costs the first packet and nothing heard. A codec and device type that failed before a picture are remembered for the rest of the process. The decoder's name follows what it does: `ffmpeg (videotoolbox)` on the device, `ffmpeg` in software. A seek the Engine makes for itself (a track switch, a subtitle track, a fallback) never replaces one the application asked for.

**State.** `IDLE → OPENING → BUFFERING ⇄ PLAYING ⇄ PAUSED → ENDED`, `ERROR` from any state. Observable properties: `position`, `duration`, `bufferedAhead`, `volume`, `muted`, `rate`, `tracks`, `selectedTracks`, `videoSize`, `isLive`, `isSeekable`, `bufferedRanges`, `nowPlaying` (ICY), `error` (incl. `UNSUPPORTED_CODEC` with codec name).

**Seeking.** Requests are coalesced (only the latest runs). `MediaPlayer.seek(position, SeekMode)`: `KEYFRAME` during a slider drag shows the keyframe; `ACCURATE` on release (and the default) decodes and discards to the target pts and shows the picture that covers it. A paused player still decodes one picture after each seek, so a paused seek, and a scrub, show where they landed; audio honours the seek while paused too, so play afterwards does not replay the old position's queued samples. A play that comes before the audio thread has caught up with the seek leaves the sink stopped until it has: the sink starts only when no seek is pending or under way and the audio thread has dropped what it held from before. The seek bar learns of the release from `slider`'s commit hook (ADR-0464). Frame step: `MediaPlayer.step(n)` pauses and makes an accurate seek to the shown picture's time plus `n` picture lengths (as the video thread measured them), which lands on the first instant of the picture `n` on; clamped to the first and last pictures. An accurate seek is exact to the container's timestamps: FLAC, WAV and MP4 count in samples, so the first sample heard is the target's; Matroska counts in milliseconds, so it lands within half a millisecond (24 samples at 48 kHz). Both are tested against the fixture corpus.

**Rate.** v1 uses `SDL_SetAudioStreamFrequencyRatio` (pitch shifts with rate). Pitch-preserving tempo is post-v1. As built: `MediaPlayer.setRate`, 0.25 to 4, kept for the next source. The sink resamples (`AudioSink.setRate`, which a sink that cannot answers false, and the player then refuses the rate); the audio clock needs no change, since the queue is counted in stream samples; the free-running clock counts stream time at the rate; and the audio thread keeps `200 ms × rate` queued, so the device waits no shorter a wall-clock time for its next write. `SdlAudioSink`'s smoothing between pulls drains at the rate too.

## 4. I/O and network streaming

FFmpeg performs no I/O of its own. Every Source resolves to a MediaIO:

| MediaIO | Backing | Notes |
|---|---|---|
| `FileIO` | `FileChannel` | Same path as network, one code path to test. |
| `HttpIO` | `java.net.http.HttpClient` | Range requests for seek; Read-ahead cache; reconnect with backoff resuming at the last byte offset; headers, auth, proxy, HTTP/2 and TLS from the JDK. Servers without Range support → `isSeekable = false`. ICY metadata stripped before the cache when the server sends `icy-metaint`, publishing `nowPlaying`; an ICY server's stream is a Live Source. |

As built (ADR-0465):

- **`HttpIO`.** The first request asks for `bytes=0-`: a `206` makes the stream seekable and gives its length, a `200` means the server ignores Range. A virtual thread fetches the first byte the reader lacks into the Read-ahead cache, at most `readAhead` (8 MB) past the reader, in a cache of `cacheSize` (32 MB) that evicts other extents farthest first and then what has been read. A seek inside the cache opens no connection; one outside it abandons the connection in hand and opens a Range at the new place. A connection that fails, closes short, or delivers nothing for `stallTimeout` (10 s) is made again after a doubling backoff (250 ms to 8 s, eight in a row), resuming at the byte it broke at; a `4xx` other than 408 and 429 is final at once. A read waits no longer than the Source's timeout. Plain `http:` speaks HTTP/1.1, so a radio server is never offered an `h2c` upgrade.
- **ICY** is not a MediaIO of its own. `HttpIO` asks with `Icy-MetaData: 1`, and when `icy-metaint` comes back reads the body through `IcyStream`, which takes the metadata out before the cache. Titles are kept by the offset they took effect at, so `nowPlaying` is the title at the demuxer's position, not the fetcher's. A station that hangs up is a drop, and reconnected.
- **Live.** `isLive` is true for an ICY server, or a response with neither a length nor Range. A server that ignores Range but gives a length is unseekable and not live: it ends.
- **Buffering.** The high Water mark: BUFFERING becomes PLAYING when every track has been demuxed `highWaterMark` past the clock, the source has ended, or the queues are full. The low Water mark is empty: a track stalls when its decoder runs out of packets with more to come, and only then does playback go back to BUFFERING, with the sink paused and the free-running clock held. `bufferedAhead` is the least any playing track has demuxed past the clock.
- **`bufferedRanges`** are the Read-ahead cache's real byte ranges, mapped to time in proportion to the source's length and duration: exact for a constant bit rate, close otherwise. Empty for a local file, and for a source whose length or duration is unknown. The seek bar draws them as `slider` spans, under the fill (ADR-0466), and the widgets look again every quarter second while a fetch goes on.
- WebM over HTTP: the demuxer seeks to the Cues at the tail on open; `HttpIO` serves that as one extra Range request and caches it.
- Opening reads little of a compressed stream: measured on 30–60 s files, Opus in WebM, MP3 and FLAC are each identified and described from their first 32 KB, local or not. Raw PCM in WAV is the exception, at about 830 KB (4.4 s at 48 kHz stereo). A smaller `probesize` for network sources was tried and cut that to 544 KB and nothing else, so it was not kept.
- Out of scope by decision: RTSP/RTP (no free-codec content; would need a Java RTSP client).
- Post-v1: HLS/DASH with playlist/manifest handling in Java feeding segments through MediaIO. Because segment selection is ours, ABR becomes possible. Free-codec HLS/DASH content is rare today, hence not v1.

## 5. Extensibility: bring your own codec

Goldberry publishes only royalty-free natives and a neutral interface. Anything patented is brought and answered for by the app that wants it. The single supported mechanism is the **Decoder SPI**; rebuilding or swapping the FFmpeg natives is not supported (the published codec set is the only one the Engine is tested against).

**Decoder SPI.** Discovered via `ServiceLoader`, consulted before the built-in FFmpeg provider.

```java
public interface DecoderProvider {
    String name();
    default int priority() { return 0; }            // higher is asked first; built-ins rank below all
    boolean supports(DecoderRequest request);       // codec, name, params, extradata, time base
    Decoder open(DecoderRequest request);
}
public interface Decoder extends AutoCloseable {
    boolean send(Packet packet);     // false = full: receive first
    void sendEnd();                  // drain
    Received receive();              // Decoded(frame) | NeedsInput | Ended
    void flush();                    // on seek
}
```

As built in phase 2 (`docs/media-plan.md` records why each differs from the first
sketch): one `DecoderRequest` rather than separate arguments; a sealed `Received`
rather than an out-parameter; the Serial kept by the Engine, which flushes the
decoder on a seek, so a provider never sees one. The built-in FFmpeg decoders are
not a `DecoderProvider`: they open from the stream's own `AVCodecParameters`, and
rank last.

`CodecId` is Goldberry's own enum mapped from `AVCodecID` (the SPI never exposes FFmpeg types). `TrackParams` carries what `AVCodecParameters` holds: dimensions, pixel/sample format, profile, level, bit depth, sample rate, channel layout, colorspace. `Packet` and `Frame` wrap native `MemorySegment`s; a provider may decode straight from the packet buffer without copying.

Frame contract: video as NV12 / I420 / P010 / I010 planes in native `MemorySegment`s with pts, colorspace and range (I010, 10-bit planar 4:2:0, joined in phase 3 because it is what dav1d and VP9 profile 2 produce; the built-in decoder converts any other format to I420); audio in any of twelve `SampleFormat`s (u8, s16, s32, s64, f32, f64, each interleaved or planar) with pts, rate and channel count. The Engine's one resampling pass converts all of them, so no provider converts, and the built-in decoder lends FFmpeg's buffers without a copy. A frame is borrowed until the decoder's next call. A provider that decodes on the GPU performs its own Copy-back; a provider failing in `open` or mid-stream drops the Engine to the next provider (Fallback ladder extended: provider → next provider → built-in). HW decode inside a provider is the provider's business; the Engine's `hw-decode` option governs only the built-in provider.

Container reach: `mov` and `matroska` deliver whole frames plus container extradata (avcC/hvcC/esds), so H.264/HEVC/AAC in MP4/MKV reach a provider with the published natives untouched. Formats that need a parser to frame the stream (MPEG-TS, raw Annex B, ADTS) are out of scope: the published natives build no parsers for patented codecs and the `mpegts` demuxer is not built.

Providers. **Shipped:** the operating system's decoders in `goldberry-media`'s `…media.platform` ([ADR-0472](../book/src/adr/0472-the-platform-decoders-bind-the-system-frameworks.md); a module of their own until [ADR-0493](../book/src/adr/0493-the-platform-decoders-are-part-of-media.md)), found by `ServiceLoader` or listed by `PlatformDecoders.providers()`. The OS vendor holds the licences. On macOS: `videotoolbox` (H.264 and HEVC, 8- and 10-bit 4:2:0, lent as NV12 or P010 straight from the pixel buffer, reordered by the SPS's reorder depth) and `audiotoolbox` (AAC, AC-3, E-AC-3, f32 in FFmpeg's channel order). On Linux (ADR-0489): `gstreamer-video` and `gstreamer-audio`, a GStreamer pipeline per track with the decoder GStreamer ranks highest at the stream's size (gst-libav, a VA-API or NVIDIA decoder, `openh264dec`, `faad`, `a52dec`), replaced by the next if it fails before its first frame, colour from the stream's VUI before the decoder's caps. On Windows (ADR-0489, written and not yet run there): `mediafoundation-video` and `mediafoundation-audio` over Media Foundation's MFTs. The system libraries are bound with FFM, as FFmpeg is, and no native code is built. **Intended:** a Cisco OpenH264 provider fetching Cisco's binary on first use (terms and profile support to be verified); commercial SDKs licensed by an app vendor.

I/O has the same shape already: a custom `MediaIO` registered for a URL scheme adds a protocol without touching natives.

## 6. Widgets

| Widget | Content |
|---|---|
| `video-view` | Surface only. `fit: contain \| cover \| fill`. |
| `media-controls` | Composed from core widgets: play/pause, seek slider (buffered range, hover-time tooltip), elapsed/remaining labels, volume button + fader popover, mute, rate menu, audio/subtitle Track menus, loop, fullscreen. Auto-hides on pointer idle. |
| `media-player` | `video-view` + `media-controls` overlay + subtitle overlay. |
| `audio-player` | Compact `media-controls`; optional cover art from the attached-picture stream; `nowPlaying` line for Live Sources. |

Keys: Space (and K) play/pause, ←/→ ±5 s, ↑/↓ volume, M mute, Home to the start, `,` `.` a picture back and on (pausing), `<` `>` slower and faster through 0.25–2; `F` fullscreen and `Esc` out of it, in `media-player` where the host has a window (ADR-0473): a copy of the player covers the window through `Host.fill`, `.is-fullscreen`, and the window is asked to fill its display; leaving gives the window back as it was, and the platform's own way out takes the copy away. The rate shows as `.media-rate` beside the times when it is not 1. Answered by the widget's own focusable node, where a key bubbles to from a control that does not want it.

Audio track menu, as built (ADR-0467): a `select` of the audio tracks (`.media-audio-track`), for a source with two or more, labelled by title and language (`Track.title`, `Track.language` from the stream's metadata). Choosing one is `MediaPlayer.selectTrack`: the demux thread retires the audio thread, starts one on the new track and seeks accurately to the position.

Video track menu, as built (ADR-0469): the same for video tracks (`.media-video-track`), in `media-player` and `media-controls` only, cover art never offered. The demux thread retires the video thread, releases the frame queue's waiters, starts a new thread on the **same** frame queue and seeks accurately to the position, so the old track's last picture stays up until the new track's picture covering the position replaces it.

Subtitles: text formats decode to ASS events → tags stripped → drawn by Goldberry's text stack as an overlay. External `.srt` / `.vtt`. Bitmap subtitles (PGS/DVB): post-v1. As built (ADR-0468): no FFmpeg subtitle decoder; a text subtitle packet is one cue (SubRip, WebVTT, ASS's text field, `mov_text`), parsed in Java with external SubRip and WebVTT files into plain-line `Cue`s. The demux thread collects a chosen track's cues into a timeline; `MediaPlayer.selectTrack` chooses a subtitle track, `loadSubtitles` a file, `hideSubtitles` none, and `currentSubtitles()` is what shows at the clock. `media-player` draws the lines over the foot of the picture (`.media-subtitles`, `.media-subtitle`), above the controls or lower while they hide, and it and `media-controls` have a subtitles menu (`.media-subtitles-menu`).

Java + KDL + CSS parity as for all widgets. Component tokens `--gb-media-*`. Icons from Lucide.

## 7. Key scenarios

**S1. Local file, GPU present, HW decode.** App sets a Source on the Engine → OPENING; the Source resolves to a `FileIO`; Demux thread reads through MediaIO, probes Tracks, selects default audio/video Tracks, fills Packet queues → BUFFERING until the high Water mark → PLAYING. Video decode thread negotiates a HW pixel format via `get_format`; each surface goes through Copy-back to NV12 into the Frame queue. Audio decode thread feeds SDL; the audio clock becomes Master clock. `video-view` presents via GPU present on each frame tick.

**S2. Scrub and release.** User drags the seek slider: coalesced keyframe seeks, each bumps the Serial; stale packets and frames are dropped; the keyframe is shown while PAUSED-for-seek. On release, one accurate seek decodes and discards to the target pts, then PLAYING resumes against the Master clock.

**S3. Progressive HTTP over a flaky link.** Source resolves to `HttpIO`. The connection drops; the Read-ahead cache drains, Packet queues fall below the low Water mark → BUFFERING. `HttpIO` reconnects with backoff and resumes by Range at the last byte offset; queues refill past the high Water mark → PLAYING. The seek slider shows `bufferedRanges` from the Read-ahead cache; a seek inside a cached range issues no request, a seek outside it opens a new Range under a new Serial.

**S4. Fallback ladder.** HW device creation fails on OPENING → software decode, no state change visible to the app. Copy-back fails mid-stream → codec reopened in software from the last keyframe. GPU canvas absent (headless backend) → CPU present.

**S5. Deterministic golden test.** `hw-decode: off`, CPU present, virtual Master clock. Test advances the clock to fixed pts values and asserts byte-exact `BLImage` output (software H.264/HEVC/VP9/AV1 decode is bit-exact by specification).

**S6. Live Source (internet radio).** Source resolves to `HttpIO`, which finds `icy-metaint` in the response → `isLive = true`, `isSeekable = false`; `audio-player` hides the seek slider, shows a LIVE badge and the `nowPlaying` line from ICY metadata (`media-player` shows it in its overlay); after the initial BUFFERING the Water marks only trigger on network stalls.

**S7. Unsupported codec.** User opens an MP4 with H.264/AAC. `mov` demuxer lists the Tracks; no decoder exists for them → ERROR with `UNSUPPORTED_CODEC(h264, aac)`; `media-player` shows the codec names in its error state. Every chosen track is checked before any plays, so the error names every codec that has no decoder; a file where only one of the two is missing fails too, rather than playing half of it.

**S8. Bring your own codec.** An app adds a DecoderProvider for H.264/AAC on the classpath. User opens the MP4 from S7: `mov` lists the Tracks; Codec resolution finds the provider (`supports` → true), opens a Decoder per Track with the container extradata; decode threads run `send/receive` exactly as with the built-in provider; frames enter the Frame queue, Master clock and Present path unchanged. The provider fails mid-stream → Fallback ladder tries the next provider, finds none → ERROR `UNSUPPORTED_CODEC`.

## 8. Phases and exit criteria

1. **Natives + bindings + MediaIO.** FFmpeg + dav1d on all platforms in CI, size gate; hand-written `goldberry.media.ffi` bindings and struct views; `offsetof` properties generated by the superbuild and the layout test; custom `AVIOContext` over `FileIO`. Exit: Java probes a file through MediaIO and lists Tracks on all platforms with the layout test green.
2. **Audio player + Decoder SPI.** Decoder/DecoderProvider interfaces, Codec resolution, built-in FFmpeg provider as the only implementation; demux + audio decode + SDL audio + Master clock; pause/seek/volume; `audio-player`. Exit: mp3/flac/opus/vorbis from file with seeking; a test-only fake DecoderProvider (sine generator) is selected over the built-in one by priority.
3. **Video, software + CPU present.** Video decode thread, A/V sync, `video-view`, `media-controls`. Exit: S2, S5 and S7 pass; S8 passes with a fake video DecoderProvider.
4. **GPU present.** Plane upload + shader, colorspace handling. Exit: visual parity with CPU present within tolerance on 601/709 content.
5. **HW decode.** d3d11va / VideoToolbox / VAAPI for VP9/AV1 with Copy-back and the Fallback ladder. Exit: 4K60 VP9 without dropped frames on GPU present; S4 passes with injected failures.
6. **Network.** `HttpIO` (Range, Read-ahead cache, reconnect, ICY), Water marks, Live Source. Exit: S3 and S6 pass with a fault-injecting fake MediaIO and against a local HTTP server.
7. **Tracks, subtitles, rate, fullscreen, polish.**

Post-v1: HLS/DASH in Java (with ABR), zero-copy GPU interop, HDR tone mapping, bitmap subtitles, pitch-preserving rate, playlists/gapless.

## 9. Testing

Per TESTING.md. Additions: a small CC-licensed fixture corpus (one clip per container/codec pair, ≤ 2 s each); fault-injecting fake MediaIO (stalls, drops, short reads, no-Range) for S3/S6, and a local HTTP server (`com.sun.net.httpserver`) that drops, stalls, ignores Range and speaks ICY; layout test against the superbuild-generated `offsetof` properties for every field in the §2 table; HW decode lane is smoke-only (non-deterministic surfaces), all goldens run on the software + CPU present path.

## 10. Risks

| Risk | Mitigation |
|---|---|
| Free-codec-only excludes most mainstream content (H.264/AAC MP4, public HLS, IP cameras) | Explicit scope; clear `UNSUPPORTED_CODEC` errors; both BYO levels available from v1 (§5); OS-decoder providers post-v1. |
| Upcall cost on the I/O path | FFmpeg reads in 32 KB+ blocks, so upcall frequency is low; `HttpIO` serves from the Read-ahead cache without blocking when possible. Benchmarked in phase 1. |
| Own HTTP streaming logic (Range, reconnect, cache) | Small, pure Java, fully testable with a fake MediaIO; no native network code at all. |
| Hand-written struct layouts drift from the built headers | Struct access limited to the §2 table; generated `offsetof` properties checked in tests and at startup; wrong layout fails before any struct access. |
| Hand-written bindings miss a function or descriptor detail (by-value `AVRational`, variadics) | ~60 functions, each wrapped and unit-tested against a fixture file; no variadic FFmpeg functions are used (`av_log_set_callback` excluded, logging via `av_log_set_level` only). |
| Decoder SPI adds an indirection on the hot path | One interface call per packet/frame, negligible against decode cost; frames stay in native memory (`MemorySegment`), no copies introduced by the SPI. |
| FFmpeg struct ABI changes across majors | Pin the major; upgrade deliberately by re-reviewing the §2 table; startup version check and layout test fail fast. |
| Licensing | LGPL-2.1+ on every platform, dynamic linking only; never `--enable-gpl` / `--enable-nonfree` / `--enable-version3`. dav1d BSD-2. No patent-pool codecs shipped. |
| GraalVM native-image | Three upcalls (`read_packet`, `seek`, `get_format`) need their function descriptors registered in reachability metadata; the system decoders add two more (VideoToolbox's output callback, AudioToolbox's input procedure); their GStreamer and Media Foundation bindings call nothing back. `:media:foreignMetadata` generates one file from both families of bindings (ADR-0493); no image has been built against it yet. |
| Windows build complexity (msys2 configure) | Isolated in the media superbuild; artifacts cached; core superbuild untouched. |

## 11. Decision record: FFmpeg-direct over libVLC

libVLC 3.0.x was the earlier choice (stable C API, vmem → `BLImage`, vlcj precedent). Reversed because:

- **Size.** Pruned libVLC is ~32–56 MB per platform (avcodec plugin alone ~20 MB, monolithic); FFmpeg-direct with a restricted decoder list is ≤ 6 MB.
- **Build.** VLC 3.x is autotools + ~100 contribs, mingw-only on Windows, no official Linux binaries. Not superbuild material.
- **Duplication.** VLC's subtitle renderer bundles its own FreeType/HarfBuzz; its TLS bundles gnutls. FFmpeg-direct reuses Goldberry's text stack and the OS TLS.
- **Determinism.** VLC owns its clocks and threads; FFmpeg-direct runs under Goldberry's Clock SPI, enabling byte-exact goldens.
- **GPU path.** vmem forces CPU frames; the zero-copy API exists only in unreleased libVLC 4.

Cost accepted: the Engine (§3) and the FFM bindings (§2) are ours to write and maintain. Bindings are hand-written rather than generated: jextract would emit thousands of lines covering every FFmpeg struct and function, while the Engine touches ~60 functions and nine structs; a hand-written surface is reviewable, keeps struct access explicit and small, and matches how the rest of Goldberry binds its natives.

Two further scope decisions:

- **Royalty-free codecs only.** Removes patent-pool exposure for Goldberry and for apps that bundle the natives, removes the need for `full`/`free` build flavours, and shrinks avcodec. Cost: mainstream H.264/HEVC/AAC content does not play out of the box. Mitigated by §5: the Decoder SPI exists from v1, so the restriction is a default, not a ceiling, and the responsibility for patented codecs sits with whoever adds a provider. Swapping the natives is deliberately not a supported path: one tested codec set, one binding surface.
- **No FFmpeg network layer.** All I/O through MediaIO in Java. Removes every TLS dependency (schannel / SecureTransport / mbedTLS) and with it the LGPL-3 build on Linux; gives real `bufferedRanges`, JDK proxy/auth/HTTP2, Java-side abort, and network tests without a server. Cost: RTSP is out; HLS/DASH must be written in Java (post-v1), which in return permits ABR.
