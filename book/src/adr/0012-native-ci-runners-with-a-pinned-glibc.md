# ADR-0012: Native CI runners, with a pinned glibc on Linux

- **Status:** Accepted
- **Date:** 2026-08-15
- **Supersedes:** [ADR-0011](0011-zig-cross-compilation-toolchain.md)
- **Relates to:** `docs/ARCHITECTURE.md` §3.2, §15

## Context

[ADR-0011](0011-zig-cross-compilation-toolchain.md) chose `zig cc` so that one
Linux host could produce every artifact in the §15 matrix. Its own consequences
section recorded a caveat on macOS; that caveat turned out to be decisive.

Zig bundles libSystem stubs, which cover libc — but SDL3's macOS backend needs
the Cocoa, Metal, CoreGraphics, and CoreText *frameworks*, and those headers ship
only in Apple's SDK, whose licence restricts use to Apple-branded hardware. That
is not a gap Zig can close. macOS needs a native runner regardless of what the
other targets do.

Once macOS is native, the question becomes whether Zig is worth keeping for the
other four artifacts, and the answer changes:

- **Windows.** Zig links through mingw-w64. Blend2D's AsmJit under mingw was
  already flagged in ADR-0011 as one of the two most likely first failures, and
  MSVC is the toolchain Blend2D and SDL3 are primarily tested against. Choosing
  mingw buys nothing and costs a known risk.
- **Linux.** Zig's advantage here was never really cross-compilation — it was the
  *glibc floor*. This part of ADR-0011 was correct and still is: building
  natively on `ubuntu-24.04` links against glibc 2.39, so the artifact refuses to
  load on RHEL 8 (2.28), RHEL 9 (2.34), Debian 12 (2.36), or Ubuntu 22.04 (2.35).
  For a library published to Maven Central that is a distribution defect, and
  native runners do not fix it by themselves.

So the real decision is not "cross-compile or not". It is "how is the glibc floor
pinned", and Zig was only one answer to it.

## Decision

Build every artifact on a native runner, and pin the Linux glibc floor with a
container rather than with a cross-compiler. Four runners produce four artifacts:

| Runner | Container | Produces |
|---|---|---|
| `ubuntu-24.04` | `manylinux_2_28_x86_64` | `linux-x64` |
| `ubuntu-24.04-arm` | `manylinux_2_28_aarch64` | `linux-aarch64` |
| `windows-2022` | — | `windows-x64` |
| `macos-14` | — | `macos-aarch64` |

One runner per artifact, and no cross-targeting: every leg builds for the machine
it is running on.

> **Amended by [ADR-0041](0041-three-platforms-four-artifacts-two-backends.md).**
> This ADR originally had the same four runners produce six artifacts, with two
> of them cross-targeting inside their own native toolchain — MSVC at `-A ARM64`
> and Xcode at `CMAKE_OSX_ARCHITECTURES=x86_64`. Those two rows were cut. The
> mechanism is unchanged and is what either row would come back through; what
> changed is the matrix, not the decision.

The `manylinux_2_28` images are the mechanism for the glibc floor: glibc 2.28
headers — the RHEL 8 baseline — with a modern GCC on top. This is the same
approach the Python wheel ecosystem has used for a decade.

Zig is removed entirely.

## Alternatives considered

- **Zig everywhere ([ADR-0011](0011-zig-cross-compilation-toolchain.md)).**
  Rejected: cannot do macOS at all, and on Windows it trades a well-tested
  toolchain for a risk that was already identified.
- **Native runners with no container, building on `ubuntu-22.04`.** Rejected: a
  glibc 2.35 floor still excludes RHEL 9 at 2.34, so it does not actually solve
  the problem — it just makes it less visible.
- **Zig for Linux only, native for Windows and macOS** — which is what
  `docs/ARCHITECTURE.md` §3.2 originally specified. A reasonable option, and the
  runner-up. Rejected because a container achieves the same glibc floor with the
  toolchain the upstream projects are actually tested against, and keeping Zig
  for one platform means maintaining a second compiler for one row of the matrix.
- **Symbol-versioning tricks to fake an older glibc.** Rejected: fragile, and it
  fails in ways that surface at load time on a user's machine.

## Consequences

- Every dependency builds with the toolchain its maintainers test against. Both
  risks ADR-0011 named — AsmJit under mingw-w64, HarfBuzz under Zig — disappear.
- Linux artifacts run on anything from RHEL 8 onward, and this is enforced by
  the build environment rather than by discipline.
- Should Windows on ARM come back, MSVC cross-targeting ARM64 from the same x64
  runner is well-travelled, unlike Zig's mingw path for the same target.
- **Local cross-building is gone.** A developer on Linux builds `linux-x64` and
  must push to find out about the rest. This is the cost ADR-0011 was trying to
  avoid, and it is accepted deliberately: the alternative paid for that feedback
  with toolchain risk on the riskiest part of the project, and `linux-x64` is
  the target one actually iterates against.
- **A locally built library is not the published artifact.** Local builds link
  against the host's glibc, so a `.so` built on a developer machine has a higher
  floor than the released one. Only the container build is shippable, and that
  has to be understood rather than discovered.
- CI becomes the only place a complete artifact set exists.
- ARM runners are free for public repositories. This decision assumes the
  repository stays public; going private makes `linux-aarch64` and the macOS legs
  billable, at which point the Zig-for-Linux runner-up becomes attractive again.
- The Linux legs are the one place CMake is not driven by Gradle: putting a JDK
  inside the manylinux image would buy nothing. CI invokes CMake directly and
  Gradle only packages the results, so the CMake arguments now exist in two
  places and must be kept in step.
