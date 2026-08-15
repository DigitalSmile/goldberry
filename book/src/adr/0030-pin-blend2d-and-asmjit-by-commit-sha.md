# ADR-0030: Pin Blend2D and AsmJit by commit SHA

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.2, [ADR-0002](0002-cpu-rasterization-with-blend2d.md), [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md), [ADR-0015](0015-licensing-and-third-party-disclosure.md)

## Context

Every upstream in the superbuild is pinned to a git ref, and for four of them
that ref is a release tag. Blend2D and AsmJit have no tags at all — Blend2D has
shipped from `master` for years and has never cut one — so both were pinned to
the branch name. `gradle/libs.versions.toml` said so in a comment, and
`book/src/status.md` has carried it as a release blocker since the log began:
*the build is NOT yet reproducible.*

Two things make now the moment to fix it rather than later.

**M1 binds Blend2D's C API.** Blend2D's objects are `BLObjectCore` unions whose
layout the hand-written bindings will model field by field, checked against the
compiled library by the layout table ([ADR-0010](0010-hand-written-ffm-bindings.md)).
Against a floating branch, an upstream change to one of those layouts arrives
between two builds of the same commit of Goldberry, and the layout table reports
it as *our* bug. The check is only as trustworthy as the thing it checks against.

**Nobody could say what the last artifact was built from.** The local checkout
here was on a Blend2D commit from November and an AsmJit commit from March, and
whether CI had the same pair was unknowable — `master` on the day of the run is
not a fact that survives the run.

There was a second problem hiding behind the first. The refs live in **four**
places: the version catalog, the CMake defaults, and three CI workflows, which
cannot read the catalog because the manylinux container has no JDK
([ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md)). Nothing checked that
they agreed. That is survivable while every copy reads `master`, because all four
are equally wrong; it stops being survivable the moment they carry a SHA.

## Decision

**Both are pinned by commit SHA:**

| | Ref | |
|---|---|---|
| Blend2D | `6dbc2cefbc996379e07104e34519a440b49b15d7` | master @ 2025-11-29 |
| AsmJit | `0bd5787b54b575ed94bf32ac452153b34385c514` | master @ 2026-03-26 |

Both are the current head of upstream `master`, and both are the commits that
have actually built, linked and passed the tests on this machine — not the
newest thing available, but the pair with evidence behind it.

They are pinned **together**. AsmJit is not linked against Blend2D; Blend2D
compiles its sources directly via `add_subdirectory` with `ASMJIT_EMBED`, so the
two refs have to suit each other and nothing upstream publishes which pairs do.
Pinning one of the two would pin neither.

**`GIT_SHALLOW` is removed from both.** CMake's own documentation is explicit:
*"If `GIT_SHALLOW` is enabled then `GIT_TAG` works only with branch names and
tags. A commit hash is not allowed."* It would have appeared to work anyway — a
shallow clone fetches `--depth 1 --no-single-branch`, which contains every branch
*tip*, and these SHAs are the tips of `master` today. The failure would arrive
the day upstream commits anything, on a clean clone, in CI, as `Failed to
checkout tag` with nothing pointing at the cause.

**`:natives:checkPinnedRefs` fails when the four copies disagree**, and runs as
part of `check`. It parses the CMake defaults and each workflow's `env` block and
compares them against the catalog, which is the source of truth. A workflow that
does not mention a ref is not at fault — `macos.yml` has no `XKBCOMMON_REF`
because libxkbcommon is Linux-only.

## Alternatives considered

**Wait for upstream to tag a release.** The cleanest pin is a tag, and Blend2D
would have to cut one. It has not in the project's lifetime, and a plan that
begins with someone else changing their release practice is not a plan.

**Git submodules instead of `FetchContent`.** Submodules pin by SHA natively and
would delete the four-place duplication outright. They also change how the
repository is cloned for everyone, including Java-only contributors who never
build native code at all, and they would make these two upstreams work
differently from the four that are perfectly well served by a tag. The
duplication is real but it is now checked; the clone story is not worth trading
for it.

**Vendor the sources into the repository.** Maximum reproducibility, and it makes
the licence disclosure in [ADR-0015](0015-licensing-and-third-party-disclosure.md)
concrete rather than by reference. It also puts megabytes of someone else's C++
in the history forever and makes every upstream bump a diff nobody can review.

**Pin only Blend2D.** What was asked for, and half a pin: AsmJit is compiled into
Blend2D, so a floating AsmJit leaves the artifact exactly as irreproducible as
before. The two move as one or the pin means nothing.

**Generate the workflow `env` blocks from the catalog.** It would remove the
duplication rather than police it. It needs a generator, a committed-output check
so the generated files cannot go stale, and it still cannot run inside the
container. A check that fails in the same place costs a few lines.

**Keep `GIT_SHALLOW` and accept it.** Saves a full-history clone per build.
Measured, that is 6.6 MB for Blend2D and 11 MB for AsmJit — against a failure
that is delayed, misattributed, and lands on whoever happens to build after
upstream's next commit.

## Consequences

**The build is reproducible for the first time.** Every one of the six upstreams
now resolves to exactly one commit, and a release blocker listed since the log
began is closed. What remains for a publishable artifact is the licence texts in
`licenses/`, which are still placeholders.

**The four copies are now checked rather than merely commented.** The check has
been verified to fail, not just to pass: pointing one workflow back at `master`
produces `macos.yml has BLEND2D_REF=master, catalog says 6dbc2ce…`. It is wired
into `check`, so a Java-only contributor with no native toolchain still runs it.

**Full-history clones cost 17.6 MB across the two.** Once per clean build
directory, and CI caches `_deps`. The clean rebuild that verified this pin —
re-clone plus a full Blend2D and AsmJit compile — took 43 seconds.

**Moving the pins forward is now a deliberate edit in four files.** That is the
point, and it is also the cost: a security fix upstream no longer arrives by
rebuilding. Nothing watches these repositories for us, and nothing here changes
that.

**The pins are a snapshot of `master`, not a blessed release.** Nobody upstream
has said this Blend2D commit is stable, only that it is what `master` was on a
Saturday in November. That is strictly better than "whatever `master` is when you
happen to build", which is what it replaces, and strictly worse than a tag — which
is why this record exists rather than the problem simply being closed.
