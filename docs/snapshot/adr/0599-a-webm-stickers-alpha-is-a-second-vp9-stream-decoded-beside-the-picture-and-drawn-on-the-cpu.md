# ADR-0599: A WebM sticker's alpha is a second VP9 stream, decoded beside the picture and drawn on the CPU

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** docs/gaps.md (G55),
  [ADR-0594](0594-a-lottie-animation-is-read-in-java-and-drawn-through-the-toolkits-own-painter.md)

## Context

G55 asks for Telegram's animated stickers. ADR-0594 closed the `tgs` half
with `VectorAnimation` and `AnimationView`, and listed what the `webm` half
would take in `:media`. The alpha never reached a decoder:

- `Demuxer.read` copied data, size, timestamps and the key flag, and dropped
  the packet's side data. The bundled FFmpeg (n8.1.3) does put a Matroska
  BlockAdditional there as `AV_PKT_DATA_MATROSKA_BLOCKADDITIONAL`, and nothing
  bound `av_packet_get_side_data`.
- VP9 decodes through FFmpeg's own `vp9` decoder, found by codec id, which
  reads no alpha. libvpx, which does, is not in the build: alone it is about
  7 MB, the size of the whole size gate.
- `PixelFormat` had four opaque layouts. `VideoConverter.toBgra` wrote opaque
  pixels, and the GPU path (`PlaneLayout`, `yuv.hlsli`, `VideoLayer` with
  `REPLACE`) is opaque too.

Tessera holds a sticker's bytes in memory, loops it, plays it silent and shows
several in a timeline. It wants the same widget for a `tgs` and a `webm`, with
`autoplay(false)` and reduced motion followed the way `AnimationView` follows
them.

## Decision

### The side data travels on the packet

`AvCodecCalls.PacketGetSideData` binds `av_packet_get_side_data`. The type is
`FfmpegConstants.pktDataMatroskaBlockAdditional`, read from the layout probe
like every other constant: `ffmpeg_layout.c` reports
`AV_PKT_DATA_MATROSKA_BLOCKADDITIONAL` (15 in avcodec 62).

The payload is what `matroska_parse_block_additional` in FFmpeg's
`libavformat/matroskadec.c` writes: `AV_WB64(side_data, id)`, then the bytes.
`Demuxer.read` keeps the bytes after an 8-byte big-endian BlockAddID of 1,
which is where WebM keeps a picture's alpha (libvpx's decoder checks the same
ID). Any other ID is left alone.

The bytes go on `codec.Packet` as `alpha()`, `MemorySegment.NULL` when there
are none. `Packet.withAlpha(segment)` returns a packet that carries them and
takes over the release, so the alpha lives as long as the data and the
packet's owner frees both. **`AVPacket`'s layout does not change.** The
function reads `side_data` itself, so `FfmpegStructs` keeps its padding there
and nothing is added to the struct check.

### The track says it has alpha

matroskadec turns `AlphaMode` into the stream's metadata, `alpha_mode` = `1`
(`av_dict_set_int(&st->metadata, "alpha_mode", 1, 0)`), and sets nothing in
the codec parameters. So the demuxer reads it the way it reads `language` and
`title`, and `TrackParams.Video` gets a component, `alpha`. The two existing
shorter constructors stay and mean an opaque track. A provider sees the flag
in its `DecoderRequest`.

A track without it takes the path it took before: one decoder, the thread
count and hardware rungs as they were, I420 or I010 out.

### A second `vp9` decoder decodes the alpha

`Decoders.open` gives a track with alpha no hardware rung and opens the
built-in decoder with `FfmpegDecoder.open(…, Hardware.OFF, alpha)`. That opens
a second context of the same codec from the same parameters, for the alpha
stream. Both run one thread, so a packet's picture and its alpha come out
together. With frame threads, the two queues could disagree about which
picture is ready. `send` passes the packet's data to the first decoder and
`alpha()` to the second. `receive` takes the picture, then the alpha
decoder's picture, and pairs them when size and timestamp agree.

The alpha picture's luma is the alpha plane. The picture is lent as the new
**`PixelFormat.I420A`**: I420's three planes, then a full-resolution plane of
straight 8-bit alpha. Colour that is not I420 (VP9 profile 1 or 2) is
converted to I420 first. `FfmpegConstants.Video` maps it to
`AV_PIX_FMT_YUVA420P`, also read from the probe, so a decoder that produced
`yuva420p` itself would be lent the same way.

A picture whose alpha is missing, or does not decode, is lent opaque, and an
alpha stream that is not 8-bit is logged once and ignored. libvpx's own
wrapper drops such a picture instead. Here a damaged alpha stream does not
stop the video.

### Premultiplied on the way to BGRA

swscale converts `yuva420p` to BGRA with straight alpha. `toBgra` then
multiplies each pixel's colour by its alpha with `(c × a + 127) / 255`, the
rounding `Image` uses, since the toolkit's images and blits are premultiplied
(`PixelFormat.BGRA32_PREMULTIPLIED`). Opaque pixels are skipped and
transparent ones become zero.

`VideoConverter` gained an `exact` switch. The default stays bit-exact for the
goldens. `false` drops `SWS_BITEXACT | SWS_ACCURATE_RND | SWS_FULL_CHR_H_INT`.
Measured here on a 512×512 sticker, the conversion fell from 2.2 ms to 0.4 ms
a picture, premultiplying included. Its arena is shared now. It is still used
by one thread at a time, but a cleaner may close it from another.

### Drawn on the CPU, routed at the video layer

A picture with alpha never goes to the GPU:

- `FrameQueue.Shape.of` converts an `I420A` frame whatever the player's
  `PictureForm`. A view that asks for planes must draw a `VideoPicture` too,
  so nothing new is asked of it.
- `VideoPicture` has `opaque()`. A slot records it when it converts.
- `GpuVideoPresenter.place` declines a picture that is not opaque before the
  layer sees it, and reports "not on the GPU" as it does for a frame without
  a device. `VideoSurface` then blits it, and Blend2D's blit is source-over,
  so the picture is drawn over the box's background.

The decision sits in the presenter and the queue, not in how the frame
clips. A rounded clip that keeps GPU layers changes nothing for a sticker.

**The GPU path (a fourth texture and a `yuv` shader with alpha) is not
built.** Compiling shaders here rewrites the shipped DXIL unsigned, which
Direct3D 12 refuses in a release, so a shipped shader change has to be
compiled on macOS. A 512×512 sticker costs the CPU about 1.5 ms a picture,
and the CPU path is enough.

### The sticker is a moving picture, and `AnimationView` plays it

`dev.goldberry.image.anim.MovingPicture` is new in `:core`: `width()`,
`height()`, `isDoneAt(elapsed)` and `paint(frame, elapsed, x, y, w, h)`.
`VectorAnimation` implements it unchanged, and `AnimationView`,
`AnimationPaint` and its `Moment` take a `MovingPicture`. So the view's
frame-loop playing, `autoplay(false)`, reduced motion, `loops`, alt text and
sizing apply to anything that implements it.

`dev.goldberry.media.VideoAnimation` is the second one:

- `of(ByteBuffer)` and `of(byte[])` copy the bytes and open a `Demuxer` over
  them (a private `BytesIO`), select the default video track, and open its
  decoder through `Decoders`, so the alpha path above is the one used. It
  throws `MediaException` when the bytes are not a video this build decodes,
  including when FFmpeg is not loaded.
- `paint` decodes on the calling thread, from the picture shown to the one
  for the moment asked: the last picture whose time is at or before it. An
  earlier moment seeks back to the start. The picture is converted into one
  premultiplied `PixelBuffer` and drawn with `Frame.drawImage`. `imageAt`
  returns a copy for a caller that keeps a still.
- One pass is the track's duration, else the container's, else how long its
  packets last. It is endless until `loops(n)`, as `VectorAnimation` is.
- `close()` frees the natives. A `Cleaner` frees them for an animation
  nothing refers to, since a widget tree does not close what it drops.
- Sound is never selected.

This is not a `MediaPlayer` because a player per sticker is a demux thread, a
video thread and an audio sink of its own.
Its source is a URI, and it has no autoplay or reduced motion.
`video-view` also paints the black background `media.css` gives it.
`AnimationView` already settles all of those for the Lottie half.

The player plays an alpha WebM correctly too. `video-view` blends it over
its background, which stays black unless a stylesheet says
`background-color: transparent`.

### No platform decoder claims an alpha stream

`GStreamerVideoProvider`, `VideoToolboxProvider` and the Media Foundation one
claim H.264 and HEVC with a configuration record (`ParameterSets.of`), and
never VP9. An alpha WebM always reaches the built-in decoder. The hardware
rungs that do decode VP9 (VideoToolbox, D3D11) are skipped for a track with
alpha by `Decoders`.

## Consequences

- **Native: two lines in the FFmpeg layout probe, nothing else.** FFmpeg is
  not recompiled, the superbuild's flags are unchanged, and libgoldberry's
  ABI stays 22. The new constants are required: a layout file without them
  makes `FfmpegConstants.from` refuse to load FFmpeg, and every FFmpeg test
  skips or fails. So, in this order:
  1. the main tree's probe is rebuilt, `cmake --build
     media/build/ffmpeg/linux-x64/cmake --target install`, which rewrites
     `ffmpeg-layout.properties` without compiling FFmpeg (in this worktree the
     probe was compiled by hand against `media/.deps/linux-x64/stage/include`,
     and its output matched the installed file plus the two new lines);
  2. the macOS fixture, `ffmpeg-layout-macos-aarch64.properties`, has the two
     lines already: enum values are the same on every target;
  3. CI's media cache key hashes `media/src/main/cmake/**`, so `media.yml`
     rebuilds FFmpeg and the probe on all three runners by itself;
  4. a `goldberry-media` jar with this change refuses an `ffmpeg-<target>`
     natives jar built before it, which matters to any downstream that pins
     natives separately.
- **Not done:**
  - The GPU path, above. `media.gpu.PictureRenderer` hands a picture with
    alpha to the layer as BGRA, unchanged.
  - Alpha in 10 bits, and alpha as planes (`PictureForm.PLANES`).
  - VP8 with alpha. The path does not depend on the codec, and FFmpeg's `vp8`
    decoder would decode the alpha stream the same way, but no fixture tests
    it.
  - A bound on how many stickers decode at once. Each visible
    `VideoAnimation` decodes on the UI thread when its frame paints it. It
    joins ADR-0594's unbounded Lottie stickers, and the frame budget is
    still a question for the loop.
  - Two views showing one `VideoAnimation` at two moments seek and decode
    against each other. The class says a view wants an animation of its own.
- **Where the prediction differed:** ADR-0594 expected the side data to need
  `AVPacket` named in `FfmpegStructs`. The function reads it, so only the
  constant is new. `alpha_mode` is stream metadata, not a codec parameter.
  The proposed `I420A` "through the layout probe" is a constant in the probe
  (`AV_PIX_FMT_YUVA420P`), not a layout.
- **Tests.** The fixture `sticker-vp9-alpha.webm` (1 KB, scripted in
  `make-fixtures.sh`) is lossless VP9 with alpha, 64×64 at 10 fps for one
  second. It is red, with an opaque square moving 4 pixels a picture, half
  alpha in the bottom rows, and transparency elsewhere.
  - `ffi.AlphaDecodeTest`: the track's flag and a VP9 frame of alpha on every
    packet, none on `clip-vp9.webm`; every pixel of the alpha plane of all ten
    pictures; opaque I420 without the flag; premultiplied conversion; the
    rounding.
  - `VideoAnimationTest`: size, duration and flag; pictures at moments,
    looping and rewinding; an `AnimationView` over a blue background showing
    through and blending; a loop and a single pass on the `Filmstrip`'s
    virtual clock; `autoplay(false)`; close; refusals.
  - `VideoPlaybackTest`: the player hands out premultiplied pictures with
    `opaque()` false, converted even for a view of planes, and an opaque clip
    stays opaque. `MediaWidgetsTest`: `video-view`'s painter blends a sticker
    over a background.
  - `GpuVideoTest`: the presenter declines a picture with alpha before the
    layer. On the GPU lane, `VideoOnGpuTest`: a sticker in a frame that shows
    layers places none and blends on the CPU.
  - `AnimationViewTest`: a `MovingPicture` that is not Lottie plays and
    stops. `PicturesTest`: `I420A` has no GPU layout.
- The guide text is parked in `docs/snapshot/components-media-video-sticker.md`.
