# ADR-0719: Camera and microphone are two modules over SDL, and Linux counts two pulls

- **Status:** Accepted. The decisions of `docs/camera-and-mic.md` (W3.4,
  Tessera's T-05), and its phase 1, the SDL surface. Phases 2–6 build on it.
- **Date:** 2026-11-21
- **Relates to:** ADR-0190 (content modules), ADR-0191 (the tray's SDL
  surface), ADR-0461 and ADR-0462 (audio out through SDL), ADR-0488 (the audio
  build check), ADR-0560 (no clock bounds in tests)

## Context

Tessera needs voice messages and a call grid, so microphone and camera capture.
D8 of the gaps plan (2026-11-08) put them in two new modules, `goldberry-mic`
and `goldberry-camera`, as `ARCHITECTURE.md` always named them. Read from the
tree and from SDL 3.4.18 before any code:

- SDL already compiles its camera subsystem (V4L2 here, Media Foundation,
  CoreMedia) and recording. The export list had playback only.
- Device hot-plug and a camera's permission answer arrive only through
  `SDL_PumpEvents`, which the window loop calls on the main thread.
- SDL converts between uncompressed camera formats on its camera thread, and
  never decodes MJPG. On the C922 here, uncompressed YUY2 reaches 720p at
  10 fps; 1080p30 and 720p60 are MJPG only.
- `VideoSurface` draws `VideoPlanes` on the GPU only: a CPU-presented window
  and every offscreen golden show nothing for them.
- PipeWire was off for want of headers. Compiled in, SDL prefers it for
  playback too wherever PipeWire is the session's server (1.0 and later).

## Decision

1. **D1.** `goldberry-camera` requires `goldberry-media`: a `Camera` is a
   `PictureSource`, and `camera-view` is a `PictureView` with a camera's states.
2. **D2.** `VideoSurface` converts `VideoPlanes` to BGRA on the CPU with libyuv
   when no presenter shows them, which closes the same gap for pushed pictures.
3. **D3.** A recording hands its samples over on a capture thread of the
   module's own, with a pull `read` beside it. No new upcall shape.
4. **D4.** Uncompressed camera modes only, opened as NV12 so SDL converts. MJPG
   waits for a JPEG decoder of the toolkit's own, a separate item.
5. **D5.** A camera frame is copied into a pool on the capture thread and
   handed back to SDL at once.
6. **D6.** Hot-plug and permission through an `SdlEventWatch` (the existing
   upcall shape); without a window, `devices()` re-reads SDL's list.
7. **D7.** The default recording device follows the system; a pinned device
   that goes ends its recording `Lost`. A camera's default is the first
   front-facing one, else the first.
8. **D8.** PipeWire is compiled in, loaded at run time, and checked like ALSA
   and Pulse; the camera hint becomes `pipewire,v4l2`; playback follows SDL's
   preference. Taken on a measurement (below), with one correction to the sink.
9. **D9.** All four widgets now: `level-meter`, `waveform-view`,
   `spectrum-view`, `camera-view`.

**Phase 1, the SDL surface.** Sixteen symbols on the export list (five for
recording and devices, eleven for cameras); shim rows for `SDL_CameraSpec`,
`SDL_AudioDeviceEvent`, `SDL_CameraDeviceEvent`, the default recording device,
seven event types, camera positions, permission states and eight pixel formats;
native ABI 29. In `:natives`: `sdl.audio.SdlAudioDevices` and
`SdlRecordingStream`; a new `sdl.camera` (`SdlCameras`, `SdlCamera`,
`SdlCameraFrame`, `SdlCameraSpec`, `SdlFrameLayout`, the enums); a new
`sdl.device` (`SdlDeviceEvent`, sealed, and `SdlDeviceWatch`). Exported by name
to `dev.goldberry.mic` and `dev.goldberry.camera`. A device id lasts only while
the audio subsystem is up, so a caller holds `SdlAudioDevices` open while it
uses one. A lent frame's bytes are a view scoped to an arena closed on release.

**The measurement behind D8.** `:media:audioDriverProbe` plays through both of
SDL's drivers on one PipeWire 1.6.2 desktop, and a second process records the
sink's monitor, always through PulseAudio, to time a click every half second
against the sink's clock:

| | PulseAudio | PipeWire |
|---|---|---|
| SDL's pull | 256 frames, every 5.33 ms | 1024 frames, every 21.33 ms |
| The graph's quantum while playing | 256 | 1024 |
| Clock drift, scatter p99 | −20 to −26 ppm, 0.2–2.0 ms | 0 to −17 ppm, 0.2–1.4 ms |
| Heard after the sink's clock, one pull counted | 15.8 ms | 31.9 ms |
| Heard after the sink's clock, two pulls counted (steady rounds) | 10.3 ms, 10.3 ms | 10.5 ms, 10.5 ms |

With one pull counted, as the sink did on Linux, PipeWire would have put sound
16 ms later than the pictures expect, exactly the difference between the two
pull sizes: on both paths a sample is heard one pull after the pull the sink
counts. **`SdlAudioSink` now counts two pulls on Linux**, which puts both paths
within two tenths of a millisecond of each other, and corrects 5 ms the
PulseAudio path was already off by. PipeWire's own port latency says the same:
two quanta from the stream to the device. The 10 ms left is the recorder's
latency, which is common to both and is not the player's.

## Consequences

- No new upcall shape: `ForeignSurfaceTest` still counts twenty-eight.
- Playback on a PipeWire desktop keeps the graph at its default quantum of 1024,
  where the PulseAudio path pulled the whole graph down to 256.
- The PipeWire stream announces itself with the media role `Game` and the name
  `java`, SDL's defaults. `:media` can name both through SDL's hints; that is
  left to the phase that touches playback.
- CI builds without PipeWire until phase 5 adds `pipewire-devel` to the
  containers and the build check, so shipped libraries are unchanged until then.
- A real camera is tested where there is one (`SdlCameraLiveTest`, skipped in
  CI and on macOS); recording is tested on SDL's `disk` driver.
