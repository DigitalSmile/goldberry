# ADR-0724: Linux builds PipeWire in, and patches SDL's PipeWire camera

- **Status:** Accepted, as phase 5 of `docs/camera-and-mic.md` (W3.4,
  Tessera's T-05). Builds ADR-0719's D8 (a). The first patch the superbuild
  carries on SDL. Amended by ADR-0727: AlmaLinux 8's PipeWire is below SDL's
  floor, and the release container stages pinned headers.
- **Date:** 2026-11-22
- **Relates to:** ADR-0719 (D8 taken on a measurement), ADR-0488 (the Linux
  build fails without the audio headers), ADR-0723 (Motion JPEG)

## Context

ADR-0719 took D8 (a): compile SDL's PipeWire drivers in, loaded at run time,
checked as ALSA and Pulse are, with the camera hint `pipewire,v4l2`. What was
left was where the check goes, which workflows install the headers, and the
hint.

SDL compiles its PipeWire audio and camera drivers from one pkg-config check,
`libpipewire-0.3 >= 0.3.44`. Without the headers both drop out and the
configure succeeds. This happened twice on the machine the work was done on:
a configure without the locally unpacked headers on `PKG_CONFIG_PATH` quietly
built a library with no PipeWire. Ubuntu 22.04's `libpipewire-0.3-dev` (0.3.48)
meets the floor. This ADR said AlmaLinux 8's `pipewire-devel` did too. It is
0.3.6 and does not (ADR-0727).

SDL starts the first camera driver in its list that starts, whether or not it
lists a camera. Its own order is V4L2, then PipeWire, and V4L2 starts wherever
the kernel has video nodes. So the hint is what puts PipeWire first.

**With the hint, SDL 3.4.18's PipeWire camera never streamed.** On PipeWire
1.6.2 with the C922, it lists the camera and its 218 modes and opens a stream.
PipeWire then refuses the stream: `error use input buffers: -22 (Invalid
argument)`. SDL's `on_stream_param_changed` is empty, so it never answers the
negotiated format with `SPA_PARAM_Buffers`, and PipeWire's V4L2 source does not
take the default buffers. The stream never reaches `STREAMING`. That is the
only place the driver approves a camera, so the permission stays pending for
ever. `SDL_CloseCamera` then hangs, because 3.4.18's `WaitDevice` waits on
PipeWire's loop with no timeout. SDL's main branch has since made that wait
timed, but its parameter callback is still empty. GStreamer's `pipewiresrc`,
which sends the parameters, streams the same camera. The hang reached two test
JVMs, whose live tests sat in a wait loop that a same-thread `@Timeout` could
not break.

The owner was offered three ways: V4L2 first with no patch, a patch, or V4L2
with a fallback to PipeWire. V4L2 first was built while the question was open.
The owner then chose the patch.

## Decision

1. **`libpipewire-0.3` is a required row** of `LinuxDependencies`
   (`libpipewire-0.3-dev`, `pipewire-devel`). It backs no capability, so
   `-Pgoldberry.allowDegradedPlatform=true` cannot waive it, any more than it
   waives the audio rows. `checkToolchain` names the package before CMake
   runs.
2. **The superbuild stops** when SDL's configure-time header lacks
   `SDL_CAMERA_DRIVER_V4L2`, `SDL_CAMERA_DRIVER_PIPEWIRE` or
   `SDL_AUDIO_DRIVER_PIPEWIRE`. The match ends at ` 1`, so the `_DYNAMIC`
   defines cannot satisfy it alone. The check is fatal, with no waiver, and
   names the package on both package managers. The release container runs
   CMake past `checkToolchain`, so this is the only check it meets.
3. **Every workflow that builds `libgoldberry` installs the headers**: the
   manylinux release job and the web-view job in `linux.yml`, and
   `showcase.yml`, `nightly.yml` and `benchmarks.yml`. `media.yml` builds no
   SDL and is unchanged. `LinuxDependenciesTest`'s existing sweeps hold the
   workflows to the table.
4. **SDL's PipeWire camera is patched**, by
   `natives/src/main/cmake/patches/sdl3-pipewire-camera.patch`, on Linux only,
   where the file is compiled. The patch does three things, each marked in the
   source as the zlib licence asks of an altered source:
   - It answers the negotiated format with `SPA_PARAM_Buffers`, as PipeWire's
     own capture example does: 2 to 16 buffers, one block each, memory the
     process can map (`MemPtr` or `MemFd`). The size and stride are the
     source's.
   - It waits at most a second in `WaitDevice`, as SDL's main branch does, so
     a stream that never delivers cannot hold `SDL_CloseCamera`.
   - It approves a camera only while it is still pending. The state callback
     runs under the loop's lock and takes the device's, which the camera
     thread holds while it takes the loop's. A stream that came back to
     `STREAMING` would otherwise deadlock the two.

   `pw_stream_update_params` and `pw_thread_loop_timed_wait` join SDL's list
   of symbols loaded at run time; both are in PipeWire 0.3. The patch is
   applied by `apply_patch.cmake` as FetchContent's patch step. The script
   resets the files the patch touches to the pinned ref, then applies it, so
   it is idempotent and follows a changed patch, and a patch that no longer
   applies stops the configure. The patch's hash is part of the declaration,
   so a changed patch runs the step again. `SdlPatchTest` holds the
   declaration, the patch's content, and that the SDL fetched here has it
   applied. `THIRD-PARTY-NOTICES.md` says SDL is altered on Linux.
5. **Cameras try PipeWire, then V4L2, on Linux.** `Sdl` sets
   `SDL_CAMERA_DRIVER=pipewire,v4l2` once, when the library is first loaded,
   at SDL's normal priority. The environment variable of the same name, and
   any later `setHint` (a test's `dummy`), still decide. Where no PipeWire
   daemon runs, its driver does not start and V4L2 does.
   `CameraDriverOrderTest` asserts `pipewire` where a daemon's socket exists.
   macOS and Windows keep SDL's order. No new symbol: an `SDL_GetHint` or
   `SDL_SetHintWithPriority` binding would have cost an ABI bump for the same
   effect.
6. **Playback follows SDL's own order**, which puts PipeWire first where it is
   the session's server, as D8 measured. `SdlAudioSink` already counts two
   pulls on Linux (ADR-0719), which puts both paths within 0.2 ms.
7. **The live camera tests time out on a thread of their own**
   (`ThreadMode.SEPARATE_THREAD`), so a camera that never answers fails them
   at 20 seconds instead of holding the build.

## Consequences

- With the patch, the C922 streams through PipeWire in every mode tried:
  640×480 NV12 at 30.1 fps, 1280×720 YUY2 at 9.9, 1280×720 MJPG at 30.0 and
  1920×1080 MJPG at 30.0. The first frame arrives 430–650 ms after opening,
  and a close takes about 2.4 s. The 1280×720 MJPG mode gave 17 fps through
  V4L2 in the earlier probe and 30 here, though the light in the room may
  differ between the two runs.
- A camera only PipeWire serves, such as a laptop's MIPI camera behind
  libcamera, is listed. Two applications can share a camera.
- Under PipeWire a camera is named by its node's description, so the C922
  reads "C922 Pro Stream Webcam (V4L2)".
- A Linux build needs `libpipewire-0.3-dev` (or `pipewire-devel`), as it
  needs ALSA's and Pulse's headers. The shipped library does not need PipeWire
  installed.
- An SDL bump has to rebase or drop the patch; the configure says which. The
  fix belongs upstream, and offering it there is the owner's call.
- Sandboxed applications still see no camera: SDL's PipeWire camera does not
  ask the portal (ADR-0719).
