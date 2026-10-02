# ADR-0520: Media Foundation is held back from 2026.2

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0472](0472-the-platform-decoders-bind-the-system-frameworks.md),
  [ADR-0489](0489-linux-and-windows-platform-decoders-are-gstreamer-and-media-foundation.md),
  [ADR-0493](0493-the-platform-decoders-are-part-of-media.md),
  [ADR-0495](0495-media-is-published-and-snapshots-publish-again.md),
  `docs/releasing.md`, `docs/media-plan.md`

## Context

A release of `goldberry-media` refuses to go without FFmpeg for all four targets
(ADR-0495). For 2026.2 the Media workflow gained `windows-x64` and
`linux-aarch64`. linux-aarch64 passed on its first run. Windows took four
rounds, each one a fault in code that had never run on Windows:

1. dav1d linked the C runtime as a DLL and FFmpeg the static one, so FFmpeg's
   check for dav1d failed to link. Both use `/MD` now, the runtime libgoldberry
   uses and the JDK carries.
2. The `d3d11va2` hwaccels were enabled without the `d3d11va` ones, and FFmpeg's
   Makefile builds the objects both need only under the second name.
3. `AVIOContext` holds an `unsigned long`, which is 4 bytes on Windows, so the
   struct is 200 bytes there and the binding said 208. The layout check refused
   it, as it was written to. The binding now takes C's `long` from the native
   linker.
4. A GPU-less Direct3D refuses AV1 while the formats are agreed, and FFmpeg
   finishes the track in software. `HardwareDecodeTest` did not allow for that.

With FFmpeg working, 21 tests remained, all of them in the Media Foundation
decoders, Windows' own H.264, HEVC, AAC, AC-3 and E-AC-3 (ADR-0489):

- draining H.264 fails with `E_FAIL` from `IMFTransform::ProcessOutput`;
- both decoders refuse a packet `receive()` has just asked for, the contract
  `GstDecoder` broke and was fixed for in the same week;
- the audio provider claims AC-3 and E-AC-3 by codec, and Windows Server has no
  decoder for either, nor for HEVC, so a claimed track fails at open.

Each fix is a CI round of about fifteen minutes with no Windows machine to hand,
and the first release has waited on this lane once already.

## Decision

**2026.2 ships FFmpeg for Windows, and Media Foundation's decoders are off unless
asked for.**

- `MediaFoundation.load` refuses on Windows, before a library is opened, unless
  `-Dgoldberry.media.mediaFoundation=true` is set. The providers stay in the jar
  and on `ServiceLoader`. They support nothing, as they do on any other system,
  and `PlatformDecoders.available()` says so, with the property in its reason.
- The Windows Media job runs without `-Pgoldberry.platform.required`, so the
  decoders' tests skip there and the coverage floor does not count a package
  nothing in the lane reaches. `WindowsRequirement` skips while the switch is
  off, even where the property asks for the decoders.
- `ProvidersTest` holds the refusal on any machine: `load("Windows 11", false)` is
  unavailable, says "held back", and names the property.

## Consequences

- On Windows, 2026.2 plays what FFmpeg decodes: VP8, VP9 and AV1 video (on the
  device where D3D11 decodes them), Opus, Vorbis, FLAC, MP3 and PCM. H.264, HEVC,
  AAC, AC-3 and E-AC-3 fail with `UNSUPPORTED_CODEC` naming the codec, as on a
  system with no decoders of its own. The guide's limitations page says so.
- The switch lets anyone try the decoders on a Windows desktop, which has codecs
  a Server runner lacks. That is also the first step of the fix.
- Turning them on by default needs the three faults above fixed, the provider to
  claim only the codecs `MFTEnumEx` finds, and the Windows Media job to run them
  with `-Pgoldberry.platform.required=true` again.
