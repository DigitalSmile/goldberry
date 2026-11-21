# ADR-0713: macOS 14 is the floor, whatever the runner

- **Status:** Accepted
- **Date:** 2026-11-21
- **Relates to:** ADR-0041 (the platforms and artifacts), ADR-0337 (the native
  showcase built on every platform)

## Context

GitHub retired the `macos-14` runner image: a job that asks for it fails with
"The configuration 'macos-14-arm64-us-default' is not supported". The macOS
leg, the media lanes and the showcase all asked for it.

No deployment target was set anywhere. Apple's toolchain then takes the SDK's
version as the oldest macOS a binary loads on, so the floor for
`libgoldberry.dylib`, `libgoldberry-webview.dylib` and `libgoldberry-media`
was whatever the runner's Xcode shipped: macOS 14 in practice, by accident. A
move to a newer image would have raised it for every user without anyone
deciding to.

## Decision

- The four jobs run on `macos-15`.
- `MACOSX_DEPLOYMENT_TARGET: '14.0'` is set at the top of `macos.yml`,
  `media.yml` and `showcase.yml`. Clang, CMake and the superbuild's projects
  read it from the environment; Linux and Windows ignore it.
- `macos.yml` reads `minos` back from every built dylib with `otool -l` and
  fails when it is not the workflow's target, so a static library that ignored
  the environment cannot raise the floor unnoticed.

## Consequences

- The floor stays where users had it: macOS 14 on Apple Silicon.
- A later runner change is a one-line edit; raising the floor is a separate,
  visible edit of the target and this record.
- An API newer than macOS 14 used from native code needs an availability check,
  or the build's `-Wunguarded-availability` warnings say so.
