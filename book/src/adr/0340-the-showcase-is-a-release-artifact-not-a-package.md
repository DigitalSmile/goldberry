# 340. The showcase is a release artifact, not a package

Date: 2026-09-17

## Status

Accepted. Retires [ADR-0335](0335-the-showcase-is-a-package-and-example-yml-is-folded-in.md)'s
GitHub Packages publication and [ADR-0048](0048-the-showcase-ships-as-a-runtime-image.md)'s
jlink image; keeps [ADR-0337](0337-the-native-showcase-is-built-on-every-platform.md)'s
native image and moves it to the release.

## Context

`showcase.yml` built the showcase twice on every push to master — a jlink runtime
image and a GraalVM native image, on three runners each — and published both to
GitHub Packages as `goldberry-showcase` and `goldberry-showcase-native`, one
classifier per platform. Two things were wrong with that once it ran.

A GitHub Packages Maven registry needs a token to download from, even on a public
repository, so the images were never a link anyone could follow; and nothing
pruned them, so every push added six archives that stayed. `TODO.md` had both
written down. And the six-runner build on every push was buying a check the
Snapshot already makes: the native image is exercised for three headless frames,
which is a smoke test of the packaging and nothing else, while the example's own
tests — the part of that workflow that found real bugs — ran on the Linux leg
alone.

The jlink image was the older of the two packagings (ADR-0048), kept when the
native one arrived. Two artefacts of one program, from one workflow, with two
launchers and two upload shapes, is one more than a demonstration needs; the
native image is the one that is a single file (ADR-0159) and the one the toolkit
is designed around (ADR-0127).

## Decision

**The showcase is built as a native image only, on a `v*` tag or by hand, and a
tagged build is attached to the tag's GitHub Release.**

- `showcase.yml` runs on `push: tags: ['v*']` and `workflow_dispatch`. The `image`
  job builds `libgoldberry`, traces where a platform needs to (ADR-0338), builds
  the native image, runs it for three frames and uploads it as a run artifact.
  A new `release` job, on a tag only, downloads the three and runs
  `gh release upload`, creating the release **as a draft** when it does not
  exist — so it is published by hand at the same moment as the Central Portal
  deployment, which is what `docs/releasing.md`'s checklist already asked for.
- The `publish` job, the `maven-publish` plugin on `:example`, its two
  publications and the `githubPackages` repository are gone, and so is
  `ShowcasePackage` in build-logic with its test.
- The jlink tasks (`jlinkImage`, `showcaseImage`) and their launcher scripts are
  gone from `example/build.gradle`.
- **The example's tests move to `linux.yml`'s linux-x64 verify leg**, beside the
  `:core`/`:widgets`/`:html` suites and the coverage floors, against the same
  downloaded library. That is the step of the old workflow that ran on every push
  and found bugs, and it keeps running on every push.
- `PublishWorkflowsTest` holds the workflows to this: a tag or a dispatch and
  nothing else starts the showcase, nothing writes packages, no jlink task
  exists, and the Linux verify leg runs `:example:build`.

## Consequences

- A push to master runs one fewer workflow, three fewer runners and no native
  build. A regression in the native packaging is now found on the next release
  build or manual run rather than on the next push; `native-image` reads the
  generated foreign metadata (ADR-0339) and the declared resources (ADR-0160), and
  both are unit-tested on every push, which is where the two real image failures
  of this week would have been caught.
- The release checklist gains a step: publish the draft GitHub Release beside the
  Central deployment. Until a tag is pushed the job has never run; a manual
  `workflow_dispatch` exercises everything but the upload.
- The README's "self-contained image" section now describes the native image.
  Anyone who wanted the jlink form builds it from the ADR-0048 commit's recipe;
  nothing in the toolkit depended on it.
