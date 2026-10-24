# ADR-0654: Hardware decoding reaches the decoders

- **Status:** Accepted
- **Date:** 2026-10-24
- **Relates to:** ADR-0651 (FFmpeg leaves), ADR-0470 (hardware decoding with FFmpeg's hwaccels), ADR-0472 and ADR-0489 (the system decoders)

## Context

`HardwareDecoding { AUTO, OFF }` is public API: `MediaPlayer.setHardwareDecoding`,
its getter, and `Builder.hardwareDecoding`. With FFmpeg it chose between
FFmpeg's hwaccels (D3D11, VideoToolbox, VA-API) for VP9 and AV1 and its
software decoders, and the tests set `OFF` because software decoding is
bit-exact.

When FFmpeg left (ADR-0651), the setting was kept and read by nothing. The
built-in decoders are software; the system decoders, which can decode on the
video engine, never saw it, because `DecoderRequest`, all a provider is given,
had no field for it. Its own documentation said it was kept "for a provider
that honours it", which no provider could.

Nobody noticed because today no track has two candidates: the royalty-free
codecs go to the built-in decoders, the patented ones to the system's. It
starts to matter when the system decoders take AV1 and VP9 (`ffmpeg-out.md`
phase 4), when an application brings a hardware decoder, and whenever an
application offers what every player offers: "disable hardware
acceleration", for a driver that draws garbage or fails mid-stream.

## Decision

**The setting reaches every provider, and the system's decoders honour
`OFF`.**

- `DecoderRequest` gains a sixth component, `hardware`, with `softwareOnly()`
  and `withHardware(…)`. The five-argument constructor stays, meaning `AUTO`,
  which is what a container describes a track as; the Engine sets the
  player's value on every request, to `supports` and to `open` alike, so a
  provider with only a hardware decoder can say no and the next is asked.
- The value is the player's at open: `MediaPlayer.open` hands it to the
  `Playback`, which passes it to codec resolution. A decoder already open
  keeps what it was opened with, as before.
- **GStreamer** passes over the decoders whose class has a `Hardware` segment
  (`Codec/Decoder/Video/Hardware`: NVDEC, VA-API, V4L2) — GStreamer's own
  test, the one `GST_ELEMENT_FACTORY_TYPE_HARDWARE` makes — read with
  `gst_element_factory_get_metadata`.
- **VideoToolbox** creates its session with a decoder specification setting
  `kVTVideoDecoderSpecification_EnableHardwareAcceleratedVideoDecoder` to
  false, which asks for Apple's software decoder.
- **Media Foundation** needs nothing: its provider enumerates the synchronous
  software transforms and hands them no Direct3D device. Decoding on the GPU
  there comes with importing its surfaces (`ffmpeg-out.md` phase 4), and will
  read the setting then.
- `AUTO` is unchanged: each system chooses, the video engine first where it
  has one.

## Alternatives

- **Deprecate the setting** until phase 4 needs it. An hour's work, and a
  breaking change later to bring back the same thing, while applications
  meanwhile have no way to turn a misbehaving video engine off.
- **A hardware flag on `DecoderProvider`** that the Engine filters by.
  Hardware is not a property of a provider: GStreamer's may pick NVDEC or
  libav, VideoToolbox its media engine or its software decoder. Only the
  provider knows, so the provider is told.
- **Ordering hardware providers before software ones under `AUTO`.** Nothing
  has two candidates today; when the system decoders take AV1 and VP9, their
  `supports` answering under `OFF` already gives the ladder that order.

## Consequences

- A record pattern over `DecoderRequest` names six components; the
  constructor and accessors a provider uses are unchanged.
- With `OFF` on a Linux machine whose only H.264 decoder is NVDEC, an H.264
  track is unsupported rather than played on the video engine.
- GStreamer's half is tested here (this machine ranks `nvh264dec` first: `AUTO`
  opens it, `OFF` opens `avdec_h264` and decodes every picture);
  VideoToolbox's half is tested by the macOS lane, bit for bit against the
  same reference as the hardware path.
