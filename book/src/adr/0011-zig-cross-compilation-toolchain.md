# ADR-0011: Zig as the cross-compilation toolchain

- **Status:** Superseded by [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md)
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.2; [ADR-0008](0008-superbuild-before-the-vertical-slice.md)

> **Superseded the same day.** The macOS caveat below turned out to be decisive
> rather than incidental: once macOS needs a native runner regardless, Zig's
> remaining job is Linux and Windows, and the case for it does not survive that
> reduction. See [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md).
>
> One thing here does survive: the glibc floor. It is a real problem that native
> runners do not solve on their own, and ADR-0012 solves it a different way.

## Context

`docs/ARCHITECTURE.md` §3.2 scoped cross-compilation narrowly: native GitHub
Actions runners for Linux, Windows, and macOS, with only linux-aarch64 cross-built
using Zig. That works, but it makes the CI matrix the only place a complete set
of artifacts exists. A developer on Linux cannot find out that a change broke the
Windows build without pushing, and the feedback loop for the highest-risk part of
the project — the native superbuild, which [ADR-0008](0008-superbuild-before-the-vertical-slice.md)
placed on the critical path — runs through CI.

The goal is that one Linux host produces every artifact in the §15 matrix:
`{linux, windows, macos} × {x64, aarch64}`.

## Decision

Use `zig cc` / `zig c++` as the cross-compiler for every target, driven from
CMake toolchain files, with Gradle generating the per-target compiler wrappers.
Zig bundles what makes this possible: glibc headers for many versions, musl,
mingw-w64 for Windows, and libSystem stubs for macOS — one ~50 MB download
instead of a per-target sysroot zoo.

Six targets:

| Target | Zig triple | Output |
|---|---|---|
| `linux-x64` | `x86_64-linux-gnu.2.28` | `libgoldberry.so` |
| `linux-aarch64` | `aarch64-linux-gnu.2.28` | `libgoldberry.so` |
| `windows-x64` | `x86_64-windows-gnu` | `goldberry.dll` |
| `windows-aarch64` | `aarch64-windows-gnu` | `goldberry.dll` |
| `macos-x64` | `x86_64-macos.11` | `libgoldberry.dylib` |
| `macos-aarch64` | `aarch64-macos.11` | `libgoldberry.dylib` |

The glibc version is pinned into the triple deliberately. Building against
glibc 2.28 makes the Linux artifacts run on anything from RHEL 8 onward,
regardless of how new the build host is — a portability guarantee that is
awkward to get any other way and that native runners do not give you for free.

## Alternatives considered

- **Native runners per OS (the §3.2 plan).** Not rejected — see Consequences,
  it remains necessary for *testing*. Rejected only as the way artifacts are
  *built*, because it puts the slowest feedback loop on the riskiest work.
- **A conventional cross-toolchain set: mingw-w64 for Windows, osxcross for
  macOS, a crosstool-NG sysroot for aarch64.** Rejected: three separate
  toolchains to install, version, and keep consistent, where Zig is one.
- **Clang with per-target sysroots assembled by hand.** Rejected: this is
  approximately what Zig already is, minus the packaging and the bundled libc.
- **Docker images per target.** Rejected as the primary mechanism: it moves the
  problem into image maintenance and is markedly slower to iterate against. It
  remains a reasonable way to *reproduce* the toolchain in CI.

## Consequences

- One `zig` install cross-builds all six artifacts from Linux. A developer can
  find out that a change broke the Windows link without pushing.
- Linux artifacts get a pinned, portable glibc floor.
- **Cross-compilation produces artifacts, not test coverage.** Nothing built here
  can be *run* on Linux except the Linux targets. Golden-image tests (§14), the
  DPI behaviour, and the backend integration still have to execute on real
  Windows and macOS, so the CI matrix in §3.2 does not go away — its job changes
  from building to testing.
- **macOS needs an SDK that Zig does not and cannot ship.** Zig's bundled
  libSystem stubs cover libc, but SDL3's macOS backend needs the Cocoa, Metal,
  CoreGraphics, and CoreText *frameworks*, whose headers come only from Xcode.
  Apple's licence restricts use of that SDK to Apple-branded hardware, so this is
  a licensing question and not only a technical one. The toolchain therefore
  treats macOS as a target that activates when `goldberry.macosSdk` points at an
  SDK, and fails configuration with an explicit message otherwise. Building the
  macOS artifacts on a macOS runner remains the unambiguous option.
- `windows-aarch64` via Zig's mingw-w64 is the least-travelled path of the six
  and should be treated as experimental until it has linked and run once.
- Blend2D's AsmJit under mingw-w64, and HarfBuzz's build under Zig, are both
  unverified. They are the two most likely places the first cross-build breaks.
- The project now depends on Zig's bundled headers being correct, and on Zig's
  own release cadence. Zig is pre-1.0 and its CLI has changed across minor
  versions; the version is pinned in `gradle/libs.versions.toml` for that reason.
