# ADR-0553: macOS and Windows build and test their library in one Gradle job

- **Status:** Accepted
- **Date:** 2026-10-03
- **Amends:** [ADR-0550](0550-a-module-says-what-its-tests-need-and-a-plugin-wires-it.md),
  whose "Not done" this does for two of the three platforms
- **Relates to:** `docs/build-simplification-2026-10-03.md`,
  [ADR-0016](0016-verify-the-artifact-and-never-skip-the-check.md),
  [ADR-0422](0422-what-sdl-compiled-is-the-fact-and-decorations-are-a-capability.md)

## Context

Each platform workflow ran three jobs. The first was a Java build with
`-Pgoldberry.skipNative=true`. The second ran CMake with its arguments written
out in the workflow. The third downloaded that library and ran the tests
against it. The split exists for Linux: the release library is built in a
`manylinux_2_28` container to pin the glibc floor, and that container has no JDK.

macOS and Windows copied the shape without the reason. Their runners have a JDK,
and `showcase.yml` already builds libgoldberry on both through
`:natives:cmakeBuild`. So the split cost them a second copy of the configure
line, an upload and a download, and three queues per run. The Windows workflow
also used the Visual Studio generator where the Gradle build uses Ninja and MSVC,
so CI and a laptop built the library two ways.

## Decision

**On macOS and Windows, one job builds and tests.** It:

1. runs `./gradlew :natives:cmakeBuild`, behind `ilammy/msvc-dev-cmd` on
   Windows;
2. checks the web view library;
3. runs `./gradlew build` with `-Pgoldberry.native.required=true` and
   `-Pgoldberry.webview.required=true`;
4. uploads `native-<target>`, after the tests, so what `publish.yml` packages is
   what they passed against.

ADR-0016's rule, that CI verifies the artifact it ships and cannot skip the
check, holds without a download. The coverage floors stay on linux-x64, where
they are measured. The GPU lanes keep what they had: `:natives` and `:gpu` on
macOS, not required, and none on Windows.

The upstream sources are cached per target, keyed on the version catalog. Only
the `*-src` clones are cached, never what they compiled to.

**Linux stays three jobs, and its library is still configured by the
workflow.** In the container, `checkToolchain` would refuse to build: libdecor
is in no repository AlmaLinux 8 has, and the table marks it needed. ADR-0422
already settled what ships there: window decorations are a capability the
library reports it lacks. `-Pgoldberry.allowDegradedPlatform`, the one switch
that waives a missing package, would also waive D-Bus, IBus and udev for the
shipped library, which is the opposite of what the release leg is for. Making
the release container Gradle-driven needs the toolchain check to know a
release-container profile. That is its own decision.

## Consequences

- One configure line for macOS and Windows, in `natives/build.gradle`.
  `PublishWorkflowsTest` and `WindowsToolchainTest` fail if a workflow configures
  CMake itself again, tests a downloaded library, or uploads before the tests.
- A red test on either platform now stops the upload. Before, the natives job
  uploaded whatever happened in verify, and only `publish.yml`'s `needs` stopped
  the publish.
- These jobs run every module's tests against the library, `:example` and
  `:media` included, which no macOS or Windows job did before. A platform
  difference in those suites now shows up on a pull request.
- The Windows library is built with Ninja and MSVC rather than the Visual Studio
  generator: the same compiler, through the build a contributor runs.
- Nothing here was run on a hosted runner before it was written. The first
  pull request is the test of it.
