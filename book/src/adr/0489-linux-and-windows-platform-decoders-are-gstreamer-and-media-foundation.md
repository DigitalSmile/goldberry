# 489. The Linux and Windows platform decoders are GStreamer and Media Foundation

Date: 2026-09-28

## Status

Accepted. Continues
[ADR-0472](0472-the-platform-decoders-bind-the-system-frameworks.md), which
built `:media-platform` for macOS and left Windows (Media Foundation) and Linux
open. Linux is built and tested here. Windows is written, and not yet run on
Windows.

## Context

The published natives decode royalty-free codecs only. H.264, HEVC, AAC, AC-3
and E-AC-3 are left to whoever holds their licences (`docs/goldberry-media.md`
§5). On macOS that is Apple, through VideoToolbox and AudioToolbox. Windows and
Linux ship decoders too.

**Windows** has one media framework: Media Foundation. Its decoders are MFTs
(`IMFTransform`), COM objects found with `MFTEnumEx`. Microsoft's H.264, AAC,
AC-3 and E-AC-3 decoders come with the system, and its HEVC decoder with the
HEVC Video Extensions.

**Linux** has no decoder in the system itself. Three ways were weighed:

- **VA-API**, which ADR-0472 had pencilled in. It decodes on hardware only,
  and has no audio. It takes pictures parsed into its own parameter buffers, so
  Goldberry would parse H.264 and HEVC slice headers itself. The machine this
  was written on exposes no H.264 or HEVC decode through it at all.
- **The distribution's `libavcodec`.** ADR-0472 rejected this. Its major and
  struct layouts differ between distributions, and the loader pins both.
- **GStreamer.** It is Linux's media framework: what WebKitGTK, GNOME and KDE
  play through. Every desktop distribution installs it. Its decoders are
  whatever the distribution chose to ship and license: gst-libav's `avdec_*`,
  `openh264dec`, `faad`, `a52dec`, and VA-API or NVIDIA decoders where the
  machine has them. It handles audio as well as video, and it ranks its
  decoders, so it knows which one to use.

## Decision

**Linux: GStreamer, bound with FFM.** It lives in `…platform.linux`, with
providers `gstreamer-video` and `gstreamer-audio` that claim exactly what the
macOS providers claim. **Windows: Media Foundation**, in `…platform.windows`,
with providers `mediafoundation-video` and `mediafoundation-audio`. Neither
builds or ships native code. `PlatformDecoders` lists all six providers, and
asks only the current system's package whether its decoders are available.

### GStreamer

**One pipeline per track**, built from a description string, with the
decoder's position filled in:

```
appsrc caps="<the container's form, codec_data included>" ! parser ! decoder
  ! videoconvert (or audioconvert) ! appsink
```

- **The parser** (`h264parse`, `h265parse`, `aacparse`, `ac3parse`) converts
  between the form MP4 and Matroska store the stream in and the form the
  chosen decoder wants.
- **The converter** passes the frame contract's formats through untouched:
  NV12, I420, P010, I010, and interleaved f32. It converts anything else.
- **The Engine's decode thread drives the pipeline** with no callback. It
  pushes packets into `appsrc`, at most eight queued. It pulls whatever
  `appsink` has, and waits only when the queue is full or the stream has ended.

**The decoder is the one GStreamer ranks highest for the stream at its size**,
as `decodebin` chooses. Size matters. This machine has an NVIDIA GTX 1660 Ti,
and GStreamer ranks `nvh264dec` and `nvh265dec` (257) above gst-libav (256).
NVDEC decodes no HEVC under 144×144, and the fixtures are 160×90. Asked for
`video/x-h265` alone, the registry offered `nvh265dec`, which then failed
negotiation. Asked with the width and height, it offers `avdec_h265`.

**A decoder is replaced if it fails before its first frame.** One that cannot
build is passed over. One that starts but then fails is replaced by the next
candidate, which is given every packet sent so far; those are kept until the
first frame comes out. A hardware decoder without the stream's profile fails
exactly this way. `GStreamerTest` reproduces it portably with `funnel`, which
starts and then cannot hand H.264 to `videoconvert`.

**The stream's VUI decides the colour, before the decoder's caps.**
`nvh264dec` and `openh264dec` both report the full-range BT.709 fixture as
limited range; `avdec_h264` gets it right. `ParameterSets` already walked the
H.264 VUI to find the reorder depth. It now also records
`video_full_range_flag` and `matrix_coefficients` as a `Signal`. GStreamer's
caps are used only when the stream signals nothing, which is every HEVC
stream, since the HEVC VUI lies past what `ParameterSets` parses. The default
by height comes last.

**Times:**

- **An hour is added to every time going in and taken off coming out.**
  Matroska starts AAC at −21 ms to cover the encoder's priming, and a
  `GstClockTime` is unsigned.
- **Audio frames are timed by counting samples** from the first frame, as on
  macOS. The decoders pass on Matroska's whole milliseconds: 21 ms apart for a
  frame of 21.33.

**A flush tears the pipeline down and builds it again.** It costs
milliseconds, and it is what a seek is.

**The struct offsets are measured at load.** GStreamer's buffer times, map
info, video info and colour are fields and macros, with no function to reach
them. No machine that ran this had the development headers, so the offsets
were derived from the 1.x headers. `GstLayout.check` makes GStreamer write
values it defines, and reads them back at those offsets before any decoder
opens. If they disagree, the providers are unavailable, with the difference
as the reason. GStreamer 1.28 here agreed on every offset.

### Media Foundation

- **The decoder:** the first synchronous software MFT `MFTEnumEx` sorts for
  the input type.
- **Video:** packets are rewritten from length-prefixed to Annex B, with the
  parameter sets before each keyframe (`bitstream.AnnexB`).
- **Output:** NV12 or P010, lent from the locked output buffer, cropped to
  `MF_MT_MINIMUM_DISPLAY_APERTURE`.
- **Audio:** AAC is configured with `HEAACWAVEINFO` user data around the
  `AudioSpecificConfig`, and output is float in the speaker-mask order,
  which is FFmpeg's.
- **COM** is bound as vtable slots over unbound downcall handles.
- **Threads:** every thread that calls into Media Foundation joins the
  multithreaded apartment first.

**What only Windows can confirm.** It was written without a Windows machine,
from the SDK headers. CI on Windows must confirm:

- every vtable slot;
- the 31 GUIDs;
- the layouts of `MFT_OUTPUT_DATA_BUFFER`, `MFT_OUTPUT_STREAM_INFO`,
  `MFT_REGISTER_TYPE_INFO` and `MFVideoArea`;
- the message and flag constants;
- the call sequence of an MFT: enumerate, set the input type, choose an output
  type, begin streaming, process, drain, flush;
- that the H.264 MFT reports the aligned frame size with the crop in the
  aperture;
- that the AAC MFT offers float output.

The Windows decode tests (23) mirror the macOS and Linux ones, and skip off
Windows. The pure parts (GUID encoding, picture layout and crop, AAC user
data, claims, the binding surface) are tested here: 57 tests.

### Shared

- **`…platform.bitstream`** holds `ParameterSets` (moved from `macos`),
  `BitReader` and the new `AnnexB`. `ParameterSets.of(request)` is the one
  video claim all three systems make.
- **The test fixtures** (`Fixtures`, `Tones`, `BindingSurface`) moved to a
  shared test package. `Fixtures.md5` hashes I420 and I010 as their NV12 and
  P010 twins, so the fixtures' `framemd5` files check planar decoders too.
- **The native-image generator** moved to `…platform.nativeimage`, and writes
  the union of `MacBindings`, `LinuxBindings` and `WindowsBindings`.
- **`goldberry.platform.required` fails a test only on its own system.** Each
  job requires its own system's decoders, so the Media workflow now requires
  them on Linux too, and installs GStreamer's runtime plugins there.

## Consequences

- On this Linux machine, `:media-platform:check` with FFmpeg and the platform
  decoders required: 240 tests, none failing, 55 skipped, the macOS and Windows
  decode tests.
  - H.264, in MP4 and Matroska, decodes on `nvh264dec`, and every picture
    matches FFmpeg's `framemd5`.
  - HEVC, 8- and 10-bit, decodes on `avdec_h265`, again matching every
    picture.
  - The crop, the colour of all four tagged cases, and flush and seek pass.
  - Audio passes on tone, level, 5.1 order for AAC and AC-3, and continuous
    timing.
  - `MediaPlayer` finds the providers through `ServiceLoader` and plays H.264
    with AAC, and HEVC, to the end.
- A Linux system plays these codecs only if its distribution installed the
  decoders: `gstreamer1.0-libav` (or `openh264`, `faad` and `a52dec`) and
  the parsers in plugins-good and plugins-bad. Without them the providers
  support nothing, and the file is `UNSUPPORTED_CODEC`, as it would be without
  the module.
- Every packet is copied once into a GStreamer buffer, since the pipeline
  decodes after `send` returns.
- Loading our FFmpeg broke GStreamer's `avdec_*` in the same process, because
  both libraries answered to one soname.
  [ADR-0490](0490-goldberrys-ffmpeg-has-sonames-of-its-own.md) fixes that, and
  the Linux providers depend on it.
- Still open: output latency on Linux (PulseAudio) and Windows (WASAPI), and
  running the Windows providers on Windows.
