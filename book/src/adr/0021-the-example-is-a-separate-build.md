# ADR-0021: The example is a separate build

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §15, [ADR-0007](0007-jpms-modules-enforce-the-native-boundary.md), [ADR-0014](0014-single-widgets-module.md)

## Context

Goldberry now has a backend that opens a window, and nothing that opens one. The
tests prove the pieces; they do not prove that somebody outside this repository
can use them.

That gap is specific, not vague. The toolkit is built as JPMS modules, published
as artifacts named `goldberry-core` rather than `core`, and loads a native
library out of a classifier jar with `--enable-native-access` naming a module.
Each of those can be wrong in a way every existing test passes:

- a package that is never `exports`ed compiles fine inside its own module
- an artifact whose coordinates do not match what applications are told to write
- a native library that resolves from the build tree and from nowhere else
- an `--enable-native-access` argument naming a module that does not exist

A `:example` module added to the main build catches none of them. It would
compile against the source tree through `project(':core')`, see every package
whether exported or not, and find the native library by relative path.

## Decision

`example/` is its own Gradle build with its own settings file and wrapper, and it
depends on `io.github.digitalsmile:goldberry-core:<version>` — coordinates, not a
project path. `includeBuild('..')` makes it a composite, so those coordinates
resolve to the local source tree during development and would resolve from Maven
Central without it. The dependency is written the same way either way, which is
the point: the example's build file is a file an application could copy.

The substitution is spelled out rather than left to Gradle. A composite exposes
an included project as `group:projectName` — `io.github.digitalsmile:core` — while
the published artifact is `goldberry-core`. `base.archivesName` renames the jar,
not the module coordinates. Without an explicit `substitute module(...) using
project(...)`, the example asks for a module the composite does not think it has
and Gradle goes looking on Maven Central for a version that was never released.

The example is a **module**, because an application on the module path is exactly
the case `--enable-native-access=<module>` exists for, and it only works if the
toolkit's descriptors are right.

CI runs it under Xvfb and greps the output. A window opens, three frames are
presented, the process exits on its own.

## Alternatives considered

**A `:example` module in the main build.** One build, one command, no
substitution to explain. It also proves nothing about exports, coordinates or
packaging — the four failures above all survive it. The cost of a second build is
a settings file; the cost of not having one is finding out after publishing.

**A test in `:core` that opens a window.** It would catch the native-loading path
and nothing else, and it would need a display in CI for every test run rather than
one job. Golden-image tests will need a window-free path anyway, which is what
`headless` is for (ADR-0019).

**Publish to `mavenLocal` and depend on that.** The most faithful reproduction of
what a user does. It also means `publishToMavenLocal` before every example build,
a stale-artifact failure mode that is deeply confusing when it happens, and a CI
job that is mostly about publishing. The composite gets the same coordinates with
none of that.

**Skip the run; compiling is enough.** Compiling proves the module graph and the
coordinates, which is most of the value. It does not prove the native library
loads, which is the part with six platform-specific ways to fail — and it is one
`xvfb-run` away.

## Consequences

An application-shaped consumer is built and *run* on every push, so the failures
that only appear outside the main build appear here instead of in an issue
report.

The example is where new features get their first honest API review: if
something is awkward to write in `Showcase.java`, it is awkward, and that is
visible before the widget catalog is built on top of it.

The cost is a second build to keep working. Its Gradle wrapper is a copy, so a
version bump touches two places, and the dependency substitution is a piece of
build machinery that has to be understood before it can be changed. Both are
written down here because neither is guessable from the file.

CI needs a display. `xvfb-run` is a Linux answer; the equivalent on Windows and
macOS runners is different enough that the example job is Linux-only for now.
That is a real gap — the native library loads differently on each platform, and
this job only proves one of them.

Adding `--frames=N` to the showcase for CI's benefit puts a test affordance in
example code. It is small and it is honest about what it is, but it is the kind
of thing that grows; the moment the showcase needs a second such flag, it wants a
proper harness instead.
