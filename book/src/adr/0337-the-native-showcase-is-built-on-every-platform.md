# 337. The native showcase is built on every platform

Date: 2026-09-17

## Status

Accepted. Puts `book/src/native.md`'s "No CI job" into CI; extends ADR-0335.
Amended by [ADR-0340](0340-the-showcase-is-a-release-artifact-not-a-package.md):
the image is built on a `v*` tag or by hand and attached to the GitHub Release,
not published to GitHub Packages on every push.

## Context

`:example:nativeImage` has existed since ADR-0127/ADR-0156: a GraalVM native image
of the showcase, one executable with `libgoldberry` carried inside it (ADR-0159).
It was built and run by hand on linux-x64 and never anywhere else, because no CI
machine had a GraalVM. The traced reachability metadata it builds from was
recorded on that one machine.

The runtime images already go to GitHub Packages from `showcase.yml` (ADR-0335).
The native images should go beside them, for the same three platforms.

## Decision

**Each `showcase.yml` leg builds the native image after the runtime image**, on
the same runner:

1. `graalvm/setup-graalvm` with **`version: '25.3'`** and
   `distribution: graalvm-community` — GraalVM CE 25.3, today 25.3.4.1 on JDK
   25.0.4.1 — which sets `GRAALVM_HOME`, where `:example:nativeImage` already
   looks, and on Windows the MSVC environment `native-image` links with.
2. **macOS and Windows only:** `:example:nativeImageMetadata`, a fresh headless
   trace on that platform. Linux builds from the checked-in, reviewed trace.
3. `:example:nativeImage`.
4. The binary is run for three frames, as the runtime image is — under Xvfb on
   Linux.
5. Packaged as `goldberry-showcase-native-<target>.tar.gz` on unix, for the
   executable bit, and as `goldberry-showcase-native-windows-x64.exe` on Windows,
   where it is one file with no bit to lose.

On a push, **a second publish job** uploads the three as
`io.github.digitalsmile:goldberry-showcase-native:<version>` to GitHub Packages.
The runtime and native images are **separate artifacts published by separate
jobs**, and both jobs run unless the workflow was cancelled, so a native build
that fails on one platform does not stop the runtime images publishing. A partial
publication cannot happen: each Gradle publication names all three files, and a
missing one fails the upload. `ShowcasePackage` gained a `Kind` —
`RUNTIME_IMAGE`, `NATIVE_IMAGE` — carrying the artifact id, publication, property
and file format, and its test holds `showcase.yml` to all four.

**The pin is by GraalVM version, not Java version.** Since 25.1 GraalVM CE is
released as `graal-25.x` and versions apart from its JDK. `setup-graalvm`
resolves `java-version: '25'` alone through the older `jdk-25.*` tags, whose newest
is jdk-25.0.2 from January — two feature releases behind — which is what this
workflow first said. `GraalVmRelease.CI_LINE` holds `25.3`; a test holds
`showcase.yml` to it, and `:example:nativeImage` reads the GraalVM home's
`release` file and warns when a local build is on another line.

**`--no-fallback` is gone.** On 25.3 it is "deprecated … No effect, no
replacement": there are no fallback images left to refuse, and the flag was both
of the build's two warnings.

`:example`'s GraalVM lookup now tries `.exe` and then `.cmd` on Windows. It
asked for `java.cmd`, which GraalVM for Windows does not have — the trace step
would have failed on its first run there.

## Alternatives considered

- **A separate `native` job matrix.** Cleaner logs, and a second full superbuild
  per platform — the slowest step in the workflow, three times over, on the
  runners that cost the most.
- **Trace on every platform, Linux included.** Uniform, but it replaces the one
  trace that is reviewed as source (ADR-0156) with an unreviewed one on the
  platform where the reviewed one exists.
- **Trace nowhere; build every platform from the Linux trace.** Possibly enough:
  the bindings' descriptors are not chosen per platform today. But the trace is of
  what a run *did*, and a call, a resource or a reflective lookup only macOS's or
  Windows' code path reaches is absent from a Linux run — and a foreign descriptor
  registered nowhere is a `MissingForeignRegistrationError` at its first call.
  Whether the traced sets actually differ is unmeasured; the first CI run's
  metadata, diffed against the checked-in file, answers it.
- **Oracle GraalVM.** Profile-guided optimisation and the G1 collector, under
  the GraalVM Free Terms rather than an open-source licence; Community is what
  `native.md` was written against and is enough for a showcase.
- **Publish the binaries to the GitHub Release of a tag.** Unauthenticated
  downloads, and no address for master's snapshots. The same trade as ADR-0335.

## Consequences

- **Every showcase leg gets longer by a native-image build.** Measured on 25.3.4.1,
  linux-x64, 8 threads: 1 min 23 s, peak RSS 2.27 GiB, a 49 MiB binary. `macos-14`
  has 7 GB and 3 cores, so memory should hold and time will not; if the build is
  killed there, `-Pgraalvm.args=-J-Xmx…` is the first lever.
- **Moving to a new GraalVM line is a deliberate edit** of `CI_LINE` and the
  workflow together, followed by a local build — the way this one was found to need
  a fresh trace and a dead flag removed.
- **The macOS and Windows images are built from traces nobody reviewed.** They
  are as complete as a 120-frame headless run makes them. A screen the run never
  reaches can be missing a registration, which fails when that screen is opened —
  not in the three-frame check.
- A leg can now go red for a native-image reason while its runtime image is fine
  and published. The job summary and the separate `showcase-native-*` artifact say
  which.
- The native images are built against the library each runner builds, not the
  manylinux one (ADR-0012), exactly as the runtime images are. They are showcases,
  not the distribution.
- Snapshot pruning is now six files a push rather than three (ADR-0335).
- **The first local run proved the point of the job.** The Linux leg's commands,
  run by hand, built an image that failed on its first frame: the checked-in trace
  was from 30 August and nothing had registered the clipboard upcall added after
  it. Nothing had built an image since, so nothing had noticed. The trace was
  refreshed alongside this record — traced again on 25.3.4.1 from clean, with the
  same result — and from now on a stale one turns the Linux leg red on the next
  push rather than whenever somebody next builds by hand.
- None of this has run in CI yet.
