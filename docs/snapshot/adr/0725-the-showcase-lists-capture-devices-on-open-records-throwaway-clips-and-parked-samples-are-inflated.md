# ADR-0725: The showcase lists capture devices on open, records throwaway clips, and parked samples are inflated

- **Status:** Accepted, as phase 6 of `docs/camera-and-mic.md` (W3.4,
  Tessera's T-05). Closes the plan's code; the hand checks are left.
- **Date:** 2026-11-22
- **Relates to:** ADR-0726 (a popup fix the screen found), ADR-0719 (the plan's decisions), ADR-0720 and ADR-0721 (the
  microphone and its widgets), ADR-0722 and ADR-0723 (the camera), ADR-0724
  (PipeWire)

## Context

Phase 6 asked for "a capture card on the showcase's Media section": a level
meter, a spectrum and a camera view with a device picker. The showcase has no
Media section. Audio and Video are two screens in Graphics & media, each built
around a player the cards share, and neither has room for a second subject.

Three things constrain any screen that opens a device:

- A golden photographs every screen, and every machine must draw the same
  picture. A card that listed the system's devices in a picture would
  photograph the build machine.
- `Goldberry.ui()` starts the desktop runtime when nothing has started one, so
  a test that mounts a widget posting to it needs a headless runtime first.
- macOS ends a process that records or opens a camera without a usage string
  in its bundle, and asks a person otherwise. The showcase runs from a
  terminal.

The guide chapters for both modules are parked in `docs/snapshot/` until the
release. `BookMarkupTest` inflates the `kdl` samples under `book/src` only, so
a parked sample is first checked when it moves, which is the release commit.
The camera chapter's samples were also waiting for `:camera` to be on
`:example`'s path, which this phase does.

## Decision

1. **A Capture screen of its own**, after Video in Graphics & media, with a
   Microphone card and a Camera card. Each card's picker offers a test signal
   first and then the system's devices: `SyntheticSource` playing a 440 Hz tone
   over a little noise, and a synthetic test card in two sizes. A ticker on
   the window's loop advances them, since a synthetic source delivers only
   when advanced.
2. **Devices are listed as the screen opens; nothing records until Start.**
   The owner asked for the list on open (amending a first version that listed
   on a press). Listing asks the system nothing a person must allow, on any
   platform. Where the devices come from is a `CaptureDevices` value on
   `GalleryContext` (and `Screen`): the system's in the window, and
   `CaptureDevices.none(reason)` in every picture and measured screen, as the
   capabilities are pinned. The cards' states own what they opened and close
   it when they are disposed, so leaving the screen stops the camera.
3. **A camera's modes can be picked.** The card lists every mode the chosen
   camera offers, after "Best for a call", and says what the camera is, where
   it faces, how many of its modes are uncompressed and how many Motion JPEG,
   and which mode is open. `Cameras.open(CameraInfo, CameraFormat)` is new in
   `goldberry-camera`: it opens exactly the mode given, and refuses one the
   camera does not offer, where `open(CameraInfo, CameraSpec)` picks the
   nearest. `Cameras.driver()` is new too, and the card shows it:
   through PipeWire a USB camera is named `… (V4L2)` after the PipeWire plugin
   serving it, which read as though the driver were V4L2.
4. **Each card records a throwaway clip and plays it back.** In
   `dev.goldberry.example.capture`: an `AudioClipRecorder` listening to the
   running source keeps up to 30 s, and the clip is written to a temporary
   WAV of IEEE float samples, which the media module's own PCM decoder plays
   through a `MediaPlayer` and an `audio-player`, with the clip's outline
   drawn by `WaveformPlot` and its played part lit. A `VideoClipRecorder`
   copies each picture the camera shows into memory of its own (packed NV12
   or BGRA), at most 30 s and 256 MB, since the toolkit has no encoder; it is
   played back by pushing the pictures into a `PushedPictures` on the loop's
   clock, unmirrored, as a recording is. Discarding, or leaving the screen,
   drops the clip and deletes the file.
5. **Each card shows its source's metrics**, measured by a `RateMeter` over
   the last two seconds on the window's own clock (`Host.clock()`), so a
   window reads real time and a test reads its own: for the microphone the
   format and block size, frames a second against the rate asked, how evenly
   blocks arrive, the level and, for a recording, what is buffered and
   dropped; for the camera pictures a second against the mode's rate, how
   evenly they arrive, what a picture is (NV12 planes, or BGRA decoded from
   Motion JPEG), bytes a second and frames dropped. The screen is a wall of
   wide columns, and the pictures and readings are taller, since each card
   carries a picture, its readings, its metrics and a clip.
6. **The links point at the media chapter** until the two chapters are in the
   book. `BookSections` resolves card links against `book/src`, and a link
   into a parked chapter would fail `GalleryDocsTest` today. The snapshot's
   manifest says where they point at release.
7. **The showcase loads `mic.css` and `camera.css`**, as it loads the media
   module's sheet. Without them the meter has no track and the camera view no
   backdrop. `MediaLines`' line, actions and caption are public, so the
   capture cards read as the media cards do.
8. **`MicrophoneException` has public constructors**, as `CameraException`
   has, so a stand-in can refuse as the system does.
9. **`BookMarkupTest` inflates the parked samples too**: every `kdl` block in
   `docs/snapshot/*.md` not marked `ignore`, through the showcase's catalogues.
   On its first run it found a parked `canvas3d` sample with no renderer bound,
   which would have failed at release. It is fenced `kdl,ignore` now, as the
   book's `canvas3d` samples are.
10. **The parked chapters say what macOS needs**: `NSMicrophoneUsageDescription`
   and `NSCameraUsageDescription` in the application's `Info.plist`, what
   happens without them, that the toolkit's plugin writes no bundle, and where
   a refused user allows it again.
11. **The Windows and macOS lanes need no change.** Both run `./gradlew build`
   with the library required, which runs `:mic:test` and `:camera:test`: the
   listing, the synthetic sources, and SDL's `disk` recording driver, which
   SDL builds on every platform. The live tests skip where no device is
   listed, and on macOS. The camera goldens need the media natives as well,
   which only `media.yml` builds, and that lane has no `libgoldberry`. So they
   run where both are built, which is a developer's Linux machine today.

## Consequences

- The gallery has 31 screens. Its golden, the shell's order, the search
  index, Home's counts and the Forms pictures (whose sidebar lists Capture)
  were retaken.
- `CaptureLiveTest` drives both cards over this machine's devices: the system
  default microphone recorded, a clip of it kept and played; the first camera
  opened for a call, a second of it kept and played to the end; both stopped.
  It skips on CI and on macOS.
- Goldens pin a second of the tone with a clip kept, and the test card's card
  with a mode picked and a clip kept (its pictures draw black there, since the
  example tests pin the media natives away).
- At release the parked chapters move into the book. Then `CaptureDocs` points
  at `components/microphone` and `components/camera`, or `GalleryDocsTest`
  finds the two chapters with no card.
- A hand check is still owed: a USB camera and a headset on X11, Wayland,
  Windows and macOS, plugged in and out while open.
