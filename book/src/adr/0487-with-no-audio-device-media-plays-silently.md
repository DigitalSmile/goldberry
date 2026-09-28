# 487. With no audio device, media plays silently

Date: 2026-09-27

## Status

Accepted. Fills a gap in `docs/goldberry-media.md` §3, which did not say what
happens when a source has audio and the machine cannot play it. Builds on
[ADR-0462](0462-media-audio-leaves-through-a-sink-and-sdl.md)'s sink.

## Context

The first run of the showcase on Linux failed every video with an audio track:

```
playback of showcase:///mandelbrot.webm failed: not playable media:
SdlException: SDL_InitSubSystem failed: No available audio device
```

That machine's `libgoldberry` had been built without ALSA's and PulseAudio's
headers. The natives build warns about this and carries on, so SDL had only its
`dummy` and `disk` drivers, and SDL never picks either of those by itself. The
same happens on a headless machine, in a container, or on a desktop whose sound
server SDL has no backend for.

Two things were wrong:

- `SdlAudioSink.open` threw, and `Playback` reported the throw as
  `MediaError.InvalidData`: "not playable media". The media was fine.
- A video failed because its sound could not be heard. A browser plays the
  same video silently.

## Decision

**When SDL cannot open a device, `SdlAudioSink` plays into a
`SilentAudioSink`, and logs why.** `SilentAudioSink` plays what is written into
silence, at the sample rate times the rate, in wall time. Like a device, it
stands still once its queue runs dry. The Engine does not know the difference.
The audio clock still comes from `queuedSamples()`, so pictures keep their
times, seeks, pause and rate apply, audio tracks can still be switched, and a
source with only audio ends when its last sample would have been heard.

- **Only `SdlException` falls back.** That is what `SdlAudioStream.open`
  documents for "no device, or a refused format". Any other exception is a bug,
  and still fails the source.
- **One warning per process**: "no audio device, so media plays without sound",
  with SDL's reason. Every later source logs the same line at debug, so a
  playlist on a headless machine does not repeat the warning for every track.
- **Every `open` tries the device again.** A sink is made for every source
  opened, so the first source opened after a device appears is heard. A source
  already playing silently stays silent.
- **Only the default sink falls back.** A sink an application passes to
  `MediaPlayer.Builder.sink` is the application's, and its failures are its own.
- `SilentAudioSink` is public, for an application that wants silence on
  purpose, such as a thumbnailer or a test that should not reach SDL.

## Consequences

- Nothing in the public API tells an application that playback is silent: no
  `MediaError` variant and no status flag. The log is the only record.
  `SdlAudioSink.silent()` exists for tests and is package-private. If an
  application needs to show "no sound device", that is a new `PlayerStatus`
  field and a decision of its own.
- `SdlAudioSink` gained a package-private constructor that takes the device
  opener and the clock for the silence, so the fallback is tested without SDL:
  `SdlAudioSinkFallbackTest` and `SilentAudioSinkTest`, 9 tests.
- Checked from start to finish on the Linux machine that failed, with the test
  task's `SDL_AUDIO_DRIVER=dummy` removed for one run, so that SDL really found
  no device. `clip-vp9.webm`, a second of VP9 and Opus, played to `ENDED` in
  1.28 s with no error.
- The machine still has no sound. That is a toolchain fix, not a code fix:
  install `libasound2-dev` and `libpulse-dev`, and rebuild `libgoldberry`. Since
  [ADR-0488](0488-the-linux-build-fails-without-the-audio-headers.md), a build
  without them fails, where before it only warned.
