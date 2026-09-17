# 333. A version is a year and a count

Date: 2026-09-17

## Status

Accepted. Replaces `goldberryVersion=0.1.0-SNAPSHOT` and the "0.1 release" that
`docs/ARCHITECTURE.md` §16 named at M5.

## Context

Nothing had been published, so the version had never had to mean anything:
`gradle.properties` said `0.1.0-SNAPSHOT`, every module read it verbatim, and the
suffix was a string somebody would have had to remember to delete on release day
and put back the day after.

Publishing (ADR-0334) makes the version permanent. A release on Maven Central
cannot be withdrawn or replaced, so the three places a release names itself — the
tag, `gradle.properties` and the POM — have to agree, and the moment they could
disagree is the one moment nobody is looking at them.

Semantic versioning was the default and fits a toolkit poorly. Goldberry breaks
API on a schedule set by milestones rather than by individual changes, a `1.0`
would be a statement about stability the project is not ready to make, and a
`0.x` that runs for years says nothing at all. What a user of a UI toolkit wants
from the number is *how old is this*, which is what JetBrains' `2026.1` / `2026.2`
answers.

## Decision

**Versions are `YEAR.RELEASE[.PATCH]`** — `2026.1`, `2026.2`, `2026.2.1` — where
`RELEASE` counts the releases of that year from one and starts again in January.
One spelling per version: no `.0` patch, no leading zeros. `CalendarVersion`
parses and orders them.

**`gradle.properties` declares the release line being worked towards, never a
snapshot.** `goldberryVersion=2026.1`. The conventions plugin resolves the actual
version through `BuildVersion`:

| Inputs | Version |
|---|---|
| nothing | `2026.1-SNAPSHOT` |
| `-Pgoldberry.release=true -Pgoldberry.releaseTag=v2026.1` | `2026.1` |
| `-Pgoldberry.release=true -Pgoldberry.releaseTag=v2026.2` | build fails, naming both |
| `-Pgoldberry.release=true` without a tag | build fails |
| `goldberryVersion=2026.1-SNAPSHOT` | build fails: the suffix has one author |

So the suffix is never typed. The release workflow is the only thing that passes
the flag, and it passes the tag it was triggered by, so a tag pushed on the wrong
commit fails at configuration — before a jar exists — rather than at Central.

`./gradlew -q :core:printVersion` prints the resolved version, which is what the
workflows write into a run's summary.

After tagging `v2026.1`, master's `gradle.properties` moves to `2026.2`. A patch is
cut from a `release/2026.1` branch that declares `2026.1.1`. `docs/releasing.md`
is the checklist.

## Alternatives considered

- **Derive the version from git tags** (`axion-release`, `jgitver`, `git describe`).
  No file to bump, but a snapshot's version becomes a function of the nearest tag
  and the distance from it, which needs the full history in every CI checkout
  (`fetch-depth: 0`), differs between a shallow clone and a real one, and makes
  "what version is master building" a question with a computed answer. A property
  is readable in a diff.
- **Keep `-SNAPSHOT` in `gradle.properties` and strip it at release.** The status
  quo, and the failure it invites is the one this record exists for: the strip is
  a step, and a skipped step publishes `-SNAPSHOT` to a release repository or
  a stale number to Central.
- **Semantic versioning.** See Context: the number would claim a compatibility
  discipline the project does not practise yet, and say nothing about age.
- **A Gradle plugin for CalVer.** The logic is forty lines with a test each; a
  dependency for it would be larger than the thing it replaces.

## Consequences

- A release is a tag on the commit that declares it, and nothing else. There is no
  version-bump commit *in* the release, only the one after it.
- **Someone has to bump `gradle.properties` after every release**, or master keeps
  publishing snapshots of a version that is already out — which Maven orders
  *below* the release, so a consumer on the snapshot silently goes backwards. The
  runbook says so; nothing enforces it yet.
- The year in a version is the year of the *line*, not of the tag: `2026.3` tagged
  on 2 January 2027 is still `2026.3`. `CalendarVersion.nextRelease(Year)` starts a
  new year's count only when asked.
- `:assets` and `:weaver` do not apply the conventions and still read the property
  directly, so their unpublished jars are named `2026.1` without a suffix. They are
  build-time tools and never leave the build.
- `BuildVersionTest` reads `gradle.properties`, so a hand-written `-SNAPSHOT` there
  fails build-logic's tests as well as the build.
