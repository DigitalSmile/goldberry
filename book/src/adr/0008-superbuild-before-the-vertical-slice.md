# ADR-0008: Build the CMake superbuild before the vertical slice

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.2, §16

## Context

M0 has two separable halves. One is the *native build*: a CMake superbuild that
statically links Blend2D, Yoga, HarfBuzz, and SDL3 (plus libxkbcommon on Linux)
into a single shared library, `libgoldberry`, on three operating systems and two
architectures. The other is the *Java shape*: jextract bindings, the wrapper and
arena discipline, and a window that presents a CPU buffer.

They can be done in either order. Developing against distribution packages via
`pkg-config` on Linux first would reach a window on screen sooner and prove the
FFM design earlier; the superbuild would then follow as a packaging task. The
counter-argument is that "works against my distro's SDL3" and "works against our
statically linked single-artifact build on macOS" are different claims, and
building the Java layer against the first can bake in assumptions that the second
invalidates — library initialization order, symbol visibility, how the library is
located and loaded, and which headers jextract is actually pointed at.

## Decision

Build the CMake superbuild first, as `docs/ARCHITECTURE.md` §16 specifies. The
Java layer is developed against `libgoldberry` — the real artifact, on all target
platforms — from the beginning, not against distribution packages.

The Gradle→CMake seam exists now in `natives/build.gradle.kts` as the
`cmakeConfigure` and `cmakeBuild` tasks, deliberately not wired into `build`
until the superbuild links.

## Alternatives considered

- **System libraries via `pkg-config`, Linux-first.** This was the recommended
  option and was not taken. It reaches a window sooner and retires design risk
  before build risk, but it proves the design against an artifact that is not the
  one shipped, and the differences are exactly where native packaging goes wrong.
- **Both in parallel.** Rejected: it is the same work as the superbuild plus a
  throwaway build path, on a project with one contributor.

## Consequences

- What M0 proves is what ships. There is no second integration step where the
  Java layer meets the real artifact for the first time.
- The distribution story — LWJGL-style classifier jars, one artifact per
  platform/arch — is exercised from the start rather than designed at the end.
- **The first visible pixel is further away.** The superbuild is the highest-risk,
  lowest-feedback part of the project, and it is now the first thing built. If it
  takes longer than expected, there is no partial result to show for it.
- Every contributor needs a working C/C++ toolchain, CMake, and Ninja before they
  can build the native module, even for Java-only work. The `natives` tasks are
  kept out of the default `build` graph partly to soften this.
- Blend2D's AsmJit needs W^X handling on Apple Silicon, and libxkbcommon builds
  with meson upstream rather than CMake. Both are superbuild problems and both
  are now on the critical path.
- The pure-Java layers — the three trees, the CSS engine, the KDL inflater, the
  semantics tree — have no native dependency and can proceed in parallel with
  full unit-test coverage. That is the hedge against this decision's main risk.
