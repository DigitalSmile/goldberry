# ADR-0664: Media Foundation empties its output buffer, and its lane requires it

- **Status:** Accepted; the default changes when the Windows lane is green (below)
- **Date:** 2026-10-25
- **Relates to:** ADR-0520 (Media Foundation is held back from 2026.2), ADR-0489 (GStreamer and Media Foundation), ADR-0651 (FFmpeg leaves goldberry-media)

## Context

ADR-0520 held Media Foundation's decoders back after their first run on a
Windows runner failed 21 tests, which it put down to three faults: draining
H.264 answered `E_FAIL`, both decoders refused a packet `receive()` had just
asked for, and the audio provider claimed AC-3 and E-AC-3, which Windows
Server has no decoder for. Nothing in `platform.windows` had changed since,
except that `HardwareDecoding` reached the requests (ADR-0654). With FFmpeg
gone (ADR-0651), Windows plays no H.264, HEVC, AAC, AC-3 or E-AC-3 at all
without them.

The log of that run (Media #5, 2026-10-02) is still there, and read again it
points at one fault, not three:

- Every short H.264 clip failed at the drain, every longer one with "a decoder
  that asked for input refused it", and AAC refused the packet after its
  first. That is one `ProcessOutput` failing from the second sample on.
  `receive()` answered the failure as `NEEDS_INPUT`, so input piled up in the
  MFT until it was full and refused the next packet, or the stream ended
  first and the drain reported the failure.
- The output sample is the transform's own, allocated once and reused, and
  its buffer kept the previous sample's current length. Firefox's
  `MFTDecoder`, which drives the same Microsoft decoders, starts every output
  with an empty buffer. The first picture fits; the second meets a buffer that
  says it is full. That is the one difference from a working caller the log
  leads to, and the runner is what confirms it.
- The AC-3 and E-AC-3 claim was already answered: `supports` asks `MFTEnumEx`
  since the providers were written. It was the tests that opened the
  decoders directly and expected the claim.

No Windows machine is to hand, so the fix is made from the log and has to be
proven on the runner.

## Decision

**The reused output sample's buffer is emptied before each `ProcessOutput`,
a failed `ProcessOutput` fails the decoder at once, and the Windows `:media`
job turns the decoders on and requires them.**

- `Transform` keeps the reused sample's buffer and sets its current length to
  zero before each output.
- `Output.Failed` carries what the MFT was asked for: the output stream's flags,
  size and alignment, the sample handed to it, the status words it set and how
  many samples came before. Both decoders throw it with the `HRESULT`, draining
  or not, and never answer `NEEDS_INPUT` over it. A second round on the runner,
  if one is needed, starts from that message.
- The tests follow the system: AC-3 and E-AC-3 skip where `MFTEnumEx` finds
  no decoder, as HEVC already did, the claim test asserts `supports` equals
  what it finds, and HEVC playback skips on a Windows without the HEVC Video
  Extensions.
- `media.yml`'s Windows job passes `-Pgoldberry.media.mediaFoundation=true`
  (forwarded to the test JVM like the `required` switches, and held there by
  `ForwardedPropertiesTest`) and `-Pgoldberry.platform.required=true`, so
  a decoder that cannot bind fails instead of skipping.
- **When that job is green, the switch defaults to on**, ADR-0520's
  hold-back ends, and this record says so. Until then the decoders stay off
  unless asked for, as ADR-0520 decided.

## Alternatives

- **A new sample for every output**, as Firefox allocates. It avoids any
  state the MFT leaves on a sample, but for 4K that is a 12 MB allocation per
  picture. One sample is enough, because a picture is lent only until the
  decoder's next call.
- **Retrying a failed `ProcessOutput`** before giving up. It cannot repair a
  buffer that is full, and it hides the `HRESULT` the runner has to report.
- **Turning the switch on in the same change.** `publish.yml` would hold a
  red lane back from Central, but master would say "on by default" over
  decoders that have never passed. The decision of 2026-10-25 was to wait for
  the lane.

## Consequences

- The Windows lane runs the Media Foundation tests, which have only ever
  skipped there since 2026-10-02; those for HEVC, AC-3 and E-AC-3 still skip on
  Windows Server.
- A Windows `ProcessOutput` failure now drops the decoder (the Engine's
  fallback ladder) where it used to be counted as a bad packet. The Microsoft
  decoders report damaged input from `ProcessInput`, which still counts.
- If the job is not green, its failure message names the output stream and the
  sample handed over, so the next fix starts from facts.
