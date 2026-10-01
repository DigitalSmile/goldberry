# 508. FFmpeg's source is published beside its binaries, from the same place

Date: 2026-10-01

## Status

Accepted. Closes `book/src/TODO.md`'s "The LGPL corresponding-source offer for
FFmpeg is not decided", and with it the open row in `docs/media-plan.md`.
Completes what
[ADR-0495](0495-media-is-published-and-snapshots-publish-again.md) started when
it put FFmpeg on Maven Central, and adds a second refusal beside its
missing-target one.

## Context

`goldberry-media` carries FFmpeg's five shared libraries, with dav1d linked
statically into `libavcodec`, as `ffmpeg-<target>` classifier jars on Maven
Central (ADR-0495). Snapshots carry them as well as releases: every push to
master puts them in Central's snapshot repository, where anyone can download
them.

FFmpeg is LGPL-2.1-or-later. Those jars contain the Library itself in object
form, so the section that applies is **§4**:

> You may copy and distribute the Library […] in object code or executable form
> […] provided that you accompany it with the complete corresponding
> machine-readable source code […]. If distribution of object code is made by
> offering access to copy from a designated place, then offering equivalent
> access to copy the source code from the same place satisfies the requirement
> to distribute the source code.

The TODO entry cited §6, which is about a *work that uses the Library*: an
application linked against it. That is the application's obligation, not this
build's. What this build does is §4's case, and §4 offers two ways to comply.
It does not offer a written offer.

§0 says what "complete source code" means for a library: "all the source code
for all modules it contains, plus any associated interface definition files,
plus the scripts used to control compilation and installation of the library".
So the source alone is not enough. The scripts are the media superbuild,
because that is what turned the source into these files. dav1d is a module the
shipped `libavcodec` contains. Its own licence, BSD-2, asks only for the notice.
Its source is still part of FFmpeg-as-shipped's complete source.

A snapshot is not an exception. The licence concerns distribution, not version
labels, and a `-SNAPSHOT` jar on Central is downloadable object code like a
release is. The binaries that have already gone out as snapshots had the tags
and configure line in `ffmpeg-NOTICE.txt`, but no source.

The source also has to be exactly what was built. The superbuild clones FFmpeg
and dav1d from git with `ExternalProject` at the tags pinned in
`gradle/libs.versions.toml`. A tag can be moved, and nothing checked that one
had not been.

## Decision

**A classifier jar, `ffmpeg-sources`, goes beside the binaries under the same
coordinates.** That makes it §4's "equivalent access … from the same place":
`io.github.digitalsmile:goldberry-media:<version>:ffmpeg-sources`, in the same
repository and with the same version, published by the same Gradle invocation.
There is one jar per version, not one per target, because the source is the
same for every target. It is about 24 MB compressed: 10,664 entries and
100 MB unpacked.

```
README.txt              what this is, which binaries it is the source of, how
                        to rebuild offline and how to relink
ffmpeg-n8.1.3/          FFmpeg at the tag, as `git archive` writes it, + VERSION
dav1d-1.5.4/            dav1d at the tag, as `git archive` writes it
recipe/
  media/src/main/cmake/ the superbuild: CMakeLists.txt, package.cmake,
                        checkout.cmake, ffmpeg_layout.c
  gradle/libs.versions.toml   where it reads the tags and commits from
notices/<target>/ffmpeg-NOTICE.txt   each published target's exact configure line
licenses/ffmpeg.txt, licenses/dav1d.txt
META-INF/               Goldberry's LICENSE, NOTICE and third-party notices
```

**The trees come from git, checked against pinned commits.** `:media:ffmpegSourcesJar`
uses `UpstreamSource` in build-logic for each upstream. It shallow-fetches the
tag into a bare repository under `media/.deps/sources`, which outlives `build/`
so that later archives need no network. It refuses the tag unless it names the
commit pinned beside it in the catalog (`ffmpegCommit`, `dav1dCommit`). Then it
runs `git archive` on that commit. The clone's `info/attributes` turns off
`export-ignore` and `export-subst`, so a future upstream attribute cannot leave
a file out or rewrite one. The superbuild runs the same check: `checkout.cmake`,
as an `ExternalProject` step between download and configure, refuses a clone
that is not at the pinned commit. The binaries and the jar are each checked
against the same commit, so the published source is the built source.

Two inputs were possible, and both meet "reproducible". `git archive` of a
commit is a pure function of the commit, and the jar's timestamps, order and
modes are fixed: the archive uses `tar.umask=0022` and the files that do not
come from git are 0644. Two builds of the jar, the second from a fresh clone,
gave the same SHA-256. Only the git trees met "exactly what was built". FFmpeg's
release tarball is made by FFmpeg's release process, not from the checkout this
build compiles. It contains a `VERSION` file the tag does not, and nothing
guarantees the rest is identical. A checksum committed for it would prove that
the tarball is the tarball, not that it is our source. A pinned commit id is the
same kind of integrity check as a checksum: it is a hash of the content and its
history, and it is the id the build itself checks.

The jar adds one file to the trees, `ffmpeg-n8.1.3/VERSION`, which holds the
tag. Outside a checkout, FFmpeg's `version.sh` falls back to that file, as its
release tarballs do. With it, a rebuild reports `n8.1.3` as the shipped
libraries do, and not the `8.1.3` in `RELEASE`. The README says this is the one
file that does not come from git.

**The superbuild builds from the jar with nothing fetched.**
`-DGOLDBERRY_FFMPEG_SOURCE_DIR` and `-DGOLDBERRY_DAV1D_SOURCE_DIR` replace each
clone with a tree on disk. The commit check is skipped there, because that
check was made when the jar was. The README gives the whole command line, and
the recipe is the same code the published builds ran, so it can rebuild them.
On linux-x64, an unpacked jar rebuilt with nothing fetched. The five libraries
came to 6696 KB, against 6708 KB from the git checkout,
`ffmpeg-layout.properties` was identical byte for byte, and `libavutil`
reported `n8.1.3`.

**Publication refuses the binaries without the source.** `goldberry.publish`
calls `CorrespondingSource.require` for every `AbstractPublishToMaven` task in
the graph. It runs when the task graph is ready, before any task runs, and
covers `mavenLocal` and Central alike. Any `ffmpeg-<target>` classifier without
`ffmpeg-sources` fails the build, for snapshots and releases alike, with a
message that names this ADR. The check is placed there and not in `:media`'s
publish task because modules upload one after another. A refusal inside the
ninth module would leave eight on Central, which is what happened with the
javadoc in [ADR-0405](0405-check-generates-the-published-javadoc.md). `media/build.gradle`
attaches the jar wherever it attaches a target, and removing that line is what
the check catches.

**The jar needs git and the network, not FFmpeg.** It fetches and does not
compile, so `publish.yml`'s Java-only `maven` job builds it with no change to
the workflow. The fetch reaches `git.ffmpeg.org` and `code.videolan.org`, the
hosts the Media workflow already clones from.

**The notices say where the source is.** `ffmpeg-NOTICE.txt`, written by
`package.cmake` into every binary jar, now gives each upstream's commit and
names the `ffmpeg-sources` classifier of the same version. `NOTICE`, which
every Goldberry jar carries, and `THIRD-PARTY-NOTICES.md` point to it too.

## Consequences

- Every version on Central carries about 24 MB more. A snapshot replaces the
  previous one, so the snapshot repository does not accumulate copies.
- The `maven` job now depends on two more hosts being up. A failed fetch fails
  the publication, which is the right result: publishing the binaries without
  their source is what this decision rules out.
- Moving the `ffmpeg` or `dav1d` tag also means moving its commit pin
  (`git ls-remote <repository> 'refs/tags/<tag>^{}'`). If it is forgotten, the
  superbuild's first clone fails, before anything is compiled.
- A rebuild from the jar is not byte-identical to the published libraries.
  FFmpeg stores its configure command in `libavutil`, including the install
  prefix and the `pkg-config` path (`avutil_configuration()`). dav1d names its
  version `1.5.4` rather than `1.5.4-0-g54706fc` when there is no `.git`, and
  the build directories end up in the files. The README says so. What Goldberry
  reads the libraries by, the layout file, comes out identical.
- Only the linux-x64 rebuild from the jar has been run. The Windows and macOS
  steps are the superbuild's own steps, with the same caveats: the Windows
  superbuild has not been built yet (ADR-0495).
- An application that redistributes these binaries takes on §4 itself. Shipping
  the `ffmpeg-sources` jar beside them meets it, and `THIRD-PARTY-NOTICES.md`
  says so.
- `UpstreamSourceTest` makes a repository with git, archives a tag through
  `export-ignore` and `export-subst`, refuses a moved tag, and archives again
  offline. `CorrespondingSourceTest` refuses binaries without the source and
  checks that the convention, `:media`, the catalog and the three notices are
  wired to it.

## Alternatives considered

- **Upstream release tarballs, verified against a committed checksum.** This is
  reproducible, but not of the source that was built. The superbuild compiles a
  git checkout, and FFmpeg's tarball is a separate product of FFmpeg's release
  process, with at least a `VERSION` file the tag does not have. Changing the
  superbuild to build from tarballs would make the two match, at the cost of a
  second fetch path for the build and a checksum to maintain beside a tag. The
  commit pin already gives a hash, and it is the one the build checks.
- **B: link to upstream** (`ffmpeg.org/releases`, `git.ffmpeg.org`). §4 accepts
  access "from the same place" as the object code. Another organisation's
  server is not that place, and it can drop or move a tag without notice. A
  link also gives neither the recipe §0 counts as part of the source nor a
  single pinned dav1d. FFmpeg's own legal checklist asks distributors to host
  the source themselves.
- **C: a written offer.** §4 does not offer this option to someone distributing
  the Library itself. The offer is a §6(c) mechanism for works that *use* it,
  and the GPL's version has to be honoured for three years by whoever receives
  a request. That makes it a process someone has to keep running, which costs
  more than 24 MB on Central.
- **D: don't ship FFmpeg on Central.** ADR-0495 put it there because a
  `goldberry-media` with nothing to load fails on every machine that adds it.
  Leaving the binaries out to avoid publishing their source would bring that
  back, for snapshots too.
