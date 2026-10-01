# 495. Media is published, and snapshots publish again

Date: 2026-09-30

## Status

Accepted. Extends [ADR-0334](0334-central-is-fed-once-per-run.md)
and [ADR-0336](0336-one-dependency-to-start-from-and-a-bom-to-line-up-the-rest.md) to `:media`. Changes where FFmpeg's
libraries ship: `docs/goldberry-media.md` §2 named a separate
`goldberry-ffmpeg-natives` artifact, and they now ship as classifiers of
`goldberry-media`.

## Context

The request was for `html`, `emoji`, `media` and `gpu` to be snapshot artifacts
an application can add as optional dependencies. Three of the four already were
on paper. `PublishedModules` listed `html`, `emoji` and `gpu` as `OPTIONAL`, and
the umbrella's POM named them `<optional>`. `:media` was not published. Its
build script said why: "a `goldberry-media` on Maven Central with no FFmpeg to
load is a jar that fails on every machine that adds it", and the natives jar
that would carry FFmpeg existed for no target outside a developer's machine.

On paper was all it was. Snapshot runs 32 (2026-09-27) and 33 (2026-09-30)
failed before the Maven job started, so no snapshot of anything had gone out
since run 31. Every per-OS Java job stopped about thirty seconds in, in
`:natives:gpuTest`. The Java jobs build with `-Pgoldberry.skipNative=true`, so
there is no libgoldberry, and `GpuDeviceRequirement` correctly skips each GPU
test class from its `@BeforeAll`. The classes' `@AfterAll` then called
`Sdl.get().quit()` regardless. That loaded the library that was not there, and
the `UnsatisfiedLinkError` counted as a failure of the class. The launcher
fails on any failure, and `build` depends on it. Eight classes in `:natives`
and `:gpu` share the pattern. `WindowIdentityTest`, added in ADR-0491's commit,
has not been pushed yet, so the next run would have been red for the same
reason.

## Decision

**A teardown returns when its setup was skipped.** Each of the eight classes
(`SdlGpuDeviceTest`, `WindowIdentityTest`, `GpuApiTest`, `DrawTest`,
`YuvDrawTest`, `CompositorTest`, `LayerTexturesTest`, and
`GpuLayerBackendTest`'s `@AfterEach`) returns early when the field the
requirement fills is still null. With `-Dgoldberry.native.library` pointing
nowhere, `:natives:gpuTest`, `:gpu:gpuTest` and `:media:gpuTest` now pass:
28, 67 and 2 tests found, and every one that needs the library skipped. With
the library and `-Pgoldberry.gpu.videoDriver=x11`, `:natives:gpuTest` runs all
28 on the device, as before.

**`:media` is published, optional**, as `goldberry-media`. It is one line in
`PublishedModules` and `goldberry.publish` in its build script, so the BOM pins
it and the umbrella lists it `<optional>` without anybody editing either.

**FFmpeg ships as `goldberry-media`'s classifiers, `ffmpeg-<target>`.** That is
`dev.goldberry:goldberry-media:<v>:ffmpeg-linux-x64` and its three
siblings, the same shape as `goldberry-natives:<v>:linux-x64`. What the spec
wanted from a separate artifact still holds. The libraries are in jars of their
own, replaceable, carrying the LGPL text and the configure line. They are never
resolved unless an application names them. What the separate artifact would
have added is a second coordinate to model. `PublishedModules` has no kind for
"natives of another module", and `goldberry.publish` makes one publication per
project. A publication of classifier jars and nothing else could only be
checked against Central itself, on the one workflow that uploads.

**A snapshot carries what was built, and a release carries all four or fails.**
`publish.yml` now calls `media.yml`, which builds and tests FFmpeg on every
target it has a runner for (`macos-aarch64` and `linux-x64`) and uploads each
install. The Maven job hands them over with `-Pgoldberry.media.artifactsDir`.
`media/build.gradle` attaches a classifier for each target present, and warns
which ones are missing. For a release version it throws when any of the four
is missing, because a release on Central cannot be withdrawn and one missing a
target is a player that cannot load FFmpeg there.

Verified locally: `publishToMavenLocal` with a linux-x64 FFmpeg handed over
produces `goldberry-media-2026.1-SNAPSHOT.jar`, its sources and javadoc jars,
the POM, the module file, and `goldberry-media-2026.1-SNAPSHOT-ffmpeg-linux-x64.jar`.
`:media:javadoc` passes doclint, and `PublishedModulesTest` and
`PublishWorkflowsTest` hold the list and the workflow to it.

## Consequences

- An application adds media with
  `implementation 'dev.goldberry:goldberry-media'` and one
  `runtimeOnly '…:goldberry-media::ffmpeg-<target>'` per target it ships to. The
  loader's message when FFmpeg is missing names that coordinate.
- A snapshot run now also compiles FFmpeg, on two more runners. The FFmpeg and
  dav1d checkouts are cached by the catalog's hash, as in the Media workflow's
  own runs. A media failure now blocks every snapshot, which is the same rule
  the three per-OS workflows already follow.
- **Before the first release**: FFmpeg for `windows-x64` and `linux-aarch64`,
  which `media.yml` does not build yet. The release refuses without them. Also
  the LGPL corresponding-source offer that `docs/media-plan.md` lists as open.
- Snapshots of `goldberry-html`, `-emoji` and `-gpu` go out again with the next
  green run. None has since run 31.

## Alternatives considered

- **A `goldberry-ffmpeg-natives` artifact, as the spec named it.** It would
  need a second publication in `:media` or a Gradle project of its own, and a
  fourth kind in `PublishedModule`. It would buy a name. The name can still be
  changed before the first release, which is the point after which it cannot.
- **Publish `goldberry-media` without natives for now.** That is the jar the
  build script warned about, which fails on every machine.
- **Wait for all four targets before any snapshot.** A snapshot exists so that
  work in progress can be tried. Two targets that work are worth more than
  none.
