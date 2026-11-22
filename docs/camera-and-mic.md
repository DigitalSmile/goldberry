# Camera and microphone — implementation plan

Written 2026-11-21, before any code. This is W3.4 of
[`gaps/goldberry-gaps-plan.md`](gaps/goldberry-gaps-plan.md), Tessera's T-05:
"Camera and microphone capture", for voice messages (R1.5-05) and the call grid
(R3.1-05, R3.3-01). The owner chose D8 (b) on 2026-11-08: two new modules,
`goldberry-mic` (SDL recording streams) and `goldberry-camera` (SDL camera,
frames as `VideoPlanes` for W2.7's picture source), plus the SDL symbols, device
hot-plug events and a native ABI bump, as audio-out (ADR-0462) and the tray
(ADR-0191) did, and SDL's PipeWire camera on Linux.

It follows `drag-out.md`'s shape: what was found, the decisions with a status,
phases with exit criteria, risks, and a log. **The owner took (a) for D1–D9
on 2026-11-21**, D8 on the condition that a measurement showed playback was
fine on PipeWire; it did once the sink counts
two pulls on Linux (§3, D8). ADR-0719, parked in `docs/snapshot/adr/`, records
all nine and phase 1. On 2026-11-22 the owner took D4 (d) instead of (a), after
a measurement. **Phases 1–6 and 4b are done**; the hand checks on the four desktops are left.

## 1. Where things stand

Read from the tree on 2026-11-21 (master at `258b836f`) and from the SDL
3.4.18 sources the superbuild unpacks into `natives/.deps/linux-x64/sdl3-src`.

| Area | State |
|------|-------|
| Audio out | `natives.sdl.audio.SdlAudioStream` opens `SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK` through `SDL_OpenAudioDeviceStream`, f32 interleaved, and is exported to `dev.goldberry.media` only. `media.audio.SdlAudioSink` falls back to `SilentAudioSink` when no device opens. Nine `SDL_*Audio*` symbols are on `goldberry.symbols`; **no recording, no device list, no `SDL_GetAudioStreamData`** |
| Camera | Nothing bound. `SDL_INIT_CAMERA` is the one camera row in `goldberry_shim.c`'s table. SDL **compiles** its camera subsystem: here `SDL_CAMERA_DRIVER_V4L2` and the dummy driver; on Windows Media Foundation, on macOS CoreMedia (`SDL_build_config.h`). This machine has a Logitech C922 (V4L2 and PipeWire both list it, 218 modes): uncompressed YUY2 reaches 1280×720 at 10 fps and 1920×1080 at 5 fps, while 1080p30 and 720p60 are MJPG only. (A shell in the agent's sandbox sees no `/dev/video*`; the JVM does.) |
| PipeWire | **Off** (on since phase 5). `SDL_PIPEWIRE` defaults on for Unix, but this machine and the AlmaLinux 8 release container have no `libpipewire-0.3` headers, so neither `SDL_AUDIO_DRIVER_PIPEWIRE` nor `SDL_CAMERA_DRIVER_PIPEWIRE` is defined. The superbuild checks ALSA and Pulse against SDL's own config (fatal, no waiver, ADR-0488) and checks nothing for PipeWire |
| Pictures | `media.picture.VideoPlanes` (NV12, I420, P010, I010, I420A; direct buffers, strides, matrix, range), `PictureSource` (`shownPicture`, `untilNextPicture`, `onPicture`), `PushedPictures` (W2.7) and `media.view.PictureView`. All in `:media`. **`VideoSurface` draws `VideoPlanes` on the GPU only**: a CPU-presented window and every offscreen golden show nothing for them, as `PushedPictures`' note says. A player avoids it by converting with libyuv (`YuvConverter`, in `libgoldberry-media`) when its view says `PictureForm.CONVERTED` through `MediaPlayer.attachView`; `PictureSource` has no such negotiation. **No packed YUY2** in `PixelFormat`, the format most USB cameras give uncompressed |
| JPEG | **Corrected 2026-11-22:** the rasterizer inside `libgoldberry` decodes JPEG (with PNG and QOI), and `core.image` reaches it through `Image.decode` and `Image.decodeInto(bytes, destination, stride, ChannelOrder)`, which writes BGRA into the caller's memory. This row first said there was no decoder, and D4 was weighed on that. `libgoldberry-media`'s libyuv is built without its MJPG path (`mjpeg_decoder.cc` and `convert_jpeg.cc` are filtered out, since it needs libjpeg) |
| Events | `SdlEventWatch` (an `SDL_AddEventWatch` upcall, already counted by `ForeignSurfaceTest`) runs a handler as each event is pushed. `Sdl3Backend` polls; it does not look at audio or camera device events |
| Module wiring | `:html` is the template for an optional module that ships Java only and uses symbols on `libgoldberry`'s export list (`exports … to` by name in `natives/module-info.java`). `:media` is the template for one with natives of its own |
| Packaging | The Gradle plugin builds no `.app` and writes no `Info.plist`; a macOS application's usage strings are its own |

What SDL 3.4.18 does, read from its sources:

| Topic | SDL |
|-------|-----|
| Recording | `SDL_GetAudioRecordingDevices`, `SDL_OpenAudioDeviceStream(SDL_AUDIO_DEVICE_DEFAULT_RECORDING or an id, spec, NULL, NULL)` then `SDL_GetAudioStreamData`/`SDL_GetAudioStreamAvailable` from any thread: SDL's device thread fills the stream and converts to the asked format and rate. A put callback (`SDL_SetAudioStreamPutCallback`) exists and would be a new upcall shape |
| Default device | The default-recording id follows the system's default as it changes, as playback's does |
| Camera API | `SDL_GetCameras`, `SDL_GetCameraSupportedFormats` (format, colorspace, size, frame rate as a fraction), `SDL_GetCameraName`, `SDL_GetCameraPosition`, `SDL_OpenCamera(id, spec)`, `SDL_GetCameraPermissionState`, `SDL_GetCameraFormat`, `SDL_AcquireCameraFrame(camera, &timestampNS)` returning an `SDL_Surface` valid until `SDL_ReleaseCameraFrame`, `SDL_CloseCamera`. Frames are produced on SDL's camera thread; acquiring is a poll |
| Conversion | When the spec asked for differs from what the device gives, SDL converts on its camera thread with `SDL_ConvertPixelsAndColorspace` — YUY2 to NV12 included. **MJPG is never converted**: Media Foundation and PipeWire hand MJPG frames through as-is when that is the format opened |
| Hot-plug | `SDL_EVENT_AUDIO_DEVICE_ADDED/REMOVED/FORMAT_CHANGED` (with a `recording` flag) and `SDL_EVENT_CAMERA_DEVICE_ADDED/REMOVED/APPROVED/DENIED`. Both are queued by `SDL_UpdateAudio`/`SDL_UpdateCamera`, **which only `SDL_PumpEvents` calls**: without a loop that pumps, no device event and no camera approval ever arrives |
| Permission | macOS (AVFoundation) and Android ask the user; the camera is unusable until `APPROVED`. macOS kills a process that touches the camera or microphone without `NSCameraUsageDescription`/`NSMicrophoneUsageDescription` in its bundle (a `java` started from a terminal borrows the terminal's) |
| Linux drivers | Camera bootstrap order is V4L2, then PipeWire. `SDL_HINT_CAMERA_DRIVER` takes a comma list. **Audio's order puts `PIPEWIRE_PREFERRED` first** (taken when PipeWire is the session's server), so compiling PipeWire in also moves *playback* from Pulse to PipeWire on most current desktops |
| Test drivers | Audio: `dummy` records silence; `disk` records from the file `SDL_AUDIO_DISK_INPUT_FILE` names, which makes a deterministic microphone for CI. Camera: the dummy driver lists no device, so a test camera has to be the toolkit's own |

## 2. What this plan delivers

- **`goldberry-mic`** (`:mic`, module `dev.goldberry.mic`): the recording devices
  and their hot-plug, a `Recording` that delivers f32 PCM at the rate and channel
  count asked for, levels (peak and RMS per block), and the three widgets
  `ARCHITECTURE.md` §11.1 names: `level-meter`, `waveform-view`, `spectrum-view`.
  A synthetic source, so every widget is golden-testable.
- **`goldberry-camera`** (`:camera`, module `dev.goldberry.camera`): the cameras,
  their formats and position, permission state, hot-plug, a `Camera` that is a
  `PictureSource` of `VideoPlanes`, and `camera-view`. A synthetic camera (a
  moving test pattern), so `camera-view` is golden-testable.
- **`:natives`**: the recording and camera symbols on `goldberry.symbols`, their
  bindings in `natives.sdl.audio` and a new `natives.sdl.camera`, the event rows,
  one ABI bump (28 → 29).
- **PipeWire on Linux** only if D8 takes it.
- The showcase's capture card, and the guide sections parked in `docs/snapshot/`.

Not in it: echo cancellation, noise suppression or AGC (SDL has none; Tessera's
call stack brings WebRTC's), any encoder (T-06 declined, D9), screen capture,
and video recording to a file.

## 3. Decisions

### D1. Where `camera-view` gets its pictures from (taken: (a))

`VideoPlanes`, `PictureSource` and `PictureView` are in `:media`.

- **(a) `goldberry-camera` requires `goldberry-media`.** A `Camera` implements
  `PictureSource`; `camera-view` is a `PictureView` with a camera's states
  (asking, denied, no camera) on top, and gets the GPU presenter for free. With
  D2 (a), an application that shows a camera in a CPU-presented window also
  needs `goldberry-media`'s natives jar (for libyuv). Tessera ships `:media`
  anyway. Recommended.
- (b) Move `media.picture` and `PictureView` down into a small module both
  require. Cleaner graph, but it moves published packages a release after they
  were published.
- (c) `goldberry-camera` converts to BGRA and paints through `Image`, needing
  nothing from `:media`. Costs a conversion per frame on the CPU and loses the
  GPU path a call grid wants.

### D2. Planes where no GPU presents them (taken: (a))

A camera hands out NV12 planes. Where the view draws on the CPU (a window on
the CPU path, every offscreen golden), `VideoSurface` shows nothing for them
today.

- **(a) `VideoSurface` converts on the CPU.** When it draws `VideoPlanes`
  without a presenter, it converts them to BGRA with `YuvConverter` (libyuv,
  the conversion a player's CPU present already uses) into a buffer the view
  keeps. This closes the same gap for `PushedPictures` (Tessera's I420 call
  tiles), and goldens see camera frames. Recommended.
- (b) The camera converts every frame to BGRA itself (SDL asked for
  XRGB8888): the GPU path then uploads BGRA, four bytes a pixel instead of 1.5.
- (c) A form negotiation on `PictureSource`, as `MediaPlayer.attachView` has,
  so a source converts only for views that need it. More API for the same
  outcome as (a).

### D3. How a recording hands over its samples (taken: (a))

- **(a) A capture thread of the module's own** reads the SDL stream
  (`SDL_GetAudioStreamData`) every block (10 ms by default) and hands a borrowed
  `FloatBuffer` to listeners; `read(FloatBuffer)` is there too, for a caller
  that pulls on a thread of its own. No new upcall shape. Recommended.
- (b) SDL's put callback, on SDL's audio thread: lower latency by up to a block,
  a new upcall shape, and application code on SDL's device thread.
- (c) Pull only; every application writes the thread.

### D4. Camera formats, and MJPG (taken: (a), then (d))

Most USB cameras give 1080p30 only as MJPG, and 720p or less uncompressed (YUY2).

- **(a) Uncompressed only.** `formats()` lists what SDL reports and marks MJPG
  modes unavailable; a `Camera` opens the best uncompressed mode for the size and
  rate asked, asking SDL for NV12 so SDL's camera thread converts YUY2. MJPG
  waits for a JPEG decoder of the toolkit's own (a separate item; core has none).
  Recommended.
- (b) Decode MJPG in this work, with a baseline JPEG decoder in Java (in
  `core.image`, useful to images too). A week of work and a hot loop on the CPU
  at 30 fps.
- (c) libjpeg-turbo into `libgoldberry-media`. Fast, but it ties the camera to
  `:media`'s natives jar and adds a library to its superbuild.

**Revisited 2026-11-22, after phase 4: a fourth option, measured; taken the
same day (ADR-0723).** §1 said the tree had no JPEG decoder. It has one: the rasterizer's,
in `libgoldberry`, through `Image.decodeInto`. And (c)'s objection no longer
holds, since phase 4 already ties the camera to `:media` and its natives
(D1, D2).

- **(d) Decode MJPG with the rasterizer's decoder.** Open an MJPG mode as it
  is (SDL hands the frames through), decode each on the capture thread into a
  three-slot pool of BGRA, and show it as a `VideoPicture`. No new native code.
  Proposed as phase 4b.

Measured on the C922 here (a throwaway probe, not committed): 90 frames in
each mode, decoded three times over, the third timed, on one thread, into a
reused buffer.

| MJPG mode | Frames delivered | Frames carrying Huffman tables | Decode, median | p95 | max | Failures |
|---|---|---|---|---|---|---|
| 1280×720, offered at 60 fps | 17.0 fps | 90 of 90 | 4.7 ms | 5.1 ms | 5.4 ms | 0 |
| 1920×1080, offered at 30 fps | 27.9 fps | 90 of 90 | 11.6 ms | 12.3 ms | 12.7 ms | 0 |

So 1080p30 costs about a third of one core, and 720p about a sixth: well
inside a 33 ms frame, and libjpeg-turbo is not needed. The 720p mode gave 17
fps, not 60; the probe only copied frames, so that is the camera's own rate,
most likely its exposure in the room's light. Even so it beats what this
camera gives uncompressed (1280×720 at 10, 1920×1080 at 5). The C922 puts the
Huffman tables in every frame; many UVC cameras leave them out and rely on the
standard tables, which (d) would insert when a frame has none. The decoded
pixels were not compared against a second decoder.

### D5. Who owns a camera frame (taken: (a))

`SDL_AcquireCameraFrame` lends a surface from a few driver buffers (V4L2 mmaps
a handful); holding one stalls the camera.

- **(a) Copy on the capture thread** into a pool of `VideoPlanes`-sized direct
  buffers and release at once; a picture stays valid for two more pictures, as
  W2.7's borrowing rule says. One copy of NV12 per frame (3 MB at 1080p).
  Recommended.
- (b) Lend SDL's surface straight to the view and release on the next frame.
  No copy, but a slow frame stalls the driver, and the GPU upload would read
  memory SDL may already have handed back.

### D6. Hot-plug and permission events (taken: (a))

SDL delivers both only from `SDL_PumpEvents`, which the window loop calls on the
main thread (and which may only be called there on macOS).

- **(a) An `SdlEventWatch` per module** (an existing upcall shape) turns device
  and permission events into listeners; they arrive while an application's loop
  runs. Without a window nothing pumps, so `devices()` re-reads SDL's list each
  time it is asked and a headless program polls it. Recommended.
- (b) The modules pump events themselves on their own threads. Breaks macOS and
  races the backend's own pump.

### D7. Device selection (taken: (a))

- **(a) Default-following by default.** `Microphones.open(spec)` opens
  SDL's default-recording id, which follows the system's choice as it changes;
  `open(device, spec)` pins one, and a pinned device that goes away ends the
  recording with a `Lost` status. Cameras have no default in SDL: the first
  front-facing one, else the first. Recommended.
- (b) Always pinned: the application picks and handles changes itself.

### D8. PipeWire on Linux (taken: (a), measured)

**Why it was in W3.4 at all.** The gaps plan carried "turn on SDL's PipeWire
camera" from `answers-tessera.md` G-05, which said it makes "cameras behind the
portal work too". §5 shows it does not: SDL's PipeWire camera never asks the
portal. What PipeWire would still add, without a sandbox:

- **Cameras V4L2 cannot drive.** The MIPI cameras in many laptops from 2023 on
  (Intel IPU6 and later) run through libcamera, and upstream exposes libcamera
  through PipeWire rather than as a V4L2 node that streams. Some distributions
  ship a relay that makes a V4L2 node for them; where none is installed, V4L2
  sees no usable camera.
- **Sharing.** A V4L2 node streams to one process at a time; through PipeWire
  a call and a second application can both see the camera.

What it does not add: USB (UVC) webcams, OBS's virtual camera and
microphones all work without it. Recording goes through Pulse's API, which
`pipewire-pulse` serves on a PipeWire desktop.

- **(a) Compile it in, dynamically loaded, checked like ALSA and Pulse, camera
  hint `pipewire,v4l2`; playback moves to PipeWire where it is the server.**
  Taken, on the measurement below.
- (b) Compile it in for the camera only, audio hint pinned to
  `pulseaudio,alsa`.
- (c) Leave it out of W3.4. V4L2 covers UVC webcams; laptops whose camera
  is behind libcamera are a known limit in the guide. PipeWire comes back
  together with the portal (a remote handed to SDL), when there is a reason
  that needs both. Recommended before the measurement.

**Measured, 2026-11-21** (`:media:audioDriverProbe`, PipeWire 1.6.2, the
built-in S/PDIF sink; a second process records the sink's monitor through
PulseAudio and times a click every half second against the sink's clock):

| | PulseAudio | PipeWire |
|---|---|---|
| SDL's pull | 256 frames every 5.33 ms | 1024 frames every 21.33 ms |
| The graph's quantum while playing | 256 | 1024 |
| Clock drift; scatter p99 | −20 to −26 ppm; 0.2–2.0 ms | 0 to −17 ppm; 0.2–1.4 ms |
| Heard after the sink's clock, one pull counted | 15.8 ms | 31.9 ms |
| … two pulls counted, steady rounds | 10.3, 10.3 ms | 10.5, 10.5 ms |

Counting one pull, PipeWire would have put sound 16 ms behind the pictures:
the difference of the two pull sizes, so on both paths a sample is heard one
pull after the one the sink counted. `SdlAudioSink.pullsAhead` is now 2 on
Linux, which puts both paths within 0.2 ms and corrects 5 ms the PulseAudio
path was already off by; PipeWire's port latency (two quanta) agrees. The 10 ms
left is the recorder's own. The first round of each run is a warm-up while the
recorder settles, and one PipeWire round read 10.7 ms early, a step of the
recorder's 512-frame reads. Also seen: the PipeWire stream calls itself `java`
with SDL's media role `Game`, which `:media` can set through SDL's hints later.

### D9. Widgets' first set (taken: (a))

- **(a) All four of §11.1 in this work:** `level-meter` (peak and RMS, with a
  hold), `waveform-view` (a scrolling envelope, or a recorded clip whole),
  `spectrum-view` (an FFT of the module's own, bars or a line), `camera-view`
  (fit, mirror for a front camera, states). Recommended.
- (b) `level-meter` and `camera-view` now, the other two later.

## 4. Phases

Each phase ends green on `:natives`, the module's own tests, `BoundaryTest`,
`ForeignSurfaceTest`, `DecisionLogTest`, and a dummy-driver `:example:run`
(module path).

### Phase 1 — the SDL surface

- `goldberry.symbols`: `SDL_GetAudioRecordingDevices`, `SDL_GetAudioDeviceName`,
  `SDL_GetAudioDeviceFormat`, `SDL_GetAudioStreamData`,
  `SDL_GetAudioStreamAvailable`, `SDL_GetCameras`,
  `SDL_GetCameraSupportedFormats`, `SDL_GetCameraName`, `SDL_GetCameraPosition`,
  `SDL_OpenCamera`, `SDL_GetCameraPermissionState`, `SDL_GetCameraFormat`,
  `SDL_AcquireCameraFrame`, `SDL_ReleaseCameraFrame`, `SDL_CloseCamera`,
  `SDL_GetCurrentCameraDriver` (and `SDL_free` if it is not there).
- Shim rows: `SDL_CameraSpec` and its fields, `SDL_AudioDeviceEvent` and
  `SDL_CameraDeviceEvent` fields, `SDL_AUDIO_DEVICE_DEFAULT_RECORDING`, the
  seven event types, the camera positions and permission states. ABI 29, in
  `goldberry_shim.c`, `GoldberryShim` and the docs that state it.
- `natives.sdl.audio`: `SdlRecordingStream` and `SdlAudioDevices`;
  `natives.sdl.camera`: `SdlCameras`, `SdlCamera`, `SdlCameraSpec`,
  `SdlCameraFrame`; `SdlDeviceEvents` over `SdlEventWatch`. Exported to the new
  modules by name.
- Tests: the layout rows, a recording on SDL's `disk` driver reading a known
  file back, the dummy camera listing nothing.

### Phase 2 — `goldberry-mic`

- The module, `goldberry.publish`, the BOM and the toolkit's optional list,
  `THIRD-PARTY-NOTICES` (nothing new: SDL is already there).
- `dev.goldberry.mic`: `Microphones` (devices, default, listeners),
  `Microphone`, `RecordingSpec`, `Recording` (status, listeners, `read`),
  `RecordingStatus` (sealed: `Active`, `Paused`, `Lost`, `Closed`),
  `MicrophoneChange`, `MicrophoneException`.
- `dev.goldberry.mic.source`: the `SampleSource` the widgets read, the
  recording's and a synthetic one (tones, noise, a clip).
- `dev.goldberry.mic.analysis`: levels and the FFT (plain Java, tested
  against known signals).
- `dev.goldberry.mic.backend`, not exported: `CaptureBackend` and its SDL
  implementation, so the API is tested over a fake.
- Tests on the `disk` driver and on the synthetic source.

### Phase 3 — the microphone's widgets

- `dev.goldberry.mic.view`: `level-meter`, `waveform-view`, `spectrum-view` as
  `@Markup` widgets with a catalog, CSS properties for their colours, goldens
  over the synthetic source, `BookTest`'s headings parked with the guide.

### Phase 4 — `goldberry-camera`

- First, in `:media` (D2): `VideoSurface` converts `VideoPlanes` on the CPU
  when no presenter shows them, with a golden of pushed NV12 and I420 planes.
- The module, wired as phase 2's.
- `dev.goldberry.camera`: `Cameras`, `CameraInfo` (name, position, formats),
  `CameraFormat`, `CameraSpec`, `Camera` (a `PictureSource`, permission and
  status, `close`), the capture thread and the frame pool (D5).
- `dev.goldberry.camera.synthetic`: a camera that paints a moving test pattern
  into NV12, the same `Camera` surface.
- `dev.goldberry.camera.view`: `camera-view` over `PictureView` (D1), mirror,
  fit, and its states; goldens over the synthetic camera.

### Phase 4b — MJPG through the rasterizer's decoder (done, D4 (d))

Taken by the owner on 2026-11-22. As built (the log has what changed):

- `CameraFormat` keeps `compressed`, and MJPG modes become openable.
  `CameraSpec.choose` prefers an uncompressed mode that meets the spec and
  takes MJPG where only MJPG reaches the size or rate asked.
- The backend opens an MJPG mode in MJPG and lends the frame's bytes; the
  capture thread decodes them with `Image.decodeInto` into a three-slot BGRA
  pool and shows a `VideoPicture`. A frame that does not decode is dropped and
  counted, and the camera stays active.
- A frame with no DHT segment gets the standard Huffman tables (JPEG Annex
  K.3) inserted before its scan, as a UVC camera that omits them expects.
- Planned: `SyntheticCamera` gains a JPEG mode. Built instead: the decode path
  is tested with no device through the fake backend, on `DeviceCamera`'s own capture thread, over
  frames Pillow (libjpeg) encoded (`tools/media/mjpeg_fixtures.py`); the live
  test opens the C922 for a call, which picks 1280×720 in MJPG.
- ADR-0723 for D4 (d); the parked camera chapter, `answers-tessera.md` G-05
  and §5's risk rewritten.

### Phase 5 — PipeWire (done)

- The superbuild's check (D8), `pipewire-devel` in the Linux and media
  containers, `libpipewire-0.3-dev` in the docs' prerequisite lists, the camera
  hint. `:media`'s audio tests on a PipeWire session before it lands.
- As built: the "media containers" build no SDL (`media.yml` builds
  `libgoldberry-media` only), so the headers went into every workflow that
  builds `libgoldberry`: the manylinux release job and the web-view job in
  `linux.yml`, and `showcase.yml`, `nightly.yml`, `benchmarks.yml`. The docs'
  prerequisite list is `LinuxDependencies`, which `checkToolchain` prints and
  the guide points at; it has no list of its own.
- **SDL's PipeWire camera is patched.** With `pipewire,v4l2` the C922 never
  streamed through SDL (see the log). The superbuild patches the driver on
  Linux, and the hint stays.

### Phase 6 — the showcase, the platforms, the guide (done, hand checks left)

- A capture card on the showcase's Media section: a level meter, a spectrum
  and a camera view with a device picker.
- The Windows and macOS lanes run the modules' tests (no device there: the
  listing, the synthetic sources, the `disk` driver where SDL has it).
- The guide sections parked in `docs/snapshot/`, with the macOS usage strings.
- Hand checks: a USB camera and a headset microphone on X11, Wayland (PipeWire),
  Windows and macOS, plugged in and out while open.
- As built: the showcase has no Media section (Audio and Video are two screens
  around their players), so the cards are a **Capture** screen of their own
  after Video, in `dev.goldberry.example.ui.capture`. Each card's picker offers
  a test signal first (a 440 Hz tone, the synthetic test card), advanced by a
  ticker on the window's loop, then the devices, listed as the screen opens
  (a `CaptureDevices` on `GalleryContext`; none in pictures). Nothing records
  until Start, and leaving the screen closes what was opened. The microphone
  card has all three widgets; the camera card a mirror picker, a mode picker
  over every mode the camera offers (`Cameras.open(CameraInfo, CameraFormat)`
  is new), and what the camera is and offers. Each card records a throwaway
  clip and plays it back: sound kept up to 30 s and played from a temporary
  float WAV through a `MediaPlayer`; pictures copied up to 30 s or 256 MB and
  pushed back on the loop's clock. The links point at the media chapter until
  the two parked chapters move.
- The lanes needed no change: both run `./gradlew build` with the library
  required. The camera goldens need the media natives as well and skip
  without them, so off a developer's Linux machine they skip.
- `BookMarkupTest` inflates the parked `kdl` samples now, which found a
  parked `canvas3d` sample of another batch that would have failed at
  release.

## 5. Risks

- **CI has no camera.** This machine has one (a C922), so phase 4's real path
  runs here (`SdlCameraLiveTest` skips elsewhere); CI's tests are carried by the
  synthetic camera.
- **Uncompressed is slow on USB 2 cameras** (D4): the C922 gives 640×480 at
  30 fps or 1280×720 at 10 fps without MJPG. Answered by phase 4b, which
  decodes MJPG with the rasterizer (measured: 4.7 ms a 720p frame, 11.6 ms a
  1080p one).
- **MJPG without Huffman tables** (phase 4b). Many UVC cameras send frames
  that rely on JPEG's standard tables, and the rasterizer refuses such a frame
  (tested). The C922 sends the tables; phase 4b inserts them where a frame has
  none, tested on a frame stripped of them, which decodes to the same pixels.
- **SDL's PipeWire camera did not stream** (phase 5, found and patched). SDL
  3.4.18 never answers PipeWire's format with `SPA_PARAM_Buffers`, so PipeWire
  1.6 refuses the stream ("use input buffers: Invalid argument"), the
  permission stays pending, and `SDL_CloseCamera` hangs on an untimed wait.
  Upstream main has a timed wait but the same empty parameter callback. The
  superbuild carries a patch (`patches/sdl3-pipewire-camera.patch`), and an
  SDL bump must rebase or drop it; the configure stops when it no longer
  applies.
- **PipeWire moves playback** (D8 (a)). `:media`'s A/V sync assumes SDL's pull
  sizes; PipeWire's quantum differs from Pulse's. `SdlAudioSink`'s drain
  estimate measures pulls rather than assuming them, which is why it is expected
  to hold.
- **AlmaLinux 8's PipeWire** is 0.3.6, below SDL's 0.3.44, which this risk
  first missed (Snapshot #88). SDL loads the library dynamically, so only the
  headers matter at build time: the superbuild stages the catalog's PipeWire
  headers where the system's are too old (ADR-0727). An old daemon at run time
  falls back to the next driver.
- **The camera portal is not SDL's.** `answers-tessera.md` G-05 promises
  "cameras behind the portal work too". SDL's PipeWire camera connects to the
  session's daemon itself; nothing in `SDL_camera_pipewire.c` asks
  `org.freedesktop.portal.Camera` for a remote. So inside a Flatpak sandbox it
  sees no camera. (a) gives PipeWire-only cameras outside a sandbox; the portal
  is a later item (`AccessCamera` and `OpenPipeWireRemote` over the toolkit's
  D-Bus, and a way to hand SDL that remote, which SDL 3.4 does not offer), and the answers file gets
  corrected when D8 is taken.
- **macOS permission** stalls a test that opens a real camera until a person
  answers; the macOS lane opens none.
- **Frame copies** (D5 (a)) at 1080p30 are ~90 MB/s; fine on the CPU, and the
  pool keeps them allocation-free.

## 6. Documents kept in step

- `gaps/goldberry-gaps-plan.md` §8 (T-05) and §10, `gaps/answers-tessera.md`.
- `ARCHITECTURE.md` §11.1's two rows, and §3.2's count of SDL symbols.
- `docs/status.md`, `docs/testing.md` (the `disk` driver recipe), the README's
  module list.

## 7. Status and log

| Phase | State |
|-------|-------|
| Decisions | D1–D9 taken (a); ADR-0719 |
| 1 SDL surface | done: 16 symbols, ABI 29, `natives.sdl.audio` recording and devices, `natives.sdl.camera`, `natives.sdl.device`; the D8 measurement and `SdlAudioSink`'s two pulls on Linux |
| 2 `goldberry-mic` | done: the module (published, optional), recording, the synthetic source, levels, FFT, spectrum, envelope; ADR-0720; the guide chapter parked |
| 3 mic widgets | done: `level-meter`, `waveform-view`, `spectrum-view` over exported leaves, `mic.css`, six goldens; ADR-0721; the sections parked with the chapter |
| 4 `goldberry-camera` | done: `PictureView` converts planes on the CPU; the module, `Camera` sealed over a device's and a synthetic one, mode choice, the pool, permission followed, `camera-view` with states and mirror; three goldens; ADR-0722; the chapter parked |
| 4b MJPG | done: every mode openable, MJPG decoded on the capture thread into a BGRA pool, standard Huffman tables inserted, dropped frames counted; ADR-0723 |
| 5 PipeWire | done: required row in `LinuxDependencies`, a fatal superbuild check for V4L2 and both PipeWire drivers, the headers in every `libgoldberry` workflow, SDL's PipeWire camera patched, cameras try `pipewire,v4l2`; ADR-0724. The release container's 0.3.6 is below SDL's floor, so the pinned headers are staged there; ADR-0727 |
| 6 showcase, platforms, guide | done: the Capture screen (a Microphone and a Camera card, a test signal first, devices listed on open, camera modes picked, throwaway clips recorded and played back), `mic.css` and `camera.css` in the showcase, parked samples inflated, the macOS usage strings parked; the lanes unchanged; ADR-0725. **Left: the hand checks on four desktops** |

- **2026-11-21.** Written from the tree at `258b836f`. D1–D9 asked. Next ADR
  0719.
- **2026-11-21, answered.** D1–D7 and D9: (a). D8 sent back with a question:
  why PipeWire at all. See D8's note.
- **2026-11-21, D8 measured, phase 1.** PipeWire headers unpacked locally
  (no root here: `apt-get download libpipewire-0.3-dev libspa-0.2-dev` into
  `~/.local/pipewire-dev`, the `.pc` prefix rewritten, `PKG_CONFIG_PATH`); SDL
  then lists `pipewire(dynamic)` for audio and camera. The probe's table is in
  D8; `SdlAudioSink` counts two pulls on Linux. D8 taken: (a). Phase 1 built:
  the export list and shim rows (ABI 29), `SdlAudioDevices`,
  `SdlRecordingStream`, `natives.sdl.camera`, `natives.sdl.device`, tested on
  SDL's `disk` recording driver, the dummy camera driver, fabricated device
  events, and the C922 (`SdlCameraLiveTest`, NV12 frames read in place). Found
  on the way: a device id dies with the audio subsystem's last reference.
  ADR-0719. Next ADR 0720.
- **2026-11-22, phase 1 committed, phase 2.** Phase 1 is `ade8b594`.
  `goldberry-mic` built: `Microphones`, `Recording` (a capture thread, whole
  blocks to listeners, a pull ring that drops and counts past its buffer, a
  sealed status with `Lost` for an unplugged pinned device), `RecordingSpec`;
  `SampleSource`, borrowed `SampleBlock`s and a deterministic
  `SyntheticSource` over a sealed `Signal`; `Levels`, `LevelTracker`, `Fft`,
  `SpectrumAnalyzer`, `EnvelopeTracker`; SDL behind an unexported
  `CaptureBackend`. 32 tests: over a fake backend, on the synthetic source, and
  on SDL's `disk` driver recording a file. Published as `goldberry-mic`,
  optional in the umbrella. ADR-0720; the chapter parked as
  `docs/snapshot/components-microphone.md`. Next ADR 0721.
- **2026-11-22, phase 2 committed, phase 3.** Phase 2 is `77d569e1`. The
  widgets, in `dev.goldberry.mic.view`: each a stateful `@Markup` widget that
  feeds a tracker of its own on the source's thread and hands one rebuild to
  the UI thread however many blocks arrive. They build leaves in the exported
  `…mic.view.draw` (`LevelBar`, `WaveformPlot`, `SpectrumPlot`, with the node
  names as their CSS types), which an application also draws a clip's outline
  with: `Envelope.of` is new, and a short outline draws as bars with gaps.
  Colours are `--gb-mic-*` tokens in `mic.css`, read at render; the package
  is opened to `:core` and the sheet declared for native images
  (`DeclaredResourcesTest` caught it). An unbound `source=` draws silence. 42
  new tests: pixels of each leaf, markup, mounting under a headless runtime,
  the descriptor, and six goldens (three scenes, both themes) drawn from
  synthetic sources. `:mic` now requires `:widgets`; `MarkupNames.MODULES`
  lists `mic`. Seen on the way: an application rule setting a custom property
  on the render root itself does not beat a base-layer `:root` rule for it;
  inside any container it does, which is every window. `answers-tessera.md`
  G-05's portal promise corrected. ADR-0721. Next ADR 0722.
- **2026-11-22, phase 3 committed, phase 4.** Phase 3 is `a1458a9f`. First
  D2 in `:media`: `PictureView`'s state keeps a `PlanesOnCpu`, which its
  `VideoSurface` asks for a picture of planes only when the GPU did not place
  it, converting once a picture into a reused buffer (`picture-view-planes`
  golden, NV12 and I420). Then `goldberry-camera`: `Cameras` (list,
  `preferred`, hot-plug, open), a sealed `Camera` over a package-private
  device camera and `SyntheticCamera` (a test card painted as a function of
  the picture number); `CameraSpec.choose` takes the uncompressed mode
  nearest in log size plus twice the rate shortfall, which gives 640×480 at 30
  on the C922 for a 720p30 call; SDL is asked for NV12 in the mode's own
  colorspace (V4L2 tags YUYV BT.709 limited) and `Colorimetry` reads the
  matrix and range back; the capture thread copies into a three-slot
  `PlanePool` and polls a pending permission every 20 ms. `camera-view`
  builds a `CameraBox` with the picture or a message (six new labels in
  `ToolkitLabel`), its state as a class, and mirrors with a view-side
  `MirroredPictures` copy rather than CSS (a GPU layer would not flip). Found
  on the way: `camera-view > video-view` does not match, since the
  `picture-view` element sits between; `camera.css` uses a descendant
  selector. 35 tests, `CamerasLiveTest` taking pictures from the C922. The
  parked guide's "`VideoPlanes` are drawn on the GPU only" corrected.
  ADR-0722. Next ADR 0723.
- **2026-11-22, phase 4 committed; MJPG measured.** Phase 4 is `7461a17d`.
  Asked what kept MJPG out: only D4, which was weighed on §1's claim that
  the tree had no JPEG decoder. The rasterizer in `libgoldberry` has one,
  reached through `Image.decodeInto`, and phase 4 had already tied the camera
  to `:media`'s natives, which was (c)'s objection. A probe on the C922 (not
  committed) decoded 90 MJPG frames a mode with no failure: 4.7 ms median a
  1280×720 frame and 11.6 ms a 1920×1080 one, every frame carrying its Huffman
  tables; the camera delivered 17 fps at 720p (offered 60) and 27.9 at 1080p.
  §1 corrected, D4 (d) and phase 4b written up as a proposal, §5's risks
  updated. No ADR until D4 (d) is taken; next ADR still 0723.
- **2026-11-22, phases 4b and 5.** The owner took D4 (d) and asked for 4b and
  5 together. **4b:** `CameraSpec.choose` takes every mode, a tie going to an
  uncompressed mode, then the larger, then the rate nearer the one asked (so
  the C922's call is 1280×720 at 30 in MJPG, not at 60). The backend asks SDL
  for `MJPG` and lends a sealed `LentFrame` (`Nv12Frame` or `JpegFrame`). The
  capture thread decodes into a `BgraPool` with `MjpegDecoder` (new unexported
  `dev.goldberry.camera.mjpeg`), and the pool names a slot before taking it, so
  a failed frame takes none. A bad frame is dropped and counted
  (`Camera.droppedFrames()`). `JpegHeader` finds the first scan, and
  `StandardHuffmanTables` (Annex K.3) goes in before it where there is no DHT.
  `MirroredPictures` flips BGRA too. The fixtures come from Pillow
  (`tools/media/mjpeg_fixtures.py`). The inserted tables equal libjpeg's byte
  for byte, and a frame stripped of them decodes to the same pixels while the
  rasterizer alone refuses it. 16 new camera tests, `CamerasLiveTest` taking MJPG
  pictures from the C922 with none dropped. ADR-0723. **5:** `libpipewire-0.3`
  is a required `LinuxDependencies` row, and the superbuild stops without
  `SDL_CAMERA_DRIVER_V4L2`, `SDL_CAMERA_DRIVER_PIPEWIRE` or
  `SDL_AUDIO_DRIVER_PIPEWIRE`. `pipewire-devel` or `libpipewire-0.3-dev` is in
  `linux.yml` (both jobs), `showcase.yml`, `nightly.yml` and `benchmarks.yml`.
  Found on the way: the local library had lost PipeWire again on an earlier
  configure without `PKG_CONFIG_PATH`; `checkToolchain` now says so. **The
  camera hint was tried and dropped.** With `pipewire,v4l2` both live tests
  hung: SDL 3.4.18's PipeWire camera lists the C922's 218 modes, but PipeWire
  1.6.2 refuses its stream (`error use input buffers: -22`), because SDL never
  sends `SPA_PARAM_Buffers`. The stream never reaches `STREAMING`, the only
  place SDL approves a camera, so the permission stays pending, and
  `SDL_CloseCamera` hangs on 3.4.18's untimed `WaitDevice`. GStreamer's
  `pipewiresrc` streams the same node. Upstream main has a timed wait and the
  same empty `on_stream_param_changed`. The owner was asked (V4L2 first, an
  SDL patch, or a V4L2-then-PipeWire fallback) and did not answer, so the
  recommended V4L2 first was built at first. The live tests now time out on
  a thread of their own, since a same-thread `@Timeout` let the hang run 13
  minutes. ADR-0724. Next ADR 0725.
- **2026-11-22, the patch.** The owner chose the patch.
  `patches/sdl3-pipewire-camera.patch` sends `SPA_PARAM_Buffers` from
  `on_stream_param_changed` (2–16 buffers, one block, `MemPtr` or `MemFd`), as
  PipeWire's capture example does. It makes `WaitDevice` a one-second wait, as
  SDL's main branch has it, and approves a camera only while it is pending,
  since the state callback holds the loop's lock and takes the device's. Two
  more PipeWire symbols are loaded at run time. `apply_patch.cmake` is the
  patch step: it resets the touched files to the pinned ref, then applies,
  and stops the configure when the patch no longer applies. All three
  behaviours were tried by hand. Linux only, and the patch's hash is in the
  declaration. Probed on the C922 through PipeWire: 640×480 NV12 at 30.1 fps,
  1280×720 YUY2 at 9.9, 1280×720 MJPG at 30.0, 1920×1080 MJPG at 30.0; first
  frame 430–650 ms after opening; close about 2.4 s. The hint `pipewire,v4l2`
  is back, set once at load. `CameraDriverOrderTest` asserts `pipewire` where
  the daemon's socket exists, and it does here. `SdlPatchTest` holds the
  declaration, the patch and the fetched source. `THIRD-PARTY-NOTICES.md`
  marks SDL altered.
- **2026-11-22, phases 4b and 5 committed, phase 6.** Phases 4b and 5 are
  `a50b0dca`. The plan's "Media section" does not exist, so the capture cards
  are a Capture screen after Video (31 screens). `MicrophoneCard` and
  `CameraCard` each take a `Supplier` of the device list, so a test hands in
  one that refuses; their pickers put a test signal before the devices
  (`DeviceChoice`, keyed by place, chosen by object, so a hot-plug moves the
  key); a `Ticker` advances the synthetic sources on the loop. Nothing opens
  while a card is built, so the golden is the same everywhere. Found on the
  way: the showcase never loaded `mic.css` or `camera.css` (no meter track, no
  camera backdrop); `MicrophoneException` had no public constructor; a card
  test that mounts the meters needs `HeadlessRuntime`, since the redraw posts
  to `Goldberry.ui()`, and `UiExecutor.drain()` runs it. Tests: 15 in
  `ui.capture` (the picker, both cards on test signals and on a refusing
  system, a golden of a second of the tone), and `CaptureLiveTest`'s two, which
  recorded this machine's default microphone and opened the C922 for a call
  through the cards. `BookMarkupTest` now inflates the parked `kdl` samples
  too; its first run caught a parked `canvas3d` sample with no renderer
  (fenced `kdl,ignore` now). The parked chapters have an `### On macOS` each
  with the usage string. The Windows and macOS lanes already run both modules'
  tests; nothing changed there. ADR-0725. Next ADR 0726. Left: the hand checks
  (a USB camera and a headset on X11, Wayland, Windows and macOS, plugged in
  and out while open).
- **2026-11-22, phase 6 extended.** The owner asked for three more things:
  throwaway clips played back on the same screen, a camera's modes shown and
  picked, and the devices listed as the screen opens. Built: `CaptureDevices`
  on `Screen` and `GalleryContext` (the system's in the window,
  `CaptureDevices.none` in `ShowcaseScene`, the text-scale test, the shell
  test and the frame-budget benchmark), so pictures stay the same everywhere;
  `Cameras.open(CameraInfo, CameraFormat)` in `goldberry-camera`; a mode
  picker and three lines of what the camera is and offers; the test card in
  two sizes; and in a new `dev.goldberry.example.capture`, `AudioClip` (a
  float WAV), `AudioClipRecorder`, `VideoClip`, `VideoClipRecorder` and the
  picture copies. A clip's sound plays through a `MediaPlayer` of the card's
  own, its outline lit as it plays; its pictures are pushed into a
  `PushedPictures` a `picture-view` draws, unmirrored. Seen on the way: a
  golden taken right after a press catches the buttons' colours mid-ease;
  advance the clock first. Tests: 36 in the two capture packages, two goldens
  (a second of the tone with a clip, the test card's card with a mode and a
  clip), one exact-mode test in `:camera`; `CaptureLiveTest` now keeps and
  plays a clip from the default microphone and from the C922. ADR-0725
  amended in place (it was not committed) and renamed for its new title.
- **2026-11-22, bigger cards, metrics, and a popup fix.** The owner asked for
  bigger cards, metrics of the microphone and the camera, and reported an
  exception on opening the mode picker. The exception was the toolkit's: a
  `select` whose list is taller than the screen opens it inside a viewport,
  and re-describes it on every build, which `Popup.content` handed to
  `ElementTree.update` as the bare list. The first rebuild with the list open
  threw. `Popup.content` now fits the new description as the popup was opened,
  and `ElementTree` can `describe` (update or replace) its root; a
  `SelectLoopTest` reproduces it through the real loop (ADR-0726). The Capture
  screen is a wall of wide columns with taller pictures and readings, and each
  card shows metrics from a `RateMeter` read on the window's clock, so the
  goldens show what the test's clock says, not the machine's. Next ADR 0727.
- **2026-11-22, the camera's driver shown.** The owner asked why the camera
  was V4L2 and not PipeWire. It was PipeWire: a probe printed
  `driver=pipewire` and `C922 Pro Stream Webcam (V4L2)`, the name PipeWire
  gives the node its V4L2 plugin serves. `Cameras.driver()` is new in
  `goldberry-camera` (through `CameraBackend.driver()`), and the camera card has
  a Driver line saying which driver and what it is. ADR-0725 amended in place.
- **2026-11-22, Snapshot #88 and Qodana #92 on `b8e75f60`.** The first CI
  run on phases 1 to 6 was red in three places:
  - **libgoldberry on both Linux arches** stopped at the camera check.
    AlmaLinux 8's `pipewire-devel` is 0.3.6, SDL wants 0.3.44, and the
    §5 risk had assumed otherwise. `pipewire = "1.6.2"` is now in the
    catalog. Where the system's PipeWire is older than the floor, the
    superbuild stages that release's headers with `.pc` files that keep the
    system's libdir (`pipewire_headers.cmake`), so SDL still loads the
    system's library (ADR-0727). Built in the cached `manylinux_2_28_x86_64`
    image with linux.yml's packages and Configure: both PipeWire drivers
    were defined.
  - **Linux Java** had no library, as always. `SdlRecordingStreamTest` and
    `SdlCamerasTest` skipped in `@BeforeEach`, but their `@AfterEach` still
    called `Sdl.get()`, so seven tests failed. `SdlMicrophonesTest` in `:mic`
    had the same teardown, behind `:natives`. Each now undoes only what it
    set.
  - **Qodana: six Highs.** `Camera` and `YuvConverter` are in the profile's
    owned types (CameraView borrows the app's camera, and PlanesOnCpu caches
    its converter). `@Serial` is on both camera exceptions.
    `MirroredPictures` returns `Optional.of` the picture it just made.

  Next ADR 0728.
