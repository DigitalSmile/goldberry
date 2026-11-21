# ADR-0720: A recording is a sample source on a thread of its own

- **Status:** Accepted, as phase 2 of `docs/camera-and-mic.md` (W3.4,
  Tessera's T-05). Builds ADR-0719's D3, D6 and D7.
- **Date:** 2026-11-22
- **Relates to:** ADR-0719 (the plan's decisions and the SDL surface),
  ADR-0190 (content modules), ADR-0560 (no clock bounds in tests)

## Context

ADR-0719 put SDL's recording, device list and device events in `:natives`,
exported to `dev.goldberry.mic`. What was left to decide is what an
application holds: how a recording hands its samples over, what the widgets
of phase 3 read, how a device going away is said, and how all of it is tested
on machines with no microphone.

## Decision

1. **`goldberry-mic` is an optional module with no natives of its own**, like
   `goldberry-html`: `:common` and `:natives` only, published as
   `goldberry-mic` and listed as optional in the umbrella. Four packages:
   `dev.goldberry.mic` (`Microphones`, `Microphone`, `Recording`,
   `RecordingSpec`, `RecordingStatus`, `MicrophoneChange`,
   `MicrophoneException`), `dev.goldberry.mic.source`, `dev.goldberry.mic.analysis`,
   and `dev.goldberry.mic.backend`, which is not exported.
2. **The widgets and the analysis read a `SampleSource`**, not a recording:
   `format()` and `onSamples(SampleListener)`, blocks of interleaved float,
   **borrowed** for the call. A `Recording` is one, and so is a
   `SyntheticSource`, which makes its frames from a sealed `Signal` (silence,
   tone, noise, clip, mix) as a pure function of the frame number and delivers
   them only when advanced. A golden image of a meter is therefore the same
   every run, whatever cuts the time up.
3. **A recording has a capture thread of its own** (a daemon platform thread),
   which takes what SDL has every half block and delivers whole blocks of
   `RecordingSpec.blockFrames`. Beside the listeners, the same frames go into a
   ring `read(FloatBuffer)` pulls from; past `RecordingSpec.buffer` the oldest
   are dropped and counted, rather than the device being stalled.
4. **Status is sealed**: `Active`, `Paused`, `Lost(reason)`, `Closed`, the last
   two final. A recording pinned to a device is lost when its removal is heard,
   or when a read fails; one from the system's choice follows the choice. A
   lost recording stays lost when closed.
5. **`Microphones` holds the device list open**, since an id dies with SDL's
   audio subsystem, and closing it does not end the recordings made through
   it, which hold their own reference. Device events reach `onChange` on the
   window loop's thread; without a window, `list()` asks again.
6. **The SDL code sits behind `CaptureBackend`**, so `Recording` and
   `Microphones` are tested over a fake that the test feeds and raises events
   on; the real backend is tested on SDL's `disk` driver, which records a file.
7. **The analysis is plain Java and allocation-free per block**: `Levels` and a
   `LevelTracker` whose peak holds and falls by the blocks' own clock, a
   radix-2 `Fft`, a `SpectrumAnalyzer` (Hann window, 0 dB for a full-scale sine
   on a bin, log-spaced bands), an `EnvelopeTracker` of min/max buckets.

## Consequences

- No new symbol and no ABI change: phase 2 is Java over ADR-0719's surface.
- Tessera's voice messages read `Recording` directly; an encoder is theirs
  (T-06 declined).
- Phase 3's widgets draw from a `SampleSource` and the trackers, so their
  goldens run on the synthetic source with no device.
- The guide's chapter is parked in `docs/snapshot/components-microphone.md`.
