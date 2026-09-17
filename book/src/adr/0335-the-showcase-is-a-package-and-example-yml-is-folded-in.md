# 335. The showcase is a package, and example.yml is folded in

Date: 2026-09-17

## Status

Accepted. Extends ADR-0048; retires the workflow ADR-0021 and ADR-0023 describe.

## Context

`showcase.yml` builds a runnable image on each desktop platform — a jlink'd JDK,
the modules, `libgoldberry` and a launcher (ADR-0048) — runs it until it has
presented three frames, and uploads it as a run artifact. Run artifacts expire
after ninety days, need a GitHub login to download, and have no stable address:
there was no link to "the current showcase".

`example.yml` predates the image. It builds `libgoldberry` on an Ubuntu runner,
runs `./gradlew :example:build` — the example's goldens and screen tests, which
need a real rasterizer and skip in `linux.yml`'s `java` job — and then
`./gradlew :example:run` under Xvfb until three frames are presented. Its header
gives two reasons to exist: the module path, and the reflective binding exercised
as a real module (ADR-0155).

Both reasons are now met more strictly by `showcase.yml`'s Linux leg: the image
*is* a module-path launch, with nothing on it jlink did not keep, and the same
three-frame assertion. The one thing `example.yml` did that nothing else did is
run the example's tests against a built library.

## Decision

**The images are published to GitHub Packages** as a Maven artifact,
`io.github.digitalsmile:goldberry-showcase:<version>`, one classifier per target:

| Classifier | File |
|---|---|
| `linux-x64` | `.tar.gz` |
| `macos-aarch64` | `.tar.gz` |
| `windows-x64` | `.zip` |

A final `publish` job in `showcase.yml` runs on pushes only — master publishes a
snapshot, a `v*` tag the release, with the version resolved as ADR-0333 says —
downloads the three archives and uploads them in one Gradle call,
`:example:publishShowcasePublicationToGithubPackagesRepository`, with the job's
`GITHUB_TOKEN` and `packages: write`. Once, not per leg, for ADR-0334's metadata
reason. `ShowcasePackage` owns the target list and the tar-versus-zip rule, and
its test holds the workflow's matrix and file names to it.

`example/build.gradle` declares the publication only when
`-Pgoldberry.showcaseDir` is given, and no longer disables
`PublishToMavenRepository` — that blanket switch-off would have made the upload
report SKIPPED in a green run.

**`example.yml` is deleted.** Its `:example:build` step moves into `showcase.yml`'s
Linux leg, after the library is built. `LinuxDependenciesTest` now guards
`showcase.yml` alone, and `PublishWorkflowsTest` fails if `example.yml` returns.

## Alternatives considered

- **GitHub Releases for the images.** Public, unauthenticated downloads, which
  Packages is not — but only for tags. A master build has no release to attach to
  short of a rolling `nightly` release rewritten on every push. Worth adding for
  tagged releases later; it does not replace a snapshot address.
- **An OCI artifact on `ghcr.io`** through ORAS. Anonymous pulls for public
  packages, but a user needs `oras` to fetch a zip, and nothing else in this build
  speaks OCI.
- **Maven Central.** An application image with a bundled JDK is not a library, and
  Central's validation expects javadoc and sources for every artifact.
- **Keep `example.yml`.** It would duplicate the Linux leg's native build — the
  slowest step in either workflow — to run a launch the image already runs more
  strictly.

## Consequences

- **GitHub Packages' Maven registry needs authentication to download, even from a
  public repository**: a personal access token with `read:packages`. The images
  are an address for people with a GitHub account, not for everyone.
- Snapshot uploads accumulate: every push to master adds three images, each a
  trimmed JDK. GitHub does not prune Maven snapshots on its own; old files have to
  be deleted, by hand or by a scheduled `actions/delete-package-versions`, which is
  not written.
- The release images go to Packages from `showcase.yml`, not from `release.yml` —
  a tag triggers both, independently, so a release can reach Central while its
  showcase failed, or the reverse.
- The example's tests still run on Linux only, as they did.
- The README loses its Example badge.
