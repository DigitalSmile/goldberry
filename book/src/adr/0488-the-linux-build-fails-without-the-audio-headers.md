# 488. The Linux build fails without the audio headers

Date: 2026-09-27

## Status

Accepted. Follows [ADR-0487](0487-with-no-audio-device-media-plays-silently.md),
which made a machine with no audio device play silently. That decision is for
a machine with no sound. This one stops a build from producing a library that
has no sound on a machine that does. It moves two rows of
[ADR-0082](0082-a-preflight-check-that-cannot-fail-is-not-a-check.md)'s table, and
fixes the cross-check of [ADR-0325](0325-a-build-says-what-it-can-ask-the-desktop.md).

## Context

`LinuxDependencies` listed `alsa` and `libpulse` as `OPTIONAL`, "a feature the
toolkit does not use". That was true until goldberry-media began playing its
sound through SDL's audio stream (ADR-0462). SDL loads `libasound` and
`libpulse` at run time, but compiles the two drivers in only when their headers
are there at build time. Without them it builds with `dummy` and `disk` only,
and never picks either by itself. The configure succeeds, the toolchain check
warns, and on that `libgoldberry` every source with audio plays silently. On
one machine the warning went unread until the showcase had no sound.

Making the configure fail on that exposed a second defect. The superbuild
cross-checks SDL's decisions (`docs/gaps.md` G32) by reading
`include-config-<config>/build_config/SDL_build_config.h`. SDL writes that file
with `file(GENERATE)`, and generation runs after the whole configure. During the
configure that checks it, the file holds the previous configure's answer, or
nothing on a clean build directory. So after the audio headers were installed,
the first build failed. SDL had found ALSA, but the header still said it had
not. Read that way, the D-Bus cross-check would also pass a build that had just
lost D-Bus, and never ran at all in CI's fresh containers.

## Decision

**`alsa` and `libpulse` are `NEEDED`.** Both are needed, not either one:
PulseAudio is what a desktop answers on, PipeWire's included, and ALSA is what
a machine without a sound server has. `checkToolchain` fails and names
`libasound2-dev libpulse-dev` (`alsa-lib-devel pulseaudio-libs-devel` with
dnf). Neither row names a capability, so `-Pgoldberry.allowDegradedPlatform=true`,
which waives only capability rows, does not waive them. A library built without
them has nothing to report through `Goldberry.capabilities()`. It is simply
wrong.

**The superbuild fails when SDL did not compile in `SDL_AUDIO_DRIVER_ALSA` and
`SDL_AUDIO_DRIVER_PULSEAUDIO`**, with the install line for both package
managers. The match ends at the ` 1`, because `SDL_AUDIO_DRIVER_ALSA_DYNAMIC`
shares the prefix. This is the only guard the manylinux release job meets,
since it runs CMake directly and never runs `checkToolchain`.
`linux.yml` already installs both packages, and `LinuxDependenciesTest` now
fails if it stops.

**SDL's answer is read from `CMakeFiles/SDL_build_config.h.intermediate`**,
which SDL's `configure_file` writes during the configure, before
`FetchContent_MakeAvailable` returns. The generated header stays as a fallback,
in case a future SDL stops writing the intermediate file. The fix applies to
every check that reads the file: D-Bus, IBus, udev, Wayland, libdecor and audio.

## Consequences

- Checked on this machine, both ways:
  - With a `PKG_CONFIG_LIBDIR` that hid only `alsa.pc` and `libpulse.pc`,
    `checkToolchain` failed, even with `allowDegradedPlatform`, and named the
    two packages.
  - With the headers installed, `:natives:cmakeBuild` passed. SDL's header has
    both drivers, and `libgoldberry.so` carries `libasound.so.2` and
    `libpulse.so.0` to load.
  - Reconfiguring with `-DSDL_PULSEAUDIO=OFF` failed on
    `SDL_AUDIO_DRIVER_PULSEAUDIO`, reading the intermediate file. It was then
    set back.
- A machine without the headers can no longer build `libgoldberry` at all. This
  is the point: the silent library was the worse outcome.
- The memory of this repo's local build, "use allowDegradedPlatform when D-Bus
  is missing", no longer covers audio. Install the packages.
- Tests: `LinuxDependenciesTest.requiresAudio` for both rows,
  `theContainerWorkflowInstallsAudio`, and in `PlatformIntegrationBuildTest`
  `audioStopsTheConfigure` and `sdlAnswerIsReadFromThisConfigure`.
- ADR-0487's silent sink is still needed. A machine with the libraries but no
  device, such as a headless box or a container, still has no device to open.
