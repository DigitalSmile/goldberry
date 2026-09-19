# 421. The release line moves on by itself

Date: 2026-09-19

## Status

Accepted. Closes the consequence [ADR-0333](0333-a-version-is-a-year-and-a-count.md)
recorded and left to a runbook.

## Context

ADR-0333 listed this among its own consequences and wrote down that nothing
enforced it:

> **Someone has to bump `gradle.properties` after every release**, or master
> keeps publishing snapshots of a version that is already out — which Maven
> orders *below* the release, so a consumer on the snapshot silently goes
> backwards. The runbook says so; nothing enforces it yet.

`docs/releasing.md` step 5 is the runbook, and it is the fifth of five steps in a
list whose fourth step is "Central takes a few minutes to an hour to sync". The
release is done by then. Everything that made the day feel like release day is
over, and the remaining work is one line in one file.

**What the forgotten step costs is not a red tick.** It is a `2026.1-SNAPSHOT`
published on top of `2026.1`, which resolves for anybody on the snapshot line and
resolves to *older code than the release*, because Maven orders a snapshot below
the version it is a snapshot of. Nothing fails. The consumer gets last week's
toolkit and an explanation is weeks away.

This is the shape of failure the repository already has a word for — a check that
only a person performs is not a check — so the question was only where to put the
enforcement.

## Decision

**A successful release opens the bump as a pull request.** Three parts, and each
boundary is deliberate.

### The arithmetic is a tested value

`VersionBump` in `build-logic` takes the text of `gradle.properties` and a year
and returns the line that follows, the line that was there, and the rewritten
file. It is a record with a static factory and no I/O, so
`VersionBumpTest` states the rules directly.

Two of those rules are decisions rather than mechanics.

**A patch line bumps its patch.** `2026.1.1` lives on a `release/2026.1` branch,
and the next thing that branch can cut is `2026.1.2`. `CalendarVersion` already
had `nextRelease(Year)` and `nextPatch()`, and `nextRelease` had no caller at all
until now; picking between them on `isPatch()` is the whole of it. A branch that
bumped to `2026.2` would have a maintenance branch claiming the next feature
release, which is the one number it must never claim.

**Only the value is rewritten.** The declaration has four comment lines above it
saying why it is never a snapshot, and a `java.util.Properties` round trip drops
every comment in the file. So this is a single-line substitution against a
pattern anchored to the start of a line — which is also what makes a commented-out
copy of the declaration an error rather than something to bump.

### The task is `:core:bumpVersion`

Registered in `goldberry.versioning` beside `printVersion`, and invoked through a
module for the same reason that one is: the plugin is applied per module and the
root project cannot apply it. It rewrites the settings directory's file whichever
module it is asked through, so there is one file and one answer, and its only
output is the line `2026.1 -> 2026.2`, which the workflow parses.

### The job is a pull request, after `publish`

```yaml
  bump:
    needs: publish
    if: github.event_name == 'push'
```

`needs: publish` because a release that went red has nothing to follow.
`github.event_name == 'push'` because a `workflow_dispatch` rehearsal publishes
nothing, and a rehearsal that proposed a version bump would train everybody to
close the pull request without reading it.

**A pull request rather than a push to master.** Master may be protected, and a
workflow that pushed to it anyway would be the one commit in the repository
nobody reviewed — on the file that decides what every artifact is called. It is
also the visible form of the failure this record is about: a bump that did not
happen is now an open pull request, which is a thing somebody notices.

**The guard is one comparison, and it covers two cases.** The job checks out the
default branch and compares what that branch declares against the tag:

```sh
declared=$(sed -n 's/^goldberryVersion=//p' gradle.properties)
if [ "v$declared" = "$TAG" ]; then ...
```

A patch tag is cut from a `release/*` branch whose line master left long ago, so
the comparison fails and nothing is proposed — correctly, because master's line is
already ahead of the patch. A re-run of a tag whose bump has already merged finds
master ahead for the same reason. Neither needed a rule of its own.

The branch is named `bump/<to>`, for the version it moves *to*, so re-running the
job reuses one branch rather than opening a second pull request.

## Consequences

- **`release.yml` needs `contents: write` and `pull-requests: write` on that one
  job.** The workflow's own `permissions:` stays `contents: read`, so the grant is
  as narrow as the thing that needs it.
- **The repository setting "Allow GitHub Actions to create and approve pull
  requests" has to be on**, or `gh pr create` fails with a 403 after the branch has
  already been pushed. It is in `docs/releasing.md`'s setup list now. This is the
  one way the job can fail *after* a release is permanent, and the damage is a
  pushed branch with no pull request on it — recoverable by hand, which is why it
  is a consequence rather than a blocker.
- **The bump is still a human decision, one click later.** The job proposes; a
  person merges. That is deliberate: a release sometimes turns out to need a patch
  before the next feature release, and the version the line moves to is a thing
  worth looking at once.
- **`CalendarVersion.nextRelease` has a caller.** It was written by ADR-0333 and
  used by nothing, which is the state a method is in just before it drifts.
- `ReleaseBumpWorkflowTest` holds the job as text, the way ADR-0082's other drift
  guards do — the `needs:`, the event condition, both permissions, the default
  branch, and the fact that the arithmetic is delegated rather than `sed`-ed. It
  also holds the guard's `sed` pattern against the real `gradle.properties`,
  because that pattern runs before the JDK is set up and so is the one piece that
  could not move into Java.
- **It has never run**, like everything else in `release.yml`. The task half is
  exercised locally and by its tests; the job half is first exercised by the first
  tag, which is the same sentence this workflow's header already carries.
