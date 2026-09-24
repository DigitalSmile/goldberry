# 472. The platform decoders are the system's own, bound with FFM

Date: 2026-09-24

## Status

Accepted. Implements the first of the "intended providers" in
`docs/goldberry-media.md` §5: operating-system decoders in an optional
`goldberry-media-platform` module.

## Context

The published natives decode royalty-free codecs only (§2, §11). H.264, HEVC,
AAC, AC-3 and E-AC-3 open, list their tracks, and fail with
`UNSUPPORTED_CODEC`. That is most of the video people actually have. The Decoder
SPI exists so that whoever holds the licences can bring the decoders, and an
operating system that ships decoders for these codecs holds them.

Other ways to use "codecs already on the system" were weighed first:

- **Load the system's FFmpeg instead of ours.** The loader pins library majors
  and needs a layout file generated against the same headers. Homebrew's FFmpeg
  on the development machine is libavcodec 63, and we pin 62. §5 already rules
  out swapping the natives, and distributions differ in what they compile in.
- **FFmpeg's own hardware paths (VideoToolbox, D3D11, VAAPI) for H.264.** Those
  run inside FFmpeg's software H.264 and HEVC decoders, which would then be
  compiled into the published natives. That is the patent exposure the policy
  exists to avoid.

## Decision

**A new module, `:media-platform` (`goldberry-media-platform`), provides
`DecoderProvider`s over the system frameworks.** It starts with macOS:

| Provider | Codecs | Framework |
|---|---|---|
| `videotoolbox` | H.264, HEVC; 8- and 10-bit 4:2:0 | VideoToolbox |
| `audiotoolbox` | AAC (LC, HE, HEv2), AC-3, E-AC-3 | AudioToolbox |

**The frameworks are bound with FFM, the way `:media` binds FFmpeg.** No native
code is built or shipped. The frameworks are opened by their install path, which
`dlopen` resolves from the dyld shared cache. Each function's unbound handle is a
`static final` constant (ADR-0173, ADR-0161). Struct layouts were measured
against the SDK with clang, not guessed. The module is found by `ServiceLoader`,
and `PlatformDecoders.providers()` lists the providers for an application that
builds its own list. On other systems the providers support nothing, so a file
fails as it would without the module.

**Video.** Each track's format description is built with
`CMVideoFormatDescriptionCreateFrom{H264,HEVC}ParameterSets`, from the parameter
sets in the container's `avcC` or `hvcC`. A description built from the raw
record (`SampleDescriptionExtensionAtoms`) decodes the same pixels, but Core
Media does not read the VUI from it: VideoToolbox then attaches
`ColorInfoGuessedBy = VideoToolbox` and guesses the matrix from the picture
size. That was measured: a BT.709-tagged 160×90 clip came out as BT.601.
Packets are already length-prefixed, so they are copied into a sample buffer
unchanged. Decoding is synchronous.

VideoToolbox emits pictures **in decoding order**. This was measured too: with
the reorder buffer at depth 0, B-frame clips come out of order. The provider
holds pictures in a `ReorderBuffer` and releases the earliest one once more than
the stream's reorder depth are held. The depth comes from the SPS, derived the
way FFmpeg derives `has_b_frames`:

- H.264: the VUI's `max_num_reorder_frames`;
- otherwise 0 for picture order type 2, no reference frames, or an intra-only
  profile;
- otherwise the level's `MaxDpbMbs` divided by the picture size, at most 16;
- HEVC: `sps_max_num_reorder_pics` of the highest sub-layer.

Decoding times are not used, since Matroska stores none.

Pictures are **lent, not copied.** The provider hands over the `CVPixelBuffer`
itself, locked for the CPU, as NV12 or P010. It asks for both range variants,
so VideoToolbox keeps the stream's range. The buffer is unlocked and released
at the decoder's next call. The matrix comes from the buffer's attachment. An
untagged stream gets VideoToolbox's guess by size, which is the built-in
decoder's default rule: BT.709 from 720 rows up, BT.601 below.

**Audio.** AAC is configured by a magic cookie, the `ES_Descriptor` built around
the `AudioSpecificConfig`, as FFmpeg's `audiotoolboxdec` builds it. The decoder
uses the first entry of `kAudioFormatProperty_FormatList`, which for HE-AAC is
the full-rate layer. Output is interleaved f32. For 3–8 channels the converter
is given an output channel layout in FFmpeg's default order, because the
Engine's resampler reads frames that way. Each frame is timed by counting
samples from the first packet after an open or flush. A packet more than
200 ms off that count starts the count again from its own time, since that
means the stream has a gap.

**Failures.** One packet the decoder rejects is dropped, as FFmpeg's decoders
drop one. After 30 in a row the decoder throws, and the Engine walks its
fallback ladder. An invalid VideoToolbox session (after sleep, or a GPU change)
is replaced, and decoding resumes at the next keyframe. Nothing is thrown back
into native code from a callback. An error in a callback is kept and rethrown
on the decode thread.

## Alternatives considered

- **Parse the VUI ourselves and attach the colour.** HEVC's VUI sits behind
  scaling lists and short-term reference picture sets. Core Media already
  parses both codecs when handed the parameter sets.
- **`kVTDecodeFrame_EnableTemporalProcessing` instead of a reorder buffer.**
  It is documented as a hint ("may delay"), not as a guarantee of display
  order.
- **A native shim in C.** It would add a build per target and a library to
  ship. It would buy nothing FFM does not do, and FFM already carries the
  struct-by-value `CMTime` the output callback receives.

## Consequences

- Tests use `media-platform/src/test/fixtures/make-fixtures.sh`. Every H.264
  and HEVC fixture ships with FFmpeg's `framemd5` in NV12 or P010. The
  VideoToolbox tests require every picture to match byte for byte, in order and
  at its time, for MP4 and Matroska, 8-bit and 10-bit. Audio is checked by
  frequency, level, channel order and continuous timing. A `MediaPlayer` plays
  H.264 with AAC, and HEVC, to the end on the two providers.
- `-Pgoldberry.platform.required=true` turns the macOS skips into failures. The
  Media workflow runs `:media-platform:check` on both of its targets and
  requires the decoders on the macOS one.
- Not done yet:
  - Windows (Media Foundation, a COM API that needs its own binding layer) and
    Linux (VAAPI decodes on hardware only, and has no audio).
  - Native-image metadata for the upcalls. `:media` generates its own from the
    bindings; this module does not yet.
  - Bitmap output for 4:2:2 and 4:4:4 streams. The provider does not claim them
    now, so they stay `UNSUPPORTED_CODEC`.
