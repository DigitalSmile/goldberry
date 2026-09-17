# 334. Central is fed once per run

Date: 2026-09-17

## Status

Accepted. Builds the publishing half `docs/ARCHITECTURE.md` §15 and ADR-0009
promised and `book/src/TODO.md` recorded as missing. Never yet run against Central.

## Context

§15 says `goldberry-common`, `-natives`, `-core`, `-widgets`, `-html` and `-gpu` go
to Maven Central under `io.github.digitalsmile`, with the natives as four
classifier jars. Until now the artifact half existed — `release.yml` reused the
three per-OS workflows, gathered four libraries and ran `:natives:nativeJars` —
and the publishing half did not: no module applied `maven-publish`, so there was
no POM, no javadoc jar, no signature, and `release.yml` ended at
`upload-artifact`.

Two further asks shaped this: every push to master should publish a `-SNAPSHOT`
automatically, and it should come out of the Linux, macOS and Windows workflows.

The last one runs into how Maven snapshots work. A snapshot upload writes
`maven-metadata.xml` for its version, and that file's `<snapshotVersions>` lists
the artifacts *of that upload* — which classifier resolves to which timestamped
file. If the Linux runner published `goldberry-natives:2026.1-SNAPSHOT` with its two
classifiers and the Windows runner then published it with its one, the metadata
would name only `windows-x64`, and a consumer asking for `linux-x64` would get a
404. The Java modules would be published three times over, racing. The per-OS
workflows cannot each publish; they can each *build* for a publish.

## Decision

**One reusable workflow, `publish.yml`, is the only thing that uploads to Central.**
It calls `linux.yml`, `macos.yml` and `windows.yml`, waits for all three, downloads
the four libraries they uploaded, and runs one Gradle invocation:

```sh
./gradlew publishToMavenCentral -Pgoldberry.skipNative=true \
    -Pgoldberry.artifactsDir=artifacts [-Pgoldberry.release=true -Pgoldberry.releaseTag=v…]
```

Two thin callers:

- **`snapshot.yml`** — every push to master, `release: false`. Publishes to
  `https://central.sonatype.com/repository/maven-snapshots/`. Queued, never
  cancelled, so an upload is not stopped halfway.
- **`release.yml`** — every `v*` tag, `release: true`. A `workflow_dispatch` run is
  a rehearsal: the same chain as a snapshot, into `mavenLocal`, uploading nothing.

**The per-OS workflows lose their `push` trigger.** On master they run as jobs of
`snapshot.yml`, so each library is built once per commit and the one that passed
`verify` is the one published. Pull requests still run them directly, as before.
This is how "Linux, macOS and Windows publish snapshots" is met without four
publishers.

**In Gradle, a precompiled `goldberry.publish` plugin** applies
`com.vanniktech.maven.publish` 0.37.0, which speaks the Central Portal API
(OSSRH was retired in 2025) for both releases and snapshots and signs from an
in-memory key:

- Coordinates from `PublishedModules`, which is the list of what ships; applying
  the plugin to anything else fails configuration.
- Sources and javadoc jars; **javadoc with doclint off** (see Consequences).
- `:core`'s test fixtures are skipped from the component, including their sources
  variant, so no consumer's POM names them.
- Signing only when the version is not a snapshot.
- A release stops in the Portal for a person to press Publish, unless
  `-Pgoldberry.centralAutoRelease=true` — which `publish.yml` passes from the
  repository variable `CENTRAL_AUTO_RELEASE`.
- `:natives` attaches the four `nativeJar*` classifier jars to its publication
  only when `-Pgoldberry.artifactsDir` is given, and each jar already refuses to
  build without its library.

Credentials and key come from secrets as `ORG_GRADLE_PROJECT_*`. Without
`MAVEN_CENTRAL_USERNAME` a snapshot run rehearses into `mavenLocal` and says so in
the summary, the way `qodana.yml` waits for its token; a release without it fails.

`PublishWorkflowsTest` holds the shape: no `push` trigger on the per-OS workflows,
both callers going through `publish.yml` with `secrets: inherit`, and no other
workflow mentioning `publishToMavenCentral`. `PublishedModulesTest` holds each
module's build script to the list.

## Alternatives considered

- **Each per-OS workflow publishes its own classifier.** The literal reading of
  the request, and broken by the metadata argument above. Separate artifact ids
  per platform (`goldberry-natives-linux-x64`) would make it work, at the price of
  changing §15's coordinates, giving every platform its own POM and snapshot
  timeline, and still leaving three runners racing to publish the Java modules.
- **Keep `push` on the per-OS workflows and add `snapshot.yml` beside them.** Every
  commit would run the twenty-minute superbuild on every platform twice.
- **`workflow_run` after the per-OS workflows.** It fires once per completed
  workflow and has no "after all three" form; joining them means polling.
- **JReleaser.** Capable, and a second configuration language for what one Gradle
  plugin already does; its strength is GitHub releases, changelogs and
  announcements, none of which is asked for.
- **Plain `maven-publish` + `signing` + the Portal's upload API by hand.** The
  Portal takes a bundle zip through a REST call with polling for validation; that
  is exactly the code the vanniktech plugin maintains.
- **Fix the 120 javadoc errors first.** Right eventually, and not a reason to hold
  publishing: none of them stops the pages rendering.

## Consequences

- **Snapshots appear only after Linux, macOS and Windows are all green.** One
  broken platform stops every snapshot, which is the point — a snapshot missing a
  platform is broken for that platform's users — and also means a Windows-only
  flake holds up Linux consumers.
- The README's per-OS badges now report pull-request runs; master's health is the
  Snapshot badge.
- Central's side has to be set up by a person and cannot be tested from here: the
  `io.github.digitalsmile` namespace verified, **snapshots enabled for the
  namespace** in the Portal (they are off by default), a user token, and a GPG key
  published to a keyserver. `docs/releasing.md` lists them.
- `-Xdoclint:none` on the published javadoc means a broken `{@link}` ships quietly.
  The 120 findings are in `book/src/TODO.md`.
- The `natives` classifier jars are extra artifacts on the Maven publication and
  are not described in Gradle Module Metadata. A Gradle consumer asks for them by
  classifier, as a Maven consumer does.
- `publish.yml` runs the per-OS workflows three levels deep
  (`snapshot` → `publish` → `linux`); GitHub allows four.
- The first real run is untested. Every step up to the upload is exercised by the
  rehearsal (`release.yml`'s dispatch, or any snapshot run before the secrets
  exist), and was run locally with stand-in libraries into a throwaway repository.
