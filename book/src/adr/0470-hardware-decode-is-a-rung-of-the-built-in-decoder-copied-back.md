# 470. Hardware decode is a rung of the built-in decoder, copied back

Date: 2026-09-23

## Status

Accepted. Phase 5 of `goldberry-media` (`docs/media-plan.md`): hardware decode,
with copy-back and the fallback ladder of `docs/goldberry-media.md` §3.

## Context

VP9 and AV1 at 4K are more than a laptop's CPU should spend its battery on, and
every platform Goldberry targets has a video engine that decodes them:
VideoToolbox on macOS, D3D11 on Windows, VAAPI on Linux. FFmpeg drives all
three through `hwaccel`s. Each is a decoder that is handed a device in
`AVCodecContext.hw_device_ctx`, asks for a surface format through the
`get_format` callback, and returns frames that are surfaces on the device
rather than pixels.

There is no GPU present yet. Phase 4 waits on M4, so a surface cannot be drawn
where it is. Hardware is also unreliable in ways software is not. A device
opens and then cannot decode the codec: an M1 has VideoToolbox and no AV1
engine. Drivers fail mid-stream. And a device's output need not be the same
bytes as software's, which is what the goldens compare.

## Decision

**Copy-back, always.** Every hardware frame is copied into system memory with
`av_hwframe_transfer_data`. That gives NV12 for 8-bit and P010 for 10-bit, both
already in the frame contract, with `av_frame_copy_props` for the timestamps
and colour. From there it is lent and presented exactly as a software frame is.
CPU present needs nothing new. GPU present (phase 4) will be able to skip the
copy when it exists.

**One more rung on the ladder, the built-in decoder's.** The ladder becomes
*providers → FFmpeg on the device → FFmpeg in software*. The hardware rung
exists for a video track when hardware decode is on and this build has a
hardware path for the codec on the platform's device type. A mid-stream failure
on the device walks one rung down, like a provider's failure.

**The hardware rung chooses its own decoder.** `avcodec_find_decoder(AV1)` is
`libdav1d`, which has no hardware path. FFmpeg's own `av1` decoder is hardware
only. `HardwareDecoder.choose` therefore looks through every decoder of the
codec for a `HW_DEVICE_CTX` configuration of one of the wanted device types.
The software rung stays `avcodec_find_decoder`'s.

**`get_format` is an upcall that asks for the device's format, and otherwise
asks FFmpeg.** When the device's surface format is offered it takes it. When it
is not, because FFmpeg could not start the device for this stream and has
offered again without it, the upcall hands the list to
`avcodec_default_get_format`. So VP9 on a device with no VP9 engine decodes in
software inside the same decoder, and nothing fails. An empty list is answered
`NONE` without asking, because FFmpeg's default reads the last entry. Nothing
is thrown into C. The stub is bound to its decoder (ADR-0017) and freed after
the codec context.

**Failures on the device are thrown for the ladder, not as playback errors.**
On the hardware path, `send`, `receive` and copy-back throw `FfmpegException`,
not `MediaException`. The video thread then reopens the track on the next rung.
The new decoder has none of the pictures the next packets refer to, so the
thread drops the queued packets and makes an accurate seek. If no picture has
been shown since the last seek, the target is that seek's target (or the
start). Otherwise it is the position. A device that cannot decode the codec
fails on its first packet, before anything has played, so the seek back to the
start is not heard.

**What failed is remembered, per process.** A codec and device type that
failed before giving a picture, or that FFmpeg declined in `get_format`, are
recorded in the `Hardware` policy. The next decoder of that codec goes straight
to software. `HardwareDecoding.AUTO` is one policy per process. The rung itself
is counted whether or not it failed before (it then opens in software), so the
ladder's positions do not move between the first open and a fallback.

**`HardwareDecoding.AUTO` by default, `OFF` for determinism.** An application
gets hardware decode without asking. Tests that compare pictures byte for byte
ask for `OFF`.

**On where the OS provides it.** The media superbuild enables VideoToolbox on
macOS and D3D11VA on Windows by default: system frameworks, nothing to install
or ship. It stays off on Linux. VAAPI is `libva`, which `libavutil` would then
link directly, and a machine without it could not load FFmpeg at all, not even
to play audio. `-Pgoldberry.media.hwaccel` overrides either way.

The decoder names itself after what it does: `ffmpeg (videotoolbox)` while its
pictures come from the device, and `ffmpeg` once they do not.
`PlayerStatus.videoDecoder` follows it.

## Alternatives considered

- **Present surfaces directly (zero-copy).** This needs GPU present and
  platform interop (IOSurface, D3D11 shared handles, dma-buf), which is post-v1
  in the design. The copy costs about a frame's worth of memory bandwidth, which
  is small next to the decode it saves.
- **Hardware as a `DecoderProvider`.** The SPI's request does not carry
  `AVCodecParameters`, and the device, the `get_format` stub and the codec
  context would all have to be rebuilt outside the built-in decoder. The design
  keeps OS decoders (Media Foundation, VideoToolbox proper) for an optional
  provider module post-v1. FFmpeg's hwaccels belong to the built-in decoder.
- **Probe the device's codec support before choosing.** FFmpeg has no portable
  query, and VideoToolbox's is behind the hwaccel's own initialisation. The
  first packet is the probe, and the record keeps it to once per process.
- **Fail the playback when the device fails.** This contradicts S4. The
  application sees no difference between software and hardware decode except
  in the CPU meter.
- **Link VAAPI on Linux and load `libva` lazily.** FFmpeg links it at build
  time. Making it lazy means patching FFmpeg, which the LGPL allows and the pin
  discipline (ADR-0030) does not want. This is left open.

## Consequences

- `MediaPlayer.Builder.hardwareDecoding(HardwareDecoding)`, `AUTO` by default.
- Seven more FFmpeg functions (`avcodec_get_hw_config`,
  `avcodec_default_get_format`, `av_hwdevice_find_type_by_name`,
  `av_hwdevice_ctx_create`, `av_hwframe_transfer_data`, `av_frame_copy_props`,
  `av_buffer_unref`), one more struct (`AVCodecHWConfig`), two constants, and
  one more upcall shape in the native-image metadata. The macOS libraries grow
  by 43 KB and link VideoToolbox, CoreMedia and CoreVideo.
- On an M1, VP9 decodes on VideoToolbox, 8-bit and 10-bit. Its luma is the
  software decoder's byte for byte, and its pictures pass the software goldens.
  AV1 falls to dav1d on the first packet, once per process.
- A mid-stream fallback seeks, so for a moment the audio restarts at the
  position, as with a track switch (ADR-0469).
- The design's exit criterion for phase 5, 4K60 without dropped frames on GPU
  present, waits on phase 4. What can be measured without it, CPU time with the
  device against without, is recorded in the plan.
- Linux hardware decode is open: an opt-in build flag today, and a decision
  about `libva` before it is on by default.
