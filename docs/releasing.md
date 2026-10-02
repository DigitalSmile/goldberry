# Releasing

How Goldberry is versioned, published and released. The reasoning is in
[ADR-0333](../book/src/adr/0333-a-version-is-a-year-and-a-count.md) (versions),
[ADR-0334](../book/src/adr/0334-central-is-fed-once-per-run.md) (Maven Central),
[ADR-0510](../book/src/adr/0510-publish-under-dev-goldberry.md) (the `dev.goldberry` group) and
[ADR-0336](../book/src/adr/0336-one-dependency-to-start-from-and-a-bom-to-line-up-the-rest.md)
(the BOM and the umbrella) and
[ADR-0340](../book/src/adr/0340-the-showcase-is-a-release-artifact-not-a-package.md)
(the native showcase on the GitHub Release); this page is the checklist.

## Status

| Piece | State |
|---|---|
| Calendar versions, resolved in Gradle | **built**, tested (`CalendarVersionTest`, `BuildVersionTest`) |
| `goldberry.publish` — POMs, sources, javadoc, signing, classifier jars | **built**, rehearsed locally into a throwaway repository |
| `goldberry-bom` and the `goldberry` umbrella, `html`/`emoji`/`gpu`/`media` optional | **built**, resolved by a local consumer build; `media` and its `ffmpeg-linux-x64` classifier published into `mavenLocal` on 2026-09-30 (ADR-0495) |
| `snapshot.yml` → `publish.yml` → Central snapshots | **the secrets are set and the first run went out** (run 17, 2026-09-19) — and failed partway: `:widgets:javadoc` refused a broken `[link]` after seven of the nine modules had uploaded, leaving `:widgets` and `:html` off that snapshot. The next green snapshot overwrites it. `check` generates javadoc now, so the same mistake fails on Linux four minutes in ([ADR-0405](../book/src/adr/0405-check-generates-the-published-javadoc.md)). **Runs 32 and 33 (2026-09-27, 2026-09-30) never reached the upload**: every per-OS Java job failed in `:natives:gpuTest`, where GPU test classes called SDL from their teardown after the missing library had skipped their setup. The teardowns now return when setup was skipped ([ADR-0495](../book/src/adr/0495-media-is-published-and-snapshots-publish-again.md)) |
| The `dev.goldberry` namespace on Central (ADR-0510) | **not yet verified**: until the TXT record is in place and snapshots are enabled for it (one-time setup, steps 1 and 2), `publish.yml` is refused. Snapshots before 2026-10-01 are under `io.github.digitalsmile` |
| `release.yml` → `publish.yml` → Central Portal deployment | **first run red, nothing uploaded** (run 36985188261, `v2026.2`, 2026-10-02): every lane green but `publish / windows / Java`, where `HttpIOTest`'s read-timeout test timed out in the open on a loaded runner. Fixed in the test; the run needs its failed jobs re-run ([release-2026-10-02.md](release-2026-10-02.md)) |
| `goldberry-media` FFmpeg for all four targets | `media.yml` builds `windows-x64` (MSVC under MSYS2) and `linux-aarch64` as well as `macos-aarch64` and `linux-x64` since 2026-10-02; a release refuses with one missing. Windows took four rounds (C runtime, the d3d11va hwaccels, `AVIOContext`'s C `long`, a GPU-less Direct3D), and its Media Foundation decoders are held back from 2026.2 ([ADR-0520](../book/src/adr/0520-media-foundation-is-held-back-from-2026-2.md)) |
| Release notes: `CHANGELOG.md` → `.github/release-notes.md` → the draft GitHub Release | **built** (2026-10-02): `:core:releaseNotes` writes the body, `release.yml`'s `notes` job opens the draft with it. `ReleaseNotesTest` and `ReleaseNotesRepositoryTest` hold the changelog, the template, the job and the documented coordinates |
| `showcase.yml` → native images on the tag's draft GitHub Release | **built; the images work** — a manual run built them on all three platforms and the html, canvas and Markdown screens were checked by hand (2026-09-17). The release upload has not run: no tag yet |
| Licence texts vendored (`checkLicenses -Pgoldberry.releaseCheck=true`) | **done** (2026-09-17) — all seven upstream files copied verbatim from the pinned checkouts; the check passes with eleven components |

## Versions

`YEAR.RELEASE[.PATCH]`: `2026.1`, `2026.2`, `2026.2.1`. The release count starts at
1 each year. There is no `.0` patch.

`gradle.properties` holds the line being worked towards — `goldberryVersion=2026.2`
— and **never** `-SNAPSHOT`:

```sh
./gradlew -q :core:printVersion                    # 2026.1-SNAPSHOT
./gradlew -q :core:printVersion \
    -Pgoldberry.release=true -Pgoldberry.releaseTag=v2026.1   # 2026.1
```

A release tag that does not match the property fails the build before anything is
compiled.

## Where things go

| What | Where | When |
|---|---|---|
| `goldberry-{common,natives,core,widgets,html,emoji,gpu,media}`, `goldberry-bom`, `goldberry` — `-SNAPSHOT` | `https://central.sonatype.com/repository/maven-snapshots/` | every push to master (`snapshot.yml`) |
| the same, released | Maven Central | a `v*` tag (`release.yml`) |
| `goldberry-natives` classifiers `linux-x64`, `linux-aarch64`, `macos-aarch64`, `windows-x64` | beside `goldberry-natives` | with it |
| `goldberry-media` classifiers `ffmpeg-<target>`: FFmpeg for each target the Media workflow built (`macos-aarch64` and `linux-x64` today). A release needs all four and refuses without them (ADR-0495) | beside `goldberry-media` | with it |
| `goldberry-media` classifier `ffmpeg-sources`: FFmpeg's and dav1d's complete source at the pinned tags, the superbuild, and how to rebuild and relink — LGPL-2.1 §4's corresponding source. One per version, about 24 MB. Every publication that carries an `ffmpeg-<target>` refuses to go without it, snapshots too (ADR-0508) | beside `goldberry-media` | with the `ffmpeg-<target>` classifiers |
| `goldberry-showcase-native-{linux-x64,macos-aarch64}.tar.gz`, `goldberry-showcase-native-windows-x64.exe` — the showcase as a GraalVM native image | the tag's GitHub Release, created as a draft; also the run's artifacts | a `v*` tag (`showcase.yml`); a manual run builds them as artifacts only |

Consuming it — the BOM for the version, the umbrella for the toolkit, and the
optional modules by name:

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }   // snapshots only
}
dependencies {
    implementation platform('dev.goldberry:goldberry-bom:2026.1-SNAPSHOT')
    implementation 'dev.goldberry:goldberry'                // common, natives, core, widgets
    implementation 'dev.goldberry:goldberry-html'           // optional: Markdown and HTML
    implementation 'dev.goldberry:goldberry-emoji'          // optional: the Noto Color Emoji face (OFL)
    implementation 'dev.goldberry:goldberry-gpu'            // optional: canvas3d and GPU composition
    implementation 'dev.goldberry:goldberry-media'          // optional: audio and video
    runtimeOnly 'dev.goldberry:goldberry-media::ffmpeg-linux-x64'   // FFmpeg, per target, as below
    // All four: `NativeLibrary` picks the right one at run time by `os.name` and
    // `os.arch`, so this works on every machine the application is built or run
    // on. Slim it to one line for a single target deliberately -- see below.
    runtimeOnly 'dev.goldberry:goldberry-natives::linux-x64'
    runtimeOnly 'dev.goldberry:goldberry-natives::linux-aarch64'
    runtimeOnly 'dev.goldberry:goldberry-natives::macos-aarch64'
    runtimeOnly 'dev.goldberry:goldberry-natives::windows-x64'
}
```

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.goldberry</groupId>
      <artifactId>goldberry-bom</artifactId>
      <version>2026.1</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency><groupId>dev.goldberry</groupId><artifactId>goldberry</artifactId></dependency>
  <dependency><groupId>dev.goldberry</groupId><artifactId>goldberry-gpu</artifactId></dependency>
</dependencies>
```

**The BOM knows versions, not platforms.** A Maven BOM is a version table and
nothing else: it cannot pick `goldberry-natives::linux-x64` for the machine that
builds against it, because a POM has no notion of an operating system or an
architecture. The umbrella `goldberry` depends on the bindings jar
`goldberry-natives` without a classifier, so the platform's classifier jars are
what an application adds itself. **All four is the default worth writing**, since
`NativeLibrary` picks the right one at run time by `os.name` and `os.arch` — the
one-platform form is the one that goes wrong quietly, on a developer building on
macOS for an application that ships to Linux.

**A Gradle plugin is what would choose for the consumer, and module-metadata
variants are not** — that pair used to be offered here as two options and only one
of them works
([ADR-0438](../book/src/adr/0438-a-jvm-consumer-carries-no-platform-so-a-variant-has-nothing-to-match.md)).
A variant is selected by matching the consumer's attributes, and a plain JVM
consumer declares no operating system to match on: it resolves the unattributed
jar silently. A consumer that *does* declare one then ties, because a variant
missing an attribute is compatible with every value of it — and the rule that
would break the tie is registered on the consumer's schema, where a producer
cannot put it. So the plugin is the whole of the answer, and it is not built.

A new optional module is one line in `PublishedModules`
(`new Library("pdf", Inclusion.OPTIONAL)`) plus `id 'goldberry.publish'` in its
build script; the BOM and the umbrella pick it up.

The showcase binaries are on the GitHub Release of each tag, as plain downloads;
a manual run of the *Showcase* workflow leaves them as the run's artifacts.

## One-time setup

Done by a person, once. Nothing here can be checked from the repository.

1. **Central Portal namespace.** Sign in at <https://central.sonatype.com>, add the
   namespace `dev.goldberry` (Namespaces → *Add Namespace*), and publish the
   verification key it issues as a DNS **TXT** record on `goldberry.dev`. Central
   verifies a reverse-domain namespace from the domain, not from a GitHub account
   (ADR-0510). The old `io.github.digitalsmile` namespace keeps the snapshots
   uploaded before the rename. Nothing is published there any more.
2. **Enable snapshots for the namespace** (Namespaces → the namespace → *Enable
   SNAPSHOTs*). Off by default; without it every snapshot upload is refused.
3. **A user token** (Account → *Generate User Token*). Its two halves are the
   secrets `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` — not the account
   password.
4. **A signing key.**
   ```sh
   gpg --quick-gen-key "Goldberry <maintainer@example.org>" rsa4096 sign 5y
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --armor --export-secret-keys <KEY_ID>      # -> secret SIGNING_KEY
   ```
   The passphrase is `SIGNING_KEY_PASSWORD`. Central checks the public key against
   the keyservers, so step two of this item is not optional.
5. **Repository secrets** (Settings → Secrets and variables → Actions):
   `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`,
   `SIGNING_KEY_PASSWORD`.
6. **Optional variable** `CENTRAL_AUTO_RELEASE=true`, once a few releases have been
   pressed through the Portal by hand. Until then a release stops there as a
   validated deployment.
7. **Settings → Actions → General → "Allow GitHub Actions to create and approve
   pull requests"**, or the bump job pushes its branch and then fails to open the
   pull request on it (ADR-0421).

The GitHub Release needs no setup: the workflow's own `GITHUB_TOKEN` creates the
draft and uploads to it.

Until step 5, snapshot runs rehearse into `mavenLocal` and say so in the run's
summary instead of failing.

## Cutting a release

1. **Rehearse.** Actions → *Release* → *Run workflow* on master. It builds every
   platform and runs the whole chain into `mavenLocal`, uploading nothing — javadoc,
   POMs, the classifier jars. Fix anything red before tagging.
2. **Check the licences**: `./gradlew checkLicenses -Pgoldberry.releaseCheck=true`
   must pass. The release run enforces it. A bumped upstream pin in
   `libs.versions.toml` means re-copying that component's file from the new
   checkout, since the copyright lines are the upstream's. A bumped `ffmpeg` or
   `dav1d` tag means its `ffmpegCommit` or `dav1dCommit` too
   (`git ls-remote <repository> 'refs/tags/<tag>^{}'`): the superbuild and
   `:media:ffmpegSourcesJar` both refuse a tag that does not name the pinned
   commit, and publication refuses the FFmpeg binaries without that jar
   (ADR-0508).
3. **Write the release's section of `CHANGELOG.md`**: rename `## Unreleased` to
   `## 2026.2 — <date>`, or write the section if there is none. It is the body of
   the GitHub Release, through `.github/release-notes.md`, and
   `./gradlew -q :core:releaseNotes` shows what it will be. Move the coordinates
   in the README and in the guide's *Installing* and *Your first Java
   application* to the version in the same commit; `ReleaseNotesRepositoryTest`
   holds them to the changelog's newest section.
4. **Tag the commit that declares the version**:
   ```sh
   git tag -a v2026.1 -m "Goldberry 2026.1"
   git push origin v2026.1
   ```
   `release.yml` publishes to a Portal deployment and opens the tag's GitHub
   Release as a **draft** with the notes; `showcase.yml` builds the native images
   and attaches them to it.
5. **Publish in the Portal** (Deployments → the deployment → *Publish*), unless
   `CENTRAL_AUTO_RELEASE` is on. Central takes a few minutes to an hour to sync.
   Then **publish the draft GitHub Release**, so the binaries and the artifacts
   appear together.
6. **Merge the bump.** `release.yml` opens it as a pull request — `bump/2026.2`,
   moving `goldberryVersion` to `2026.2`
   ([ADR-0421](../book/src/adr/0421-the-release-line-moves-on-by-itself.md)).
   Until it is merged master publishes `2026.1-SNAPSHOT`, which Maven orders
   *below* the release it follows. If the job could not open it, the branch is
   pushed and `./gradlew -q :core:bumpVersion` is what it ran.

## A patch

```sh
git switch -c release/2026.1 v2026.1
# fix, then set goldberryVersion=2026.1.1 -- or `./gradlew -q :core:bumpVersion`,
# which moves a patch line to its next patch rather than to the next release
git tag -a v2026.1.1 -m "Goldberry 2026.1.1" && git push origin release/2026.1 v2026.1.1
```

Patch branches publish no snapshots: `snapshot.yml` runs on master only.

## Rehearsing locally

The chain without any upload, with stand-in libraries:

```sh
for t in linux-x64:libgoldberry.so linux-aarch64:libgoldberry.so \
         windows-x64:goldberry.dll macos-aarch64:libgoldberry.dylib; do
  mkdir -p /tmp/art/${t%%:*} && echo stand-in > /tmp/art/${t%%:*}/${t#*:}
done
./gradlew publishToMavenLocal -Dmaven.repo.local=/tmp/m2 \
    -Pgoldberry.skipNative=true -Pgoldberry.artifactsDir=/tmp/art -x test
```

With FFmpeg too, from a local `./gradlew :media:ffmpegBuild`: hand the install
over as one target's build, and `goldberry-media` publishes `ffmpeg-linux-x64`
with `ffmpeg-sources` beside it. The source jar fetches FFmpeg and dav1d from
git the first time, into `media/.deps/sources`.

```sh
mkdir -p /tmp/ffmpeg && cp -r media/build/ffmpeg/linux-x64/install/lib /tmp/ffmpeg/linux-x64
./gradlew :media:publishToMavenLocal -Dmaven.repo.local=/tmp/m2 \
    -Pgoldberry.skipNative=true -Pgoldberry.media.artifactsDir=/tmp/ffmpeg -x test
```
