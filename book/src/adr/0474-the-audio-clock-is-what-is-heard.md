# 474. The audio clock is what is heard

Date: 2026-09-24

## Status

Accepted. Closes the "− device latency" correction in `docs/media-plan.md`,
open since phase 3. SDL's buffers are counted in typical pulls, the median of the last fifteen, since ADR-0485: two pulls seen at once no longer double the latency.

## Context

The master clock is the audio clock: the sample just past the last one
written, less what the sink still holds (§3, ADR-0463). That is when a sample
**leaves** SDL's queue, not when it is **heard**. The design said "− device
latency", and it was left out because SDL 3 does not report any.

Between leaving and being heard there are two stretches:

- **SDL's own buffers.** The sink's smoothing (ADR-0463) drains the next pull
  between pulls, so by construction it runs one pull ahead of what SDL has
  handed over. On macOS, SDL's CoreAudio backend (`SDL_coreaudio.m`) then keeps
  three AudioQueue buffers, and a pull fills the one that has just finished,
  behind the other two. That is three pulls in all: 64 ms at 48 kHz.
- **The operating system's.** CoreAudio reports it as four properties of the
  output device: its latency, its safety offset, its IO buffer, and its first
  stream's latency. Bluetooth reports its radio link in the first of these.

Measured on the development Mac, whose default output is a Bluetooth headset:
`device 84 (Bluetooth) at 44100 Hz: 11166 + 0 + 512 + 0 frames = 264 ms`. With
SDL's ~70 ms, pictures were about a third of a second ahead of the sound, more
than the "up to ~200 ms" the plan had guessed.

## Decision

**The audio clock is what is heard: what has left the queue, less the sink's
latency, less the application's delay.**

- `AudioSink.latencyNanos()`, 0 by default, is wall-clock time from leaving
  the queue to being heard. The Engine takes it off the clock at the current
  rate, since in that time the device plays `rate` times as much stream.
- `SdlAudioSink` reports SDL's buffers, as pulls of the measured pull size
  (three on macOS; elsewhere only the pull the smoothing runs ahead by, until
  SDL's WASAPI and PulseAudio backends are read as closely), plus the system's
  latency from an `OutputLatency`.
- `OutputLatency` is an SPI in `:media`, found by `ServiceLoader` and asked
  about the default output device, which is the one SDL's stream follows. The
  sink asks on open and then at most once a second from the audio thread, so a
  headset connected mid-song is in the clock within a second.
  `goldberry-media-platform` provides `CoreAudioLatency`, one binding
  (`AudioObjectGetPropertyData`) and PortAudio's sum of the four properties.
- `MediaPlayer.setAudioDelay(Duration)`, within ±2 s and kept across sources,
  is the correction by hand. Positive means the sound is heard later than
  reported (a receiver, a device that under-reports, a system with no provider
  yet). Negative means the picture is the late one, as on a television.
  `MediaPlayer.audioLatency()` reports the total being taken off.

Two consequences had to be designed:

- **A seek does not step back.** Just after a seek, `left − latency` is before
  the target. The clock is floored at the last seek's target, so the position
  holds there while the first samples travel.
- **A track ends when its last sample is heard.** Once the queue is empty,
  nothing leaves it, so the clock would stop a latency short and the audio
  would be reported done while its tail was still in the headset. A player that
  opens the next track at `ENDED` would cut that tail off. An `AudioTail` counts
  the latency down in wall time from the moment the queue empties: the clock
  runs on to the end, and the audio thread reports done when the tail has
  lasted the latency. Paused, the tail stands still.

## Alternatives considered

- **A user offset only.** That's simple and portable, but every Bluetooth user
  on every system has to find and turn a knob, and the right value changes
  whenever the headset does. The offset is kept, as the correction on top.
- **The latency query in `libgoldberry`.** The toolkit's natives would take on
  a media concern, and each platform's C would need building on three
  platforms. The frameworks are the operating system's, and `:media-platform`
  already binds them with FFM (ADR-0472).
- **SDL's device buffer size read from SDL** (`SDL_GetAudioDeviceFormat`). It
  needs two more exports for a number the sink already measures: the size of a
  pull.
- **A property listener on the default device.** It would be exact at the
  moment of a switch, but needs an upcall and its lifetime. A read once a
  second costs a few system calls.

## Consequences

- On this Mac, over Bluetooth, the pictures are now held back by about 330 ms,
  and the position shown is what is heard. This hasn't been checked with a
  microphone: the numbers are the system's, and the sum is PortAudio's.
  `CoreAudioLatency` logs every change at debug level, so a report from another
  headset comes with its numbers.
- Windows and Linux count only SDL's one pull until their providers (WASAPI,
  PulseAudio) and SDL's buffering on those backends are written; the delay is
  the workaround there.
- `ENDED` arrives a latency later than it did: 264 ms here over Bluetooth, and
  tens of milliseconds on built-in speakers. That is when the sound ends.
- After a pause, the pictures replay up to a latency of what was already heard,
  while the resumed sound travels. The device's buffer at the moment of a pause
  is the platform's, and nothing reports what became of it.
- Every clock reading now reads the sink's latency: a synchronized read of two
  numbers, with the system asked only from the audio thread.
