# ADR-0550: A module says what its tests need, and a plugin wires it

- **Status:** Accepted
- **Date:** 2026-10-03
- **Relates to:** `docs/build-simplification-2026-10-03.md`,
  [ADR-0016](0016-verify-the-artifact-and-never-skip-the-check.md),
  [ADR-0357](0357-a-test-that-paints-asks-for-the-library-and-a-download-asks-twice.md),
  [ADR-0475](0475-sdl-gpu-is-bound-for-core-and-gpu-and-tested-on-the-first-thread.md)

## Context

Eight build scripts wired their tests to `libgoldberry`, and each wrote the
wiring out for itself: JEP 472's grant, the library to load, the library as an
input, `:natives:cmakeBuild` first unless `-Pgoldberry.skipNative`, and a loop
handing command-line properties to the forked JVM. There were seventeen of those
loops and no two lists agreed, so a flag that worked in one module stopped at the
Gradle daemon in the next; `:gpu`'s tests did not wait for the native build at
all. `gpuTest` was registered four times, `resolveTool` three times, and
`prepareAssets` with `vendorLicences` three times.

The library's path came from `project(':natives').ext.hostNativeLibrary`, which
made seven modules call `evaluationDependsOn(':natives')`. `:media` reached into
`:gpu`'s and `:natives`' test output, and `:example` configured `:natives`' tasks
from outside. `:natives` was a hub every module had to configure through, and
none of it could survive Gradle's isolated projects.

## Decision

**A module says what its tests need; build-logic wires it.**

- `goldberry.native-tests` wires every `Test` task of a module that applies it.
  The library is the one named with `-D` or `-P`, or else the one this machine's
  `cmakeBuild` makes, at a path build-logic computes rather than one `:natives`
  publishes. The properties a test reads come from one list,
  `ForwardedProperties`, which `ForwardedPropertiesTest` holds to every switch a
  workflow sets. `nativeTests.gpuTests(package)` registers the GPU lane and
  `nativeTests.probe(...)` a measurement.
- `goldberry.asset-tool` registers `prepareAssets` and `vendorLicences` from
  `assetTool.bundle(entries, package)`.
- The target matrix is `NativeTarget` in build-logic, and tool lookup is
  `ToolLookup`. Both are ordinary Java with unit tests.
- What one module's tests share with another's is a **test fixture**:
  `:natives`' library and device requirements and its first-thread launcher,
  and `:gpu`'s compositing harness. No module names another's source sets.
- `:example`'s native image takes the host's natives jar through a resolvable
  configuration that `:natives` offers, not through `:natives`' tasks.

No build script calls `evaluationDependsOn` or reads another project's `ext`.

## Not done

CI's three platform workflows still build `libgoldberry` with CMake directly
rather than through `:natives:cmakeBuild`. Moving them needs a JDK inside the
manylinux container and a Gradle-driven MSVC build on Windows, and none of it can
be checked without running those workflows. It is the next step, and it will be
its own record.

## Consequences

- A module's script says what is particular to it. `:core`, `:widgets`, `:html`
  and `:emoji` lost the forty-odd lines each carried.
- A property added to `ForwardedProperties` reaches every test JVM. One a module
  never reads costs nothing there.
- `:gpu`'s and `:example`'s tests now wait for `cmakeBuild` like everyone else's.
  A Java-only build still passes `-Pgoldberry.skipNative=true`.
- The library's path is computed in two places, `natives/build.gradle`'s install
  directory and `NativeTarget.localLibrary`, from one constant.
  `NativeTargetTest` reads the script to hold the two together.
