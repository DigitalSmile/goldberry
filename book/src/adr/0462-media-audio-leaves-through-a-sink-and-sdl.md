# 462. Media audio leaves through a sink, and the sink is SDL's

Date: 2026-09-23

## Status

Accepted. Refines `docs/goldberry-media.md` §3, whose audio decode thread
wrote to `SDL_PutAudioStreamData` directly. Adds one package to `:natives`'
qualified exports, alongside [ADR-0461](0461-a-media-engine-binds-its-own-libraries.md).

## Context

The design has the audio thread resample to interleaved f32 and hand the
result to an SDL audio stream, and it takes the master clock from the same
stream: the presentation time of the last sample written, minus what SDL
still has queued.

Two things follow when that is built as written:

- **The Engine cannot be tested without a sound card.** Every property worth
  asserting (the first sample after a seek, the position while paused, the
  backpressure) passes through the device. A test that plays through real
  hardware is slow and nondeterministic, and it cannot run on a CI runner.
- **SDL's audio is in `libgoldberry`, and `:media` binds only FFmpeg.**
  ADR-0461 gave `:media` its own libraries. It did not give it SDL, and a
  second copy of SDL would be a second audio stack.

## Decision

**An `AudioSink` interface between the Engine and the output**, public in
`dev.goldberry.media.audio`:
`open(AudioFormat) → AudioFormat`, `write`, `queuedSamples`, `clear`, `pause`,
`resume`, `setGain`, `close`. The Engine writes one format, interleaved f32 at
one rate and channel count (`AudioFormat`), and **the sink's queue is the audio
clock**. A test's sink controls that queue, and so controls what the Engine
believes is playing: `VirtualSink` "plays" only when the test advances it, which
is §9's virtual clock for audio.

**The desktop sink is `SdlAudioSink`, over one SDL audio stream on the default
playback device.** SDL converts from the stream's format to the device's, so
the Engine's format is always the one it asked for (48 kHz stereo), and the
stream follows the default device when it changes. The eight `SDL_*AudioStream*`
functions, `SDL_AudioSpec` and two constants join the export list and the
layout table. The wrapper `natives.sdl.audio.SdlAudioStream` is exported to
`:media` and to nobody else, md4c's seal (ADR-0294). What crosses is a direct
`ByteBuffer` in and frame counts out, so ADR-0280's rule holds.

**`MediaPlayer` uses `SdlAudioSink` unless told otherwise.** The media tests run
with `SDL_AUDIO_DRIVER=dummy`, which consumes audio at the real rate and plays
nothing, so the one end-to-end test through SDL is silent and runs anywhere.

## Alternatives considered

- **Write to SDL from the Engine, as §3 said.** The tests of seeking and the
  clock would then need a device, or a mock of SDL's C API. The interface costs
  one indirection per 20 ms block.
- **A second audio library in `:media` (miniaudio, or a separately built SDL).**
  It would be a second stack, a second set of Linux backends to get right, and
  a second binary, for something `libgoldberry` already does.
- **Let the sink choose the format.** It could skip SDL's conversion when the
  device runs at 44.1 kHz. But the OS mixer converts anyway, and a sink that
  answers anything would make the Engine's single-resample promise conditional.

## Consequences

- `:media` requires `:natives`. An application that plays media already has it.
- The Engine's behaviour is covered by tests that need neither a sound card nor
  timing: sample-exact accurate seeks, position under pause, backpressure at
  200 ms, and the end.
- On Linux, SDL's audio backends are whatever the build machine had headers for.
  ALSA and PulseAudio are already required by `LinuxDependencies`, and PipeWire
  desktops play through PulseAudio compatibility.
- The Clock SPI of §3 is, for audio, the sink. The video thread will present
  against the same clock in phase 3, and a monotonic clock takes over only
  for a source with no audio track.
