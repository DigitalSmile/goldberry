# ADR-0746: goldberry-vector is built on four targets, published with its natives, and carried in the native image

- **Status:** Accepted
- **Date:** 2026-11-24
- **Relates to:** ADR-0741 (the superbuild), ADR-0745 (the patches and the
  sanitizer lane), the `:media` workflow and image arrangements it copies

## Context

Phase 5 of `docs/vector-svg.md`: libgoldberry-vector had been built only on
the machine it was written on. A release of goldberry-vector refuses to publish
without a library for each of the four targets (`vector/build.gradle`), and the
showcase's native image had never seen the module. The plan asked for "the
superbuild on the four targets in `media.yml` (or a `vector.yml` beside it),
the classifiers published with the snapshot", the sanitizer lane in CI, and "a
native-image run of the showcase with the library as a resource. `ThorvgEngine`
registers a `Cleaner`, so it is initialized at run time."

## Decision

1. **`vector.yml`, beside `media.yml` and shaped like it.** `natives` builds
   on each target where the library runs: linux-x64 and linux-aarch64 in the
   manylinux_2_28 containers (cmake, ninja and meson from pip, git from dnf
   with `media.yml`'s mirror retry, the glibc floor checked), macos-aarch64 on
   its runner, windows-x64 with MinGW-w64 from MSYS2's UCRT64. The ThorVG
   checkout is cached by the catalog and the superbuild's sources. `test` runs
   `:vector:check` against each artifact with `goldberry.vector.required`, the
   robustness tests at their default rounds included. `sanitizer` runs
   `nativesBuildAsan sanitizerTest` on the linux-x64 runner. Triggered by a
   pull request touching `vector/**`, by hand, and as a reusable workflow.
2. **Published with the snapshot.** `publish.yml` calls `vector.yml`, waits
   for it, flattens the `vector-natives-<target>` artifacts and hands them over
   with `-Pgoldberry.vector.artifactsDir`, beside `:media`'s.
   `PublishWorkflowsTest` holds both lines.
3. **The module tells an image what it needs itself**, under
   `META-INF/native-image/dev.goldberry/`:
   - `goldberry-vector/reachability-metadata.json`, written into the jar by
     `:vector:foreignMetadata` from the bindings: every `…thorvg.calls` holder
     is initialised, `Downcalls` records each descriptor it links, and
     `…vector.nativeimage.VectorForeignMetadata` writes them with the natives
     jar's glob. A foreign call is registered because it exists. ThorVG calls
     nothing back, so there are no upcalls.
   - `goldberry-vector/native-image.properties`: the holders' package and
     `Downcalls` initialised at build time, so a downcall is a direct call;
     `VectorNatives` and its holder (which open the library) and `ThorvgEngine`
     (its `Cleaner` starts a thread, and whether the engine started is a fact
     of the running process) at run time. `VectorNativeImagePropertiesTest`
     holds the file to the classes it names.
   - `goldberry-vector-resources/reachability-metadata.json`: `vector.css`,
     which `:core` reads as a resource.
4. **The showcase has `:vector` at run time**, as it has `:spell`: SVG
   wherever an image is, and `svg-view` through its catalog; nothing names a
   class of it. `run` finds the library as it finds `:media`'s (an explicit
   `goldberry.vector.libdir`, else `:vector:nativesBuild`'s); the tests point
   at a directory that cannot exist, so no golden depends on this machine's
   build. The card itself is phase 6.
5. **The image carries the library**, as it carries libgoldberry:
   `:vector`'s host natives jar (a consumable `hostNativesJar` configuration)
   on the image's class path, where `VectorNatives` finds it through the
   system class loader. Only when there is a build to pack, since the jar task
   refuses to pack nothing; an image built without one says so and refuses SVG
   images. `showcase.yml` calls `vector.yml` and builds each image with that
   target's artifact, because its Windows leg is MSVC and the library is
   MinGW's.

## Consequences

The vector-only jobs exercise ThorVG on each target. Widget painting also needs
Blend2D. Before publishing, the Linux x64 job runs the SVG widget tests with
both libraries required, using the artifacts that go into the classifiers.

- Every snapshot carries goldberry-vector's natives for the targets that
  built, and a release cannot go without all four.
- A pull request touching `vector/**` pays for ThorVG on four runners and the
  sanitizer lane; the cache keeps a rebuild to the library itself.
- The showcase release image grows by the library (about 490 KB on
  linux-x64) and its metadata, and draws SVG.
