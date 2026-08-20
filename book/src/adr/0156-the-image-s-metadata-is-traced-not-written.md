# 156. The image's metadata is traced, not written

Date: 2026-08-20

## Status

Accepted. The first thing built on
[ADR-0127](0127-the-binding-schema-fits-a-closed-world.md)'s promise, and it
takes [ADR-0155](0155-a-jar-binds-at-run-time-an-image-is-woven.md)'s weaving
flag as its input.

**No image has been built yet.** There is no GraalVM in this repository's
toolchain and none in CI, so what is recorded here is the invocation and the
decision behind it, not a binary that starts. That is stated in the tasks, in
[the native-image page](../native.md), and again in the consequences below.

## Context

ADR-0127 said the binding layer would no longer be the reason an image cannot be
attempted, and it was right: `NativeImageComplianceTest` parses the woven
bytecode and finds no `Class.forName`, no `setAccessible`, no
`defineHiddenClass`. The binding is closed-world clean.

The rest of the application is not, and the reason is `:natives`. A closed world
has to know, at image build time, every foreign function the program will call —
GraalVM folds a `Linker::downcallHandle` whose `FunctionDescriptor` is a
build-time constant, and needs `RuntimeForeignAccess.registerForDowncall` for
every one that is not.

Goldberry's are not. A binding class takes a `SymbolLookup` obtained from
`NativeLibrary` at run time and builds its handles in a constructor:

```java
private HarfBuzz(SymbolLookup lookup) {
    this.blobCreate = handle(lookup, "hb_blob_create", DESCRIPTOR);
    …
}
```

The library is `dlopen`ed when the program runs, from a path a system property
can override — which is exactly what ADR-0019 wanted and exactly what a closed
world cannot see through. `libgoldberry` exports **184 symbols**, and there is an
upcall as well: Yoga's measure callback is a `Linker::upcallStub`, which needs
registering whether or not the descriptor is constant.

There is also everything that is not FFM: the fonts and the 1544 compiled icon
paths in `:core`'s resources, the stylesheets, the KDL documents, and whatever
reflection Logback does to read `logback.xml`.

So something has to enumerate all of it.

## Decision

**GraalVM's tracing agent produces the metadata, a human reviews the diff, and
the result is checked in beside the code.**

Two tasks on `:example`:

- `nativeImageMetadata` runs the showcase under
  `-agentlib:native-image-agent`, headless, for 120 frames, and writes what it
  observed into
  `src/main/resources/META-INF/native-image/io.github.digitalsmile/goldberry-example`.
- `nativeImage` runs `native-image` over the woven jar and that metadata.

The metadata lives in `src/main/resources` and not in `build/`, because it is
**source**: it is reviewed in a pull request, it changes when the application
does, and packaging it into the jar is what lets a downstream image build find it
without being told.

`nativeImage` depends on `weaveModels` *and* `jar`, and `goldberry.weave` orders
the two — an image built from classes the weaver had not touched would be an image
that binds by reflection, which is the one thing ADR-0155 says an image never
does.

## Alternatives considered

**Write a `Feature` that registers the descriptors by hand.** The mechanically
honest option: a class implementing `org.graalvm.nativeimage.hosted.Feature` whose
`beforeAnalysis` calls `RuntimeForeignAccess.registerForDowncall` for each
descriptor. It is also 184 registrations that duplicate, in a second place, facts
the binding classes already state — and the failure mode of getting one wrong is
an image that builds and dies on the call. The binding classes are the source of
truth for what Goldberry calls; a hand-written list is a copy that goes stale
silently.

**Make the descriptors build-time constants so GraalVM can fold them.** This
would be the *best* answer and it is not available: it means resolving symbol
addresses at image build time, which means linking `libgoldberry` into the image
statically, which contradicts ADR-0019's "the library is a file the application
chooses" and ADR-0041's four platform artifacts. Worth revisiting only if a
statically linked image becomes a goal of its own.

**Ship no metadata and let `--no-fallback` fail.** Tempting, because the failure
would at least be loud. But the agent exists precisely to answer this, and asking
every consumer to derive the same file by hand is the mistake ADR-0155 just
finished undoing for the weaver.

## Consequences

**The metadata is only as complete as the run that traced it.** A screen the
120-frame run never reaches contributes nothing, and the symptom is an image that
starts and then dies opening a menu. This is the real cost of the decision and it
is not a small one: it makes the trace run part of the contract, and it means the
showcase's own coverage — every widget on a screen, §14's whole argument — is now
load-bearing for a second reason. The frame count is high enough to open the menu
and the HUD; a screen added later needs the trace re-run and the diff read.

**A reviewed diff, not a trusted tool.** Checking the output in means a change to
it shows up in review, which is the only mechanism that catches the agent
recording something surprising — a reflective call nobody meant to add.

**Two GraalVM-shaped holes remain, and neither has been walked into yet.**
Logback reads `logback.xml` reflectively and is a well-known source of
image-build friction; and `NativeLibrary` is marked `--initialize-at-run-time`
here on the reasoning that a class which `dlopen`s in its initializer must not run
in the builder, which is reasoning and not evidence.

**`build` does not depend on either task, and CI does not run them.** A task
nobody can run on the machines this project builds on would be a red build for a
missing tool. It fails with the download link when asked and says nothing
otherwise.
