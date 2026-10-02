# Releasing

<p class="gb-lede">A version is a year and a count, every push to master publishes a snapshot, and a release is a tag on the commit that declares it.</p>

`docs/releasing.md` on GitHub is the full checklist, with the status of every piece and the one-time setup. This chapter is what the process looks like once it is set up.

## Versions

Versions are `YEAR.RELEASE[.PATCH]`: `2026.1`, `2026.2`, `2026.2.1`. The release count starts at 1 each year, and there is no `.0` patch. What a user of a toolkit wants from the number is how old it is, and that is what the year answers. The record is [ADR-0333](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0333-a-version-is-a-year-and-a-count.md).

`gradle.properties` holds the line being worked towards, and never `-SNAPSHOT`:

```properties
goldberryVersion=2026.1
```

The build resolves the actual version. Nothing else types the suffix:

| Inputs | Version |
|---|---|
| nothing | `2026.1-SNAPSHOT` |
| `-Pgoldberry.release=true -Pgoldberry.releaseTag=v2026.1` | `2026.1` |
| `-Pgoldberry.release=true -Pgoldberry.releaseTag=v2026.2` | The build fails, naming both |
| `goldberryVersion=2026.1-SNAPSHOT` in the file | The build fails, and so does `BuildVersionTest` |

```sh
./gradlew -q :core:printVersion
```

A release tag that does not match the property fails the build before anything is compiled, so a tag pushed on the wrong commit stops at configuration rather than at Central.

## Where things go

The group is `dev.goldberry`, and the artifact ids are `goldberry-<module>`. The namespace is [ADR-0510](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0510-publish-under-dev-goldberry.md), which moved it from an account name to the project's own domain while nothing had been released.

| What | Where | When |
|---|---|---|
| `goldberry-{common,natives,core,widgets,html,emoji,gpu,media}`, `goldberry-bom`, `goldberry`, as `-SNAPSHOT` | The Central Portal's snapshot repository | Every push to `master` |
| The same, released | Maven Central | A `v*` tag |
| `goldberry-natives` classifiers `linux-x64`, `linux-aarch64`, `macos-aarch64`, `windows-x64` | Beside `goldberry-natives` | With it |
| `goldberry-media` classifiers `ffmpeg-<target>`, one per target the Media workflow built | Beside `goldberry-media` | With it |
| `goldberry-media` classifier `ffmpeg-sources`: FFmpeg's and dav1d's complete source at the pinned tags, the superbuild, and how to rebuild | Beside `goldberry-media` | With the `ffmpeg-<target>` classifiers |
| `goldberry-showcase-native-{linux-x64,macos-aarch64}.tar.gz`, `goldberry-showcase-native-windows-x64.exe` | The tag's GitHub Release, created as a draft | A `v*` tag. A manual run leaves them as the run's artifacts |

The BOM knows versions and not platforms, so an application adds the `goldberry-natives` classifier jars itself. All four is the default worth writing, because `NativeLibrary` picks the right one at run time. [Installing](../getting-started/installing.md) has the dependency block.

**FFmpeg's binaries do not go without their source.** The `ffmpeg-<target>` jars carry an LGPL library in object form, so the same publication carries `ffmpeg-sources` beside them. Every publication that carries a target refuses to go without it, snapshots too, and both the superbuild and the sources jar refuse a tag that does not name the pinned commit. The records are [ADR-0495](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0495-media-is-published-and-snapshots-publish-again.md) and [ADR-0508](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0508-ffmpegs-source-is-published-beside-its-binaries-from-the-same-place.md). A release needs all four targets' FFmpeg and refuses without them.

## Snapshots

Every push to `master` runs `snapshot.yml`. It calls `publish.yml`, which builds Linux, macOS and Windows through the per-OS workflows, then publishes every module and every classifier jar in **one** Gradle invocation. It has to be one: a snapshot's classifier jars are listed in the metadata its upload writes, and a runner publishing its own platform would leave Central naming whichever finished last. That is [ADR-0334](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0334-central-is-fed-once-per-run.md).

So a snapshot appears only after all three platforms are green. One broken platform stops every snapshot, which is the point. Runs are queued rather than cancelled, because an upload stopped halfway leaves a snapshot whose modules disagree about which build they are.

Until the Central secrets exist, a snapshot run rehearses into `mavenLocal` and says so in the run's summary instead of failing.

## Cutting a release

<div class="gb-steps">
<div><b>1</b><p>Rehearse. Actions, Release, Run workflow on master. It builds every platform and runs the whole chain into mavenLocal, uploading nothing. Fix anything red before tagging.</p></div>
<div><b>2</b><p>Check the licences. The release run enforces it, and a bumped upstream pin means re-copying that component's file from the new checkout.</p></div>
<div><b>3</b><p>Write the release's section of CHANGELOG.md, and move the coordinates in the README and the guide to the version. The section is the body of the GitHub Release.</p></div>
<div><b>4</b><p>Tag the commit that declares the version. release.yml publishes to a Portal deployment and opens the tag's GitHub Release as a draft with the notes, and showcase.yml builds the native images and attaches them to it.</p></div>
<div><b>5</b><p>Publish in the Portal, then publish the draft GitHub Release, so the binaries and the artifacts appear together. Central takes a few minutes to an hour to sync.</p></div>
<div><b>6</b><p>Merge the bump. release.yml opens it as a pull request moving goldberryVersion to the next line.</p></div>
</div>

Step 2 is one command, and `releaseCheck` turns a warning about an unvendored licence into a failure:

```sh
./gradlew checkLicenses -Pgoldberry.releaseCheck=true
```

Step 3 is a section of `CHANGELOG.md`, newest first: rename `## Unreleased` to the version and the date, or write one. A tag whose version has no section is refused before the release is opened. The notes it makes, through `.github/release-notes.md`, are one command away:

```sh
./gradlew -q :core:releaseNotes     # prints the file it wrote, core/build/release-notes.md
```

Step 4 is a tag:

```sh
git tag -a v2026.2 -m "Goldberry 2026.2"
git push origin v2026.2
```

A release stops in the Portal for a person to press Publish, unless the repository variable `CENTRAL_AUTO_RELEASE` is `true`. A release on Central is permanent, so the first few get looked at.

Step 6 matters more than it looks. Until the bump is merged, `master` publishes `2026.2-SNAPSHOT`, which Maven orders *below* the release it follows, so a consumer on the snapshot silently goes backwards. That is why the release workflow opens the pull request itself, which is [ADR-0421](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0421-the-release-line-moves-on-by-itself.md). If the job could not open it, the branch is pushed and this is what it ran:

```sh
./gradlew -q :core:bumpVersion
```

## A patch

A patch is cut from a release branch that declares the patch version. `bumpVersion` on a patch line moves to the next patch rather than to the next release.

```sh
git switch -c release/2026.1 v2026.1
# fix, then set goldberryVersion=2026.1.1, or ./gradlew -q :core:bumpVersion
git tag -a v2026.1.1 -m "Goldberry 2026.1.1"
git push origin release/2026.1 v2026.1.1
```

Patch branches publish no snapshots. `snapshot.yml` runs on `master` only.

## The showcase binaries

Every release tag builds the showcase as a GraalVM native image on all three platforms, one file with `libgoldberry` inside it, and attaches the three to the tag's GitHub Release. It is a release artifact and not a package: there is no jlink image and no GitHub Packages upload. A push to `master` does not build it, because a native build on three runners is an artifact rather than a check. The record is [ADR-0340](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0340-the-showcase-is-a-release-artifact-not-a-package.md).

The two Unix binaries are tarballs rather than zips, because the zip format the artifact upload writes does not carry the executable bit.

## Rehearsing locally

The whole chain without any upload, with stand-in libraries:

```sh
for t in linux-x64:libgoldberry.so linux-aarch64:libgoldberry.so \
         windows-x64:goldberry.dll macos-aarch64:libgoldberry.dylib; do
  mkdir -p /tmp/art/${t%%:*} && echo stand-in > /tmp/art/${t%%:*}/${t#*:}
done
./gradlew publishToMavenLocal -Dmaven.repo.local=/tmp/m2 \
    -Pgoldberry.skipNative=true -Pgoldberry.artifactsDir=/tmp/art -x test
```

`-Pgoldberry.artifactsDir` is what attaches the four classifier jars, and each jar refuses to build without its library.

## The one-time setup

Central's side is set up by a person, once, and nothing in the repository can check it: the `dev.goldberry` namespace verified by a DNS TXT record on `goldberry.dev`, snapshots enabled for it, a user token, a signing key on a keyserver, and the four repository secrets. [`docs/releasing.md`](https://github.com/DigitalSmile/goldberry/blob/master/docs/releasing.md) lists every step and the current status of each.

> [!NOTE]
> Nothing has been released yet. Snapshots have gone to Central, and the release leg has never run, because there is no tag.
