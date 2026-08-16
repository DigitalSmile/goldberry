# ADR-0048: The showcase ships as a runtime image

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §15; [ADR-0021](0021-the-example-is-a-separate-build.md), [ADR-0023](0023-logging-and-the-example-as-a-subproject.md), [ADR-0039](0039-macos-needs-the-first-thread.md), [ADR-0041](0041-three-platforms-four-artifacts-two-backends.md)

## Context

The showcase could only be run the way it was developed: clone the repository,
install a C toolchain, build `libgoldberry`, then `./gradlew :example:run`. That
is a reasonable ask of a contributor and an unreasonable one of somebody deciding
whether the toolkit is worth their afternoon.

It also left a class of bug with nowhere to fail. `:example:run` puts the
application on the module path from the *build* tree, where every jar is present
because Gradle put it there. A module the toolkit forgets to export, an
`--enable-native-access` naming a module that no longer exists, a `libgoldberry`
that resolves only because a Gradle system property pointed at the superbuild's
output — each of those works in `run` and breaks the moment the application is
packaged.

## Decision

Build a **self-contained runtime image per platform**, and run *that* in CI.

`:example:showcaseImage` produces a directory holding a jlink-trimmed JDK, the
application modules, `libgoldberry`, and a launcher. It needs no JDK, no Gradle
and no arguments on the machine that unpacks it. On linux-x64 it is 57 MB
unpacked and 31 MB compressed.

**jlink, not jpackage.** jpackage produces a `.deb`, a `.dmg` and an `.msi`,
which means a code-signing identity on two of the three platforms and a
notarization story on one. The showcase is something to download from a CI
artifact and double-click, not something to install; jlink needs no signing
identity to produce it. jpackage is the right tool the day the toolkit ships an
application, and this is not that day.

**One runner per platform, not one runner cross-linking three images.** jlink can
target another platform given that platform's jmods, so a single job could in
principle emit all three. It would still need three `libgoldberry` builds, and
those genuinely cannot be cross-compiled here ([ADR-0041](0041-three-platforms-four-artifacts-two-backends.md)) —
so the runner is already committed and the cross-linking buys nothing.

**The launcher is written by hand, not by `jlink --launcher`.** jlink's launcher
bakes a fixed command line, and the path to `libgoldberry` is only known relative
to wherever the image is unpacked. The generated script resolves its own
directory and passes `-Dgoldberry.native.library` — which is the override
`NativeLibrary` already documents, rather than a new mechanism.

**Logback is named in `--add-modules`.** Nothing `requires` it — that is the point
of [ADR-0023](0023-logging-and-the-example-as-a-subproject.md) — so module resolution leaves
it out of the image and the application starts with no logging at all. Naming it
explicitly is also what binds SLF4J's `ServiceLoader` provider.

## Alternatives considered

- **A fat jar.** The toolkit is a module graph and its correctness depends on
  being one ([ADR-0007](0007-jpms-modules-enforce-the-native-boundary.md)):
  flattening it onto the classpath tests the opposite of what is claimed.
- **Ship the JDK separately and just zip the jars.** Smaller, and it moves the
  "install a JDK 25" problem onto the reader, which is most of what this record
  is trying to remove.
- **Put `libgoldberry` in a classifier jar inside the image.** It would ride
  along as a module resource and need no launcher trickery — but classifier jars
  are not modular, and jlink will not link an automatic module. Shipping the
  library beside the image and pointing at it costs one line in a script.

## Consequences

- **CI runs the packaged artifact, on all three platforms.** `example.yml` still
  runs the showcase from the source tree under Xvfb — that job is about the
  module path being right in development. `showcase.yml` is about the thing a
  user would actually download, and asserts the same three painted frames.
- **The images are uploaded as `.tar.gz` and `.zip`, not as directories.**
  `upload-artifact` zips whatever it is handed, and the zip it writes does not
  carry the executable bit — which would hand somebody an image whose `bin/java`
  will not run. Verified by round-tripping the tarball and launching from the
  extracted copy, not by reasoning about it.
- **`-XstartOnFirstThread` is in the macOS launcher.** The same flag `run` needs
  ([ADR-0039](0039-macos-needs-the-first-thread.md)), and forgetting it in the
  packaged form would fail with "No available video device", which mentions
  neither threads nor the flag.
- **This is not a distribution channel.** The showcase remains unpublished
  (§15); these are CI artifacts, and nothing versions or signs them.
