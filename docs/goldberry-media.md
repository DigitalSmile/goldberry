# goldberry-media

Embedded audio/video playback for Goldberry: `audio-player`, `video-view`, `media-controls`, `media-player` widgets over an engine of Java demuxers and the reference decoders bound directly. Optional module; `goldberry-core` and the core superbuild are unaffected.

Status: **the engine changed on 2026-10-24** (`ffmpeg-out.md`, ADR-0651).
FFmpeg is gone: the demuxers are Java, and the natives are dav1d, libvpx, Opus,
Vorbis, FLAC, minimp3 and libyuv in one `libgoldberry-media`. This document
describes the module as it is now; §11 is kept as the record of the
FFmpeg-direct decision and what it decided against libVLC. The two scope
decisions stand: **royalty-free codecs built in**, and **all I/O in Java**.

## 1. Glossary

| Term | Meaning |
|---|---|
| **Engine** | The non-visual `MediaPlayer`: threads, queues, clock, state. No widget dependency. |
| **Source** | A URL or path plus open options (headers, timeouts). |
| **MediaIO** | Java SPI (`read`, `seek`, `size`, `close`, and `isLive`, `buffered`, `nowPlaying` with defaults) that supplies every byte a container reads. Implementations: `FileIO`, `HttpIO` (which strips ICY metadata itself). |
| **Decoder** | Java SPI (`send`, `receive`, `flush`, `close`) the decode threads talk to. Supplied by a **DecoderProvider**; the built-in providers (dav1d, libvpx, Opus, Vorbis, FLAC, minimp3) are asked after every application provider. |
| **Read-ahead cache** | `HttpIO`'s byte-range cache; source of the buffered ranges shown on the seek slider. |
| **Track** | One selectable audio, video or subtitle stream of a Source. |
| **Packet queue** | Bounded per-Track queue of demuxed packets. |
| **Frame queue** | Small queue (3) of pictures already converted for presentation, with pts, over a pool of at most seven reusable buffers. |
| **Serial** | Integer bumped on every seek; packets/frames with a stale Serial are discarded. |
| **Master clock** | The time base video is presented against. Audio clock when an audio Track plays, else a free-running clock over the `MediaClock` (monotonic by default); virtual in tests. |
| **Copy-back** | Transfer of a hardware-decoded surface to system memory as NV12/P010: what a system decoder provider does before it hands a frame over. |
| **Present path** | How a frame reaches the screen: **GPU present** (plane upload + shader) or **CPU present** (libyuv → `BLImage`). |
| **Fallback ladder** | Ordered degradation: one decoder provider → the next → the built-in decoders; GPU present → CPU present. |
| **Live Source** | Source with no duration and no seeking (Icecast-style radio, endless HTTP streams). |
| **Water marks** | Buffered-duration thresholds that drive BUFFERING ↔ PLAYING, measured in demuxed time ahead of the clock. The high one is `highWaterMark` (1 s by default); the low one is empty (ADR-0465). |

## 2. Natives

One shared library per target, **`libgoldberry-media`**, built by the media superbuild (`media/src/main/cmake`, `./gradlew :media:nativesBuild`). It links the reference decoders and libyuv statically and exports their own C APIs, nothing of a media framework: demuxing, clocking, A/V sync and audio conversion are Java.

| Library | Role | Licence | Pin (`gradle/libs.versions.toml`) |
|---|---|---|---|
| dav1d | AV1 | BSD-2 | 1.5.4 |
| libvpx | VP9, VP8 (decode only) | BSD-3 | v1.17.0 |
| libopus | Opus | BSD-3 | v1.6.1 |
| libogg + libvorbis | Vorbis | BSD-3 | 1.3.6, 1.3.7 |
| libFLAC | FLAC | BSD-3 | 1.5.0 |
| minimp3 | MP3 | CC0 | commit `ea99364f` (no releases) |
| libtheora | Theora (decode only) | BSD-3 | v1.2.0 |
| libyuv | YUV → BGRA for CPU present, 4:2:2/4:4:4 → 4:2:0 | BSD-3 | main at `47ba8e21`, version 1974 (no releases) |

The library's own `goldberry_media.c` instantiates minimp3, answers the ABI (3) and versions, and tone-maps CPU present's HDR pictures (§3).

Why one library and not seven: a distribution's libdav1d or libvpx may already be in the process (GStreamer's plugins load them), and one private name with every upstream symbol local means neither is ever mistaken for the other. The exported symbols are listed in `exports/goldberry-media.symbols` and checked after linking (`check_exports.cmake`); the library needs libc and libm only.

Build: every upstream at `-O2`, position-independent. dav1d through meson (nasm on x86), libvpx through its configure script with the encoders, tools, examples and tests off. Linux targets build in the manylinux_2_28 container; Windows builds with MinGW-w64 from MSYS2's UCRT64, runtime linked statically and the exports named by a `.def` file. Size: **3.7 MB stripped on linux-x64**, against 6.7 MB for FFmpeg's five libraries; the target is 5 MB and the build fails above 6.

Codec policy: royalty-free or patent-expired only. **Not built, by decision:** H.264, HEVC, AAC, AC-3/E-AC-3, MPEG-2/4. A file in one of them opens and plays through a system decoder provider (§5) where the system has one, or an application's; with neither, the Engine reports `UNSUPPORTED_CODEC` naming the codec.

No hardware decoding in the library: that is the system decoder providers' (§5). `HardwareDecoding` (`MediaPlayer.setHardwareDecoding`, the builder) reaches every provider in its `DecoderRequest`; with `OFF` GStreamer passes over the decoders whose class says `Hardware` (NVDEC, VA-API, V4L2) and VideoToolbox is asked for its software decoder (`kVTVideoDecoderSpecification_EnableHardwareAcceleratedVideoDecoder` false). Media Foundation's are the synchronous software transforms either way, and the built-in decoders are software (ADR-0654).

Packaging: `goldberry-media`'s `natives-<target>` classifiers (`natives-linux-x64`, `natives-linux-aarch64`, `natives-windows-x64`, `natives-macos-aarch64`: the project's four targets, ADR-0041), jars separate from `goldberry-media`'s own that an application gets only by naming them, as `goldberry-natives`' classifiers are (ADR-0495). Each carries the library, its `media-layout.properties`, and `media-NOTICE.txt` with every upstream's licence and pinned version. Every upstream is BSD or CC0, so there is no corresponding-source obligation and no source jar. System property `goldberry.media.libdir` points at a directory holding the library and its layout file instead; the tests set it to the superbuild's install directory.

Startup check (`MediaNatives`), each failure with a reason a person can act on: the layout file is there; the library loads; `goldberry_media_abi()` and the layout file's `abi=` line both answer the ABI the bindings are written against; every struct agrees with the probe's record; every constant was reported. Nothing native is called before all five pass.

### Bindings (hand-written, no jextract)

Package `dev.goldberry.media.ffm`: `MediaNatives` loads and checks, `MediaLibrary` holds the bound library, `NativeStructs` declares the 22 upstream structs the decoders touch (each in its LP64 and its LLP64 shape, since Windows' `long` is 32 bits), and `NativeConstants` the constants. `…ffm.calls` binds 75 functions, one holder class per library (`Dav1dCalls`, `VpxCalls`, `OpusCalls`, `VorbisCalls`, `FlacCalls`, `Mp3Calls`, `YuvCalls`, `MediaCalls`) in `:natives`' holder idiom, generated by `tools/media/generate_calls.py`.

Layout agreement: the superbuild compiles `media_layout.c`, which writes `sizeof`, `offsetof` and the constants as `media-layout.properties` beside the library. The check at startup and a test both compare it with the Java layouts; linux-x64's output is committed as a test fixture.

Frames are borrowed: dav1d's picture and libvpx's image are lent to the Engine until the decoder's next call, so no custom allocator is installed. Upcalls: libFLAC's stream decoder reads, writes and reports errors through three. They and the system decoders' (VideoToolbox's output callback, AudioToolbox's input procedure) are what a native image must register.

## 3. Engine

Platform threads (virtual threads would pin in native calls). One `Arena` per Engine; frame buffers are pooled `MemorySegment`s.

| Thread | Work |
|---|---|
| Demux | `Container.read()` → Packet queues of selected Tracks. The containers are Java: Matroska/WebM, MP4 (fragments included), Ogg, FLAC, WAV and MP3, chosen by `Containers` from the first bytes, never the name; AVI, MPEG-TS, FLV and ASF are recognised and refused as `UnsupportedContainer`. All bytes arrive through MediaIO. Owns seek: `Container.seek`, flush queues, bump Serial. Abort is Java-side: closing the MediaIO ends a pending read with `Aborted`. |
| Audio decode | `Decoder.send/receive` → `PcmConverter`, in Java, to interleaved f32 at the device rate (every sample format, the usual downmixes, a linear resampler kept for a sink that answered another rate) → an `AudioSink`, which on the desktop is an SDL audio stream (ADR-0462). With no device to open, the SDL sink plays into a `SilentAudioSink`, in wall time, and logs one warning per process: the source plays without sound instead of failing (ADR-0487). |
| Video decode | `Decoder.send/receive` → Frame queue: converted to premultiplied BGRA with libyuv for CPU present, or the planes copied as they are for GPU present. |

**Codec resolution.** The Engine holds no codec whitelist. Per Track: ask the application's DecoderProviders, highest priority first (`supports(request)`), then the built-in ones (`BuiltInDecoders`: `dav1d`, `libvpx`, `opus`, `vorbis`, `flac`, `minimp3`, and `pcm` in Java), whatever anyone's priority says. A built-in provider answers yes only while the library is loaded, PCM always. No provider → `UNSUPPORTED_CODEC`. `MediaCapabilities` lists the demuxers and the codecs the built-in decoders handle now, for apps and diagnostics.

**Audio timing.** A frame's time is its packet's, and a container with a coarse timescale rounds those: Matroska's milliseconds stamp an Opus stream's 6.5 ms codec delay as 7. So a frame within 2 ms of where the last one ended follows it directly, and only a real gap, a seam or a seek places a frame at its own time; otherwise the sample-counted clock would jump by the rounding. Nothing before zero plays: an encoder's priming is handed over at negative times (an MP4 edit list's, a LAME tag's delay plus the decoder's 529 samples, Opus's pre-skip) and dropped there, so an MP3 with a LAME tag plays gapless.

**Master clock.** Audio: pts of last queued sample − the sink's queued duration (`SDL_GetAudioStreamQueued` on the desktop). Kept in samples, so it adds up exactly. The SDL sink reports its queue draining smoothly between the device's 1024-sample pulls, so the clock moves continuously rather than in 21 ms steps (ADR-0463); since the pulls come unevenly, the drain is a line at the stream's rate steered toward where the pulls say the device is, within 10%, which never jumps (ADR-0485), and the Engine writes to the sink and records the end it wrote to under the lock the clock reads both under. A jump of a pull passes over a picture at 60 fps. No audio Track, or the rest of a video whose audio has ended: a free-running clock over `MediaClock`, the Clock SPI, which is `System.nanoTime` unless a test hands in its own. Tests: `VirtualSink` for audio, a hand-moved `MediaClock` otherwise. **Device latency is subtracted (ADR-0474)**: the clock is what is *heard*, not what has left the queue. The sink reports `AudioSink.latencyNanos()`: for SDL, its own buffers counted in typical pulls, the median of the last fifteen (three on macOS, where SDL's CoreAudio backend keeps three AudioQueue buffers), plus the operating system's latency from an `OutputLatency` provider, asked about the default device at most once a second. `goldberry-media-platform` reads CoreAudio (device latency, safety offset, IO buffer and stream latency); a Bluetooth headset measured 264 ms. `MediaPlayer.setAudioDelay` corrects by hand, either way. The clock never reads before the last seek's target, and once the queue empties at the end it runs on through the tail in wall time, so a track ends when its last sample is heard.

**Presentation.** The `video-view` shows the newest picture with pts ≤ Master clock, and the ones it passed are dropped. As built (ADR-0463): the video decode thread converts each picture it keeps as it decodes it, so the queue holds pictures ready to blit and the borrowed frame is done with before the decoder's next call; whoever presents moves the queue on, the view when it paints and the decode thread while it waits for room, so a player with no view still reaches its end; and the view paints when the next picture falls due (`MediaPlayer.untilNextPicture()`), not on every display refresh. A picture late by a whole frame is dropped before conversion, unless it is the first since a seek or no packet waits after it.
- GPU present: upload Y and UV planes, YUV→RGB in a shader; matrix (BT.601/709/2020) and range come from the frame's colorspace fields. As built (ADR-0483, ADR-0484): `video-view` shows its pictures through `:gpu`'s video layer when `:gpu` is on the module path (`requires static`), over the rectangle `Fit` gives the picture. The frame queue holds the decoded planes while every view attached to the player draws them, and BGRA otherwise. Chroma is sited centred and sampled linearly.
- Staged planes (ADR-0662): with one GPU view attached, the view offers its layer's upload memory (`PlaneStore`, `MediaPlayer.Attachment.setPlaneStore`), and the video thread copies each picture's planes there instead of into its own slot; the UI thread records the upload with no copy (0.5 ms for a 4K picture, from 2.6 ms for NV12 and 5.1 ms for P010). The layer keeps eight mapped buffers per picture shape, made on its next render after a shape is asked for, and maps a released one again with SDL's cycling before it is handed out again; every SDL call stays on the device's thread. No zero-copy import: SDL_GPU imports no outside texture.
- CPU present: libyuv → premultiplied BGRA (alpha through libyuv's attenuate) → `BLImage`, at the decoded size; the blit scales. Used by the CPU/headless backend and by all golden tests. Chroma is interpolated as GPU present interpolates it: libyuv's `…MatrixFilter` conversions with `kFilterBilinear`, 9:3:3:1 from centred samples (NV12, which has none, is split into planes and converted as I420), built with `LIBYUV_UNLIMITED_DATA` so the blue gain of limited-range BT.709 and BT.2020 is exact rather than capped at 2.0. The two paths draw the same picture: within 2 levels a channel (3 for 10-bit, whose libyuv path truncates its last shift), held by `YuvConverterTest` and `VideoOnGpuTest` against one Java reference (ADR-0653).

**HDR and wide gamut (ADR-0659).** A picture carries a `ColorDescription` beside its matrix and range: primaries (BT.709, BT.2020, Display P3), transfer function (SDR, PQ, HLG) and the mastering and content peaks, in H.273's terms. The decoder's word comes first (dav1d's sequence header and metadata OBUs; H.264's and HEVC's VUI, HEVC's read by the full parameter set reader in `bitstream.hevc`; GStreamer's caps colorimetry and HDR metadata), and the video thread fills what it leaves unspecified from the track's (Matroska's `Colour`, MP4's `colr`, `vpcC`, `mdcv` and `clli`): VP9 signals no transfer function, so a WebM's `Colour` is its only word. Anything but SDR in BT.709 is mapped to SDR in BT.709 on both present paths by one specification (`gpu.render.ToneMapping`, `picture.hdr.ToneCurve`): to light (PQ's EOTF, HLG's inverse OETF and OOTF at 1000 nits, BT.1886's 2.4 for SDR), into BT.709's primaries, BT.2390's EETF on the largest channel from the picture's peak (mastering, else MaxCLL, else 1000 nits) to BT.2408's 203-nit white, hue kept, then the inverse 2.4. GPU present uses `yuv2hdr`/`yuv3hdr`, SDR keeps its shaders; CPU present converts such a picture with `goldberry_media_tone_map`, the library's own float pass with the same chroma interpolation, since libyuv's fixed point rounds before the mapping and PQ magnifies it. CPU present is within a level of the reference, GPU present within six of CPU present on a tone-mapped picture where they differ visibly in light. A picture with alpha is drawn as SDR.

**The picture cap (ADR-0658).** Nothing past 8K is decoded: `codec.PictureLimit`, 8192 a side and 8192 × 4352 pixels. dav1d is opened with `frame_size_limit` at the pixel count, libvpx is built with `--size-limit=8192x8192`, each built-in decoder checks the other dimension as a picture comes out, the player refuses a stated size past it as it opens, and the video thread checks every provider's picture. A refusal is `InvalidData` and fails the playback; it does not try the next decoder.

**Fallback ladder.** A provider that fails to open, or fails mid-stream, is dropped and the track reopened on the next candidate, which takes over from the next packet; the built-in decoders are the last rung. GPU canvas unavailable → CPU present: as built, a paint whose frame cannot show a GPU layer draws the picture on the CPU, and the view asks the player for converted pictures, which a seek to the position brings back (ADR-0484). A seek the Engine makes for itself (a track switch, a subtitle track, a fallback) never replaces one the application asked for.

**State.** `IDLE → OPENING → BUFFERING ⇄ PLAYING ⇄ PAUSED → ENDED`, `ERROR` from any state. Observable properties: `position`, `duration`, `bufferedAhead`, `volume`, `muted`, `rate`, `tracks`, `selectedTracks`, `videoSize`, `isLive`, `isSeekable`, `bufferedRanges`, `nowPlaying` (ICY), `error` (incl. `UNSUPPORTED_CODEC` with codec name).

**Seeking.** Requests are coalesced (only the latest runs). `MediaPlayer.seek(position, SeekMode)`: `KEYFRAME` during a slider drag shows the keyframe; `ACCURATE` on release (and the default) decodes and discards to the target pts and shows the picture that covers it. A paused player still decodes one picture after each seek, so a paused seek, and a scrub, show where they landed; audio honours the seek while paused too, so play afterwards does not replay the old position's queued samples. A play that comes before the audio thread has caught up with the seek leaves the sink stopped until it has: the sink starts only when no seek is pending or under way and the audio thread has dropped what it held from before. The seek bar learns of the release from `slider`'s commit hook (ADR-0464). Frame step: `MediaPlayer.step(n)` pauses and makes an accurate seek to the shown picture's time plus `n` picture lengths (as the video thread measured them), which lands on the first instant of the picture `n` on; clamped to the first and last pictures. An accurate seek is exact to the container's timestamps: FLAC, WAV, MP3 and MP4 count in samples, so the first sample heard is the target's; Matroska counts in milliseconds, so it lands within half a millisecond (24 samples at 48 kHz). Both are tested against the fixture corpus. An MP3 seek lands on an estimated frame: by the Xing table where there is one, else by the bit rate's arithmetic, one frame early for the bit reservoir.

**Looping.** `MediaPlayer.setLooping`: at the end the demux thread seeks to the start and queues a seam, and the passes' times run on. A pass is as long as its pictures, or its sound when it has none; at the seam the sound is fitted to it, trimmed where it runs over and filled with silence where it falls short, so the audio clock never stops.

**Rate.** v1 uses `SDL_SetAudioStreamFrequencyRatio` (pitch shifts with rate). Pitch-preserving tempo is post-v1. As built: `MediaPlayer.setRate`, 0.25 to 4, kept for the next source. The sink resamples (`AudioSink.setRate`, which a sink that cannot answers false, and the player then refuses the rate); the audio clock needs no change, since the queue is counted in stream samples; the free-running clock counts stream time at the rate; and the audio thread keeps `200 ms × rate` queued, so the device waits no shorter a wall-clock time for its next write. `SdlAudioSink`'s smoothing between pulls drains at the rate too.

## 4. I/O and network streaming

No native code performs I/O. Every Source resolves to a MediaIO, which the containers read through `ByteReader`:

| MediaIO | Backing | Notes |
|---|---|---|
| `FileIO` | `FileChannel` | Same path as network, one code path to test. |
| `HttpIO` | `java.net.http.HttpClient` | Range requests for seek; Read-ahead cache; reconnect with backoff resuming at the last byte offset; headers, auth, proxy, HTTP/2 and TLS from the JDK. Servers without Range support → `isSeekable = false`. ICY metadata stripped before the cache when the server sends `icy-metaint`, publishing `nowPlaying`; an ICY server's stream is a Live Source. |

As built (ADR-0465):

- **`HttpIO`.** The first request asks for `bytes=0-`: a `206` makes the stream seekable and gives its length, a `200` means the server ignores Range. A virtual thread fetches the first byte the reader lacks into the Read-ahead cache, at most `readAhead` (8 MB) past the reader, raised once the source is open to 30 s at its mean bitrate as far as the cache has room (`MediaIO.readAhead`, `engine.PrefetchWindow`, ADR-0657), in a cache of `cacheSize` (32 MB) that evicts other extents farthest first and then what has been read. A seek inside the cache opens no connection; one outside it abandons the connection in hand and opens a Range at the new place. A connection that fails, closes short, or delivers nothing for `stallTimeout` (10 s) is made again after a doubling backoff (250 ms to 8 s, eight in a row), resuming at the byte it broke at; a `4xx` other than 408 and 429 is final at once. A read waits no longer than the Source's timeout. Plain `http:` speaks HTTP/1.1, so a radio server is never offered an `h2c` upgrade.
- **ICY** is not a MediaIO of its own. `HttpIO` asks with `Icy-MetaData: 1`, and when `icy-metaint` comes back reads the body through `IcyStream`, which takes the metadata out before the cache. Titles are kept by the offset they took effect at, so `nowPlaying` is the title at the demuxer's position, not the fetcher's. A station that hangs up is a drop, and reconnected.
- **Live.** `isLive` is true for an ICY server, or a response with neither a length nor Range. A server that ignores Range but gives a length is unseekable and not live: it ends.
- **Buffering.** The high Water mark: BUFFERING becomes PLAYING when every track has been demuxed `highWaterMark` past the clock, the source has ended, or the queues are full. The low Water mark is empty: a track stalls when its decoder runs out of packets with more to come, and only then does playback go back to BUFFERING, with the sink paused and the free-running clock held. `bufferedAhead` is the least any playing track has demuxed past the clock.
- **`bufferedRanges`** are the Read-ahead cache's real byte ranges, mapped to time in proportion to the source's length and duration: exact for a constant bit rate, close otherwise. Empty for a local file, and for a source whose length or duration is unknown. The seek bar draws them as `slider` spans, under the fill (ADR-0466), and the widgets look again every quarter second while a fetch goes on.
- WebM over HTTP: the demuxer seeks to the Cues at the tail on open; `HttpIO` serves that as one extra Range request and caches it.
- Opening reads what the container's header needs and no more: there is no probe that decodes ahead. A WAV is its header; an MP3 its tags and first frames, and its ID3v1 tag from the end where the source can seek.
- Out of scope by decision: RTSP/RTP (no free-codec content; would need a Java RTSP client).
- Post-v1: HLS/DASH with playlist/manifest handling in Java feeding segments through MediaIO. Because segment selection is ours, ABR becomes possible. Free-codec HLS/DASH content is rare today, hence not v1.

## 5. Extensibility: bring your own codec

Goldberry publishes only royalty-free natives and a neutral interface. Anything patented is brought and answered for by the app that wants it, or by the operating system's own decoders. The single supported mechanism is the **Decoder SPI**; rebuilding or swapping `libgoldberry-media` is not supported (the published codec set is the only one the Engine is tested against).

**Decoder SPI.** Discovered via `ServiceLoader`, consulted before the built-in decoders.

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
decoder on a seek, so a provider never sees one. The built-in decoders are
`DecoderProvider`s like any other, listed by `BuiltInDecoders.providers()`, and
always asked last.

`CodecId` is Goldberry's own enum, named with FFmpeg's codec names as the vocabulary applications and errors already speak; each container maps its own identifiers onto it (Matroska's `CodecID`, MP4's sample entries). `TrackParams` carries what the container states about the codec: dimensions, pixel/sample format, profile, level, bit depth, sample rate, channel layout, colorspace. `Packet` and `Frame` wrap native `MemorySegment`s; a provider may decode straight from the packet buffer without copying.

Frame contract: video as NV12 / I420 / P010 / I010 planes in native `MemorySegment`s with pts, colorspace, range and `ColorDescription` (unspecified is filled from the track's) (I010, 10-bit planar 4:2:0, is what dav1d and VP9 profile 2 produce; VP9's and Theora's 4:2:2 and 4:4:4 are subsampled to 4:2:0 with libyuv; Theora is libtheora's (`libtheora` provider), cropped to its picture region); audio in any of twelve `SampleFormat`s (u8, s16, s32, s64, f32, f64, each interleaved or planar) with pts, rate and channel count. The Engine's one resampling pass converts all of them, so no provider converts, and the built-in decoders lend their library's buffers without a copy. A frame is borrowed until the decoder's next call. A provider that decodes on the GPU performs its own Copy-back; a provider failing in `open` or mid-stream drops the Engine to the next provider (Fallback ladder: provider → next provider → built-in). Hardware decoding inside a provider is the provider's business.

Container reach: Matroska and MP4 deliver whole frames plus container extradata (avcC/hvcC/esds), so H.264/HEVC/AAC in MP4/MKV reach a provider untouched. Formats that need a parser to frame the stream (MPEG-TS, raw Annex B, ADTS) are out of scope: there is no Java reader for them.

Providers. **Shipped:** the operating system's decoders in `goldberry-media`'s `…media.platform` ([ADR-0472](../book/src/adr/0472-the-platform-decoders-bind-the-system-frameworks.md); a module of their own until [ADR-0493](../book/src/adr/0493-the-platform-decoders-are-part-of-media.md)), found by `ServiceLoader` or listed by `PlatformDecoders.providers()`. The OS vendor holds the licences. On macOS: `videotoolbox` (H.264 and HEVC, 8- and 10-bit 4:2:0, lent as NV12 or P010 straight from the pixel buffer, reordered by the SPS's reorder depth) and `audiotoolbox` (AAC, AC-3, E-AC-3, f32 in FFmpeg's channel order). On Linux (ADR-0489): `gstreamer-video` and `gstreamer-audio`, a GStreamer pipeline per track with the decoder GStreamer ranks highest at the stream's size (gst-libav, a VA-API or NVIDIA decoder, `openh264dec`, `faad`, `a52dec`), replaced by the next if it fails before its first frame, colour from the stream's VUI before the decoder's caps. On Windows (ADR-0489, written and not yet run there): `mediafoundation-video` and `mediafoundation-audio` over Media Foundation's MFTs, held back behind `-Dgoldberry.media.mediaFoundation=true`. The system libraries are bound with FFM, as the media natives are, and no native code is built for them. Every one copies back to system memory: SDL_GPU imports no outside texture, and GPU present stages the copy into upload memory instead (ADR-0662). **Intended:** a Cisco OpenH264 provider fetching Cisco's binary on first use (terms and profile support to be verified); commercial SDKs licensed by an app vendor.

**Shipped, on the GPU:** `vulkan-video` (ADR-0663), `…media.platform.vulkan.VulkanVideoProvider`, on Linux and Windows wherever the Vulkan driver has a decode queue. Vulkan Video decodes one picture at a time and is told everything, so the parameter sets, slice headers, order counts, reference sets and output order are worked out in Java (`…media.bitstream.h264`, `.hevc`, `.vp9`, `.av1`; `…platform.vulkan.h264`, `.hevc`, `.vp9`, `.av1`), on generated FFM bindings of the system's Vulkan headers (`tools/media/generate_vulkan.py`). One device a process; one session a stream, decoding on the decode queue and copying the visible picture back on the copy queue, NV12 or P010. Priority −10: the next rung after the system decoders, before `ffmpeg-cli`. It supports nothing under `HardwareDecoding.OFF`. `-Dgoldberry.media.vulkanVideo` is `auto` (H.264 and HEVC), `all` (VP9 and AV1 too, which the built-in decoders otherwise decode exactly) or `off`. H.264: progressive 8-bit 4:2:0; HEVC: Main and Main 10; VP9: profiles 0 and 2; AV1: Main, 8 or 10 bits, without film grain or superres (dav1d's).

**Shipped, off by default:** `ffmpeg-cli` (ADR-0661), `…media.ffmpegcli.FfmpegCliProvider`. It runs an `ffmpeg` the user installed, one process per track, named by `-Dgoldberry.media.ffmpeg` or passed to `FfmpegCliProvider.of(Path)`. It is the last rung before `UNSUPPORTED_CODEC`: priority −100, below the system decoders, and it never claims a royalty-free codec. Packets go to its stdin as a Matroska stream, and frames come back on stdout as another, read by the Java demuxer: NV12, or P010 past 8 bits, and interleaved f32 in FFmpeg's channel order. A frame is lent until the next call. A non-zero exit or an end with nothing decoded drops the Engine to the next rung. Nothing is linked or shipped: whoever installs the program answers for its codecs.

I/O has the same shape already: a custom `MediaIO` registered for a URL scheme adds a protocol without touching natives.

## 6. Widgets

| Widget | Content |
|---|---|
| `video-view` | Surface only. `fit: contain \| cover \| fill`. |
| `media-controls` | Composed from core widgets: play/pause, seek slider (buffered range, hover-time tooltip), elapsed/remaining labels, volume button + fader popover, mute, rate menu, audio/subtitle Track menus, loop, fullscreen. Auto-hides on pointer idle. |
| `media-player` | `video-view` + `media-controls` overlay + subtitle overlay. |
| `audio-player` | Compact `media-controls`; optional cover art from the attached-picture stream; `nowPlaying` line for Live Sources. |

Keys: Space (and K) play/pause, ←/→ ±5 s, ↑/↓ volume, M mute, Home to the start, Page Up/Page Down the chapter before and the next (only when the source has chapters, so they scroll otherwise), `,` `.` a picture back and on (pausing), `<` `>` slower and faster through 0.25–2; `F` fullscreen and `Esc` out of it, in `media-player` where the host has a window (ADR-0473): a copy of the player covers the window through `Host.fill`, `.is-fullscreen`, and the window is asked to fill its display; leaving gives the window back as it was, and the platform's own way out takes the copy away. The rate shows as `.media-rate` beside the times when it is not 1. Answered by the widget's own focusable node, where a key bubbles to from a control that does not want it.

Audio track menu, as built (ADR-0467): a `select` of the audio tracks (`.media-audio-track`), for a source with two or more, labelled by title and language (`Track.title`, `Track.language` from the stream's metadata). Choosing one is `MediaPlayer.selectTrack`: the demux thread retires the audio thread, starts one on the new track and seeks accurately to the position.

Video track menu, as built (ADR-0469): the same for video tracks (`.media-video-track`), in `media-player` and `media-controls` only, cover art never offered. The demux thread retires the video thread, releases the frame queue's waiters, starts a new thread on the **same** frame queue and seeks accurately to the position, so the old track's last picture stays up until the new track's picture covering the position replaces it.

Subtitles: text formats decode to ASS events → tags stripped → drawn by Goldberry's text stack as an overlay. External `.srt` / `.vtt`. Bitmap subtitles (PGS/DVB): post-v1. As built (ADR-0468): no native subtitle decoder; a text subtitle packet is one cue (SubRip, WebVTT, ASS's text field, `mov_text`), parsed in Java with external SubRip and WebVTT files into plain-line `Cue`s. The demux thread collects a chosen track's cues into a timeline; `MediaPlayer.selectTrack` chooses a subtitle track, `loadSubtitles` a file, `hideSubtitles` none, and `currentSubtitles()` is what shows at the clock. `media-player` draws the lines over the foot of the picture (`.media-subtitles`, `.media-subtitle`), above the controls or lower while they hide, and it and `media-controls` have a subtitles menu (`.media-subtitles-menu`).

Chapters, as built (ADR-0655): `MediaInfo.chapters()`, each a `Chapter(start, end, title)`, settled once by `chapter.ChapterList` (sorted, duplicates and those past the end dropped, unstated ends from the next start or the duration). Read from Matroska `Chapters` (the default edition's top-level atoms, through the SeekHead when after the clusters), an MP4's QuickTime chapter track (`tref/chap`, then not listed as a track) or Nero `chpl`, ID3v2 `CHAP` frames titled by `TIT2`, and the Xiph `CHAPTERnnn` comments of Ogg (Opus, Vorbis, FLAC) and bare FLAC. `MediaPlayer.nextChapter()` / `previousChapter()` (back to the current start after 3 s, else the one before). The seek bar notches each start but the first (`Slider.marks`, `slider-mark`), and `.media-chapter` is the playing chapter's title after the elapsed time.

Java + KDL + CSS parity as for all widgets. Component tokens `--gb-media-*`. Icons from Lucide.

## 7. Key scenarios

**S1. Local file, GPU present.** App sets a Source on the Engine → OPENING; the Source resolves to a `FileIO`; `Containers` sniffs the first bytes and opens the Java container, which lists the Tracks; the Demux thread selects default audio/video Tracks and fills Packet queues → BUFFERING until the high Water mark → PLAYING. The video decode thread decodes with dav1d or libvpx and queues the planes in the Frame queue. Audio decode thread feeds SDL; the audio clock becomes Master clock. `video-view` presents via GPU present on each frame tick.

**S2. Scrub and release.** User drags the seek slider: coalesced keyframe seeks, each bumps the Serial; stale packets and frames are dropped; the keyframe is shown while PAUSED-for-seek. On release, one accurate seek decodes and discards to the target pts, then PLAYING resumes against the Master clock.

**S3. Progressive HTTP over a flaky link.** Source resolves to `HttpIO`. The connection drops; the Read-ahead cache drains, Packet queues fall below the low Water mark → BUFFERING. `HttpIO` reconnects with backoff and resumes by Range at the last byte offset; queues refill past the high Water mark → PLAYING. The seek slider shows `bufferedRanges` from the Read-ahead cache; a seek inside a cached range issues no request, a seek outside it opens a new Range under a new Serial.

**S4. Fallback ladder.** A system decoder fails to open or fails mid-stream → the next provider, then the built-in decoder, takes over from the next packet, no state change visible to the app. GPU canvas absent (headless backend) → CPU present.

**S5. Deterministic golden test.** CPU present, virtual Master clock. Test advances the clock to fixed pts values and asserts byte-exact `BLImage` output per platform (software AV1 and VP9 decode is bit-exact by specification, and the decoders are held to FFmpeg's `framemd5` of the same clips).

**S6. Live Source (internet radio).** Source resolves to `HttpIO`, which finds `icy-metaint` in the response → `isLive = true`, `isSeekable = false`; `audio-player` hides the seek slider, shows a LIVE badge and the `nowPlaying` line from ICY metadata (`media-player` shows it in its overlay); after the initial BUFFERING the Water marks only trigger on network stalls.

**S7. Unsupported codec.** User opens an MP4 with H.264/AAC on a system with no decoder for them. The MP4 reader lists the Tracks; no provider supports them → ERROR with `UNSUPPORTED_CODEC(h264, aac)`; `media-player` shows the codec names in its error state. Every chosen track is checked before any plays, so the error names every codec that has no decoder; a file where only one of the two is missing fails too, rather than playing half of it.

**S8. Bring your own codec.** An app adds a DecoderProvider for H.264/AAC on the module path. User opens the MP4 from S7: the MP4 reader lists the Tracks; Codec resolution finds the provider (`supports` → true), opens a Decoder per Track with the container extradata; decode threads run `send/receive` exactly as with a built-in decoder; frames enter the Frame queue, Master clock and Present path unchanged. The provider fails mid-stream → Fallback ladder tries the next provider, finds none → ERROR `UNSUPPORTED_CODEC`.

## 8. Phases and exit criteria

The FFmpeg-direct phases (natives and bindings, audio and the Decoder SPI, video with CPU present, GPU present, hardware decoding, network, tracks and polish) were all built before 2026-10-24. The engine that replaced them is planned in `ffmpeg-out.md` §6, and its status is that file's §9.

Post-v1: HLS/DASH in Java (with ABR), zero-copy GPU import of a system decoder's frames (blocked on SDL_GPU importing outside textures; see `ffmpeg-out.md` §9), an HDR output path for HDR displays, bitmap subtitles, pitch-preserving rate, playlists.

## 9. Testing

Per TESTING.md. Additions: a small CC-licensed fixture corpus (one clip per container/codec pair, ≤ 2 s each). Every container is held to `ffprobe -show_packets` of the same files, packet for packet: stream, pts, size, keyframe, and duration where the container states one. dav1d and libvpx are held to FFmpeg's `framemd5` of the same clips byte for byte, and the audio decoders to test tones sample for sample. A fault-injecting fake MediaIO (stalls, drops, short reads, no-Range) serves S3/S6, with a local HTTP server (`com.sun.net.httpserver`) that drops, stalls, ignores Range and speaks ICY. The layout check runs against linux-x64's committed `media-layout.properties` and, on each lane, the one its build wrote. Tests that need the library skip without it (`MediaNativesRequirement`), and the Media workflow fails a skip it was built to run. All goldens run on CPU present.

## 10. Risks

| Risk | Mitigation |
|---|---|
| Free-codec-only excludes most mainstream content (H.264/AAC MP4, public HLS, IP cameras) | Explicit scope; clear `UNSUPPORTED_CODEC` errors; the system decoder providers ship (§5), and the Decoder SPI takes an application's own. |
| Java demuxers meet files FFmpeg's would have played (edit lists beyond a shift, several `moov`s, damaged Matroska) | Every container is held to `ffprobe` on the fixture corpus; what a reader does not support is ignored where playing on is right and refused with a reason where it is not. |
| Own HTTP streaming logic (Range, reconnect, cache) | Small, pure Java, fully testable with a fake MediaIO; no native network code at all. |
| Hand-written struct layouts drift from the built headers | Layout probe in the superbuild; the check at load refuses a library whose structs, constants or ABI number disagree, before any call. |
| The Windows build (libvpx's configure script, dav1d's meson, MinGW) | One lane in MSYS2's UCRT64 with the runtime linked statically; `media.yml` and `publish.yml` have not run on the runners yet. |
| A container's coarse timestamps against a sample-counted clock | Frames within 2 ms of the last one's end follow it (§3, Audio timing). |
| Decoder SPI adds an indirection on the hot path | One interface call per packet/frame, negligible against decode cost; frames stay in native memory (`MemorySegment`), no copies introduced by the SPI. |
| An upstream's struct or API changes | Each upstream is pinned, by tag or by commit; an upgrade re-runs the layout probe, and the check at load refuses a library whose structs have moved under the bindings. |
| Licensing | Every upstream BSD-2, BSD-3 or CC0, linked statically into one library; `media-NOTICE.txt` carries their licences. No patent-pool codecs shipped. |
| GraalVM native-image | `:media:foreignMetadata` registers every downcall and upcall of the media bindings (`MediaDescriptors`) and of the system decoders (`PlatformDescriptors`), and the natives jar's resources. |

## 11. Decision record: FFmpeg-direct over libVLC

> Kept as history. What it decided against libVLC stands; what it decided for
> FFmpeg was reversed by ADR-0651 (`ffmpeg-out.md`): the reference decoders
> bound directly, with the demuxing in Java.

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
