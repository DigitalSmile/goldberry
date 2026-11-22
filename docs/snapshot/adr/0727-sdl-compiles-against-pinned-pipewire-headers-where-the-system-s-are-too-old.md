# ADR-0727: SDL compiles against pinned PipeWire headers where the system's are too old

- **Status:** Accepted. Amends ADR-0724 (its premise about AlmaLinux 8 was wrong).
- **Date:** 2026-11-22
- **Relates to:** ADR-0724 (Linux builds PipeWire in), ADR-0719 (D8), ADR-0488
  (the Linux build fails without the audio headers)

## Context

ADR-0724 put `pipewire-devel` in the manylinux release job. It said AlmaLinux
8's `pipewire-devel` (0.3.x) met SDL's floor. It does not. Snapshot #88, the
first CI run on phase 5, installed `pipewire-devel-0.3.6-1.el8`, and SDL's
check answered:

```
-- Checking for module 'libpipewire-0.3>=0.3.44'
Package 'libpipewire-0.3' has version '0.3.6', required version is '>= 0.3.44'
--   SDL_PIPEWIRE                (Wanted: ON): OFF
```

ADR-0724's fatal check then stopped `libgoldberry (linux-x64)` and
`(linux-aarch64)` at Configure. Neither Verify job had an artifact to check. The
check did its job: without it, the release would have shipped with no PipeWire
camera. AlmaLinux 8 is the container's base for the glibc 2.28 floor, and RHEL 8
does not rebase PipeWire, so no package in that image meets the floor.

SDL builds both PipeWire drivers with `SDL_PIPEWIRE_SHARED`. It needs the
headers, plus a `libpipewire-0.3.so` to read the SONAME it later `dlopen()`s.
It never links the library. The machine phase 5 was built on already worked
this way: PipeWire 1.6.2's headers unpacked from debs, with the `.pc` libdir
pointing at a symlink to the system's `libpipewire-0.3.so.0`.

Other ways out were weighed:
- AlmaLinux 9's `pipewire-devel` RPM unpacked in the workflow. It works, but
  the URL names a version that moves to the vault on each update. It would
  also be a second place an upstream is pinned, outside the version catalog.
- A newer container. It moves the glibc floor, which is the reason for the
  container.
- Dropping the check. It brings back the silent build ADR-0724 exists to
  prevent.

## Decision

1. **`pipewire = "1.6.2"` is in `gradle/libs.versions.toml`**, read by
   `goldberry_pin` like every other upstream, and listed in `checkPinnedRefs`.
   1.6.2 is the release the camera's live checks on the C922 were built
   against.
2. **Staged only below SDL's floor.** Before SDL is made available, the
   superbuild asks pkg-config for the system's `libpipewire-0.3`. When it is
   older than `0.3.44`, the superbuild:
   - fetches the pinned tree (a shallow clone of GitHub's PipeWire mirror,
     with a `SOURCE_SUBDIR` that does not exist, so nothing of PipeWire's
     meson build runs);
   - stages its headers under `build/.../pipewire-headers`;
   - prepends that tree's `lib/pkgconfig` to `PKG_CONFIG_PATH`.

   A system that meets the floor uses its own headers, as before.
3. **Laid out as PipeWire installs them** (`pipewire_headers.cmake`):
   - `include/spa-0.2/spa` and `include/pipewire-0.3/pipewire` with
     `extensions/`;
   - `version.h` generated from `version.h.in` with the version from the
     tree's `meson.build`, not from the pin, so a commit pin also works;
   - `libspa-0.2.pc` and `libpipewire-0.3.pc`, with upstream's names,
     versions and flags.

   **The `.pc` libdir is the system's**, so SDL's `FindLibraryAndSONAME`
   still finds `/usr/lib64/libpipewire-0.3.so` and loads
   `libpipewire-0.3.so.0` at run time. A different release staged earlier is
   removed whole. For the same release, a staged header keeps its source's
   time (to the second) and an unchanged `version.h` is not rewritten, so a
   second configure does not rebuild SDL's PipeWire drivers.
4. **No PipeWire at all is not covered.** A system with no `libpipewire-0.3`
   still meets ADR-0724's fatal check, which names the package to install.
   Staging needs the system's library to be there for the SONAME.
5. **Tests.** `PipeWireHeadersTest` reads the superbuild's wiring as text: the
   floor is SDL's, Linux only, before SDL, the catalog's ref, the system's
   libdir. It also runs `pipewire_headers.cmake` with `cmake -P` over a small
   tree laid out as PipeWire's, checking:
   - the layout, `version.h` and both `.pc` files;
   - that a re-stage keeps the source's times and an unchanged `version.h`,
     and that a new release replaces the tree;
   - that a tree without `version.h.in` stops the script.

   It skips where `cmake` is not on the PATH.

## Consequences

- The release container builds both PipeWire drivers again. This was checked
  in the cached `manylinux_2_28_x86_64` image with linux.yml's packages and
  Configure. The configure printed the staging line, SDL defined
  `SDL_CAMERA_DRIVER_PIPEWIRE` and `SDL_AUDIO_DRIVER_PIPEWIRE`, and the
  library built.
- The shipped library is compiled against 1.6.2's headers and runs against
  whatever PipeWire the user has, as on any build machine whose own headers
  are 0.3.75 or newer. With those headers, SDL's camera driver resolves
  `pw_check_library_version`, which PipeWire has had since 0.3.75. On an
  older PipeWire (Ubuntu 22.04 has 0.3.48), the driver's symbols fail to load
  and SDL moves on to V4L2, so V4L2 cameras are still listed. SDL's PipeWire
  audio driver steps aside below PipeWire 1.0 whatever the headers, and
  playback stays on Pulse.
- The aarch64 container fetches the same tree; the headers are not
  architecture-specific.
- A PipeWire bump is a catalog edit. `pipewire_headers.cmake` stops the
  configure if the tree stops looking like PipeWire's.
