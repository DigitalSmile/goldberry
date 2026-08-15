# ADR-0009: Publish under `io.github.digitalsmile`

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.1, §15

## Context

`docs/ARCHITECTURE.md` originally specified the Maven group `io.github.digitalsmle`
and the base package `io.github.digitalsmle.goldberry`, in four places. The
project owner's GitHub account, and the package in the repository's only Java
file, are `digitalsmile` — with the `i`.

This is worth a record rather than a silent fix, because of what it would have
cost. Maven Central group ids are permanent: `io.github.<user>` coordinates are
verified against GitHub account ownership, so `io.github.digitalsmle` would not
have been claimable at all. Had it survived to a release, the base package would
be embedded in every module name, every `--enable-native-access` flag, every
import in every downstream application, and every published artifact — and Maven
Central does not delete published versions.

## Decision

The Maven group is `io.github.digitalsmile` and the base package is
`io.github.digitalsmile.goldberry`. JPMS module names follow:
`io.github.digitalsmile.goldberry.{core,natives,charts,gpu,gallery}`. Published
artifact names are `goldberry-core`, `goldberry-charts`, `goldberry-gpu`, and
`goldberry-natives-{platform}-{arch}`.

The design document has been corrected in all four places.

## Consequences

- Coordinates match the GitHub account that Maven Central will verify against.
- Fixed before the first commit, so there is nothing to migrate.
- The general lesson is worth keeping: identifiers that end up in published
  coordinates deserve a deliberate check, because they are among the few things
  in a codebase that genuinely cannot be renamed later.
