# ADR-0590: A change to the landing page alone builds only the landing page

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

`site/` is the landing page at goldberry.dev: HTML, a stylesheet, `content.js`,
`news.js` and a consent script. Nothing in the library reads it, and no jar
carries it. `pages.yml` builds it, filtered to `site/**` and `book/**`.

Every other workflow ignored the path. A push to master that touched only
`site/` started `snapshot.yml`, which calls `publish.yml`, which calls
`linux.yml`, `macos.yml` and `windows.yml`. That is the manylinux natives on two
architectures, the verify job with every golden and the GPU lane, a macOS and a
Windows build, and an upload of a `-SNAPSHOT` byte for byte the same as the one
already on Central. Qodana ran on the same push. A pull request doing the same
ran the three per-OS workflows, CodeQL and Qodana. A typo on the landing page
cost well over an hour of runner time across three operating systems.

One Java test does read `site/`. `SiteTest`, in build-logic, checks the domain
in `CNAME`, the landing page's links into the book against `SUMMARY.md`, and the
news dates. It ran in `linux.yml`'s Java job, which a site-only change would no
longer start.

## Decision

**A workflow that builds the library ignores `site/**`. `pages.yml` runs
`SiteTest` itself.**

- `paths-ignore: ['site/**']` on the `pull_request` trigger of `linux.yml`,
  `macos.yml`, `windows.yml`, `codeql.yml` and `qodana.yml`, and on the `push`
  trigger of `snapshot.yml` and `qodana.yml`. A commit that touches anything
  else as well still starts all of them. GitHub skips a workflow only when
  every changed path matches the ignore list.
- `workflow_call` takes no path filter, so `publish.yml` still calls the per-OS
  workflows on every run it makes. `release.yml` and `showcase.yml` trigger on
  tags, and GitHub does not evaluate a path filter for a tag push.
- `pages.yml` sets up Temurin 25 and runs
  `./gradlew -p build-logic test --tests 'dev.goldberry.build.site.*'` before
  it assembles the site. build-logic is a build of its own and needs no native
  library, so this costs a JDK download and a few seconds of Gradle. It runs
  on every `pages.yml` run, on a pull request and on a push.
- **`book/` is not ignored anywhere.** `BookTest` and `BookMarkupTest` hold the
  book to the code: a heading per markup name, every `kdl` sample inflated. The
  `:example` tests take the book's pictures. A chapter is documentation, but it
  is checked by the Java build, so a change to it builds.

`SiteOnlyChangeTest`, in build-logic's `ci` package, holds this as text. Every
workflow with a `push` or `pull_request` trigger has to ignore `site/**`,
allow-list its own paths without `site/` (`media.yml`), or trigger on tags
only, and only `pages.yml` is exempt, so a workflow added later is caught too.
The test also fails if any workflow ignores `book/`, or if `pages.yml` stops
running `SiteTest`.

## Consequences

- A push to master that touches only `site/` deploys the page and publishes no
  snapshot. The snapshot on Central is still the right one, because nothing it
  is built from has changed.
- `gradle.properties` is not ignored. `pages.yml` reads the version from it,
  but it is also the version every jar is built under, so a change to it builds.
- `master` has no branch protection and no required checks today. If a workflow
  skipped by a path filter is ever made a required check, GitHub reports it as
  *Pending* and a site-only pull request cannot merge. The fix then is a
  required check that always runs, not taking the filter away.

## Status

- Done 2026-10-10: the seven trigger filters, the `SiteTest` step in
  `pages.yml`, `SiteOnlyChangeTest` (12 tests, green, and it fails when the
  filter is taken out of `linux.yml`). `./gradlew -p build-logic test --tests
  'dev.goldberry.build.site.*'` passes locally against Temurin 25.0.4.
- Not yet seen on GitHub: the first site-only push is the first real run of the
  new `pages.yml` step.
- Parked for the release: the trigger column of the CI matrix in
  `book/src/contributing/testing.md`, in `docs/snapshot/contributing-testing-ci-matrix.md`.
