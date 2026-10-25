# ADR-0663: Vulkan Video decodes on the GPU, with the headers read in Java

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0472 and ADR-0489 (the system decoders), ADR-0651 (the reference codecs bound directly), ADR-0661 (`ffmpeg-cli`), ADR-0662 (staged planes)

## Context

Phase 5 of `ffmpeg-out.md` asked for a Vulkan Video provider. Where the
system has no decoder for a codec (a Linux without gst-libav or VA-API, a
Windows without the HEVC extension), the GPU's video engine usually decodes it
anyway, and Vulkan reaches that engine the same way on Linux and Windows. The
user chose all four codecs, H.264, HEVC, VP9 and AV1, on 2026-10-25.

Vulkan Video is not a decoder. It decodes one picture at a time, told
everything: the parameter sets as `StdVideo*` structs, each picture's order
count, which picture buffer slots hold its references, where its slices are.
The application parses the headers, keeps the decoded picture buffer, works
out output order, and owns every image and copy. FFmpeg does that in its
`vulkan_*.c` hwaccels on top of its own parsers; there is no small library to
bind instead.

The machine here has a GTX 1660 Ti (driver 580): H.264 from 48×16 to 4096,
HEVC from 144×144 to 8192, Main 10, VP9 from 128×128 to 8192, profile 2; no
AV1. `DPB_AND_OUTPUT_COINCIDE` only; NV12 and the P010 layout.

## Decision

**A `DecoderProvider` of its own, `vulkan-video`, with every header and the
picture buffer in Java, and each picture copied back to host memory like the
system decoders'.**

- **Bindings.** `tools/media/generate_vulkan.py` reads the system's Vulkan
  headers (1.4.341, `vulkan_core.h` and the `vk_video` std headers) and writes
  `platform.vulkan.ffm`: `VkStructs` (layouts and offsets, bitfield masks),
  `VkConstants`, `VkCalls` (a class a function, `DESCRIPTORS` for the native
  image) and `VkDeviceFunctions`, with a C file the layout test compiles
  against the same headers. The loader is opened once and asked only for
  `vkGetInstanceProcAddr`.
- **One device, one session a stream.** `VideoDevice` is made at the first
  ask: an instance of API 1.3, the first physical device with a queue family
  that decodes and one that copies, every decode extension it offers and
  synchronization2. `VideoSession` makes the session and its parameters, one
  layered image for every slot (the DPB and the output coincide), a bitstream
  buffer and a read-back buffer. A picture is decoded on the decode queue,
  signals a semaphore, and is copied (the visible rectangle only) on the copy
  queue under a fence; `readBack` copies a slot again with no decode.
  `HostPictures` holds the copies that wait for their turn to be output.
- **The codecs** each have a package: the headers come from
  `media.bitstream.*`, held to FFmpeg's `trace_headers` element by element.
  - `platform.vulkan.h264`: `H264Dpb` (POC types 0, 1 and 2, the sliding
    window, MMCO 1 to 6, gaps in `frame_num`, output by the VUI's reorder depth
    or the level's), `H264Parameters`; progressive 8-bit 4:2:0.
  - `platform.vulkan.hevc`: `H265Dpb` (POC, the reference picture set with
    long-term pictures, RASL pictures skipped after a CRA that starts afresh,
    output by reorder depth and latency, an end of sequence draining it),
    `H265Parameters`; Main and Main 10.
  - `platform.vulkan.vp9`: the uncompressed header and the superframe index
    (`bitstream.vp9`), VP9's eight reference slots mapped onto nine session
    slots, a hidden frame decoded and not shown, `show_existing_frame` read
    back from its slot with no decode; profiles 0 and 2. A keyframe of another
    size makes the session again; an inter frame of another size (reference
    scaling) is refused.
  - `platform.vulkan.av1`: OBUs, sequence and frame headers, tile groups and
    the reference update read by `bitstream.av1`; the sequence header is the
    session parameters; each frame is decoded from its whole temporal unit,
    told where its frame header OBU and each tile lie; eight reference slots
    on nine. Main profile, 4:2:0, 8 or 10 bits. Film grain and superres need
    an output picture apart from the reference, which these sessions do not
    keep: a stream with film grain is not claimed, a frame with superres is
    refused, and dav1d decodes both.
- **Policy.** Priority −10: below the system decoders, which answer 0, so it
  is the next rung where the system has none, and above `ffmpeg-cli` (−100).
  With `HardwareDecoding.OFF` it supports nothing: there is no software path
  through it. `-Dgoldberry.media.vulkanVideo` is `auto` (H.264 and HEVC),
  `all` (VP9 and AV1 too, which the built-in decoders decode exactly
  otherwise) or `off`. It claims a track when the configuration record's
  first SPS names a profile and size the device decodes; a stream that turns
  out to need what it does not (an interlaced H.264 picture, a size past the
  device's) throws, and the Engine's ladder moves on.

## Alternatives

- **FFmpeg's hwaccel through `libavcodec`.** The point of `ffmpeg-out.md` is
  that FFmpeg is not shipped.
- **Importing the decoded image into SDL_GPU.** SDL imports no outside
  texture (ADR-0662), and the Vulkan device here is not SDL's. Copy-back is the
  system decoders' contract already, and staged planes keep it to one copy on
  the video thread.
- **VA-API or NVDEC directly.** One vendor or one system each; Vulkan Video is
  both systems and every vendor whose driver has it, through one loader.
- **Claiming VP9 and AV1 by default.** The built-in libvpx and dav1d are exact
  and fast on these sizes; a GPU decode of a codec nobody pays for buys only
  CPU time, against a driver path tested here on one GPU. Left to `all`.

## Consequences

- H.264 and HEVC play where the system has no decoder for them, on any GPU
  with Vulkan Video, without a codec shipped: the decoder is the GPU's.
- On this machine H.264 and HEVC match FFmpeg's `framemd5` byte for byte:
  four H.264 fixtures, three new 256×144 HEVC fixtures (closed GOP, open GOP,
  Main 10), a seek, a seek onto a CRA, a flush after the end, and a
  `MediaPlayer` playing to the end. VP9 matches on two new 256×144 fixtures
  (alt-ref frames in superframes, profile 2), a seek and a show-existing frame
  put into the stream. AV1 is not decoded on this GPU, which has no AV1: its
  picture info is checked against four streams without a device, and a
  fixture waits for a GPU that decodes it. Without Vulkan Video the tests skip.
- The native image's foreign metadata gains every Vulkan descriptor.
- A field-coded H.264 picture, 4:2:2 or 4:4:4, more than 10 bits, an SVC or
  MVC stream are not decoded here; the ladder takes them elsewhere.
