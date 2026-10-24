# ADR-0650: The Gradle action stays on its open-source major

- **Status:** Accepted
- **Date:** 2026-10-24

## Context

Every workflow sets up Gradle with `gradle/actions/setup-gradle`, thirteen uses
in all, and none of them passes an input: the action is there for its caching.
The 2026-10-24 dependency plan moved `actions/checkout` to v7 and
`actions/setup-java` to v6, and proposed `setup-gradle` v4 → v6 alongside them.

v4 runs on Node 20, which GitHub is retiring from its runners. v5 (2025-10-01)
changes only that: Node 24, minimum runner v2.327.1, nothing else breaking.

v6 (2026-03-23) moved the caching into `gradle-actions-caching`, a proprietary
component that the MIT licence does not cover. Upgrading to v6 accepts
Gradle's Terms of Use for it, and the component is loaded whenever caching is
on, which is every use here. v6 also dropped the action's rudimentary
configuration-cache support. Nothing here used it.

## Decision

**`setup-gradle` moves to v5, not v6.** The user chose this when the plan was
applied. A licence change belongs to the project owner, not to a version bump.
v5 gets the Node 24 runtime that keeps the workflows running, with the same
caching v4 had, under the same licence.

The other two actions take their newest majors. v7's one behavioural change to
checkout, refusing a fork's head for `pull_request_target` and `workflow_run`,
touches nothing here: no workflow has either trigger. `nightly.yml` only reads
the `workflow_runs` API.

Rejected:

- **v6 now.** It would accept terms nobody had read, and caching was the only
  reason to take it.
- **Staying on v4.** Node 20 is being removed from the runners, and then every
  workflow fails at setup.
- **Dropping the action for `actions/cache` over `~/.gradle`.** It's possible,
  but it means re-deriving the cache keys the action computes, and it is a
  bigger change than a dependency update. It is still an option if v5 stops
  getting fixes.

## Consequences

- A future dependency plan will list `setup-gradle` v6 as available. This
  record is the answer until the user decides otherwise.
- The workflows now use mixed majors on purpose: checkout v7, setup-java v6,
  setup-gradle v5. The artifact, cache and pages actions were outside the plan
  and keep their majors.
